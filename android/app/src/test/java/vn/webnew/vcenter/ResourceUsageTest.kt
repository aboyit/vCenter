package vn.webnew.vcenter

import org.junit.Assert.*
import org.junit.Test

class ResourceUsageTest {
    @Test fun sumsCpuAcrossHostsAndRejectsMissingUsage() {
        val host = InventoryItem("host-1", "HostSystem", mapOf("summary.hardware.numCpuCores" to "4",
            "summary.hardware.cpuMhz" to "2500", "summary.quickStats.overallCpuUsage" to "2000"), emptyMap(), emptySet())
        val total = totalCpuUsage(InventorySnapshot(listOf(host, host.copy(ref = "host-2"))))
        assertEquals(4000L, total.used)
        assertEquals(20000L, total.capacity)
        assertEquals(16000L, total.free)
        assertNull(totalCpuUsage(InventorySnapshot(listOf(host, host.copy(ref = "host-2", values = emptyMap())))).free)
    }

    @Test fun missingAndInconsistentUsageDoesNotAppearAsFreeCapacity() {
        assertNull(ResourceUsage(null, 100).ratio)
        assertNull(ResourceUsage(101, 100).free)
        assertNull(ResourceUsage(-1, 100).free)
        assertEquals(0f, ResourceUsage(0, 0).ratio)
    }
}
