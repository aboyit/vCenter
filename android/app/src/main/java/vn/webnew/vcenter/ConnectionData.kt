package vn.webnew.vcenter

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.IDN
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class Profile(
    val id: String = UUID.randomUUID().toString(), val name: String = "", val host: String = "",
    val port: String = "443", val user: String = "", val npmEnabled: Boolean = false,
    val npmUser: String = "", val remember: Boolean = false
)

internal data class Credentials(val vcenterPassword: String, val npmPassword: String)

internal object Endpoint {
    fun url(host: String, port: String): HttpUrl {
        val raw = host.trim()
        require(raw.isNotBlank() && !raw.contains(Regex("[\\s/@?#\\\\]"))) { "Host không hợp lệ." }
        val normalized = try { if (raw.startsWith('[') && raw.endsWith(']')) raw else IDN.toASCII(raw) }
            catch (_: IllegalArgumentException) { throw IllegalArgumentException("Host không hợp lệ.") }
        require(normalized.length <= 253 && (normalized.startsWith('[') && normalized.endsWith(']') ||
            normalized.matches(Regex("[A-Za-z0-9.-]+")) &&
            normalized.split('.').all { it.isNotEmpty() && it.length <= 63 && it.first().isLetterOrDigit() && it.last().isLetterOrDigit() })) { "Host không hợp lệ." }
        val parsedPort = port.toIntOrNull()
        require(parsedPort != null && parsedPort in 1..65535 && port == parsedPort.toString()) { "Port phải từ 1 đến 65535." }
        val url = try { "https://$normalized:$parsedPort/sdk".toHttpUrl() }
            catch (_: IllegalArgumentException) { throw IllegalArgumentException("Host không hợp lệ.") }
        require(url.host.isNotBlank() && url.encodedPath == "/sdk" && url.username.isEmpty() && url.password.isEmpty() &&
            !url.host.contains('%')) { "Host không hợp lệ." }
        return url
    }
}

