package vn.webnew.vcenter

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val taskOperationLabels = mapOf(
    "powerOn" to "Bật nguồn VM", "PowerOnVM_Task" to "Bật nguồn VM",
    "powerOff" to "Tắt nguồn VM", "PowerOffVM_Task" to "Tắt nguồn VM",
    "reset" to "Reset VM", "ResetVM_Task" to "Reset VM",
    "suspend" to "Tạm ngưng VM", "SuspendVM_Task" to "Tạm ngưng VM",
    "createSnapshot" to "Tạo snapshot", "CreateSnapshot_Task" to "Tạo snapshot",
    "CreateSnapshotEx_Task" to "Tạo snapshot",
    "removeSnapshot" to "Xóa snapshot", "RemoveSnapshot_Task" to "Xóa snapshot",
    "removeAllSnapshots" to "Xóa toàn bộ snapshot", "RemoveAllSnapshots_Task" to "Xóa toàn bộ snapshot",
    "revertToSnapshot" to "Revert snapshot", "RevertToSnapshot_Task" to "Revert snapshot",
    "revertToCurrentSnapshot" to "Revert snapshot hiện tại", "RevertToCurrentSnapshot_Task" to "Revert snapshot hiện tại",
    "consolidateDisks" to "Hợp nhất đĩa snapshot", "ConsolidateVMDisks_Task" to "Hợp nhất đĩa snapshot",
    "reconfigure" to "Cấu hình VM", "ReconfigVM_Task" to "Cấu hình VM",
    "clone" to "Clone VM", "CloneVM_Task" to "Clone VM",
    "migrate" to "Di chuyển VM", "MigrateVM_Task" to "Di chuyển VM",
    "relocate" to "Di chuyển VM / lưu trữ", "RelocateVM_Task" to "Di chuyển VM / lưu trữ"
)

private val hostNetworkTaskLabels = mapOf(
    "removeVirtualSwitch" to "Xóa vSwitch", "addVirtualSwitch" to "Tạo vSwitch",
    "updateVirtualSwitch" to "Cập nhật vSwitch", "removePortGroup" to "Xóa port group",
    "addPortGroup" to "Tạo port group", "updatePortGroup" to "Cập nhật port group",
    "addVirtualNic" to "Thêm VMkernel adapter", "removeVirtualNic" to "Xóa VMkernel adapter",
    "updateVirtualNic" to "Cập nhật VMkernel adapter", "updateNetworkConfig" to "Cập nhật cấu hình mạng host"
)

internal fun VsphereTask.taskTitle(): String {
    val identifiers = listOfNotNull(descriptionId, name)
    identifiers.forEach { id ->
        if (id.startsWith("host.NetworkSystem.")) hostNetworkTaskLabels[id.substringAfterLast('.')]?.let { return it }
        if (id.startsWith("VirtualMachine.") || !id.contains('.')) {
            taskOperationLabels[id.substringAfterLast('.')]?.let { return it }
        }
    }
    return description?.takeIf { it.isNotBlank() }
        ?: descriptionId?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "Thao tác vCenter (chưa có tên)"
}

// Use the same timestamp for ordering and display; invalid values fall back to other server timestamps.
internal fun VsphereTask.listTime(): Instant? = listOf(completedAt, startedAt, queuedAt)
    .firstNotNullOfOrNull { value -> value?.let { runCatching { Instant.parse(it) }.getOrNull() } }

internal fun newestTasksFirst(tasks: List<VsphereTask>): List<VsphereTask> =
    tasks.sortedWith(compareByDescending<VsphereTask> { it.listTime() }.thenBy { it.ref })

private fun VsphereTask.canRequestCancellation() = accessible && cancelable == true &&
    state in setOf("queued", "running") && cancelled != true

internal fun taskTime(value: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (value == null) return "Chưa có dữ liệu"
    val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
    return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(zone).format(instant)
}

