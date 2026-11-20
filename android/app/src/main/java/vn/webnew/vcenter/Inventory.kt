package vn.webnew.vcenter

import org.w3c.dom.Element

internal data class VmSnapshot(val ref: String, val name: String?, val description: String?,
    val createdAt: String?, val powerState: String?, val quiesced: String?, val depth: Int)

internal data class VmSnapshotInfo(val snapshots: List<VmSnapshot>, val currentRef: String?)

internal enum class VmPowerFilter(val label: String, val state: String?) {
    All("Tất cả", null), PoweredOn("Đang bật", "poweredOn"),
    PoweredOff("Đã tắt", "poweredOff"), Suspended("Tạm ngưng", "suspended"),
    Unknown("Chưa rõ", null);

    fun matches(item: InventoryItem): Boolean = when (this) {
        All -> true
        Unknown -> item.get("runtime.powerState") !in setOf("poweredOn", "poweredOff", "suspended")
        else -> item.get("runtime.powerState") == state
    }
}

internal fun filterVms(items: List<InventoryItem>, search: String, power: VmPowerFilter) =
    items.filter { (it.get("name") ?: it.ref).contains(search.trim(), ignoreCase = true) && power.matches(it) }

internal data class HostSwitch(val name: String?, val mtu: String?, val uplinks: List<String>)
internal data class HostPortgroup(val name: String?, val switchName: String?, val vlanId: String?)
internal data class HostVmk(val device: String?, val portgroup: String?, val ip: String?)
internal data class HostUplink(val device: String?, val linkSpeedMb: String?)
internal data class HostNetwork(val switches: List<HostSwitch> = emptyList(),
    val portgroups: List<HostPortgroup> = emptyList(), val vmks: List<HostVmk> = emptyList(),
    val uplinks: List<HostUplink> = emptyList(), val available: Set<String> = emptySet())
internal data class LacpGroup(val name: String?, val key: String?, val mode: String?,
    val uplinks: List<String>, val uplinkNum: String?, val loadBalance: String?)

internal data class InventoryItem(val ref: String, val type: String, val values: Map<String, String>,
    val links: Map<String, List<String>>, val missing: Set<String>, val snapshotInfo: VmSnapshotInfo? = null,
    val hostNetwork: HostNetwork? = null, val lacpGroups: List<LacpGroup>? = null) {
    fun get(key: String) = values[key]?.takeIf { it.isNotBlank() }
    fun number(key: String) = get(key)?.toLongOrNull()
}

internal data class InventorySnapshot(val items: List<InventoryItem>, val fetchedAt: Long = System.currentTimeMillis()) {
    val vms get() = ofType("VirtualMachine")
    val hosts get() = ofType("HostSystem")
    val resourcePools get() = ofType("ResourcePool")
    val clusters get() = ofType("ClusterComputeResource")
    val datastores get() = ofType("Datastore")
    val networks get() = items.filter { it.type in setOf("Network", "DistributedVirtualPortgroup", "VmwareDistributedVirtualSwitch") }
        .distinctBy { it.type to it.ref }
    fun ofType(type: String) = items.filter { it.type == type }.distinctBy { it.ref }
    fun name(ref: String): String = items.firstOrNull { it.ref == ref }?.get("name") ?: ref
    val capacityBytes get() = datastores.map { it.number("summary.capacity") }
        .takeIf { it.isNotEmpty() && it.all { value -> value != null } }?.sumOf { it!! }
    val freeBytes get() = datastores.map { it.number("summary.freeSpace") }
        .takeIf { it.isNotEmpty() && it.all { value -> value != null } }?.sumOf { it!! }
    val cpuMhz get() = hosts.map { host ->
        val cores = host.number("summary.hardware.numCpuCores")
        val mhz = host.number("summary.hardware.cpuMhz")
        if (cores != null && mhz != null) cores * mhz else null
    }.takeIf { it.isNotEmpty() && it.all { value -> value != null } }?.sumOf { it!! }
    val memoryBytes get() = hosts.map { it.number("summary.hardware.memorySize") }
        .takeIf { it.isNotEmpty() && it.all { value -> value != null } }?.sumOf { it!! }
    val memoryUsedBytes get() = hosts.map { it.number("summary.quickStats.overallMemoryUsage") }
        .takeIf { it.isNotEmpty() && it.all { value -> value != null } }?.sumOf { it!! }?.times(1024L * 1024L)
}

