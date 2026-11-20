package vn.webnew.vcenter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun PendingOperationsPanel(operations: List<PendingOperation>, review: (String) -> Unit) {
    var selected by remember { mutableStateOf<PendingOperation?>(null) }
    operations.sortedByDescending { it.createdAt }.forEach { operation ->
        OutlinedCard(Modifier.fillMaxWidth().testTag("operation-${operation.id}")) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${operation.action} · ${operation.vmRef}", style = MaterialTheme.typography.titleSmall)
                Text(DateFormat.getDateTimeInstance().format(Date(operation.createdAt)), style = MaterialTheme.typography.bodySmall)
                Text(when (operation.status) {
                    "uncertain" -> "Chưa xác định server đã nhận lệnh. Không tự gửi lại."
                    "sent" -> "Đã gửi lệnh guest; chưa xác nhận hệ điều hành hoàn tất."
                    "unavailable" -> "Không còn truy xuất được task hoặc thiếu quyền; không suy ra thành công."
                    "reviewed" -> "Đã xác nhận đối soát thủ công; không phải kết quả thành công từ server."
                    "rejected" -> "Server từ chối lệnh."
                    else -> operation.status
                }, style = MaterialTheme.typography.bodySmall)
                operation.taskRef?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (operation.status in setOf("uncertain", "sent", "unavailable")) {
                    TextButton(onClick = { selected = operation }) { Text("Đối soát thủ công") }
                }
            }
        }
    }
    selected?.let { operation ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("Xác nhận đối soát") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            text = { Text("Kiểm tra task và trạng thái VM trên vSphere Client trước khi mở khóa gửi lệnh mới. Thao tác này không đánh dấu lệnh thành công và không gửi lại lệnh.") },
            confirmButton = { TextButton(onClick = { review(operation.id); selected = null }, modifier = Modifier.testTag("confirm-operation-review")) { Text("Đã kiểm tra trên server") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Quay lại") } })
    }
}
