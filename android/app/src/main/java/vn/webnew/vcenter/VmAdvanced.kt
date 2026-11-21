package vn.webnew.vcenter

import org.w3c.dom.Element

internal data class VmDevice(val key: Int, val type: String, val label: String, val controller: Int?,
    val unit: Int?, val capacityKb: Long?, val backing: String?, val backingType: String?,
    val networkRef: String?, val switchUuid: String?, val portKey: String?, val mac: String?,
    val connected: Boolean?, val startConnected: Boolean?)

internal data class VmConfig(val vmRef: String, val changeVersion: String, val power: String,
    val numCpu: Int, val memoryMb: Long, val coresPerSocket: Int, val cpuHotAdd: Boolean,
    val memoryHotAdd: Boolean, val guestId: String?, val firmware: String?, val secureBoot: Boolean,
    val bootDelay: Int?, val hardwareVersion: String?, val devices: List<VmDevice>) {
    fun fingerprint(): String = listOf(changeVersion, power, numCpu, memoryMb, coresPerSocket,
        cpuHotAdd, memoryHotAdd, guestId, firmware, secureBoot, bootDelay, hardwareVersion, devices).toString()
}

internal sealed class AdvancedChange {
    data class Resources(val cpu: Int, val memoryMb: Long, val cores: Int, val cpuHotAdd: Boolean,
        val memoryHotAdd: Boolean) : AdvancedChange()
    data class ExpandDisk(val key: Int, val sizeGb: Long) : AdvancedChange()
    data class RemoveDevice(val key: Int) : AdvancedChange()
    data class AddDisk(val controller: Int, val unit: Int, val sizeGb: Long, val path: String? = null) : AdvancedChange()
    data class AddNic(val type: String, val networkRef: String, val networkType: String,
        val switchUuid: String? = null, val portKey: String? = null) : AdvancedChange()
    data class EditNic(val key: Int, val networkRef: String, val networkType: String,
        val switchUuid: String? = null, val portKey: String? = null,
        val startConnected: Boolean) : AdvancedChange()
    data class Iso(val key: Int, val path: String, val startConnected: Boolean) : AdvancedChange()
    data class Boot(val delay: Int, val firmware: String, val secureBoot: Boolean) : AdvancedChange()
}

internal sealed class AdvancedCommand {
    data class Edit(val change: AdvancedChange) : AdvancedCommand()
    data class Clone(val name: String, val folder: String, val pool: String, val host: String,
        val datastore: String) : AdvancedCommand()
    data class Migrate(val host: String, val pool: String, val datastore: String?) : AdvancedCommand()
}

internal data class AdvancedOperation(val vmRef: String, val baseline: VmConfig, val command: AdvancedCommand) {
    val action: String get() = when (command) {
        is AdvancedCommand.Edit -> "RECONFIGURE_VM"
        is AdvancedCommand.Clone -> "CLONE_VM"
        is AdvancedCommand.Migrate -> "MIGRATE_VM"
    }
}