internal object InventorySoap {
    private val properties = linkedMapOf(
        "VirtualMachine" to listOf("name", "runtime.powerState", "config.hardware.numCPU", "config.hardware.memoryMB", "config.guestFullName", "guest.ipAddress", "guest.toolsRunningStatus", "runtime.consolidationNeeded", "runtime.host", "datastore", "snapshot", "parent", "resourcePool"),
        "HostSystem" to listOf("name", "runtime.connectionState", "runtime.inMaintenanceMode", "summary.hardware.numCpuCores", "summary.hardware.cpuMhz", "summary.hardware.memorySize", "summary.quickStats.overallMemoryUsage", "summary.quickStats.overallCpuUsage", "overallStatus", "config.network.vswitch", "config.network.portgroup", "config.network.vnic", "config.network.pnic", "vm", "parent", "datastore", "network"),
        "ClusterComputeResource" to listOf("name", "host", "overallStatus", "resourcePool"),
        "ComputeResource" to listOf("name", "host", "resourcePool"),
        "Folder" to listOf("name", "parent", "childType"),
        "ResourcePool" to listOf("name", "parent", "vm", "resourcePool"),
        "Datastore" to listOf("name", "summary.capacity", "summary.freeSpace", "summary.accessible"),
        "Network" to listOf("name"),
        "DistributedVirtualPortgroup" to listOf("name", "key", "config.distributedVirtualSwitch"),
        "VmwareDistributedVirtualSwitch" to listOf("name", "uuid", "config")
    )

    internal fun isRequestedProperty(path: String) = properties.values.any { path in it }

    private fun mor(type: String, ref: String) = "<vim:_this type=\"$type\">${VsphereSoap.escape(ref)}</vim:_this>"
    fun createView(manager: String, root: String) = VsphereSoap.envelope("CreateContainerView",
        mor("ViewManager", manager) + "<vim:container type=\"Folder\">${VsphereSoap.escape(root)}</vim:container>" +
            properties.keys.joinToString("") { "<vim:type>$it</vim:type>" } + "<vim:recursive>true</vim:recursive>")
    fun destroyView(view: String) = VsphereSoap.envelope("DestroyView", mor("ContainerView", view))
    fun cancel(collector: String, token: String) = VsphereSoap.envelope("CancelRetrievePropertiesEx",
        mor("PropertyCollector", collector) + "<vim:token>${VsphereSoap.escape(token)}</vim:token>")
    fun continuePage(collector: String, token: String) = VsphereSoap.envelope("ContinueRetrievePropertiesEx",
        mor("PropertyCollector", collector) + "<vim:token>${VsphereSoap.escape(token)}</vim:token>")
    fun retrieve(collector: String, view: String) = VsphereSoap.envelope("RetrievePropertiesEx",
        mor("PropertyCollector", collector) + "<vim:specSet>" +
            properties.entries.joinToString("") { (type, paths) ->
                "<vim:propSet><vim:type>$type</vim:type>" + paths.joinToString("") { "<vim:pathSet>$it</vim:pathSet>" } + "</vim:propSet>"
            } + "<vim:objectSet><vim:obj type=\"ContainerView\">${VsphereSoap.escape(view)}</vim:obj>" +
            "<vim:skip>true</vim:skip><vim:selectSet xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"vim:TraversalSpec\">" +
            "<vim:name>viewTraversal</vim:name><vim:type>ContainerView</vim:type><vim:path>view</vim:path>" +
            "<vim:skip>false</vim:skip></vim:selectSet></vim:objectSet></vim:specSet>" +
            "<vim:options><vim:maxObjects>100</vim:maxObjects></vim:options>")

    fun viewRef(xml: String): String = VsphereSoap.children(VsphereSoap.result(xml, "CreateContainerView"), "returnval")
        ?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: throw SoapFailure("Không tạo được inventory view.")