internal fun taskDuration(task: VsphereTask): String {
    val start = task.startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val end = task.completedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    if (start == null || end == null) return if (task.state in setOf("queued", "running") && task.accessible && task.cancelled != true)
        "Chưa kết thúc" else "Chưa có thời lượng"
    val duration = Duration.between(start, end)
    if (duration.isNegative) return "Thời gian không hợp lệ"
    val seconds = duration.seconds
    return when {
        seconds < 1 -> "${duration.toMillis()} ms"
        seconds < 60 -> "$seconds giây"
        seconds < 3600 -> "${seconds / 60} phút ${seconds % 60} giây"
        else -> "${seconds / 3600} giờ ${seconds % 3600 / 60} phút"
    }
}

@Composable
private fun TaskField(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "Chưa có dữ liệu", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TaskStatus(task: VsphereTask) {
    val label = when {
        !task.accessible -> "Không truy cập được"
        task.cancelled == true -> "Đã hủy"
        task.state == "success" -> "Thành công"
        task.state == "error" -> "Thất bại"
        task.state == "running" -> "Đang chạy"
        task.state == "queued" -> "Đang chờ"
        else -> "Chưa rõ trạng thái"
    }
    val color = when {
        !task.accessible || task.state == "error" && task.cancelled != true -> MaterialTheme.colorScheme.error
        task.state == "success" && task.cancelled != true -> androidx.compose.ui.graphics.Color(0xFF77D9B0)
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun TasksScreen(state: ConnectionState, refresh: () -> Unit,
    cancel: (String) -> Unit, refreshTracking: () -> Unit) {
    var cancelRef by remember { mutableStateOf<String?>(null) }
    var selectedRef by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val filters = listOf("all" to "Tất cả", "active" to "Đang xử lý", "error" to "Thất bại", "success" to "Thành công")
    val visible = newestTasksFirst(state.tasks).filter { task ->
        val matchesState = when (filter) {
            "active" -> task.accessible && task.cancelled != true && task.state in setOf("queued", "running")
            "error" -> task.accessible && task.cancelled != true && task.state == "error"
            "success" -> task.accessible && task.cancelled != true && task.state == "success"
            else -> true
        }
        matchesState && listOf(task.taskTitle(), task.entity?.name, task.entity?.ref, task.user, task.ref,
            task.descriptionId, task.description, task.name).any { it?.contains(query.trim(), ignoreCase = true) == true }
    }
    state.tasksError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.operationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.pendingOperations.isNotEmpty()) {
        Text("Lệnh từ app", style = MaterialTheme.typography.titleSmall)
        PendingOperationsPanel(state.pendingOperations, refreshTracking)
    }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("task-search"),
        placeholder = { Text("Tìm thao tác, đối tượng, tài khoản...") },
        label = { Text("Tìm task") }, singleLine = true)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        filters.forEach { (key, label) ->
            FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) },
                modifier = Modifier.testTag("task-filter-$key"))
        }
    }
    Text("${visible.size} / ${state.tasks.size} task", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.tasks.isEmpty() && !state.tasksLoading && state.tasksError == null) {
        Text("Server không trả về task trong phạm vi truy vấn.", style = MaterialTheme.typography.bodySmall)
    } else if (visible.isEmpty() && state.tasks.isNotEmpty()) {
        Text("Không có task khớp tìm kiếm hoặc bộ lọc.", style = MaterialTheme.typography.bodyMedium)
    }
    visible.forEach { task ->
        OutlinedCard(onClick = { selectedRef = task.ref }, modifier = Modifier.fillMaxWidth().testTag("task-${task.ref}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskStatus(task)
                Text(task.taskTitle(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(task.entity?.name ?: task.entity?.ref ?: "Không có đối tượng", style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Tài khoản: ${task.user ?: "Chưa có dữ liệu"}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (task.state == "running" && task.accessible && task.cancelled != true) {
                    task.progress?.let {
                        LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Tiến độ $it%", style = MaterialTheme.typography.labelSmall)
                    } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                task.error?.let { Text(it.message ?: it.type ?: "Task thất bại.", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (!task.accessible) Text("Không truy xuất được task; không suy ra thành công.", color = MaterialTheme.colorScheme.error)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("${when {
                    task.completedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } != null -> "Kết thúc"
                    task.startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } != null -> "Bắt đầu"
                    else -> "Vào hàng đợi"
                }}: ${taskTime(task.listTime()?.toString())} · ${taskDuration(task)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mã task: ${task.ref}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { selectedRef = task.ref }, modifier = Modifier.testTag("detail-${task.ref}")) { Text("Chi tiết") }
                    if (task.canRequestCancellation()) {
                        TextButton(onClick = { cancelRef = task.ref }, enabled = !state.operationBusy,
                            modifier = Modifier.testTag("cancel-${task.ref}")) { Text("Yêu cầu hủy") }
                    }
                }
            }
        }
    }
    selectedRef?.let { ref ->
        val task = state.tasks.firstOrNull { it.ref == ref }
        AlertDialog(onDismissRequest = { selectedRef = null },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("task-detail"),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Chi tiết task") },
            text = {
                SelectionContainer {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (task == null) {
                            Text("$ref không còn trong danh sách hiện tại. Làm mới để kiểm tra; không suy ra kết quả.")
                        } else {
                            TaskStatus(task)
                            Text(task.taskTitle(), style = MaterialTheme.typography.titleMedium)
                            TaskField("Đối tượng", task.entity?.name ?: task.entity?.ref)
                            TaskField("Người thực hiện", task.user)
                            task.description?.let { TaskField("Mô tả", it) }
                            HorizontalDivider()
                            Text("Thời gian · ${ZoneId.systemDefault().id}", style = MaterialTheme.typography.titleSmall)
                            TaskField("Vào hàng đợi", taskTime(task.queuedAt))
                            TaskField("Bắt đầu", taskTime(task.startedAt))
                            TaskField("Kết thúc", taskTime(task.completedAt))
                            TaskField("Thời lượng thực thi", taskDuration(task))
                            task.progress?.let { TaskField("Tiến độ server", "$it%") }
                            if (!task.accessible) Text("Không truy xuất được task; không suy ra thành công.", color = MaterialTheme.colorScheme.error)
                            task.error?.let {
                                Text("Lỗi từ vCenter", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                                TaskField("Nội dung", it.message)
                                TaskField("Loại lỗi", it.type)
                            }
                            HorizontalDivider()
                            Text("Thông tin kỹ thuật", style = MaterialTheme.typography.titleSmall)
                            TaskField("Task reference", task.ref)
                            TaskField("Trạng thái gốc", task.state)
                            TaskField("Thao tác API", task.name)
                            TaskField("Description ID", task.descriptionId)
                            TaskField("Loại / reference đối tượng", listOfNotNull(task.entity?.type, task.entity?.ref).joinToString(" · ").ifBlank { null })
                            TaskField("Cho phép yêu cầu hủy", when (task.cancelable) { true -> "Có, server sẽ kiểm tra quyền"; false -> "Không"; null -> null })
                            if (task.cancelled == true) Text("Server xác nhận cancelled = true")
                            if (task.missing.isNotEmpty()) TaskField("Thiếu property", task.missing.joinToString())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedRef = null }, modifier = Modifier.testTag("close-task-detail")) { Text("Đóng") } })
    }
    cancelRef?.let { ref ->
        AlertDialog(onDismissRequest = { cancelRef = null }, title = { Text("Yêu cầu hủy task?") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            text = { Text("$ref\nServer kiểm tra quyền hủy. Gửi yêu cầu không đồng nghĩa task đã hủy; app sẽ đọc lại trạng thái.") },
            confirmButton = { TextButton(onClick = { cancel(ref); cancelRef = null }, modifier = Modifier.testTag("confirm-cancel-task")) { Text("Gửi yêu cầu") } },
            dismissButton = { TextButton(onClick = { cancelRef = null }) { Text("Quay lại") } })
    }
}
