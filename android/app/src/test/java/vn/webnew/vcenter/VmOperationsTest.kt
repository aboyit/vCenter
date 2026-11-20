package vn.webnew.vcenter

import org.junit.Assert.*
import org.junit.Test

class VmOperationsTest {
    private fun response(method: String, content: String = "") =
        "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/' xmlns:v='urn:vim25'>" +
            "<s:Body><v:${method}Response>$content</v:${method}Response></s:Body></s:Envelope>"

    @Test fun powerAndGuestOperationsUseCorrectMethodsAndEscapeRefs() {
        val power = VmOperationSoap.request(VmOperation(VmOperationAction.POWER_ON, "vm<&"))
        assertTrue(power.contains("PowerOnVM_Task"))
        assertTrue(power.contains("vm&lt;&amp;"))
        assertFalse(power.contains("host"))
        val shutdown = VmOperationSoap.request(VmOperation(VmOperationAction.SHUTDOWN_GUEST, "vm-1"))
        assertTrue(shutdown.contains("ShutdownGuest"))
        assertFalse(shutdown.contains("_Task"))
        assertNull(VmOperationSoap.result(response("ShutdownGuest"), VmOperationAction.SHUTDOWN_GUEST))
        assertTrue(VmOperationSoap.request(VmOperation(VmOperationAction.REBOOT_GUEST, "vm-1")).contains("RebootGuest"))
        assertTrue(VmOperationSoap.request(VmOperation(VmOperationAction.POWER_OFF, "vm-1")).contains("PowerOffVM_Task"))
        assertTrue(VmOperationSoap.request(VmOperation(VmOperationAction.RESET, "vm-1")).contains("ResetVM_Task"))
    }

    @Test fun snapshotRequestsEscapeUserFieldsAndUseSnapshotReceiver() {
        val create = VmOperationSoap.request(VmOperation(VmOperationAction.CREATE_SNAPSHOT, "vm-1",
            snapshotName = "pre<&", description = "desc<&", memory = true, quiesce = true))
        assertTrue(create.contains("CreateSnapshot_Task"))
        assertTrue(create.contains("pre&lt;&amp;"))
        assertTrue(create.contains("desc&lt;&amp;"))
        assertTrue(create.contains("<vim:memory>true</vim:memory>"))
        assertTrue(create.contains("<vim:quiesce>true</vim:quiesce>"))
        val revert = VmOperationSoap.request(VmOperation(VmOperationAction.REVERT_TO_SNAPSHOT, "vm-1",
            snapshotRef = "snapshot<&", suppressPowerOn = true))
        assertTrue(revert.contains("_this type=\"VirtualMachineSnapshot\">snapshot&lt;&amp;"))
        assertFalse(revert.contains("vm-1"))
        assertTrue(revert.contains("<vim:suppressPowerOn>true</vim:suppressPowerOn>"))
        val remove = VmOperationSoap.request(VmOperation(VmOperationAction.REMOVE_SNAPSHOT, "vm-1",
            snapshotRef = "snapshot-1", removeChildren = true, consolidate = false))
        assertTrue(remove.contains("<vim:removeChildren>true</vim:removeChildren>"))
        assertTrue(remove.contains("<vim:consolidate>false</vim:consolidate>"))
    }

    @Test fun removeAllAndConsolidationUseVmTaskMethods() {
        val removeAll = VmOperationSoap.request(VmOperation(VmOperationAction.REMOVE_ALL_SNAPSHOTS, "vm-2", consolidate = false))
        assertTrue(removeAll.contains("RemoveAllSnapshots_Task"))
        assertTrue(removeAll.contains("<vim:consolidate>false</vim:consolidate>"))
        val consolidate = VmOperationSoap.request(VmOperation(VmOperationAction.CONSOLIDATE_DISKS, "vm-2"))
        assertTrue(consolidate.contains("ConsolidateVMDisks_Task"))
    }

    @Test fun validatesActionSpecificInputs() {
        assertThrows(IllegalArgumentException::class.java) { VmOperation(VmOperationAction.POWER_ON, "") }
        assertThrows(IllegalArgumentException::class.java) { VmOperation(VmOperationAction.CREATE_SNAPSHOT, "vm-1") }
        assertThrows(IllegalArgumentException::class.java) { VmOperation(VmOperationAction.REMOVE_SNAPSHOT, "vm-1") }
        assertThrows(IllegalArgumentException::class.java) {
            VmOperation(VmOperationAction.POWER_ON, "vm-1", snapshotName = "wrong")
        }
        VmOperation(VmOperationAction.REMOVE_ALL_SNAPSHOTS, "vm-1", consolidate = false)
    }

    @Test fun taskResultRequiresTypedNonEmptyTaskReference() {
        val ok = response("PowerOnVM_Task", "<v:returnval type='Task'>task-9</v:returnval>")
        assertEquals("task-9", VmOperationSoap.result(ok, VmOperationAction.POWER_ON))
        assertThrows(SoapFailure::class.java) {
            VmOperationSoap.result(response("PowerOnVM_Task", "<v:returnval type='VirtualMachine'>vm-1</v:returnval>"),
                VmOperationAction.POWER_ON)
        }
        assertThrows(SoapFailure::class.java) {
            VmOperationSoap.result(response("PowerOnVM_Task"), VmOperationAction.POWER_ON)
        }
    }
}
