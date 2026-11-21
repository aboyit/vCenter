package vn.webnew.vcenter

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

internal fun snapshotDateTime(value: String?, zone: ZoneId = ZoneId.systemDefault()): String =
    value?.let { runCatching {
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(zone).format(Instant.parse(it))
    }.getOrNull() } ?: "Chưa có thời gian hợp lệ"

@Composable
internal fun VmActionsPanel(vm: InventoryItem, busy: Boolean, blocked: Boolean,
    execute: (VmOperation) -> Unit) {
    VmActionsContent(vm, busy, blocked, false, true, execute = execute)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VmActionsContent(vm: InventoryItem, busy: Boolean, blocked: Boolean,
    detailLayout: Boolean, available: Boolean, openAdvanced: ((String) -> Unit)? = null,
    execute: (VmOperation) -> Unit) {
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
    Box {
        if (detailLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VmDetailAction(if (vm.get("runtime.powerState") == "poweredOn") "Tắt nguồn" else "Bật nguồn",
                                            Modifier.weight(1f).testTag("vm-actions-${vm.ref}"), available && !busy, icon = "power") {
                        snapshotMenu = false; menu = true
                    }
                    VmDetailAction("⇄ Migration", Modifier.weight(1f).testTag("vm-open-migration"), openAdvanced != null) {
                        openAdvanced?.invoke("Migration")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VmDetailAction("Snapshot (${if ("snapshot" in vm.missing) "?" else vm.snapshotInfo?.snapshots?.size ?: 0})",
                                            Modifier.weight(1f).testTag("vm-detail-snapshots"), true, icon = "snapshot") {
                                            snapshotRef = vm.snapshotInfo?.currentRef?.takeIf { ref -> vm.snapshotInfo.snapshots.any { it.ref == ref } }
                                                ?: vm.snapshotInfo?.snapshots?.firstOrNull()?.ref
                                            snapshotMenu = true; menu = true
                                        }
                    VmDetailAction("⚙ Cấu hình", Modifier.weight(1f).testTag("vm-open-config"), openAdvanced != null) {
                        openAdvanced?.invoke("CPU / RAM")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VmDetailAction("▣ Clone VM", Modifier.weight(1f).testTag("vm-open-clone"), openAdvanced != null) {
                        openAdvanced?.invoke("Clone")
                    }
                    VmDetailAction("⌨ Console", Modifier.weight(1f), false) {}
                }
            }
        } else {
            OutlinedButton(onClick = { menu = true }, enabled = available && !busy, modifier = Modifier.testTag("vm-actions-${vm.ref}")) {
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
                        vm.snapshotInfo?.snapshots?.firstOrNull { !detailLayout || it.ref == snapshotRef }
                            ?.let { VmOperation(action, vm.ref, snapshotRef = it.ref) }
                    else -> VmOperation(action, vm.ref)
                }
                val reason = when {
                    !available -> "Chưa có phiên thao tác"
                    busy -> "Đang xử lý lệnh"
                    blocked -> "Không đọc được lịch sử lệnh cục bộ."
                    probe == null -> "Chưa có snapshot để thực hiện"
                    else -> vmOperationBlockReason(vm, probe)
                }
                val select = {
                    selected = action; menu = false
                    if (!detailLayout) snapshotRef = vm.snapshotInfo?.currentRef ?: vm.snapshotInfo?.snapshots?.firstOrNull()?.ref
                    name = ""; description = ""; memory = false; quiesce = false; children = false; suppressPowerOn = false
                }
                if (detailLayout && snapshotMenu) {
                                    OutlinedButton(onClick = select, enabled = reason == null,
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("vm-action-${action.name}")
                                            .semantics { reason?.let { stateDescription = it } },
                                        shape = RoundedCornerShape(7.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(when (action) {
                                            VmOperationAction.CREATE_SNAPSHOT -> "Tạo mới"
                                            VmOperationAction.REVERT_TO_SNAPSHOT -> "Revert"
                                            VmOperationAction.REMOVE_SNAPSHOT -> "Xóa"
                                            VmOperationAction.REMOVE_ALL_SNAPSHOTS -> "Xóa toàn bộ"
                                            else -> "Consolidate disk"
                                        }, fontSize = 12.sp)
                                    }
                                } else if (detailLayout) {
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
                        Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            when {
                                "snapshot" in vm.missing -> Text("Không đọc được snapshot (có thể thiếu quyền).")
                                vm.snapshotInfo?.snapshots.isNullOrEmpty() -> Text("Không có snapshot được server trả về.")
                                else -> vm.snapshotInfo?.snapshots?.forEach { snapshot ->
                                    Surface(Modifier.fillMaxWidth().padding(start = (snapshot.depth.coerceAtMost(4) * 8).dp),
                                        color = if (snapshotRef == snapshot.ref) Color(0xFF29414E) else Color(0xFF21333B),
                                        shape = RoundedCornerShape(7.dp),
                                        border = BorderStroke(1.dp, if (snapshotRef == snapshot.ref) Color(0xFF4AAED9) else Color(0xFF495865))) {
                                        Row(Modifier.fillMaxWidth()
                                            .selectable(snapshotRef == snapshot.ref, role = Role.RadioButton,
                                                onClick = { snapshotRef = snapshot.ref })
                                            .testTag("snapshot-select-${snapshot.ref}")
                                            .heightIn(min = 48.dp)
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = snapshotRef == snapshot.ref, onClick = null,
                                                modifier = Modifier.size(20.dp).testTag("snapshot-radio-${snapshot.ref}"))
                                            Column(Modifier.weight(1f).testTag("snapshot-content-${snapshot.ref}"),
                                                verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(snapshot.name ?: snapshot.ref, fontSize = 12.sp, lineHeight = 16.sp,
                                                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f))
                                                    if (snapshot.ref == vm.snapshotInfo?.currentRef) {
                                                        Text("Hiện tại", color = Color(0xFF7AD2EF), fontSize = 10.sp, lineHeight = 14.sp)
                                                    }
                                                }
                                                Text("${snapshotDateTime(snapshot.createdAt)} · ${snapshot.ref}",
                                                    fontSize = 10.sp, lineHeight = 14.sp, color = Color(0xFFBACBD6))
                                                snapshot.description?.takeIf { it.isNotBlank() }?.let {
                                                    Text(it, fontSize = 11.sp, lineHeight = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("Chọn thao tác. Bạn sẽ được yêu cầu xác nhận trước khi gửi lệnh.",
                        color = Color(0xFFBACBD6), fontSize = 12.sp)
                }
                if (snapshotMenu) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) { actionItems() }
                    Text(when {
                        !available -> "Chưa có phiên thao tác."
                        busy -> "Đang xử lý lệnh."
                        blocked -> "Không đọc được lịch sử lệnh cục bộ."
                        else -> "Chọn snapshot ở trên để Xóa/Revert. Thao tác cần xác nhận."
                    }, fontSize = 11.sp, color = Color(0xFFBACBD6))
                    if (vm.get("runtime.consolidationNeeded") != "true") {
                        Text("Consolidate disk: server chưa báo cần consolidation.", fontSize = 11.sp, color = Color(0xFFBACBD6))
                    }
                } else actionItems()
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
                    val warning = when (action) {
                        VmOperationAction.POWER_OFF, VmOperationAction.RESET -> "Cưỡng bức nguồn có thể gây mất dữ liệu chưa lưu."
                        VmOperationAction.REVERT_TO_SNAPSHOT -> "Revert làm mất thay đổi sau snapshot và có thể thay đổi trạng thái nguồn."
                        VmOperationAction.REMOVE_SNAPSHOT, VmOperationAction.REMOVE_ALL_SNAPSHOTS -> "Xóa snapshot không thể hoàn tác, có thể tạo tải I/O lớn. Không xóa VM."
                        VmOperationAction.CONSOLIDATE_DISKS -> "Consolidation có thể gây tải I/O lớn; server kiểm tra điều kiện thực tế."
                        else -> null
                    }
                    warning?.let { Text(it) }
                    if (action == VmOperationAction.CREATE_SNAPSHOT) {
                        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Tên snapshot") }, modifier = Modifier.testTag("snapshot-name"))
                        OutlinedTextField(description, { description = it.take(4000) }, label = { Text("Mô tả") })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(memory, { memory = it; if (it) quiesce = false }, modifier = Modifier.testTag("snapshot-memory"))
                            Text("Lưu bộ nhớ", modifier = Modifier.testTag("snapshot-memory-label"))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(quiesce, { quiesce = it; if (it) memory = false }, modifier = Modifier.testTag("snapshot-quiesce"))
                            Text("Quiesce (VMware Tools)", modifier = Modifier.testTag("snapshot-quiesce-label"))
                        }
                    }
                    if (action in setOf(VmOperationAction.REVERT_TO_SNAPSHOT, VmOperationAction.REMOVE_SNAPSHOT)) {
                        vm.snapshotInfo?.snapshots?.forEach { snapshot ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(snapshotRef == snapshot.ref, { snapshotRef = snapshot.ref })
                                Text("${snapshot.name ?: snapshot.ref} · ${snapshot.ref}")
                            }
                        }
                        if (action == VmOperationAction.REMOVE_SNAPSHOT) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(children, { children = it }); Text("Xóa cả snapshot con")
                        } else Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(suppressPowerOn, { suppressPowerOn = it }); Text("Không tự bật nguồn khi revert")
                        }
                    }
                    reason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("Không tự gửi lại khi timeout. Theo dõi kết quả ở tab Task.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { operation?.let(execute); selected = null },
                enabled = available && operation != null && reason == null && !busy &&
                    !blocked,
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4AAED9), contentColor = Color(0xFF10222C)),
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm-vm-operation")) { Text("Xác nhận") } },
            dismissButton = { OutlinedButton(onClick = { selected = null },
                shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF4AAED9)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7AD2EF)),
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Hủy") } })
    }
}

