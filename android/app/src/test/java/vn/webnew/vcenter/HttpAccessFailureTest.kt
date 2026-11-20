package vn.webnew.vcenter

import org.junit.Assert.*
import org.junit.Test

class HttpAccessFailureTest {
    @Test fun initialForbiddenDoesNotBlameVcenterPassword() {
        val message = HttpAccessFailure.describe(403, "RetrieveServiceContent", null, false)
        assertTrue(message.contains("chưa gửi tài khoản vCenter"))
        assertTrue(message.contains("Access List/IP"))
        assertTrue(message.contains("đang tắt"))
    }

    @Test fun loginForbiddenAcknowledgesSuccessfulDiscovery() {
        val message = HttpAccessFailure.describe(403, "Login", null, true)
        assertTrue(message.contains("đã đọc được /sdk"))
        assertTrue(message.contains("không xác định chắc"))
        assertTrue(message.contains("đã gửi NPM HTTP Auth"))
    }

    @Test fun basicChallengeIsHintNotUntrustedHeaderEcho() {
        val message = HttpAccessFailure.describe(401, "Login", "Basic realm=private-value", true)
        assertTrue(message.contains("HTTP Basic Auth"))
        assertFalse(message.contains("private-value"))
    }
}
