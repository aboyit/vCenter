package vn.webnew.vcenter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.net.ssl.SSLException
import java.net.UnknownHostException
import java.net.SocketTimeoutException

internal data class ConnectionState(
    val profiles: List<Profile> = emptyList(), val active: Profile? = null,
    val credentials: Credentials = Credentials("", ""), val info: ServerInfo? = null,
    val busy: Boolean = false, val error: String? = null,
    val inventory: InventorySnapshot? = null, val inventoryLoading: Boolean = false,
    val inventoryError: String? = null,
    val performance: PerformanceSnapshot? = null, val performanceLoading: Boolean = false,
    val performanceError: String? = null,
    val monitoring: MonitoringSnapshot? = null, val monitoringLoading: Boolean = false,
    val monitoringError: String? = null,
    val tasks: List<VsphereTask> = emptyList(), val tasksLoading: Boolean = false,
    val tasksError: String? = null, val tasksFetchedAt: Long? = null,
    val pendingOperations: List<PendingOperation> = emptyList(),
    val operationBusy: Boolean = false, val operationError: String? = null,
    val vmConfig: VmConfig? = null, val vmConfigLoading: Boolean = false,
    val vmConfigError: String? = null, val advancedVerification: String? = null,
    val advancedPrepared: AdvancedOperation? = null, val advancedPrecheckMessage: String? = null
)

