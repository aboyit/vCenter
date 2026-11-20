package vn.webnew.vcenter

import org.junit.Assert.*
import org.junit.Test

class VmOperationPolicyTest {
    private fun vm(power: String = "poweredOn", tools: String? = null) = InventoryItem("vm-1", "VirtualMachine",
        mapOf("runtime.powerState" to power) + (tools?.let { mapOf("guest.toolsRunningStatus" to it) } ?: emptyMap()), emptyMap(), emptySet())

    @Test fun guestOperationsRequireRunningToolsAndPowerState() {
        val operation = VmOperation(VmOperationAction.SHUTDOWN_GUEST, "vm-1")
        assertNotNull(vmOperationBlockReason(vm(), operation))
        assertNotNull(vmOperationBlockReason(vm("poweredOff", "guestToolsRunning"), operation))
        assertNull(vmOperationBlockReason(vm(tools = "guestToolsRunning"), operation))
    }

    @Test fun missingSnapshotsCannotBeRevertedAndMemoryCannotBeQuiesced() {
        assertNotNull(vmOperationBlockReason(vm(), VmOperation(VmOperationAction.REVERT_TO_SNAPSHOT, "vm-1", snapshotRef = "snapshot-1")))
        assertNotNull(vmOperationBlockReason(vm(tools = "guestToolsRunning"), VmOperation(VmOperationAction.CREATE_SNAPSHOT,
            "vm-1", snapshotName = "backup", memory = true, quiesce = true)))
        assertNotNull(vmOperationBlockReason(vm("poweredOff"), VmOperation(VmOperationAction.CREATE_SNAPSHOT,
            "vm-1", snapshotName = "backup", memory = true)))
        assertNull(vmOperationBlockReason(vm("poweredOff"), VmOperation(VmOperationAction.CREATE_SNAPSHOT,
            "vm-1", snapshotName = "backup")))
    }
}
