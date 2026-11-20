package vn.webnew.vcenter

import okhttp3.Cookie
import org.junit.Assert.*
import org.junit.Test

class SessionIsolationTest {
    @Test fun npmAuthorizationNeverContainsVcenterCredentials() {
        val plain = VsphereSession(Profile(host = "vc.example.org", user = "vc-user"), Credentials("vc-secret", "npm-secret"))
        val xml = VsphereSoap.login("SessionManager", "vc-user", "vc-secret")
        assertNull(plain.requestFor(xml).header("Authorization"))
        val proxied = VsphereSession(Profile(host = "vc.example.org", user = "vc-user", npmEnabled = true, npmUser = "npm-user"), Credentials("vc-secret", "npm-secret"))
        val header = proxied.requestFor(xml).header("Authorization")!!
        assertTrue(header.startsWith("Basic "))
        val decoded = String(java.util.Base64.getDecoder().decode(header.removePrefix("Basic ")))
        assertEquals("npm-user:npm-secret", decoded)
        assertFalse(decoded.contains("vc-secret"))
    }

    @Test fun cookiesAreScopedToSessionAndClearedOnClose() {
        val profile = Profile(host = "vc.example.org")
        val first = VsphereSession(profile, Credentials("a", ""))
        val other = VsphereSession(profile, Credentials("b", ""))
        val url = Endpoint.url(profile.host, profile.port)
        first.cookieJar().saveFromResponse(url, listOf(Cookie.Builder().name("vmware_soap_session").value("token").hostOnlyDomain(url.host).secure().build()))
        assertEquals(1, first.cookieJar().loadForRequest(url).size)
        assertTrue(other.cookieJar().loadForRequest(url).isEmpty())
        first.close()
        assertTrue(first.cookieJar().loadForRequest(url).isEmpty())
        assertTrue(first.cookieJar().loadForRequest(Endpoint.url("other.example.org", "443")).isEmpty())
    }
}
