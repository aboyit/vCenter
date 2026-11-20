package vn.webnew.vcenter

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal data class ServerInfo(val name: String, val version: String, val instanceId: String)
internal data class ServiceContent(
    val manager: String, val info: ServerInfo, val collector: String, val viewManager: String,
    val rootFolder: String, val performanceManager: String? = null, val eventManager: String? = null,
    val taskManager: String? = null, val provisioningChecker: String? = null
)
internal class SoapFailure(message: String, val expired: Boolean = false) : Exception(message)

internal object VsphereSoap {
    private const val VIM = "urn:vim25"
    private const val SOAP = "http://schemas.xmlsoap.org/soap/envelope/"

    fun escape(value: String): String = buildString {
        value.forEach { c ->
            append(when (c) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&apos;"
                else -> if (c.code < 32 && c != '\t' && c != '\n' && c != '\r') "" else c.toString()
            })
        }
    }

    internal fun envelope(method: String, fields: String) =
        """<?xml version="1.0" encoding="UTF-8"?><soapenv:Envelope xmlns:soapenv="$SOAP" xmlns:vim="$VIM"><soapenv:Body><vim:$method>$fields</vim:$method></soapenv:Body></soapenv:Envelope>"""

    fun retrieve() = envelope("RetrieveServiceContent", "<vim:_this type=\"ServiceInstance\">ServiceInstance</vim:_this>")
    fun login(manager: String, user: String, password: String) = envelope("Login",
        "<vim:_this type=\"SessionManager\">${escape(manager)}</vim:_this><vim:userName>${escape(user)}</vim:userName><vim:password>${escape(password)}</vim:password>")
    fun logout(manager: String) = envelope("Logout", "<vim:_this type=\"SessionManager\">${escape(manager)}</vim:_this>")

    internal fun children(parent: Element, name: String, ns: String? = null): Element? {
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.localName == name && (ns == null || node.namespaceURI == ns)) return node
        }
        return null
    }

    private fun required(parent: Element, name: String): String =
        children(parent, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw SoapFailure("Phản hồi SOAP thiếu dữ liệu bắt buộc.")

    fun result(xml: String, method: String): Element {
        try {
            // Android's XML provider does not implement all desktop JAXP features.
            // Reject declarations before parsing so no DTD or external entity can be resolved.
            if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) {
                throw SoapFailure("Phản hồi SOAP chứa khai báo XML không được phép.")
            }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val body = children(doc.documentElement, "Body", SOAP) ?: throw SoapFailure("Phản hồi SOAP không hợp lệ.")
            children(body, "Fault", SOAP)?.let { fault ->
                val detail = children(fault, "detail")
                val invalidLogin = detail?.getElementsByTagNameNS("*", "InvalidLoginFault")?.length ?: 0
                val noPermission = detail?.getElementsByTagNameNS("*", "NoPermissionFault")?.length ?: 0
                val expired = (detail?.getElementsByTagNameNS("*", "NotAuthenticatedFault")?.length ?: 0) > 0
                val knownFault = listOf("InvalidProperty", "InvalidArgument", "NotSupported", "RuntimeFault")
                    .firstOrNull { name -> (detail?.getElementsByTagNameNS("*", "${name}Fault")?.length ?: 0) > 0 }
                val invalidProperty = detail?.getElementsByTagNameNS("*", "InvalidPropertyFault")?.item(0) as? Element
                val property = invalidProperty?.let { children(it, "name")?.textContent?.trim() }
                    ?.takeIf { InventorySoap.isRequestedProperty(it) }
                val propertyHint = property?.let { ": $it" }.orEmpty()
                throw SoapFailure(when {
                    expired -> "Phiên vCenter hết hạn; vui lòng đăng nhập lại."
                    invalidLogin > 0 -> "SOAP InvalidLogin: vCenter từ chối tài khoản hoặc mật khẩu."
                    noPermission > 0 -> "SOAP NoPermission: tài khoản thiếu quyền trên vCenter."
                    knownFault != null -> "SOAP $knownFault ($method$propertyHint): vCenter từ chối yêu cầu API."
                    else -> "Endpoint SOAP trả về fault ($method); chưa xác định nguyên nhân."
                }, expired)
            }
            return children(body, "${method}Response", VIM) ?: throw SoapFailure("Phản hồi SOAP không đúng phương thức.")
        } catch (e: SoapFailure) {
            throw e
        } catch (_: Exception) {
            throw SoapFailure("Phản hồi SOAP không hợp lệ.")
        }
    }

    fun serviceContent(xml: String): ServiceContent {
        val content = children(result(xml, "RetrieveServiceContent"), "returnval")
            ?: throw SoapFailure("Thiếu ServiceContent.")
        val manager = required(content, "sessionManager")
        val about = children(content, "about") ?: throw SoapFailure("Thiếu thông tin server.")
        return ServiceContent(manager, ServerInfo(required(about, "fullName"),
            required(about, "version"), required(about, "instanceUuid")),
            required(content, "propertyCollector"), required(content, "viewManager"), required(content, "rootFolder"),
            children(content, "perfManager")?.textContent?.trim()?.takeIf { it.isNotEmpty() },
            children(content, "eventManager")?.textContent?.trim()?.takeIf { it.isNotEmpty() },
            children(content, "taskManager")?.textContent?.trim()?.takeIf { it.isNotEmpty() },
            children(content, "vmProvisioningChecker")?.textContent?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun loginResult(xml: String) {
        val response = result(xml, "Login")
        if (children(response, "returnval") == null) throw SoapFailure("Đăng nhập không được xác nhận bởi vCenter.")
    }
}
