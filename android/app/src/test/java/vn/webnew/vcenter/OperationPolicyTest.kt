package vn.webnew.vcenter

import org.junit.Assert.*
import org.junit.Test

class OperationPolicyTest {
    private val pending = PendingOperation("op", "scope", "vm-1", "PowerOn", 1)

    @Test fun unresolvedOperationsBlockOnlyTheirOwnScopeAndVm() {
        for (status in listOf("uncertain", "sent", "queued", "running", "unavailable")) {
            val operation = pending.copy(status = status)
            assertTrue(OperationPolicy.blocks(operation, "scope", "vm-1"))
            assertFalse(OperationPolicy.blocks(operation, "other", "vm-1"))
            assertFalse(OperationPolicy.blocks(operation, "scope", "vm-2"))
        }
        for (status in OperationPolicy.terminal) {
            assertFalse(OperationPolicy.blocks(pending.copy(status = status), "scope", "vm-1"))
        }
    }

    @Test fun missingOrUnknownTaskNeverBecomesSuccess() {
        assertEquals("unavailable", OperationPolicy.reconciledStatus(null))
        assertEquals("unavailable", OperationPolicy.reconciledStatus("unknown"))
        assertEquals("error", OperationPolicy.reconciledStatus("error"))
        assertEquals("success", OperationPolicy.reconciledStatus("success"))
    }
}
