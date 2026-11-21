package vn.webnew.vcenter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VmAdvancedPanel(inventory: InventorySnapshot, vmRef: String, config: VmConfig?,
    loading: Boolean, error: String?, busy: Boolean, blocked: Boolean,
    load: () -> Unit, execute: (AdvancedOperation) -> Unit, requestedMode: String? = null,
    onDismiss: () -> Unit = {}) {
    var opened by remember(vmRef, requestedMode) { mutableStateOf(requestedMode != null) }
    if (requestedMode == null) OutlinedButton(onClick = { opened = true }, modifier = Modifier.testTag("advanced-toggle")) {
        Text("Cấu hình VM")
    }
    if (!opened) return
    val close = { opened = false; onDismiss() }
    val title = when (requestedMode) { "Migration" -> "Di chuyển VM"; "Clone" -> "Nhân bản VM"; else -> "Cấu hình VM" }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = 12.dp, vertical = 24.dp).fillMaxWidth().imePadding()
            .testTag("advanced-dialog"), color = Color(0xFF21333B), shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF495865))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(inventory.name(vmRef), color = Color(0xFFBACBD6), fontSize = 13.sp)
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Đang tải cấu hình VM…") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (blocked) Text("Không đọc được lịch sử lệnh cục bộ; chưa thể gửi thay đổi.", color = MaterialTheme.colorScheme.error)
                    if (config != null && config.vmRef == vmRef && !loading && error == null) {
                        key(config.fingerprint()) { AdvancedEditor(inventory, config, busy, blocked, execute, requestedMode) }
                    }
                }
                HorizontalDivider(color = Color(0xFF495865))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = load, enabled = !loading && !busy,
                        modifier = Modifier.testTag("advanced-load")) { Text("Tải lại cấu hình") }
                    TextButton(onClick = close, modifier = Modifier.testTag("advanced-close")) { Text("Đóng") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedEditor(inventory: InventorySnapshot, config: VmConfig, busy: Boolean,
    blocked: Boolean, execute: (AdvancedOperation) -> Unit, requestedMode: String?) {
    val modes = if (requestedMode in listOf("Clone", "Migration")) listOf(requestedMode!!) else
        listOf("CPU / RAM", "Tăng disk", "Thêm disk", "Gắn VMDK", "Tháo thiết bị", "Thêm NIC", "Sửa NIC", "ISO", "Boot")
    var mode by remember(requestedMode) { mutableStateOf(requestedMode?.takeIf { it in modes } ?: modes.first()) }
    var pending by remember { mutableStateOf<AdvancedOperation?>(null) }
    Text(when (config.power) { "poweredOn" -> "Đang chạy"; "poweredOff" -> "Đã tắt"; "suspended" -> "Tạm ngưng"; else -> "Chưa rõ trạng thái nguồn" },
        color = Color(0xFFBACBD6), fontSize = 12.sp)
    if (modes.size > 1) AdvancedChoice("Thiết lập", modes, mode, { mode = it }) { it }
    if (mode in listOf("Clone", "Migration")) Text("Hiện hỗ trợ VM đã tắt. vCenter kiểm tra quyền và khả năng tương thích khi gửi lệnh.",
        fontSize = 12.sp, color = Color(0xFFBACBD6))
    key(mode) {
        var cpu by remember { mutableStateOf(config.numCpu.toString()) }
        var ram by remember { mutableStateOf(config.memoryMb.toString()) }
        var cores by remember { mutableStateOf(config.coresPerSocket.toString()) }
        var cpuHot by remember { mutableStateOf(config.cpuHotAdd) }
        var ramHot by remember { mutableStateOf(config.memoryHotAdd) }
        var device by remember { mutableStateOf<String?>(null) }
        var controller by remember { mutableStateOf<String?>(null) }
        var unit by remember { mutableStateOf("") }
        var size by remember { mutableStateOf("") }
        var path by remember { mutableStateOf("") }
        var network by remember { mutableStateOf<String?>(null) }
        var adapter by remember { mutableStateOf("VirtualVmxnet3") }
        var startConnected by remember { mutableStateOf(false) }
        var delay by remember { mutableStateOf(config.bootDelay?.toString() ?: "") }
        var firmware by remember { mutableStateOf(config.firmware) }
        var secureBoot by remember { mutableStateOf(config.secureBoot) }
        var cloneName by remember { mutableStateOf("") }
        var host by remember { mutableStateOf<String?>(null) }
        val placement = VmPlacement(inventory, host)
        var folder by remember(host) { mutableStateOf<String?>(null) }
        var pool by remember(host) { mutableStateOf<String?>(null) }
        var datastore by remember(host) {
            mutableStateOf<String?>(if (mode == "Migration" && placement.canKeepDatastores(config.vmRef)) "" else null)
        }
        var attempted by remember { mutableStateOf(false) }
        val devices = config.devices.filter { when (mode) {
            "Tăng disk" -> it.type == "VirtualDisk"
            "Sửa NIC" -> it.type in setOf("VirtualVmxnet3", "VirtualE1000e", "VirtualE1000")
            "ISO" -> it.type == "VirtualCdrom"
            else -> it.type in setOf("VirtualDisk", "VirtualCdrom", "VirtualVmxnet3", "VirtualE1000e", "VirtualE1000")
        } }
        val selectedDevice = devices.firstOrNull { it.key.toString() == device }
        when (mode) {
            "CPU / RAM" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { AdvancedField("vCPU", cpu) { cpu = it } }
                    Box(Modifier.weight(1f)) { AdvancedField("Core mỗi socket", cores) { cores = it } }
                }
                AdvancedField("RAM (MiB)", ram) { ram = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdvancedCheck("CPU hot add", cpuHot) { cpuHot = it }
                    AdvancedCheck("RAM hot add", ramHot) { ramHot = it }
                }
            }
            "Tăng disk", "Tháo thiết bị", "Sửa NIC", "ISO" -> {
                AdvancedChoice("Thiết bị", devices.map { it.key.toString() }, device, { key ->
                    device = key
                    devices.firstOrNull { it.key.toString() == key }?.let { startConnected = it.startConnected == true }
                }) { key -> devices.first { it.key.toString() == key }.let { "${it.label} · key ${it.key}" } }
                selectedDevice?.let { Text("${it.type} · ${it.capacityKb?.let { kb -> "${kb / 1_048_576.0} GiB" } ?: ""} · ${it.backing ?: it.networkRef ?: ""}") }
                if (mode == "Tháo thiết bị") Text("Chỉ tháo thiết bị khỏi VM, không xóa file backing.")
                if (mode == "Tăng disk") AdvancedField("Dung lượng mới (GiB)", size) { size = it }
            }
            "Thêm disk", "Gắn VMDK" -> {
                val controllers = config.devices.filter { it.type in setOf("VirtualLsiLogicController", "VirtualLsiLogicSASController", "ParaVirtualSCSIController", "VirtualAHCIController", "VirtualNVMEController") }
                AdvancedChoice("Controller", controllers.map { it.key.toString() }, controller, { controller = it }) { key ->
                    controllers.first { it.key.toString() == key }.let { "${it.label} · ${it.key}" }
                }
                AdvancedField("Unit number", unit) { unit = it }
                if (mode == "Thêm disk") AdvancedField("Dung lượng (GiB)", size) { size = it }
                else AdvancedField("Đường dẫn [datastore] folder/disk.vmdk", path) { path = it }
            }
        }
        if (mode in listOf("Thêm NIC", "Sửa NIC")) {
            if (mode == "Thêm NIC") AdvancedChoice("Adapter", listOf("VirtualVmxnet3", "VirtualE1000e"), adapter, { adapter = it }) { it }
            val networks = inventory.networks.filter { it.type in setOf("Network", "DistributedVirtualPortgroup") }
            AdvancedChoice("Network", networks.map { it.ref }, network, { network = it }) { inventory.name(it) + " · " + it }
            if (mode == "Sửa NIC") AdvancedCheck("Kết nối khi bật VM", startConnected) { startConnected = it }
        }
        if (mode == "ISO") {
            AdvancedField("Đường dẫn [datastore] folder/image.iso", path) { path = it }
            AdvancedCheck("Kết nối khi bật VM", startConnected) { startConnected = it }
        }
        if (mode == "Boot") {
            AdvancedField("Boot delay (ms)", delay) { delay = it }
            AdvancedChoice("Firmware", listOf("bios", "efi"), firmware, { firmware = it }) { it }
            AdvancedCheck("EFI Secure Boot", secureBoot) { secureBoot = it }
        }
        if (mode in listOf("Clone", "Migration")) {
            val hosts = inventory.hosts.filter { it.get("runtime.connectionState") == "connected" &&
                it.get("runtime.inMaintenanceMode") == "false" }
            AdvancedChoice("ESXi đích", hosts.map { it.ref }, host, { host = it }) { inventory.name(it) }
            if (host == null) Text("Chọn ESXi đích để tải các lựa chọn phù hợp.",
                fontSize = 12.sp, color = Color(0xFFBACBD6))
            if (host != null) {
                if (mode == "Clone") {
                    AdvancedField("Tên VM mới", cloneName) { cloneName = it }
                    AdvancedChoice("Folder đích", placement.folders.map { it.ref }, folder, { folder = it }) { inventory.name(it) }
                }
                AdvancedChoice("Resource pool đích", placement.pools.map { it.ref }, pool, { pool = it }) { inventory.name(it) }
                val stores = placement.datastores.map { it.ref }
                val keep = mode == "Migration" && placement.canKeepDatastores(config.vmRef)
                AdvancedChoice("Datastore đích", if (keep) listOf("") + stores else stores,
                    datastore, { datastore = it }) { if (it.isEmpty()) "Giữ datastore hiện tại" else inventory.name(it) }
            }
        }
        val net = inventory.networks.firstOrNull { it.ref == network }
        val switchRef = net?.links?.get("config.distributedVirtualSwitch")?.firstOrNull()
        val switchUuid = inventory.ofType("VmwareDistributedVirtualSwitch").firstOrNull { it.ref == switchRef }?.get("uuid")
        val portKey = net?.get("key")
        val change: AdvancedChange? = when (mode) {
            "CPU / RAM" -> cpu.toIntOrNull()?.let { c -> ram.toLongOrNull()?.let { m -> cores.toIntOrNull()?.let { AdvancedChange.Resources(c, m, it, cpuHot, ramHot) } } }
            "Tăng disk" -> selectedDevice?.let { d -> size.toLongOrNull()?.let { AdvancedChange.ExpandDisk(d.key, it) } }
            "Tháo thiết bị" -> selectedDevice?.let { AdvancedChange.RemoveDevice(it.key) }
            "Thêm disk", "Gắn VMDK" -> controller?.toIntOrNull()?.let { c -> unit.toIntOrNull()?.let { u ->
                if (mode == "Gắn VMDK") AdvancedChange.AddDisk(c, u, 0, path.trim())
                else size.toLongOrNull()?.let { AdvancedChange.AddDisk(c, u, it) }
            } }
            "Thêm NIC" -> net?.let { AdvancedChange.AddNic(adapter, it.ref, it.type, switchUuid, portKey) }
            "Sửa NIC" -> selectedDevice?.let { d -> net?.let { AdvancedChange.EditNic(d.key, it.ref, it.type, switchUuid, portKey, startConnected) } }
            "ISO" -> selectedDevice?.let { AdvancedChange.Iso(it.key, path.trim(), startConnected) }
            "Boot" -> delay.toIntOrNull()?.let { d -> firmware?.let { AdvancedChange.Boot(d, it, secureBoot) } }
            else -> null
        }
        val command: AdvancedCommand? = when (mode) {
            "Clone" -> folder?.let { f -> host?.let { h -> pool?.let { p -> datastore?.let { AdvancedCommand.Clone(cloneName.trim(), f, p, h, it) } } } }
            "Migration" -> host?.let { h -> pool?.let { p -> datastore?.let { AdvancedCommand.Migrate(h, p, it.takeIf(String::isNotEmpty)) } } }
            else -> change?.let { AdvancedCommand.Edit(it) }
        }
        val reason = when {
            busy -> "Đang xử lý lệnh."
            blocked -> "Không đọc được lịch sử lệnh cục bộ."
            command == null -> "Nhập đầy đủ giá trị hợp lệ và chọn đối tượng đích."
            else -> VmAdvancedSoap.validate(config, command, inventory)
        }
        if (attempted) reason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy && !blocked, onClick = {
            attempted = true
            if (reason == null) command?.let { pending = AdvancedOperation(config.vmRef, config, it) }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("advanced-review"),
            shape = RoundedCornerShape(7.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4AAED9), contentColor = Color(0xFF0E161B))) {
            Text("Tiếp tục")
        }
    }
    pending?.let { operation ->
        val reason = when {
            busy || blocked -> "Đang gửi yêu cầu hoặc không đọc được lịch sử lệnh cục bộ."
            else -> VmAdvancedSoap.validate(config, operation.command, inventory)
        }
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(when (operation.command) {
            is AdvancedCommand.Clone -> "Xác nhận nhân bản"
            is AdvancedCommand.Migrate -> "Xác nhận di chuyển"
            is AdvancedCommand.Edit -> "Xác nhận cấu hình"
        }) },
            text = { Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                Text("VM: ${inventory.name(config.vmRef)} · ${config.vmRef}")
                Text(advancedSummary(operation.command, inventory, config))
                reason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = { TextButton(enabled = reason == null, onClick = {
                execute(operation); pending = null
            }, modifier = Modifier.testTag("advanced-confirm")) { Text("Xác nhận gửi lệnh") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Hủy") } })
    }
}

@Composable
private fun AdvancedField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, { change(it.take(4000)) }, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun AdvancedCheck(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(value, change); Text(label) }
}