    data class Page(val items: List<InventoryItem>, val token: String?)
    fun page(xml: String, method: String): Page {
        val response = VsphereSoap.result(xml, method)
        val result = VsphereSoap.children(response, "returnval") ?: return Page(emptyList(), null)
        val items = direct(result, "objects").map { obj ->
            val mor = VsphereSoap.children(obj, "obj") ?: throw SoapFailure("Inventory thiếu object reference.")
            val type = mor.getAttribute("type")
            val ref = mor.textContent.trim()
            if (type.isEmpty() || ref.isEmpty()) throw SoapFailure("Inventory reference không hợp lệ.")
            val values = mutableMapOf<String, String>()
            val links = mutableMapOf<String, List<String>>()
            var snapshotInfo: VmSnapshotInfo? = null
            var network = HostNetwork()
            var lacpGroups: List<LacpGroup>? = null
            direct(obj, "propSet").forEach { prop ->
                val path = VsphereSoap.children(prop, "name")?.textContent?.trim() ?: return@forEach
                val value = VsphereSoap.children(prop, "val") ?: return@forEach
                if (path == "snapshot") {
                    snapshotInfo = parseSnapshots(value)
                    return@forEach
                }
                if (type == "HostSystem" && path.startsWith("config.network.")) {
                    val elements = directElements(value)
                    network = when (path) {
                        "config.network.vswitch" -> network.copy(switches = elements.map { node ->
                            val bond = child(node, "spec")?.let { child(it, "policy") }?.let { child(it, "nicTeaming") }
                            HostSwitch(text(node, "name"), text(node, "mtu"),
                                (direct(node, "pnic") + (bond?.let { child(it, "nicOrder") }
                                    ?.let { direct(it, "activeNic") + direct(it, "standbyNic") }.orEmpty()))
                                    .map { it.textContent.trim() }.filter { it.isNotEmpty() }.distinct())
                        }, available = network.available + path)
                        "config.network.portgroup" -> network.copy(portgroups = elements.map { node ->
                            val spec = child(node, "spec")
                            HostPortgroup(text(spec, "name") ?: text(node, "key"), text(spec, "vswitchName"), text(spec, "vlanId"))
                        }, available = network.available + path)
                        "config.network.vnic" -> network.copy(vmks = elements.map { node ->
                            val spec = child(node, "spec")
                            HostVmk(text(node, "device"), text(node, "portgroup"),
                                spec?.let { child(it, "ip") }?.let { text(it, "ipAddress") })
                        }, available = network.available + path)
                        "config.network.pnic" -> network.copy(uplinks = elements.map { node ->
                            HostUplink(text(node, "device"), node.let { child(it, "linkSpeed") }?.let { text(it, "speedMb") })
                        }, available = network.available + path)
                        else -> network
                    }
                    return@forEach
                }
                // LACP fields belong to the runtime VMwareDVSConfigInfo subtype, not DVSConfigInfo.
                if (type == "VmwareDistributedVirtualSwitch" && path == "config") {
                    text(value, "lacpApiVersion")?.let { values["config.lacpApiVersion"] = it }
                    if (child(value, "lacpApiVersion") != null || child(value, "lacpGroupConfig") != null) {
                        lacpGroups = direct(value, "lacpGroupConfig").map { node ->
                            LacpGroup(text(node, "name"), text(node, "key"), text(node, "mode"),
                                direct(node, "uplinkName").map { it.textContent.trim() }.filter { it.isNotEmpty() },
                                text(node, "uplinkNum"), text(node, "loadbalanceAlgorithm"))
                        }
                    }
                    return@forEach
                }
                if (type == "VmwareDistributedVirtualSwitch" && path == "config.lacpGroupConfig") {
                    lacpGroups = directElements(value).map { node ->
                        LacpGroup(text(node, "name"), text(node, "key"), text(node, "mode"),
                            direct(node, "uplinkName").map { it.textContent.trim() }.filter { it.isNotEmpty() },
                            text(node, "uplinkNum"), text(node, "loadbalanceAlgorithm"))
                    }
                    return@forEach
                }
                if (type == "Folder" && path == "childType") {
                    links[path] = directElements(value).map { it.textContent.trim() }.filter { it.isNotEmpty() }
                    return@forEach
                }
                val refs = mutableListOf<String>()
                fun visit(element: Element) {
                    if (element.hasAttribute("type") && element.textContent.isNotBlank() &&
                        (directElements(element).isEmpty() || element.localName == "val"))
                        refs.add(element.textContent.trim())
                    else directElements(element).forEach(::visit)
                }
                visit(value)
                if (refs.isNotEmpty()) links[path] = refs.distinct()
                else if (directElements(value).isEmpty()) values[path] = value.textContent.trim()
                if (path == "config.distributedVirtualSwitch") {
                    val switchRef = value.getElementsByTagNameNS("*", "switchUuid")
                    if (switchRef.length > 0) values["switchUuid"] = switchRef.item(0).textContent.trim()
                }
            }
            val missing = direct(obj, "missingSet").mapNotNull {
                VsphereSoap.children(it, "path")?.textContent?.trim()
            }.toSet()
            InventoryItem(ref, type, values, links, missing, snapshotInfo,
                network.takeIf { type == "HostSystem" }, lacpGroups)
        }
        return Page(items, VsphereSoap.children(result, "token")?.textContent?.trim()?.takeIf { it.isNotEmpty() })
    }

    private fun parseSnapshots(value: Element): VmSnapshotInfo? {
        if (value.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "nil") in setOf("true", "1")) return null
        fun text(element: Element, name: String) = VsphereSoap.children(element, name)
            ?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        val snapshots = mutableListOf<VmSnapshot>()
        val pending = java.util.ArrayDeque<Pair<Element, Int>>()
        direct(value, "rootSnapshotList").asReversed().forEach { pending.addLast(it to 0) }
        // Walk iteratively to preserve tree order without depending on the VM's snapshot depth.
        while (pending.isNotEmpty()) {
            val (node, depth) = pending.removeLast()
            val ref = text(node, "snapshot") ?: throw SoapFailure("Snapshot thiếu object reference.")
            snapshots.add(VmSnapshot(ref, text(node, "name"), text(node, "description"),
                text(node, "createTime"), text(node, "state"), text(node, "quiesced"), depth))
            direct(node, "childSnapshotList").asReversed().forEach { pending.addLast(it to depth + 1) }
        }
        return VmSnapshotInfo(snapshots, text(value, "currentSnapshot"))
    }

    private fun child(element: Element, name: String): Element? = VsphereSoap.children(element, name)
    private fun text(element: Element?, name: String): String? = element?.let { child(it, name) }
        ?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun directElements(element: Element): List<Element> = (0 until element.childNodes.length)
        .mapNotNull { element.childNodes.item(it) as? Element }
    private fun direct(element: Element, name: String) = directElements(element).filter { it.localName == name }
}
