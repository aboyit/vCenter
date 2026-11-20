package vn.webnew.vcenter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun VmActionsPanel(vm: InventoryItem, busy: Boolean, blocked: Boolean,
    execute: (VmOperation) -> Unit) {
    VmActionsContent(vm, busy, blocked, false, true, execute)
}

@Composable
internal fun VmActionsContent(vm: InventoryItem, busy: Boolean, blocked: Boolean,
    detailLayout: Boolean, available: Boolean, execute: (VmOperation) -> Unit) {
    var selected by remember(vm.ref) { mutableStateOf<VmOperationAction?>(null) }
    var snapshotRef by remember(vm.ref) { mutableStateOf<String?>(null) }
    var name by remember(vm.ref) { mutableStateOf("") }
    var description by remember(vm.ref) { mutableStateOf("") }
    var memory by remember(vm.ref) { mutableStateOf(false) }
    var quiesce by remember(vm.ref) { mutableStateOf(false) }
    var children by remember(vm.ref) { mutableStateOf(false) }
    var suppressPowerOn by remember(vm.ref) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var snapshotMenu by remember { mutableStateOf(false) }
    if (!detailLayout) Text("Thao tác VM", style = MaterialTheme.typography.titleSmall)
    if (blocked) Text("Có lệnh chưa đối soát. Kiểm tra tab Task trước.", style = MaterialTheme.typography.bodySmall)
    Box {
        if (detailLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VmDetailAction(if (vm.get("runtime.powerState") == "poweredOn") "⏻ Tắt nguồn" else "⏻ Bật nguồn",
                        Modifier.weight(1f).testTag("vm-actions-${vm.ref}"), available && !busy && !blocked) {
                        snapshotMenu = false; menu = true
                    }
                    VmDetailAction("⇄ Migration", Modifier.weight(1f), false) {}
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VmDetailAction("◫ Snapshot (${if ("snapshot" in vm.missing) "?" else vm.snapshotInfo?.snapshots?.size ?: 0})",
                        Modifier.weight(1f).testTag("vm-detail-snapshots"), true) { snapshotMenu = true; menu = true }
                    VmDetailAction("⚙ Cấu hình", Modifier.weight(1f), false) {}
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VmDetailAction("▣ Clone VM", Modifier.weight(1f), false) {}
                    VmDetailAction("⌨ Console", Modifier.weight(1f), false) {}
                }
            }
        } else {
            OutlinedButton(onClick = { menu = true }, enabled = !busy && !blocked, modifier = Modifier.testTag("vm-actions-${vm.ref}")) {
                Text("Nguồn / Snapshot")
            }
        }
        val actionItems: @Composable () -> Unit = {
            VmOperationAction.entries.filter { !detailLayout ||
                (it in setOf(VmOperationAction.POWER_ON, VmOperationAction.POWER_OFF, VmOperationAction.RESET,
                    VmOperationAction.SHUTDOWN_GUEST, VmOperationAction.REBOOT_GUEST)) != snapshotMenu
            }.forEach { action ->
                val probe = when (action) {
                    VmOperationAction.CREATE_SNAPSHOT -> VmOperation(action, vm.ref, snapshotName = "snapshot")
                    VmOperationAction.REVERT_TO_SNAPSHOT, VmOperationAction.REMOVE_SNAPSHOT ->
                        vm.snapshotInfo?.snapshots?.firstOrNull()?.let { VmOperation(action, vm.ref, snapshotRef = it.ref) }
                    else -> VmOperation(action, vm.ref)
                }
                val reason = when {
                    !available -> "Chưa có phiên thao tác"
                    busy -> "Đang xử lý lệnh"
                    blocked -> "Kiểm tra lệnh chưa đối soát ở tab Task"
                    probe == null -> "Chưa có snapshot để thực hiện"
                    else -> vmOperationBlockReason(vm, probe)
                }
                val select = {
                    selected = action; menu = false
                    snapshotRef = vm.snapshotInfo?.currentRef ?: vm.snapshotInfo?.snapshots?.firstOrNull()?.ref
                    name = ""; description = ""; memory = false; quiesce = false; children = false; suppressPowerOn = false
                }
                if (detailLayout) {
                    OutlinedButton(onClick = select, enabled = reason == null,
                        modifier = Modifier.fillMaxWidth().testTag("vm-action-${action.name}"),
                        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFF495865)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7AD2EF),
                            disabledContentColor = Color(0xFF8B9DA6)),
                        contentPadding = PaddingValues(14.dp)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(vmActionLabel(action), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            reason?.let { Text(it, fontSize = 11.sp) }
                        }
                    }
                } else {
                    DropdownMenuItem(text = { Text(vmActionLabel(action)) }, enabled = reason == null,
                        modifier = Modifier.testTag("vm-action-${action.name}"), onClick = select)
                }
            }
        }
        if (detailLayout && menu) {
            VmActionDialog(if (snapshotMenu) "Snapshot" else "Điều khiển nguồn", vm, { menu = false }) {
                if (snapshotMenu) {
                    Surface(color = Color(0xFF1B2A32), shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) { VmSnapshots(vm) }
                    }
                } else {
                    Text("Chọn thao tác. Bạn sẽ được yêu cầu xác nhận trước khi gửi lệnh.",
                        color = Color(0xFFBACBD6), fontSize = 12.sp)
                }
                actionItems()
            }
        } else if (!detailLayout) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { actionItems() }
        }
    }
    selected?.let { action ->
        val operation = when (action) {
            VmOperationAction.CREATE_SNAPSHOT -> if (name.isBlank()) null else VmOperation(action, vm.ref,
                snapshotName = name.trim(), description = description, memory = memory, quiesce = quiesce)
            VmOperationAction.REVERT_TO_SNAPSHOT -> snapshotRef?.let { VmOperation(action, vm.ref, snapshotRef = it, suppressPowerOn = suppressPowerOn) }
            VmOperationAction.REMOVE_SNAPSHOT -> snapshotRef?.let { VmOperation(action, vm.ref, snapshotRef = it, removeChildren = children) }
            else -> VmOperation(action, vm.ref)
        }
        val reason = operation?.let { vmOperationBlockReason(vm, it) }
        AlertDialog(onDismissRequest = { selected = null },
            containerColor = Color(0xFF21333B), titleContentColor = Color(0xFFF3F6F8),
            textContentColor = Color(0xFFBACBD6), shape = RoundedCornerShape(5.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(vmActionLabel(action)) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${vm.get("name") ?: vm.ref} · ${vm.ref}")
                    Text(when (action) {
                        VmOperationAction.POWER_OFF, VmOperationAction.RESET -> "Cưỡng bức nguồn có thể gây mất dữ liệu chưa lưu."
                        VmOperationAction.REVERT_TO_SNAPSHOT -> "Revert làm mất thay đổi sau snapshot và có thể thay đổi trạng thái nguồn."
                        VmOperationAction.REMOVE_SNAPSHOT, VmOperationAction.REMOVE_ALL_SNAPSHOTS -> "Xóa snapshot không thể hoàn tác, có thể tạo tải I/O lớn. Không xóa VM."
                        VmOperationAction.CONSOLIDATE_DISKS -> "Consolidation có thể gây tải I/O lớn; server kiểm tra điều kiện thực tế."
                        else -> "Server kiểm tra quyền và điều kiện tại thời điểm gửi lệnh."
                    })
                    if (action == VmOperationAction.CREATE_SNAPSHOT) {
                        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Tên snapshot") }, modifier = Modifier.testTag("snapshot-name"))
                        OutlinedTextField(description, { description = it.take(4000) }, label = { Text("Mô tả") })
                        Row { Checkbox(memory, { memory = it; if (it) quiesce = false }); Text("Lưu bộ nhớ") }
                        Row { Checkbox(quiesce, { quiesce = it; if (it) memory = false }); Text("Quiesce (VMware Tools)") }
                    }
                    if (action in setOf(VmOperationAction.REVERT_TO_SNAPSHOT, VmOperationAction.REMOVE_SNAPSHOT)) {
                        vm.snapshotInfo?.snapshots?.forEach { snapshot ->
                            Row { RadioButton(snapshotRef == snapshot.ref, { snapshotRef = snapshot.ref }); Text("${snapshot.name ?: snapshot.ref} · ${snapshot.ref}") }
                        }
                        if (action == VmOperationAction.REMOVE_SNAPSHOT) Row { Checkbox(children, { children = it }); Text("Xóa cả snapshot con") }
                        else Row { Checkbox(suppressPowerOn, { suppressPowerOn = it }); Text("Không tự bật nguồn khi revert") }
                    }
                    reason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("Không tự gửi lại khi timeout. Theo dõi kết quả ở tab Task.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { operation?.let(execute); selected = null },
                enabled = operation != null && reason == null && !busy && !blocked,
                modifier = Modifier.testTag("confirm-vm-operation")) { Text("Xác nhận gửi lệnh") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Hủy") } })
    }
}

@Composable
private fun VmDetailAction(label: String, modifier: Modifier, enabled: Boolean,
    onClick: () -> Unit) {
    val accent = Color(0xFF4AAED9)
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, accent),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFF7AD2EF),
            disabledContentColor = Color(0xFF8B9DA6),
            disabledContainerColor = Color(0xFF1B2A32)), 
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 12.sp)
            if (!enabled) Text(if (label in setOf("⇄ Migration", "⚙ Cấu hình", "▣ Clone VM", "⌨ Console"))
                "Chưa hỗ trợ" else "Tạm không khả dụng", fontSize = 9.sp)
        }
    }
}

@Composable
private fun VmActionDialog(title: String, vm: InventoryItem, onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp).fillMaxWidth(),
            color = Color(0xFF21333B), contentColor = Color(0xFFF3F6F8),
            shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, Color(0xFF495865))) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(vm.get("name") ?: vm.ref, fontSize = 12.sp, color = Color(0xFFBACBD6))
                content()
                OutlinedButton(onClick = onBack, modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF4AAED9))) {
                    Text("Quay lại", color = Color(0xFF7AD2EF))
                }
            }
        }
    }
}