@Composable
private fun AdvancedChoice(label: String, options: List<String>, selected: String?,
    change: (String) -> Unit, name: (String) -> String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 12.sp, color = Color(0xFFBACBD6))
        Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("advanced-choice-$label"),
            shape = RoundedCornerShape(7.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            Text(selected?.let(name) ?: "Chọn $label", modifier = Modifier.weight(1f), color = Color(0xFFF3F6F8))
            Text("▾", color = Color(0xFF7AD2EF))
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            options.forEach { option -> DropdownMenuItem(text = { Text(name(option)) }, onClick = {
                change(option); expanded = false
            }) }
        }
    }
        if (options.isEmpty()) Text("Không có $label trong dữ liệu đã tải.", fontSize = 12.sp, color = Color(0xFFBACBD6))
    }
}

private fun advancedSummary(command: AdvancedCommand, inventory: InventorySnapshot, config: VmConfig): String {
    fun device(key: Int) = config.devices.firstOrNull { it.key == key }?.label ?: "Thiết bị $key"
    return when (command) {
        is AdvancedCommand.Clone -> "Tên VM mới: ${command.name}\nFolder: ${inventory.name(command.folder)}\nHost: ${inventory.name(command.host)}\nResource pool: ${inventory.name(command.pool)}\nDatastore: ${inventory.name(command.datastore)}"
        is AdvancedCommand.Migrate -> "Host: ${inventory.name(command.host)}\nResource pool: ${inventory.name(command.pool)}\nDatastore: ${command.datastore?.let(inventory::name) ?: "Giữ datastore hiện tại"}"
        is AdvancedCommand.Edit -> when (val change = command.change) {
            is AdvancedChange.Resources -> "vCPU: ${config.numCpu} → ${change.cpu}\nRAM (MiB): ${config.memoryMb} → ${change.memoryMb}\nCore mỗi socket: ${change.cores}\nCPU hot add: ${if (change.cpuHotAdd) "Bật" else "Tắt"}\nRAM hot add: ${if (change.memoryHotAdd) "Bật" else "Tắt"}"
            is AdvancedChange.ExpandDisk -> "${device(change.key)}\nDung lượng mới: ${change.sizeGb} GiB"
            is AdvancedChange.RemoveDevice -> "Tháo ${device(change.key)}\nGiữ nguyên file dữ liệu."
            is AdvancedChange.AddDisk -> "${if (change.path == null) "Thêm disk ${change.sizeGb} GiB" else "Gắn VMDK: ${change.path}"}\nController: ${device(change.controller)}\nUnit: ${change.unit}"
            is AdvancedChange.AddNic -> "Thêm card mạng ${change.type}\nNetwork: ${inventory.name(change.networkRef)}"
            is AdvancedChange.EditNic -> "${device(change.key)}\nNetwork: ${inventory.name(change.networkRef)}\nKết nối khi bật VM: ${if (change.startConnected) "Có" else "Không"}"
            is AdvancedChange.Iso -> "${device(change.key)}\nISO: ${change.path}\nKết nối khi bật VM: ${if (change.startConnected) "Có" else "Không"}"
            is AdvancedChange.Boot -> "Firmware: ${change.firmware}\nBoot delay: ${change.delay} ms\nSecure Boot: ${if (change.secureBoot) "Bật" else "Tắt"}"
        }
    }
}
