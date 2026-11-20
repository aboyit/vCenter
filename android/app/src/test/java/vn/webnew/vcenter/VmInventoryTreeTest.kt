package vn.webnew.vcenter

import org.junit.Assert.assertEquals
import org.junit.Test

class VmInventoryTreeTest {
    private fun item(ref: String, type: String, links: Map<String, List<String>> = emptyMap()) =
        InventoryItem(ref, type, mapOf("name" to ref), links, emptySet())

    @Test fun groupsClusterStandaloneAndUnavailableHostsWithoutLosingVms() {
        val cluster = item("cluster-1", "ClusterComputeResource", mapOf("host" to listOf("host-1")))
        val host = item("host-1", "HostSystem")
        val standalone = item("host-2", "HostSystem", mapOf("vm" to listOf("vm-2")))
        val vm = item("vm-1", "VirtualMachine", mapOf("runtime.host" to listOf("host-1")))
        val fallback = item("vm-2", "VirtualMachine")
        val missing = item("vm-3", "VirtualMachine", mapOf("runtime.host" to listOf("host-hidden")))
        val unknown = item("vm-4", "VirtualMachine")
        val inventory = InventorySnapshot(listOf(cluster, host, standalone, vm, fallback, missing, unknown))
        val tree = vmInventoryTree(inventory, inventory.vms)
        assertEquals(listOf(vm), tree.clusters.single().hosts.single().vms)
        assertEquals(listOf(fallback), tree.standalone.single().vms)
        assertEquals(setOf(missing, unknown), tree.unassigned.toSet())
        val filtered = vmInventoryTree(inventory, listOf(fallback))
        assertEquals(emptyList<InventoryItem>(), filtered.clusters.single().hosts.single().vms)
        assertEquals(listOf(fallback), filtered.standalone.single().vms)
    }

    @Test fun runtimeHostWinsOverStaleHostVmMembership() {
        val old = item("host-1", "HostSystem", mapOf("vm" to listOf("vm-1")))
        val current = item("host-2", "HostSystem")
        val vm = item("vm-1", "VirtualMachine", mapOf("runtime.host" to listOf("host-2")))
        val tree = vmInventoryTree(InventorySnapshot(listOf(old, current, vm)), listOf(vm))
        assertEquals(emptyList<InventoryItem>(), tree.standalone.first().vms)
        assertEquals(listOf(vm), tree.standalone.last().vms)
    }
}