internal object VmAdvancedSoap {
    private fun e(value: String) = VsphereSoap.escape(value)
    private fun mor(type: String, ref: String) = "<vim:_this type=\"$type\">${e(ref)}</vim:_this>"
    private fun field(name: String, value: Any) = "<vim:$name>${e(value.toString())}</vim:$name>"
    private fun direct(parent: Element, name: String): List<Element> = (0 until parent.childNodes.length)
        .mapNotNull { parent.childNodes.item(it) as? Element }.filter { it.localName == name }
    private fun child(parent: Element?, name: String): Element? = parent?.let { VsphereSoap.children(it, name) }
    private fun text(parent: Element?, name: String) = child(parent, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    private fun kind(element: Element): String = element.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type")
        .substringAfter(':').ifBlank { element.localName ?: "" }
    private fun ref(type: String, value: String) = "<vim:${type} type=\"${type}\">${e(value)}</vim:${type}>"

    fun read(collector: String, vmRef: String) = VsphereSoap.envelope("RetrievePropertiesEx",
        mor("PropertyCollector", collector) + "<vim:specSet><vim:propSet><vim:type>VirtualMachine</vim:type>" +
            "<vim:pathSet>config</vim:pathSet><vim:pathSet>runtime.powerState</vim:pathSet></vim:propSet>" +
            "<vim:objectSet><vim:obj type=\"VirtualMachine\">${e(vmRef)}</vim:obj></vim:objectSet></vim:specSet>" +
            "<vim:options><vim:maxObjects>1</vim:maxObjects></vim:options>")

    fun parse(xml: String, vmRef: String): VmConfig {
        val result = child(VsphereSoap.result(xml, "RetrievePropertiesEx"), "returnval")
            ?: throw SoapFailure("Không đọc được cấu hình VM.")
        if (text(result, "token") != null) throw SoapFailure("Cấu hình VM bị phân trang; không cho phép chỉnh sửa.")
        val obj = direct(result, "objects").singleOrNull() ?: throw SoapFailure("VM không còn trong inventory.")
        val objectRef = child(obj, "obj") ?: throw SoapFailure("Thiếu VM reference.")
        if (objectRef.getAttribute("type") != "VirtualMachine" || objectRef.textContent.trim() != vmRef)
            throw SoapFailure("Cấu hình trả về không thuộc VM đã chọn.")
        val props = direct(obj, "propSet").associate { text(it, "name") to child(it, "val") }
        val config = props["config"] ?: throw SoapFailure("Thiếu quyền đọc config VM; không cho phép chỉnh sửa.")
        val power = props["runtime.powerState"]?.textContent?.trim()
            ?.takeIf { it in setOf("poweredOn", "poweredOff", "suspended") }
            ?: throw SoapFailure("Không xác định được nguồn VM.")
        val version = text(config, "changeVersion") ?: throw SoapFailure("VM thiếu changeVersion.")
        val hardware = child(config, "hardware") ?: throw SoapFailure("VM thiếu cấu hình phần cứng.")
        val devices = direct(hardware, "device").map { node ->
            val backing = child(node, "backing")
            val connect = child(node, "connectable")
            val port = child(backing, "port")
            VmDevice(text(node, "key")?.toIntOrNull() ?: throw SoapFailure("Thiết bị thiếu key."),
                kind(node), text(child(node, "deviceInfo"), "label") ?: kind(node),
                text(node, "controllerKey")?.toIntOrNull(), text(node, "unitNumber")?.toIntOrNull(),
                text(node, "capacityInKB")?.toLongOrNull(), text(backing, "fileName") ?: text(backing, "deviceName"),
                backing?.let(::kind), text(backing, "network"), text(port, "switchUuid"),
                text(port, "portgroupKey"), text(node, "macAddress"),
                text(connect, "connected")?.toBooleanStrictOrNull(),
                text(connect, "startConnected")?.toBooleanStrictOrNull())
        }
        return VmConfig(vmRef, version, power,
            text(hardware, "numCPU")?.toIntOrNull() ?: throw SoapFailure("Thiếu số CPU."),
            text(hardware, "memoryMB")?.toLongOrNull() ?: throw SoapFailure("Thiếu RAM."),
            text(hardware, "numCoresPerSocket")?.toIntOrNull() ?: 1,
            text(config, "cpuHotAddEnabled") == "true", text(config, "memoryHotAddEnabled") == "true",
            text(config, "guestId"), text(config, "firmware"), text(child(config, "bootOptions"), "efiSecureBootEnabled") == "true",
            text(child(config, "bootOptions"), "bootDelay")?.toIntOrNull(), text(hardware, "version"), devices)
    }

    fun validate(config: VmConfig, command: AdvancedCommand, inventory: InventorySnapshot): String? {
        if (config.numCpu <= 0 || config.memoryMb <= 0) return "Cấu hình VM hiện tại không hợp lệ."
        return when (command) {
            is AdvancedCommand.Edit -> when (val change = command.change) {
                is AdvancedChange.Resources -> when {
                    change.cpu !in 1..768 || change.memoryMb !in 4..25_165_824L || change.cores !in 1..change.cpu || change.cpu % change.cores != 0 -> "CPU/RAM hoặc topology không hợp lệ."
                    config.power != "poweredOff" && (change.cores != config.coresPerSocket ||
                        change.cpuHotAdd != config.cpuHotAdd || change.memoryHotAdd != config.memoryHotAdd) -> "Thay đổi topology/hot add cần tắt VM."
                    config.power == "poweredOn" && ((change.cpu != config.numCpu && (!config.cpuHotAdd || change.cpu < config.numCpu)) ||
                        (change.memoryMb != config.memoryMb && (!config.memoryHotAdd || change.memoryMb < config.memoryMb))) -> "VM đang chạy chỉ cho phép tăng CPU/RAM khi hot add đã bật."
                    config.power == "suspended" -> "VM tạm dừng không hỗ trợ thay đổi tài nguyên."
                    else -> null
                }
                is AdvancedChange.ExpandDisk -> {
                    val disk = config.devices.firstOrNull { it.key == change.key && it.type == "VirtualDisk" }
                    when {
                        disk?.capacityKb == null || disk.backingType != "VirtualDiskFlatVer2BackingInfo" -> "Chỉ tăng dung lượng VMDK flat hiện có."
                        change.sizeGb <= disk.capacityKb / 1_048_576L || change.sizeGb !in 1..62_000L -> "Dung lượng mới phải lớn hơn dung lượng hiện tại."
                        else -> null
                    }
                }
                is AdvancedChange.RemoveDevice -> when {
                    config.power != "poweredOff" -> "Tháo thiết bị cần tắt VM."
                    config.devices.none { it.key == change.key && it.type in setOf("VirtualDisk", "VirtualCdrom", "VirtualE1000", "VirtualE1000e", "VirtualVmxnet3") } -> "Chỉ tháo disk/NIC/CD hiện có. Không xóa file backing."
                    else -> null
                }
                is AdvancedChange.AddDisk -> when {
                    config.power != "poweredOff" -> "Thêm disk cần tắt VM."
                    config.devices.none { it.key == change.controller && it.type in setOf("VirtualLsiLogicController", "VirtualLsiLogicSASController", "ParaVirtualSCSIController", "VirtualAHCIController", "VirtualNVMEController") } -> "Controller không được hỗ trợ hoặc không tồn tại."
                    change.unit !in 0..15 || (change.unit == 7 && config.devices.any { it.key == change.controller && it.type.contains("SCSI", true) }) ||
                        config.devices.any { it.controller == change.controller && it.unit == change.unit } -> "Unit đã dùng hoặc không hợp lệ."
                    change.path == null && change.sizeGb !in 1..62_000L -> "Dung lượng disk không hợp lệ."
                    change.path != null && !validDatastorePath(change.path) -> "Đường dẫn VMDK phải là [datastore] path.vmdk."
                    else -> null
                }
                is AdvancedChange.AddNic -> when {
                    config.power != "poweredOff" -> "Thêm NIC cần tắt VM."
                    change.type !in setOf("VirtualVmxnet3", "VirtualE1000e") -> "Adapter không hỗ trợ."
                    else -> networkReason(change.networkType, change.networkRef, change.switchUuid, change.portKey, inventory)
                }
                is AdvancedChange.EditNic -> when {
                    config.power != "poweredOff" -> "Sửa backing NIC cần tắt VM."
                    config.devices.none { it.key == change.key && it.type in setOf("VirtualVmxnet3", "VirtualE1000e", "VirtualE1000") } -> "NIC không tồn tại hoặc adapter chưa hỗ trợ."
                    else -> networkReason(change.networkType, change.networkRef, change.switchUuid, change.portKey, inventory)
                }
                is AdvancedChange.Iso -> when {
                    config.power != "poweredOff" -> "Sửa ISO cần tắt VM."
                    config.devices.none { it.key == change.key && it.type == "VirtualCdrom" } -> "CD/DVD không tồn tại."
                    !validDatastorePath(change.path) || !change.path.endsWith(".iso", true) -> "ISO cần đường dẫn [datastore] path.iso."
                    else -> null
                }
                is AdvancedChange.Boot -> when {
                    config.power != "poweredOff" -> "Boot/firmware cần tắt VM."
                    change.delay !in 0..10_000 || change.firmware !in setOf("bios", "efi") || (change.secureBoot && change.firmware != "efi") -> "Boot delay/firmware không hợp lệ."
                    else -> null
                }
            }
            is AdvancedCommand.Clone -> when {
                config.power != "poweredOff" -> "Clone an toàn trong app chỉ hỗ trợ VM đã tắt."
                command.name.isBlank() || command.name.length > 80 -> "Tên clone cần 1-80 ký tự."
                VmPlacement(inventory, command.host).folders.none { it.ref == command.folder } -> "Chọn thư mục VM thuộc datacenter của ESXi đích."
                else -> targetReason(command.host, command.pool, command.datastore, inventory)
            }
            is AdvancedCommand.Migrate -> when {
                config.power != "poweredOff" -> "Migration trong app chỉ hỗ trợ VM đã tắt."
                command.datastore == null && command.host == inventory.vms.firstOrNull { it.ref == config.vmRef }
                    ?.links?.get("runtime.host")?.firstOrNull() -> "Host đích trùng host hiện tại."
                command.datastore == null && !VmPlacement(inventory, command.host).canKeepDatastores(config.vmRef) ->
                    "ESXi đích không truy cập đủ datastore hiện tại; chọn datastore đích."
                else -> targetReason(command.host, command.pool, command.datastore, inventory)
            }
        }
    }

    private fun validDatastorePath(path: String) = Regex("^\\[[^]\\r\\n]+] [^\\r\\n<>]+$").matches(path.trim()) &&
        !path.contains("..")

    private fun networkReason(type: String, ref: String, uuid: String?, key: String?, inventory: InventorySnapshot): String? {
        val item = inventory.networks.firstOrNull { it.ref == ref && it.type == type }
            ?: return "Network đích không có trong inventory."
        return when (type) {
            "Network" -> null
            "DistributedVirtualPortgroup" -> if (uuid.isNullOrBlank() || key.isNullOrBlank() ||
                inventory.ofType("VmwareDistributedVirtualSwitch").none { it.get("uuid") == uuid })
                "Distributed port group thiếu switch UUID / portgroup key." else null
            else -> "Loại network chưa hỗ trợ."
        }
    }

    private fun targetReason(host: String, pool: String, datastore: String?, inventory: InventorySnapshot): String? = when {
        inventory.hosts.none { it.ref == host && it.get("runtime.connectionState") == "connected" &&
            it.get("runtime.inMaintenanceMode") == "false" } -> "Host đích phải kết nối và không maintenance."
        VmPlacement(inventory, host).pools.none { it.ref == pool } -> "Resource pool không thuộc compute/cluster của ESXi đích."
        datastore != null && VmPlacement(inventory, host).datastores.none { it.ref == datastore } -> "ESXi đích không truy cập được datastore đã chọn."
        else -> null
    }

    private fun backing(type: String, ref: String, uuid: String?, portKey: String?): String = if (type == "Network")
        "<vim:backing xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"vim:VirtualEthernetCardNetworkBackingInfo\">" +
            field("deviceName", "") + "<vim:network type=\"Network\">${e(ref)}</vim:network></vim:backing>"
    else "<vim:backing xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"vim:VirtualEthernetCardDistributedVirtualPortBackingInfo\">" +
        "<vim:port>${field("switchUuid", uuid!!)}${field("portgroupKey", portKey!!)}</vim:port></vim:backing>"

    private fun deviceSpec(operation: String, type: String, body: String, fileOperation: String = "") =
        "<vim:deviceChange><vim:operation>$operation</vim:operation>$fileOperation" +
            "<vim:device xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"vim:$type\">$body</vim:device></vim:deviceChange>"

    fun request(operation: AdvancedOperation, inventory: InventorySnapshot): String {
        val config = operation.baseline
        val reason = validate(config, operation.command, inventory)
        require(reason == null) { reason ?: "Invalid operation" }
        val command = operation.command
        return when (command) {
            is AdvancedCommand.Edit -> {
                val changes = when (val c = command.change) {
                    is AdvancedChange.Resources -> listOf(field("numCPUs", c.cpu), field("memoryMB", c.memoryMb),
                        field("numCoresPerSocket", c.cores), field("cpuHotAddEnabled", c.cpuHotAdd),
                        field("memoryHotAddEnabled", c.memoryHotAdd)).joinToString("")
                    is AdvancedChange.Boot -> field("firmware", c.firmware) + "<vim:bootOptions>" +
                        field("bootDelay", c.delay) + field("efiSecureBootEnabled", c.secureBoot) + "</vim:bootOptions>"
                    is AdvancedChange.RemoveDevice -> {
                        val d = config.devices.first { it.key == c.key }
                        deviceSpec("remove", d.type, field("key", d.key))
                    }
                    is AdvancedChange.ExpandDisk -> {
                        val d = config.devices.first { it.key == c.key }
                        deviceSpec("edit", "VirtualDisk", field("key", d.key) + field("capacityInKB", c.sizeGb * 1_048_576L))
                    }
                    is AdvancedChange.AddDisk -> {
                        val diskBacking = "<vim:backing xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"vim:VirtualDiskFlatVer2BackingInfo\">" +
                            (c.path?.let { field("fileName", it) } ?: field("fileName", "")) +
                            field("diskMode", "persistent") + field("thinProvisioned", true) + "</vim:backing>"
                        deviceSpec("add", "VirtualDisk", field("key", -1) + field("controllerKey", c.controller) +
                            field("unitNumber", c.unit) + (if (c.path == null) field("capacityInKB", c.sizeGb * 1_048_576L) else "") + diskBacking,
                            if (c.path == null) "<vim:fileOperation>create</vim:fileOperation>" else "")
                    }
                    is AdvancedChange.AddNic -> deviceSpec("add", c.type, field("key", -1) +
                        field("addressType", "generated") + backing(c.networkType, c.networkRef, c.switchUuid, c.portKey) +
                        "<vim:connectable>${field("startConnected", true)}${field("allowGuestControl", true)}</vim:connectable>")
                    is AdvancedChange.EditNic -> {
                        val d = config.devices.first { it.key == c.key }
                        deviceSpec("edit", d.type, field("key", d.key) +
                            field("addressType", if (d.mac.isNullOrBlank()) "generated" else "manual") +
                            (d.mac?.let { field("macAddress", it) } ?: "") +
                            backing(c.networkType, c.networkRef, c.switchUuid, c.portKey) +
                            "<vim:connectable>${field("startConnected", c.startConnected)}${field("allowGuestControl", true)}</vim:connectable>")
                    }
                    is AdvancedChange.Iso -> deviceSpec("edit", "VirtualCdrom", field("key", c.key) +
                        "<vim:backing xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"vim:VirtualCdromIsoBackingInfo\">" +
                        field("fileName", c.path) + "</vim:backing>" +
                        "<vim:connectable>${field("startConnected", c.startConnected)}${field("allowGuestControl", true)}</vim:connectable>")
                }
                VsphereSoap.envelope("ReconfigVM_Task", mor("VirtualMachine", operation.vmRef) +
                    "<vim:spec>${field("changeVersion", config.changeVersion)}$changes</vim:spec>")
            }
            is AdvancedCommand.Clone -> VsphereSoap.envelope("CloneVM_Task", mor("VirtualMachine", operation.vmRef) +
                "<vim:folder type=\"Folder\">${e(command.folder)}</vim:folder>${field("name", command.name.trim())}" +
                "<vim:spec><vim:location><vim:datastore type=\"Datastore\">${e(command.datastore)}</vim:datastore>" +
                "<vim:pool type=\"ResourcePool\">${e(command.pool)}</vim:pool>" +
                "<vim:host type=\"HostSystem\">${e(command.host)}</vim:host></vim:location>" +
                field("powerOn", false) + field("template", false) + "</vim:spec>")
            is AdvancedCommand.Migrate -> if (command.datastore == null)
                VsphereSoap.envelope("MigrateVM_Task", mor("VirtualMachine", operation.vmRef) +
                    "<vim:pool type=\"ResourcePool\">${e(command.pool)}</vim:pool>" +
                    "<vim:host type=\"HostSystem\">${e(command.host)}</vim:host>${field("priority", "defaultPriority")}" +
                    field("state", "poweredOff"))
            else VsphereSoap.envelope("RelocateVM_Task", mor("VirtualMachine", operation.vmRef) +
                "<vim:spec><vim:datastore type=\"Datastore\">${e(command.datastore)}</vim:datastore>" +
                "<vim:pool type=\"ResourcePool\">${e(command.pool)}</vim:pool>" +
                "<vim:host type=\"HostSystem\">${e(command.host)}</vim:host></vim:spec>" +
                field("priority", "defaultPriority"))
        }
    }

    fun method(command: AdvancedCommand) = when (command) {
        is AdvancedCommand.Edit -> "ReconfigVM_Task"
        is AdvancedCommand.Clone -> "CloneVM_Task"
        is AdvancedCommand.Migrate -> if (command.datastore == null) "MigrateVM_Task" else "RelocateVM_Task"
    }

    fun taskRef(xml: String, method: String): String {
        val task = child(VsphereSoap.result(xml, method), "returnval")
            ?: throw SoapFailure("vCenter không trả về Task.")
        return task.textContent.trim().takeIf { task.getAttribute("type") == "Task" && it.isNotEmpty() }
            ?: throw SoapFailure("Task reference không hợp lệ.")
    }
}
