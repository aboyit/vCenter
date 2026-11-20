package vn.webnew.vcenter

import org.junit.Assert.*
import org.junit.Test

class VsphereSoapTest {
    private val envelope = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:v="urn:vim25"><s:Body>%s</s:Body></s:Envelope>"""

    @Test fun parsesNamespacedServiceContent() {
        val xml = envelope.format("""<v:RetrieveServiceContentResponse><v:returnval><v:sessionManager type="SessionManager">SessionManager</v:sessionManager><v:propertyCollector type="PropertyCollector">propertyCollector</v:propertyCollector><v:viewManager type="ViewManager">viewManager</v:viewManager><v:rootFolder type="Folder">group-d1</v:rootFolder><v:taskManager type="TaskManager">TaskManager</v:taskManager><v:about><v:fullName>VMware vCenter Server 8.0</v:fullName><v:version>8.0.3</v:version><v:instanceUuid>abc-123</v:instanceUuid></v:about></v:returnval></v:RetrieveServiceContentResponse>""")
        assertEquals(ServiceContent("SessionManager", ServerInfo("VMware vCenter Server 8.0", "8.0.3", "abc-123"), "propertyCollector", "viewManager", "group-d1", taskManager = "TaskManager"), VsphereSoap.serviceContent(xml))
    }

    @Test fun rejectsFaultAndMissingFieldsAndEntities() {
        val fault = envelope.format("<s:Fault><faultstring>secret password</faultstring></s:Fault>")
        try { VsphereSoap.serviceContent(fault); fail("fault expected") } catch (e: SoapFailure) {
            assertFalse(e.message!!.contains("secret"))
        }
        try { VsphereSoap.serviceContent(envelope.format("<v:RetrieveServiceContentResponse/>")); fail("missing expected") } catch (_: SoapFailure) { }
        try { VsphereSoap.result("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x/>", "Login"); fail("doctype expected") } catch (_: SoapFailure) { }
    }

    @Test fun reportsFaultTypeAndMethodWithoutServerText() {
        val xml = envelope.format("""<s:Fault><faultstring>secret password</faultstring><detail><v:InvalidPropertyFault><v:name>private value</v:name></v:InvalidPropertyFault></detail></s:Fault>""")
        try { VsphereSoap.result(xml, "RetrievePropertiesEx"); fail("fault expected") } catch (e: SoapFailure) {
            assertTrue(e.message!!.contains("InvalidProperty (RetrievePropertiesEx)"))
            assertFalse(e.message!!.contains("secret"))
            assertFalse(e.message!!.contains("private"))
            assertFalse(e.expired)
        }
    }

    @Test fun loginRequiresReturnValueAndEscapesCredentials() {
        val xml = VsphereSoap.login("SessionManager", "a<&\"'", "p<>&")
        assertTrue(xml.contains("a&lt;&amp;&quot;&apos;"))
        assertTrue(xml.contains("p&lt;&gt;&amp;"))
        assertFalse(xml.contains("Authorization"))
        try { VsphereSoap.loginResult(envelope.format("<v:LoginResponse/>")); fail("missing session") } catch (_: SoapFailure) { }
        VsphereSoap.loginResult(envelope.format("<v:LoginResponse><v:returnval><v:key>x</v:key></v:returnval></v:LoginResponse>"))
    }
}