@Composable
private fun VmDetailAction(label: String, modifier: Modifier, enabled: Boolean,
    icon: String? = null, onClick: () -> Unit) {
    val accent = Color(0xFF4AAED9)
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, accent),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFF7AD2EF),
            disabledContentColor = Color(0xFF8B9DA6),
            disabledContainerColor = Color(0xFF1B2A32)), 
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (icon != null) {
                    val color = LocalContentColor.current
                    Canvas(Modifier.size(14.dp)) {
                        val stroke = Stroke(width = 1.6.dp.toPx())
                        if (icon == "power") {
                            drawArc(color, -55f, 290f, false, Offset(size.width * .15f, size.height * .15f),
                                Size(size.width * .7f, size.height * .7f), style = stroke)
                            drawLine(color, Offset(size.width * .5f, 0f), Offset(size.width * .5f, size.height * .48f), strokeWidth = stroke.width)
                        } else {
                            drawRect(color, Offset(size.width * .25f, size.height * .1f), Size(size.width * .65f, size.height * .65f), style = stroke)
                            drawRect(color, Offset(size.width * .1f, size.height * .3f), Size(size.width * .65f, size.height * .65f), style = stroke)
                        }
                    }
                }
                Text(label, fontSize = 12.sp)
            }
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
                if (title == "Snapshot") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("snapshot-dialog-title"))
                        Text(vm.get("name") ?: vm.ref, fontSize = 12.sp, color = Color(0xFFBACBD6),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f).testTag("snapshot-dialog-vm"))
                    }
                } else {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(vm.get("name") ?: vm.ref, fontSize = 12.sp, color = Color(0xFFBACBD6))
                }
                content()
                OutlinedButton(onClick = onBack, modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color(0xFF4AAED9))) {
                    Text("Quay lại", color = Color(0xFF7AD2EF))
                }
            }
        }
    }
}
