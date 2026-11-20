package vn.webnew.vcenter

import org.junit.Assert.*
import org.junit.Test

class AdvancedExecutionPolicyTest {
    private fun config() = VmConfig("vm-1", "17", "poweredOff", 2, 4096, 1,
        false, false, "otherGuest64", "bios", false, 0, "vmx-20", emptyList())

    @Test fun staleVersionAndPowerStateRequireReloadEvenIfResourceValuesMatch() {
        val baseline = config()
        assertNull(AdvancedExecutionPolicy.conflict(baseline, baseline.copy()))
        assertNotNull(AdvancedExecutionPolicy.conflict(baseline, baseline.copy(changeVersion = "18")))
        assertNotNull(AdvancedExecutionPolicy.conflict(baseline, baseline.copy(power = "poweredOn")))
    }

    @Test fun cannotApplyConfigToDifferentVm() {
        assertNotNull(AdvancedExecutionPolicy.conflict(config(), config().copy(vmRef = "vm-2")))
    }
}
