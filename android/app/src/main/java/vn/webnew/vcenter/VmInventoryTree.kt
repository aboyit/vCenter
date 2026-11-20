package vn.webnew.vcenter

internal data class VmHostBranch(val host: InventoryItem, val vms: List<InventoryItem>)
internal data class VmClusterBranch(val cluster: InventoryItem, val hosts: List<VmHostBranch>)
internal data class VmInventoryTree(
    val clusters: List<VmClusterBranch>,
    val standalone: List<VmHostBranch>,
    val unassigned: List<InventoryItem>
)

internal fun vmInventoryTree(inventory: InventorySnapshot, shown: List<InventoryItem>): VmInventoryTree {
    val hosts = inventory.hosts.sortedBy { it.get("name") ?: it.ref }
    val hostRefs = hosts.map { it.ref }.toSet()
    val byHost = shown.groupBy { vm ->
        val runtimeHost = vm.links["runtime.host"]?.firstOrNull()
        runtimeHost ?: hosts.singleOrNull { vm.ref in it.links["vm"].orEmpty() }?.ref
    }
    val branches = hosts.map { VmHostBranch(it, byHost[it.ref].orEmpty()) }
    val assigned = mutableSetOf<String>()
    val clusters = inventory.clusters.sortedBy { it.get("name") ?: it.ref }.map { cluster ->
        val members = branches.filter { it.host.ref in cluster.links["host"].orEmpty() && assigned.add(it.host.ref) }
        VmClusterBranch(cluster, members)
    }
    return VmInventoryTree(clusters, branches.filter { it.host.ref !in assigned },
        byHost.filterKeys { it !in hostRefs }.values.flatten())
}
