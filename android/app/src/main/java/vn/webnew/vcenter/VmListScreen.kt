package vn.webnew.vcenter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import java.text.DateFormat
import java.util.Date

private val vmMuted = Color(0xFFBACBD6)
private val vmAccent = Color(0xFF4AAED9)
private val vmBorder = Color(0xFF495865)

@Composable
private fun VmTreeBranch(id: String, title: String, subtitle: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(id) { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(color = Color(0xFF29414E), shape = RoundedCornerShape(7.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.testTag("vm-tree-$id").heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (expanded) "▾" else "▸", color = vmAccent)
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = vmMuted, fontSize = 11.sp)
                }
            }
        }
        if (expanded) Column(Modifier.padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun VmTreeContent(inventory: InventorySnapshot, shown: List<InventoryItem>, filtering: Boolean,
    onSelect: (InventoryItem) -> Unit) {
    val tree = vmInventoryTree(inventory, shown)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tree.clusters.forEach { branch ->
            val hosts = branch.hosts.filter { !filtering || it.vms.isNotEmpty() }
            if (!filtering || hosts.isNotEmpty()) key(branch.cluster.ref) {
                VmTreeBranch(branch.cluster.ref, "Cluster · ${branch.cluster.get("name") ?: branch.cluster.ref}",
                    "${hosts.size} ESXi · ${hosts.sumOf { it.vms.size }} máy ảo") {
                    hosts.forEach { host -> key(host.host.ref) { VmHostContent(inventory, host, onSelect) } }
                    if (hosts.isEmpty()) Text("Không có ESXi trong phạm vi dữ liệu trả về.", color = vmMuted, fontSize = 11.sp)
                }
            }
        }
        tree.standalone.filter { !filtering || it.vms.isNotEmpty() }.forEach { host ->
            key(host.host.ref) { VmHostContent(inventory, host, onSelect) }
        }
        if (tree.unassigned.isNotEmpty()) {
            VmTreeBranch("unassigned", "Chưa xác định ESXi / Cluster", "${tree.unassigned.size} máy ảo") {
                VmInventoryTable(inventory, tree.unassigned, onSelect)
            }
        }
    }
}

@Composable
private fun VmHostContent(inventory: InventorySnapshot, branch: VmHostBranch, onSelect: (InventoryItem) -> Unit) {
    VmTreeBranch(branch.host.ref, "ESXi · ${branch.host.get("name") ?: branch.host.ref}", "${branch.vms.size} máy ảo") {
        if (branch.vms.isEmpty()) Text("Không có máy ảo.", color = vmMuted, fontSize = 11.sp)
        else VmInventoryTable(inventory, branch.vms, onSelect)
    }
}

@Composable
internal fun VmListScreen(inventory: InventorySnapshot, state: ConnectionState = ConnectionState(),
    operate: ((VmOperation) -> Unit)? = null, loadConfig: ((String) -> Unit)? = null,
    advanced: ((AdvancedOperation) -> Unit)? = null) {
    var search by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(VmPowerFilter.All.name) }
    var menu by remember { mutableStateOf(false) }
    var selectedRef by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = VmPowerFilter.valueOf(filterName)
    val shown = filterVms(inventory.vms, search, filter)
    Surface(color = Color(0xFF21333B), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF0E161B)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().testTag("vm-list-toolbar"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = Color(0xFF1B2A32), shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, vmBorder), modifier = Modifier.weight(1f)) {
                    BasicTextField(value = search, onValueChange = { search = it }, singleLine = true,
                        textStyle = TextStyle(color = Color(0xFFF3F6F8), fontSize = 11.sp), cursorBrush = SolidColor(vmAccent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(12.dp).testTag("vm-search"),
                        decorationBox = { input ->
                            Box { if (search.isEmpty()) Text("Tìm máy ảo…", color = vmMuted, fontSize = 11.sp); input() }
                        })
                }
                Box {
                    OutlinedButton(onClick = { menu = true }, shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, vmBorder), contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.widthIn(min = 135.dp).testTag("vm-power-filter")) {
                        Text(if (filter == VmPowerFilter.All) "Tất cả trạng thái ▾" else "${filter.label} ▾", fontSize = 11.sp, color = vmMuted)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        VmPowerFilter.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { filterName = option.name; menu = false })
                        }
                    }
                }
            }
            VmTreeContent(inventory, shown, search.isNotBlank() || filter != VmPowerFilter.All) { selectedRef = it.ref }
            if (shown.isEmpty()) Text(if (inventory.vms.isEmpty()) "Không có đối tượng trả về trong phạm vi quyền hiện tại."
                else "Không có máy ảo phù hợp.", color = vmMuted, fontSize = 12.sp)
            Text("Hiển thị ${shown.size}/${inventory.vms.size} máy ảo", color = vmMuted, fontSize = 11.sp)
        }
    }
    inventory.vms.firstOrNull { it.ref == selectedRef }?.let { vm ->
        VmDetailDialog(inventory, vm, state, operate, loadConfig, advanced) { selectedRef = null }
    }
}