internal class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    val activeId: String? get() = prefs.getString("active", null)

    fun all(): List<Profile> = try {
        val json = JSONArray(prefs.getString("items", "[]"))
        (0 until json.length()).map { index ->
            val p = json.getJSONObject(index)
            Profile(p.getString("id"), p.getString("name"), p.getString("host"),
                p.getString("port"), p.getString("user"), p.optBoolean("npmEnabled"),
                p.optString("npmUser"), p.optBoolean("remember"))
        }
    } catch (_: Exception) { emptyList() }

    fun save(profile: Profile) {
        val entries = all().filterNot { it.id == profile.id } + profile
        prefs.edit().putString("items", JSONArray().apply { entries.forEach { p ->
            put(JSONObject().put("id", p.id).put("name", p.name).put("host", p.host)
                .put("port", p.port).put("user", p.user).put("npmEnabled", p.npmEnabled)
                .put("npmUser", p.npmUser).put("remember", p.remember))
        } }.toString()).putString("active", profile.id).apply()
        if (!profile.remember) forgetSecrets(profile.id)
    }

    fun select(id: String) { prefs.edit().putString("active", id).apply() }

    fun delete(id: String) {
        val remaining = all().filterNot { it.id == id }
        forgetSecrets(id)
        prefs.edit().putString("items", JSONArray().apply { remaining.forEach { p ->
            put(JSONObject().put("id", p.id).put("name", p.name).put("host", p.host)
                .put("port", p.port).put("user", p.user).put("npmEnabled", p.npmEnabled)
                .put("npmUser", p.npmUser).put("remember", p.remember))
        } }.toString()).remove("active").apply()
    }

    private fun cipher(mode: Int, iv: ByteArray? = null): Cipher {
        val alias = "vcenter-profile-secrets"
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            }.generateKey()
        }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            val key = ks.getKey(alias, null)
            if (iv == null) init(mode, key) else init(mode, key, GCMParameterSpec(128, iv))
        }
    }

    fun remember(id: String, credentials: Credentials) {
        val c = cipher(Cipher.ENCRYPT_MODE)
        val bytes = JSONObject().put("v", credentials.vcenterPassword).put("n", credentials.npmPassword)
            .toString().toByteArray(Charsets.UTF_8)
        prefs.edit().putString("secret-$id", Base64.encodeToString(c.iv + c.doFinal(bytes), Base64.NO_WRAP)).apply()
    }

    fun credentials(id: String): Credentials? = try {
        val encoded = prefs.getString("secret-$id", null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val data = JSONObject(String(cipher(Cipher.DECRYPT_MODE, bytes.copyOfRange(0, 12))
            .doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        Credentials(data.getString("v"), data.getString("n"))
    } catch (_: Exception) { null }

    fun forgetSecrets(id: String) { prefs.edit().remove("secret-$id").apply() }
}

internal class VsphereSession(private val profile: Profile, private val credentials: Credentials) {
    private val url = Endpoint.url(profile.host, profile.port)
    private val cookies = mutableListOf<Cookie>()
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(requestUrl: HttpUrl, newCookies: List<Cookie>) {
                if (requestUrl.host == url.host && requestUrl.port == url.port && requestUrl.scheme == "https") {
                    synchronized(cookies) {
                        cookies.removeAll { old -> newCookies.any { it.name == old.name } }
                        cookies.addAll(newCookies.filter { it.matches(url) })
                    }
                }
            }
            override fun loadForRequest(requestUrl: HttpUrl): List<Cookie> = synchronized(cookies) {
                if (requestUrl.host == url.host && requestUrl.port == url.port && requestUrl.scheme == "https")
                    cookies.filter { it.matches(requestUrl) && it.expiresAt > System.currentTimeMillis() }
                else emptyList()
            }
        }).build()
    private var manager: String? = null
    private var service: ServiceContent? = null
    private var loggedIn = false
    private var performanceCounters: Pair<Int, Int>? = null

    internal fun requestFor(xml: String): Request {
        val req = Request.Builder().url(url).post(xml.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .header("SOAPAction", "\"urn:vim25/8.0.0.0\"")
        if (profile.npmEnabled) {
            req.header("Authorization", okhttp3.Credentials.basic(profile.npmUser, credentials.npmPassword, Charsets.UTF_8))
        }
        return req.build()
    }

    internal fun cookieJar(): CookieJar = client.cookieJar

    private fun call(xml: String, stage: String): String {
        client.newCall(requestFor(xml)).execute().use { response ->
            if (response.code in 300..399) throw SoapFailure("Proxy chuyển hướng; kết nối bị từ chối để bảo vệ tài khoản.")
            if (response.code == 401 || response.code == 403) {
                throw SoapFailure(HttpAccessFailure.describe(response.code, stage,
                    response.header("WWW-Authenticate"), profile.npmEnabled))
            }
            if (response.code == 408 || response.code == 504) throw SoapFailure("Server hết thời gian phản hồi.")
            if (response.code !in 200..299 && response.code != 500) throw SoapFailure("Lỗi HTTP ${response.code} từ server.")
            val body = response.body ?: throw SoapFailure("Server trả về phản hồi rỗng.")
            if (body.contentLength() > 4_194_304) throw SoapFailure("Phản hồi SOAP quá lớn.")
            val source = body.source()
            source.request(4_194_305L)
            if (source.buffer.size > 4_194_304) throw SoapFailure("Phản hồi SOAP quá lớn.")
            return source.buffer.readUtf8()
        }
    }

    suspend fun connect(): ServerInfo = withContext(Dispatchers.IO) {
        try {
            val content = VsphereSoap.serviceContent(call(VsphereSoap.retrieve(), "RetrieveServiceContent"))
            manager = content.manager
            VsphereSoap.loginResult(call(VsphereSoap.login(content.manager, profile.user, credentials.vcenterPassword), "Login"))
            loggedIn = true
            service = content
            content.info
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    suspend fun inventory(): InventorySnapshot = withContext(Dispatchers.IO) {
        val context = service?.takeIf { loggedIn } ?: throw SoapFailure("Chưa đăng nhập vCenter.")
        var view: String? = null
        var token: String? = null
        try {
            view = InventorySoap.viewRef(call(InventorySoap.createView(context.viewManager, context.rootFolder), "CreateContainerView"))
            val all = LinkedHashMap<String, InventoryItem>()
            var page = InventorySoap.page(call(InventorySoap.retrieve(context.collector, view), "RetrievePropertiesEx"), "RetrievePropertiesEx")
            var pages = 0
            while (true) {
                token = page.token
                coroutineContext.ensureActive()
                pages++
                if (pages > 100 || all.size + page.items.size > 10_000) throw SoapFailure("Inventory quá lớn; đã giới hạn 10.000 đối tượng / 100 trang.")
                page.items.forEach { all["${it.type}:${it.ref}"] = it }
                token = page.token ?: break
                val previousToken = token
                token = null
                page = InventorySoap.page(call(InventorySoap.continuePage(context.collector, previousToken), "ContinueRetrievePropertiesEx"), "ContinueRetrievePropertiesEx")
                token = page.token
            }
            coroutineContext.ensureActive()
            InventorySnapshot(all.values.toList())
        } finally {
            token?.let { try { VsphereSoap.result(call(InventorySoap.cancel(context.collector, it), "CancelRetrievePropertiesEx"), "CancelRetrievePropertiesEx") } catch (_: Exception) { } }
            view?.let { try { VsphereSoap.result(call(InventorySoap.destroyView(it), "DestroyView"), "DestroyView") } catch (_: Exception) { } }
        }
    }

    suspend fun monitoring(): MonitoringSnapshot = withContext(Dispatchers.IO) {
        val context = service?.takeIf { loggedIn } ?: throw SoapFailure("Chưa đăng nhập vCenter.")
        var alarms = emptyList<MonitoringAlarm>()
        var events = emptyList<MonitoringEvent>()
        var alarmError: String? = null
        var eventError: String? = null
        var alarmLimit = false
        try {
            val page = MonitoringSoap.parseAlarms(call(MonitoringSoap.alarms(context.collector, context.rootFolder), "RetrievePropertiesEx"))
            alarms = page.alarms
            alarmLimit = page.limitReached
            if (page.alarmRefs.isNotEmpty()) {
                coroutineContext.ensureActive()
                val names = MonitoringSoap.parseAlarmNames(call(MonitoringSoap.alarmNames(context.collector, page.alarmRefs), "RetrievePropertiesEx"))
                alarms = alarms.map { it.copy(name = names[it.alarmRef]) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            if (e is SoapFailure && e.expired) throw e
            alarmError = if (e is SoapFailure) e.message else "Không đọc được cảnh báo từ vCenter."
        }
        coroutineContext.ensureActive()
        try {
            val eventManager = context.eventManager ?: throw SoapFailure("vCenter không trả về EventManager.")
            val now = PerformanceSoap.serverTime(call(PerformanceSoap.currentTime(), "CurrentTime"))
            events = MonitoringSoap.parseEvents(call(MonitoringSoap.events(eventManager, now), "QueryEvents"))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            if (e is SoapFailure && e.expired) throw e
            eventError = if (e is SoapFailure) e.message else "Không đọc được sự kiện từ vCenter."
        }
        coroutineContext.ensureActive()
        MonitoringSnapshot(alarms, events, alarmError, eventError, alarmLimit, events.size >= MonitoringSoap.MAX_EVENTS)
    }

    suspend fun performance(hosts: List<InventoryItem>): PerformanceSnapshot = withContext(Dispatchers.IO) {
        val context = service?.takeIf { loggedIn } ?: throw SoapFailure("Chưa đăng nhập vCenter.")
        val manager = context.performanceManager ?: throw SoapFailure("vCenter không trả về PerformanceManager.")
        if (hosts.size > 200) throw SoapFailure("Hiệu năng hiện giới hạn 200 ESXi mỗi lần tải.")
        val end = PerformanceSoap.serverTime(call(PerformanceSoap.currentTime(), "CurrentTime"))
        val start = end.minusSeconds(3600)
        val ids = performanceCounters ?: PerformanceSoap.counters(
            call(PerformanceSoap.metadata(context.collector, manager), "RetrievePropertiesEx")).also { performanceCounters = it }
        val results = mutableListOf<HostPerformance>()
        for (batch in hosts.chunked(20)) {
            coroutineContext.ensureActive()
            results += PerformanceSoap.parse(call(PerformanceSoap.query(manager, batch.map { it.ref }, ids, start, end), "QueryPerf"), ids)
        }
        coroutineContext.ensureActive()
        PerformanceSnapshot(start.toEpochMilli(), end.toEpochMilli(), results)
    }

    suspend fun executeOperation(operation: VmOperation): String? = withContext(Dispatchers.IO) {
        service?.takeIf { loggedIn } ?: throw SoapFailure("Chưa đăng nhập vCenter.")
        val method = VmOperationSoap.method(operation.action)
        VmOperationSoap.result(call(VmOperationSoap.request(operation), method), operation.action)
    }

    suspend fun vmConfig(vmRef: String): VmConfig = withContext(Dispatchers.IO) {
        val context = service?.takeIf { loggedIn } ?: throw SoapFailure("Chưa đăng nhập vCenter.")
        VmAdvancedSoap.parse(call(VmAdvancedSoap.read(context.collector, vmRef), "RetrievePropertiesEx"), vmRef)
    }

    suspend fun executeAdvanced(operation: AdvancedOperation, inventory: InventorySnapshot): String = withContext(Dispatchers.IO) {
        service?.takeIf { loggedIn } ?: throw SoapFailure("Chưa đăng nhập vCenter.")
        val method = VmAdvancedSoap.method(operation.command)
        VmAdvancedSoap.taskRef(call(VmAdvancedSoap.request(operation, inventory), method), method)
    }

    suspend fun readTasks(refs: List<String>): List<VsphereTask> = withContext(Dispatchers.IO) {
        val context = service?.takeIf { loggedIn } ?: throw SoapFailure("Chưa đăng nhập vCenter.")
        if (refs.isEmpty()) return@withContext emptyList()
        TaskSoap.parseTasks(call(TaskSoap.readTasks(context.collector, refs), "RetrievePropertiesEx"), refs)
    }

    suspend fun taskHistory(): List<VsphereTask> = withContext(Dispatchers.IO) {
        val context = service?.takeIf { loggedIn } ?: throw SoapFailure("Chưa đăng nhập vCenter.")
        val manager = context.taskManager ?: throw SoapFailure("vCenter không trả về TaskManager.")
        var historyCollector: String? = null
        try {
            val now = PerformanceSoap.serverTime(call(PerformanceSoap.currentTime(), "CurrentTime"))
            historyCollector = TaskSoap.collectorRef(call(
                TaskSoap.createHistoryCollector(manager, context.rootFolder, now), "CreateCollectorForTasks"))
            coroutineContext.ensureActive()
            TaskSoap.parseHistory(call(TaskSoap.readNext(historyCollector), "ReadNextTasks"))
        } finally {
            historyCollector?.let {
                try { VsphereSoap.result(call(TaskSoap.destroyCollector(it), "DestroyCollector"), "DestroyCollector") }
                catch (_: Exception) { }
            }
        }
    }

    suspend fun cancelTask(ref: String) = withContext(Dispatchers.IO) {
        service?.takeIf { loggedIn } ?: throw SoapFailure("Chưa đăng nhập vCenter.")
        require(ref.isNotBlank()) { "Task reference is required." }
        VsphereSoap.result(call(TaskSoap.cancelTask(ref), "CancelTask"), "CancelTask")
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try { if (loggedIn) manager?.let { VsphereSoap.result(call(VsphereSoap.logout(it), "Logout"), "Logout") } }
        finally { close() }
    }

    fun close() {
        loggedIn = false
        performanceCounters = null
        manager = null
        service = null
        synchronized(cookies) { cookies.clear() }
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }
}
