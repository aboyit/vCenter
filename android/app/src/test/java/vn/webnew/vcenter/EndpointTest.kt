package vn.webnew.vcenter

import org.junit.Assert.*
import org.junit.Test

class EndpointTest {
    @Test fun acceptsOnlyHostAndValidPort() {
        assertEquals("https://vc.example.org/sdk", Endpoint.url("vc.example.org", "443").toString())
        assertEquals("https://192.0.2.1:8443/sdk", Endpoint.url("192.0.2.1", "8443").toString())
        listOf("https://vc.example.org", "vc.example.org/x", "vc.example.org?x=y", "user@vc.example.org", "vc.example.org:81", "vc example.org", "").forEach {
            try { Endpoint.url(it, "443"); fail("accepted $it") } catch (_: IllegalArgumentException) { }
        }
        listOf("0", "65536", "abc", "0443").forEach {
            try { Endpoint.url("vc.example.org", it); fail("accepted port $it") } catch (_: IllegalArgumentException) { }
        }
    }
}
