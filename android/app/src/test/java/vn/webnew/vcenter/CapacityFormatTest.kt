package vn.webnew.vcenter

import org.junit.Assert.assertEquals
import org.junit.Test

class CapacityFormatTest {
    @Test fun switchesToTibAtOneThousandGib() {
        val gib = 1_073_741_824L
        assertEquals("999.0 GiB", capacityDisplay(999 * gib).toString())
        assertEquals("1.0 TiB", capacityDisplay(1000 * gib).toString())
        assertEquals("2.0 TiB", capacityDisplay(2048 * gib).toString())
    }

    @Test fun preservesZeroAndMissingValues() {
        assertEquals("0.0 GiB", capacityDisplay(0).toString())
        assertEquals("—", capacityDisplay(null).value)
    }
}
