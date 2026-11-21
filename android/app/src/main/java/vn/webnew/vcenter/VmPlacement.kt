package vn.webnew.vcenter

internal class VmPlacement(private val inventory: InventorySnapshot, val hostRef: String?) {
    private val host = inventory.hosts.firstOrNull { it.ref == hostRef }
    private val byRef = inventory.items.associateBy { it.ref }

    private fun ancestors(ref: String): List<InventoryItem> {
        val visited = mutableSetOf<String>()
        return generateSequence(byRef[ref]) { item ->
            item.links["parent"]?.singleOrNull()?.let(byRef::get)
        }.takeWhile { visited.add(it.ref) }.toList()
    }

    private val compute = host?.links?.get("parent")?.singleOrNull()?.let(byRef::get)
    private val rootPool = compute?.links?.get("resourcePool")?.singleOrNull()
    private val vmFolder = host?.let { ancestors(it.ref).firstOrNull { item -> item.type == "Datacenter" } }
        ?.links?.get("vmFolder")?.singleOrNull()

    val pools: List<InventoryItem> get() = inventory.resourcePools.filter { pool ->
        rootPool != null && ancestors(pool.ref).any { it.ref == rootPool }
    }
    val datastores: List<InventoryItem> get() = inventory.datastores.filter {
        it.ref in host?.links?.get("datastore").orEmpty() && it.get("summary.accessible") == "true"
    }
    val folders: List<InventoryItem> get() = inventory.ofType("Folder").filter { folder ->
        vmFolder != null && ancestors(folder.ref).any { it.ref == vmFolder } &&
            "VirtualMachine" in folder.links["childType"].orEmpty()
    }
    fun canKeepDatastores(vmRef: String): Boolean {
        val refs = inventory.vms.firstOrNull { it.ref == vmRef }?.links?.get("datastore")
        return !refs.isNullOrEmpty() && refs.all { ref -> datastores.any { it.ref == ref } }
    }
}