internal class ConnectionViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore(app)
    private val journal = OperationJournal(app)
    private var tasksJob: Job? = null
    private var operationJob: Job? = null
    private var configJob: Job? = null

    private fun activeOperationScope(): String? {
        val state = _state.value
        return state.active?.let { profile -> state.info?.let { operationScope(profile, it) } }
    }

    private fun loadOperations() {
        val scope = activeOperationScope() ?: return
        try {
            val operations = journal.all().filter { it.scope == scope }
            _state.value = _state.value.copy(pendingOperations = operations,
                operationError = if (operations.none { it.status !in OperationPolicy.terminal }) null else _state.value.operationError)
        } catch (_: Exception) {
            _state.value = _state.value.copy(operationError = "Không đọc được sổ lệnh; khóa thao tác để tránh gửi trùng.")
        }
    }

    fun reviewOperation(id: String) {
        val operation = _state.value.pendingOperations.firstOrNull { it.id == id } ?: return
        if (operation.status !in setOf("uncertain", "sent", "unavailable") || _state.value.operationBusy) return
        try {
            journal.put(operation.copy(status = "reviewed"))
            _state.value = _state.value.copy(operationError = null)
            loadOperations()
        } catch (_: Exception) {
            _state.value = _state.value.copy(operationError = "Không lưu được kết quả đối soát; chưa mở khóa lệnh.")
        }
    }
    private val _state = MutableStateFlow(ConnectionState())
    val state = _state.asStateFlow()
    private var session: VsphereSession? = null
    private var job: Job? = null
    private var inventoryJob: Job? = null
    private var performanceJob: Job? = null
    private var monitoringJob: Job? = null
    private var generation = 0
    private var savedProfile: Profile? = null
    private var rememberedCredentials: Credentials? = null

    init { refresh() }

    private fun refresh(id: String? = store.activeId) {
        val all = store.all()
        val active = all.firstOrNull { it.id == id }
        savedProfile = active
        rememberedCredentials = active?.takeIf { it.remember }?.let { store.credentials(it.id) }
        _state.value = ConnectionState(profiles = all, active = active,
            credentials = rememberedCredentials ?: Credentials("", ""))
    }

    fun edit(profile: Profile, credentials: Credentials) {
        val previous = _state.value.active
        if (profile.id != previous?.id || profile.host != previous.host || profile.port != previous.port ||
            profile.user != previous.user || profile.npmEnabled != previous.npmEnabled ||
            profile.npmUser != previous.npmUser || credentials != _state.value.credentials) invalidate()
        if (savedProfile?.id == profile.id && savedProfile != profile &&
            (savedProfile?.host != profile.host || savedProfile?.port != profile.port ||
                savedProfile?.user != profile.user || savedProfile?.npmEnabled != profile.npmEnabled ||
                savedProfile?.npmUser != profile.npmUser || savedProfile?.remember != profile.remember)) {
            store.forgetSecrets(profile.id)
            rememberedCredentials = null
        }
        if (rememberedCredentials != null && credentials != rememberedCredentials) {
            store.forgetSecrets(profile.id)
            rememberedCredentials = null
        }
        _state.value = _state.value.copy(active = profile, credentials = credentials, error = null)
    }

    fun newProfile() { invalidate(); savedProfile = null; rememberedCredentials = null; _state.value = _state.value.copy(active = Profile(), credentials = Credentials("", ""), error = null) }

    fun select(id: String) {
        if (id == _state.value.active?.id) return
        invalidate()
        store.select(id)
        refresh(id)
    }

    fun delete() {
        val id = _state.value.active?.id
        invalidate()
        id?.let { journal.removeScope(it); store.delete(it) }
        refresh(null)
    }

    fun save() {
        val p = _state.value.active ?: Profile()
        val error = validate(p)
        if (error != null) { _state.value = _state.value.copy(error = error); return }
        try {
            store.save(p)
            if (p.remember) store.remember(p.id, _state.value.credentials) else store.forgetSecrets(p.id)
            savedProfile = p
            rememberedCredentials = if (p.remember) _state.value.credentials else null
            _state.value = _state.value.copy(profiles = store.all(), error = null)
        } catch (_: Exception) { _state.value = _state.value.copy(error = "Không thể lưu mật khẩu an toàn trên thiết bị.") }
    }

    private fun validate(p: Profile): String? = try {
        require(p.name.isNotBlank() && p.user.isNotBlank()) { "Nhập tên hồ sơ và tài khoản vCenter." }
        require(!p.npmEnabled || p.npmUser.isNotBlank()) { "Nhập tài khoản HTTP Auth NPM." }
        Endpoint.url(p.host, p.port)
        null
    } catch (e: IllegalArgumentException) { e.message ?: "Thông tin kết nối không hợp lệ." }

    fun connect() {
        val p = _state.value.active ?: Profile()
        val creds = _state.value.credentials
        val error = validate(p) ?: when {
            creds.vcenterPassword.isEmpty() -> "Nhập mật khẩu vCenter."
            p.npmEnabled && creds.npmPassword.isEmpty() -> "Nhập mật khẩu HTTP Auth NPM."
            else -> null
        }
        if (error != null) { _state.value = _state.value.copy(error = error); return }
        if (_state.value.busy) return
        invalidate()
        try {
            store.save(p)
            if (p.remember) store.remember(p.id, creds)
            savedProfile = p
            rememberedCredentials = if (p.remember) creds else null
            _state.value = _state.value.copy(profiles = store.all(), busy = true, error = null)
        } catch (_: Exception) {
            _state.value = _state.value.copy(error = "Không thể lưu mật khẩu an toàn trên thiết bị.")
            return
        }
        val attempt = generation
        job = viewModelScope.launch {
            val candidate = VsphereSession(p, creds)
            try {
                val info = candidate.connect()
                if (generation == attempt) {
                    session = candidate
                    _state.value = _state.value.copy(info = info, busy = false, error = null)
                    loadOperations()
                    refreshInventory()
                    refreshTasks()
                } else candidate.close()
            } catch (e: CancellationException) { candidate.close(); throw e }
            catch (e: Exception) {
                candidate.close()
                if (generation == attempt) _state.value = _state.value.copy(busy = false, info = null,
                    error = when (e) {
                        is SoapFailure -> e.message ?: "Kết nối SOAP thất bại."
                        is SSLException -> "Chứng chỉ TLS không hợp lệ. Chỉ hỗ trợ CA hệ thống Android; chưa hỗ trợ nhập CA riêng."
                        is UnknownHostException -> "Không tìm thấy host (DNS)."
                        is SocketTimeoutException -> "Kết nối hết thời gian chờ."
                        is IOException -> "Không thể kết nối mạng tới server."
                        else -> "Không thể kết nối; kiểm tra host, port và kết nối mạng."
                    })
            }
        }
    }

    fun refreshInventory() {
        val active = session ?: return
        performanceJob?.cancel()
        inventoryJob?.cancel()
        val attempt = generation
        _state.value = _state.value.copy(inventoryLoading = true, inventoryError = null,
            performanceLoading = false, performanceError = null)
        inventoryJob = viewModelScope.launch {
            try {
                val snapshot = active.inventory()
                if (generation == attempt && session === active) {
                    _state.value = _state.value.copy(inventory = snapshot, inventoryLoading = false, inventoryError = null)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == attempt && session === active) {
                    if (e is SoapFailure && e.expired) {
                        invalidate()
                        _state.value = _state.value.copy(error = e.message)
                    } else _state.value = _state.value.copy(inventoryLoading = false, inventoryError = when (e) {
                        is SoapFailure -> e.message ?: "Không đọc được inventory từ vCenter."
                        is SSLException -> "Chứng chỉ TLS không hợp lệ."
                        is SocketTimeoutException -> "Inventory hết thời gian chờ."
                        is IOException -> "Mất kết nối tới vCenter."
                        else -> "Không đọc được inventory từ vCenter."
                    })
                }
            }
        }
    }

    fun refreshPerformanceIfIdle() {
        if (session == null || _state.value.inventory == null || inventoryJob?.isActive == true) return
        refreshInventory()
    }

    fun stopPerformanceRefresh() {
        performanceJob?.cancel()
        inventoryJob?.cancel()
        _state.value = _state.value.copy(performanceLoading = false, inventoryLoading = false)
    }

    private fun refreshPerformance(active: VsphereSession, inventory: InventorySnapshot, attempt: Int) {
        performanceJob?.cancel()
        _state.value = _state.value.copy(performanceLoading = true, performanceError = null)
        performanceJob = viewModelScope.launch {
            try {
                val result = active.performance(inventory.hosts)
                if (generation == attempt && session === active) _state.value = _state.value.copy(
                    performance = result, performanceLoading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == attempt && session === active) {
                    if (e is SoapFailure && e.expired) {
                        invalidate()
                        _state.value = _state.value.copy(error = e.message)
                    } else _state.value = _state.value.copy(performanceLoading = false, performanceError =
                        if (e is SoapFailure) e.message else "Không tải được lịch sử CPU/RAM ESXi; kiểm tra mạng và quyền Performance.")
                }
            }
        }
    }

    fun refreshMonitoring() {
        val active = session ?: return
        if (monitoringJob?.isActive == true) return
        val attempt = generation
        _state.value = _state.value.copy(monitoringLoading = true, monitoringError = null)
        monitoringJob = viewModelScope.launch {
            try {
                val result = active.monitoring()
                if (generation == attempt && session === active) _state.value = _state.value.copy(
                    monitoring = result, monitoringLoading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == attempt && session === active) {
                    if (e is SoapFailure && e.expired) {
                        invalidate()
                        _state.value = _state.value.copy(error = e.message)
                    } else _state.value = _state.value.copy(monitoringLoading = false,
                        monitoringError = if (e is SoapFailure) e.message else "Không đọc được alarm/event; kiểm tra kết nối và quyền.")
                }
            }
        }
    }

    fun refreshTasks() {
        val active = session ?: return
        if (tasksJob?.isActive == true || operationJob?.isActive == true) return
        val attempt = generation
        val scope = activeOperationScope() ?: return
        _state.value = _state.value.copy(tasksLoading = true, tasksError = null)
        tasksJob = viewModelScope.launch {
            try {
                val tracked = journal.all().filter { it.scope == scope && it.taskRef != null && it.status !in OperationPolicy.terminal }
                val refs = tracked.mapNotNull { it.taskRef }.distinct()
                val current = refs.chunked(100).flatMap { active.readTasks(it) }
                var historyError: String? = null
                val history = try { active.taskHistory() } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (e is SoapFailure && e.expired) throw e
                    historyError = if (e is SoapFailure) e.message else "Không đọc được lịch sử task; vẫn đối soát task đã lưu."
                    _state.value.tasks.filterNot { old -> current.any { it.ref == old.ref } }
                }
                if (generation != attempt || session !== active) return@launch
                tracked.forEach { operation ->
                    val task = current.firstOrNull { it.ref == operation.taskRef }
                    journal.put(operation.copy(status = OperationPolicy.reconciledStatus(task?.state)))
                    if (task?.state == "success" && operation.action in setOf("RECONFIGURE_VM", "CLONE_VM", "MIGRATE_VM")) {
                        val target = if (operation.action == "CLONE_VM") task.resultRef
                            ?.takeIf { task.resultType == "VirtualMachine" } else operation.vmRef
                        val verified = if (target == null) null else try { active.vmConfig(target) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { null }
                        if (generation != attempt || session !== active) return@launch
                        _state.value = _state.value.copy(advancedVerification = if (verified == null)
                            "Task ${task.ref} thành công; chưa đọc lại được VM kết quả từ server."
                            else "Task ${task.ref} thành công; đã đọc lại cấu hình VM ${verified.vmRef} từ server (changeVersion ${verified.changeVersion}).")
                    }
                }
                val previous = _state.value.pendingOperations.associateBy { it.id }
                loadOperations()
                _state.value = _state.value.copy(tasks = (current + history).distinctBy { it.ref },
                    tasksLoading = false, tasksError = historyError, tasksFetchedAt = System.currentTimeMillis())
                if (_state.value.pendingOperations.any { it.status in setOf("success", "error") && previous[it.id]?.status != it.status }) refreshInventory()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == attempt && session === active) {
                    if (e is SoapFailure && e.expired) { invalidate(); _state.value = _state.value.copy(error = e.message) }
                    else _state.value = _state.value.copy(tasksLoading = false,
                        tasksError = if (e is SoapFailure) e.message else "Không đọc được task; dữ liệu cũ giữ nguyên. Không suy ra thành công.")
                }
            }
        }
    }

    fun loadVmConfig(vmRef: String) {
        val active = session ?: return
        configJob?.cancel()
        val attempt = generation
        _state.value = _state.value.copy(vmConfig = null, vmConfigLoading = true, vmConfigError = null,
            advancedPrepared = null, advancedPrecheckMessage = null)
        configJob = viewModelScope.launch {
            try {
                val config = active.vmConfig(vmRef)
                if (generation == attempt && session === active) _state.value = _state.value.copy(
                    vmConfig = config, vmConfigLoading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == attempt && session === active) {
                    if (e is SoapFailure && e.expired) { invalidate(); _state.value = _state.value.copy(error = e.message) }
                    else _state.value = _state.value.copy(vmConfigLoading = false,
                        vmConfigError = if (e is SoapFailure) e.message else "Không đọc được cấu hình; chưa cho phép sửa.")
                }
            }
        }
    }

    fun executeAdvanced(operation: AdvancedOperation) {
        val active = session ?: return
        val scope = activeOperationScope() ?: return
        if (_state.value.operationBusy || _state.value.operationError != null) return
        val inventory = _state.value.inventory ?: return
        val reason = VmAdvancedSoap.validate(operation.baseline, operation.command, inventory)
        if (reason != null || operation.baseline.vmRef != operation.vmRef) {
            _state.value = _state.value.copy(vmConfigError = reason ?: "VM không khớp cấu hình đã đọc.")
            return
        }
        val attempt = generation
        _state.value = _state.value.copy(operationBusy = true, vmConfigError = null)
        operationJob = viewModelScope.launch {
            var pending: PendingOperation? = null
            try {
                if (journal.all().any { OperationPolicy.blocks(it, scope, operation.vmRef) })
                    throw SoapFailure("VM còn lệnh chưa đối soát; kiểm tra tab Task.")
                val freshInventory = active.inventory()
                val fresh = active.vmConfig(operation.vmRef)
                AdvancedExecutionPolicy.conflict(operation.baseline, fresh)?.let { throw SoapFailure(it) }
                VmAdvancedSoap.validate(fresh, operation.command, freshInventory)?.let { throw SoapFailure(it) }
                coroutineContext.ensureActive()
                if (generation != attempt || session !== active) return@launch
                val commandEntry = PendingOperation(java.util.UUID.randomUUID().toString(), scope, operation.vmRef,
                    operation.action, System.currentTimeMillis())
                journal.put(commandEntry)
                pending = commandEntry
                loadOperations()
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    val ref = active.executeAdvanced(operation, freshInventory)
                    journal.put(commandEntry.copy(taskRef = ref, status = "queued"))
                }
                if (generation == attempt && session === active) {
                    loadOperations()
                    _state.value = _state.value.copy(operationBusy = false, vmConfig = null)
                    refreshTasks()
                    refreshInventory()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == attempt && session === active) {
                    loadOperations()
                    val message = if (pending == null) "Chưa gửi lệnh. " else
                        "Chưa xác định kết quả; không tự gửi lại. Kiểm tra Task / vSphere Client. "
                    _state.value = _state.value.copy(operationBusy = false,
                        vmConfigError = message + (if (e is SoapFailure) e.message else "Kiểm tra mạng và sổ lệnh."),
                        operationError = if (pending != null) message else _state.value.operationError)
                    if (e is SoapFailure && e.expired) { invalidate(); _state.value = _state.value.copy(error = e.message) }
                }
            }
        }
    }

    fun executeOperation(operation: VmOperation) {
        val active = session ?: return
        val scope = activeOperationScope() ?: return
        if (_state.value.operationBusy || _state.value.operationError != null) return
        val vm = _state.value.inventory?.vms?.firstOrNull { it.ref == operation.vmRef } ?: return
        val reason = vmOperationBlockReason(vm, operation)
        if (reason != null) { _state.value = _state.value.copy(operationError = reason); return }
        val pending = PendingOperation(java.util.UUID.randomUUID().toString(), scope, operation.vmRef,
            operation.action.name, System.currentTimeMillis())
        try {
            if (journal.all().any { OperationPolicy.blocks(it, scope, operation.vmRef) }) {
                _state.value = _state.value.copy(operationError = "VM còn lệnh chưa đối soát; kiểm tra tab Task trước.")
                return
            }
            // Persist before dispatch: a process death must never make an uncertain command retryable.
            journal.put(pending)
            loadOperations()
        } catch (_: Exception) {
            _state.value = _state.value.copy(operationError = "Không lưu được sổ lệnh; chưa gửi lệnh lên server.")
            return
        }
        val attempt = generation
        _state.value = _state.value.copy(operationBusy = true, operationError = null)
        operationJob = viewModelScope.launch {
            try {
                val fresh = active.inventory().vms.firstOrNull { it.ref == operation.vmRef }
                if (fresh == null) {
                    journal.put(pending.copy(status = "rejected"))
                    throw SoapFailure("Không đọc được VM hiện tại; chưa gửi lệnh.")
                }
                val preflight = vmOperationBlockReason(fresh, operation)
                if (preflight != null) {
                    journal.put(pending.copy(status = "rejected"))
                    throw SoapFailure(preflight)
                }
                coroutineContext.ensureActive()
                if (generation != attempt || session !== active) return@launch
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    val ref = active.executeOperation(operation)
                    journal.put(pending.copy(taskRef = ref, status = if (ref == null) "sent" else "queued"))
                }
                if (generation == attempt && session === active) {
                    loadOperations()
                    _state.value = _state.value.copy(operationBusy = false)
                    refreshInventory()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == attempt && session === active) {
                    loadOperations()
                    _state.value = _state.value.copy(operationBusy = false,
                        operationError = "Chưa xác định kết quả lệnh. Không tự gửi lại; kiểm tra Task / vSphere Client." +
                            if (e is SoapFailure) " ${e.message}" else "")
                    if (e is SoapFailure && e.expired) { invalidate(); _state.value = _state.value.copy(error = e.message) }
                }
            }
        }
    }

    fun cancelTask(ref: String) {
        val active = session ?: return
        if (_state.value.operationBusy) return
        val task = _state.value.tasks.firstOrNull { it.ref == ref } ?: return
        if (task.cancelable != true || task.state !in setOf("queued", "running") || task.cancelled == true) return
        val attempt = generation
        _state.value = _state.value.copy(operationBusy = true, operationError = null)
        operationJob = viewModelScope.launch {
            try {
                val fresh = active.readTasks(listOf(ref)).singleOrNull()
                if (fresh?.cancelable != true || fresh.state !in setOf("queued", "running") || fresh.cancelled == true) {
                    throw SoapFailure("Task không còn cho phép hủy.")
                }
                active.cancelTask(ref)
                val updated = active.readTasks(listOf(ref))
                if (generation == attempt && session === active) _state.value = _state.value.copy(
                    tasks = updated + _state.value.tasks.filterNot { it.ref == ref }, operationBusy = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == attempt && session === active) _state.value = _state.value.copy(operationBusy = false,
                    operationError = if (e is SoapFailure) e.message else "Chưa xác nhận được yêu cầu hủy; đọc lại task trước khi gửi lệnh khác.")
            }
        }
    }

    fun stopMonitoringRefresh() {
        monitoringJob?.cancel()
        _state.value = _state.value.copy(monitoringLoading = false)
    }

    private fun stopOperations() {
        configJob?.cancel()
        configJob = null
        _state.value = _state.value.copy(vmConfig = null, vmConfigLoading = false, vmConfigError = null,
            advancedVerification = null, advancedPrepared = null, advancedPrecheckMessage = null)
        tasksJob?.cancel()
        tasksJob = null
        operationJob?.cancel()
        operationJob = null
        _state.value = _state.value.copy(tasks = emptyList(), tasksLoading = false, tasksError = null,
            tasksFetchedAt = null, pendingOperations = emptyList(), operationBusy = false, operationError = null)
    }

    fun logout() {
        stopOperations()
        val old = session
        generation++
        job?.cancel()
        job = null
        performanceJob?.cancel()
        inventoryJob?.cancel()
        inventoryJob = null
        monitoringJob?.cancel()
        monitoringJob = null
        session = null
        _state.value = _state.value.copy(info = null, busy = false, error = null,
            inventory = null, inventoryLoading = false, inventoryError = null, performance = null, performanceLoading = false, performanceError = null, monitoring = null, monitoringLoading = false, monitoringError = null,
            credentials = if (_state.value.active?.remember == true) _state.value.credentials else Credentials("", ""))
        if (old != null) viewModelScope.launch {
            try { old.logout() } catch (_: Exception) {
                old.close()
                _state.value = _state.value.copy(error = "Không xác nhận được logout từ server; phiên cục bộ đã xóa.")
            }
        }
    }

    private fun invalidate() {
        stopOperations()
        generation++
        job?.cancel()
        job = null
        performanceJob?.cancel()
        inventoryJob?.cancel()
        inventoryJob = null
        monitoringJob?.cancel()
        monitoringJob = null
        val old = session
        session = null
        if (old != null) viewModelScope.launch {
            try { old.logout() } catch (_: Exception) { old.close() }
        }
        _state.value = _state.value.copy(info = null, busy = false, error = null,
            inventory = null, inventoryLoading = false, inventoryError = null, performance = null, performanceLoading = false, performanceError = null, monitoring = null, monitoringLoading = false, monitoringError = null)
    }

    override fun onCleared() {
        stopOperations()
        job?.cancel()
        performanceJob?.cancel()
        inventoryJob?.cancel()
        monitoringJob?.cancel()
        session?.close()
        session = null
        super.onCleared()
    }
}