@Composable
internal fun VmDetailDialog(inventory: InventorySnapshot, vm: InventoryItem, state: ConnectionState,
    operate: ((VmOperation) -> Unit)?, loadConfig: ((String) -> Unit)? = null,
    advanced: ((AdvancedOperation) -> Unit)? = null, onDismiss: () -> Unit) {
    val power = vm.get("runtime.powerState")
    val status = when (power) {
        "poweredOn" -> "Đang chạy"
        "poweredOff" -> "Đã tắt"
        "suspended" -> "Tạm ngưng"
        else -> "Chưa rõ"
    }
    val memory = vm.get("config.hardware.memoryMB")?.toLongOrNull()?.takeIf { it >= 0 }?.let {
        String.format(Locale.US, "%.1f", it / 1024.0).removeSuffix(".0") + " GiB"
    } ?: "—"
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp)
            .fillMaxWidth().testTag("vm-detail"), color = Color(0xFF21333B),
            shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, vmBorder)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vm.get("name") ?: vm.ref, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Surface(color = if (power == "poweredOn") Color(0xFF284638) else Color(0xFF29414E),
                        shape = RoundedCornerShape(5.dp)) {
                        Text(status, color = if (power == "poweredOn") Color(0xFF9DD18E) else vmMuted,
                            fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                }
                Text("${vm.get("config.guestFullName") ?: "Chưa có OS"} · ${vm.get("guest.ipAddress") ?: "Chưa có IP"}",
                    color = vmMuted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VmDetailMetric("CPU / RAM", "${vm.get("config.hardware.numCPU") ?: "—"} vCPU / $memory",
                        Modifier.weight(1f).testTag("vm-detail-resources"))
                    VmDetailMetric("Host / Datastore",
                        "${vm.links["runtime.host"]?.firstOrNull()?.let(inventory::name) ?: "Chưa có host"} / " +
                            (vm.links["datastore"]?.takeIf { it.isNotEmpty() }?.joinToString { inventory.name(it) } ?: "Chưa có datastore"),
                        Modifier.weight(1f).testTag("vm-detail-placement"))
                }
                VmActionsContent(vm, state.operationBusy,
                    state.operationError != null || state.pendingOperations.any {
                        it.vmRef == vm.ref && it.status !in OperationPolicy.terminal
                    }, detailLayout = true, available = operate != null, execute = operate ?: {})
                if (vm.missing.isNotEmpty()) Text("Property không trả về: ${vm.missing.joinToString()}",
                    color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                state.operationError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                if (loadConfig != null && advanced != null) VmAdvancedPanel(inventory, vm.ref,
                    state.vmConfig?.takeIf { it.vmRef == vm.ref }, state.vmConfigLoading,
                    state.vmConfigError, state.operationBusy,
                    state.operationError != null || state.pendingOperations.any {
                        it.vmRef == vm.ref && it.status !in OperationPolicy.terminal
                    }, load = { loadConfig(vm.ref) }, execute = advanced)
                state.advancedVerification?.let { Text(it, color = vmMuted, fontSize = 11.sp) }
                Text("Console chưa tích hợp. M4 chưa nghiệm thu trên vCenter thật.", color = vmMuted, fontSize = 11.sp)
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, vmAccent), contentPadding = PaddingValues(16.dp, 10.dp),
                    modifier = Modifier.align(Alignment.End).testTag("vm-detail-close")) {
                    Text("Đóng", color = Color(0xFF7AD2EF), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun VmDetailMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, color = Color.Transparent, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF0E161B))) {
        Column(Modifier.heightIn(min = 96.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(label, color = vmMuted, fontSize = 10.sp)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
