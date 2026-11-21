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
internal fun PendingOperationsPanel(operations: List<PendingOperation>, refresh: () -> Unit) {
    operations.sortedByDescending { it.createdAt }.forEach { operation ->
        OutlinedCard(Modifier.fillMaxWidth().testTag("operation-${operation.id}")) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${operation.action} · ${operation.vmRef}", style = MaterialTheme.typography.titleSmall)
                Text(DateFormat.getDateTimeInstance().format(Date(operation.createdAt)), style = MaterialTheme.typography.bodySmall)
                Text(when (operation.status) {
                    "uncertain" -> "Chưa xác định server đã nhận lệnh. Không tự gửi lại."
                    "sent" -> "Đã gửi lệnh guest; chưa xác nhận hệ điều hành hoàn tất."
                    "unavailable" -> "Không còn truy xuất được task hoặc thiếu quyền; không suy ra thành công."
                    "reviewed" -> "Bản ghi cũ đã đóng; không phải kết quả thành công từ server."
                    "rejected" -> "Lệnh không được thực hiện (kiểm tra trước khi gửi hoặc server từ chối)."
                    "accepted" -> "Server đã nhận lệnh reboot; API không trả Task, chưa xác nhận reboot hoàn tất."
                    "observed" -> "Đã đọc lại từ server: VM đã tắt sau yêu cầu shutdown."
                    "success" -> "Task hoàn tất thành công."
                    "error" -> "Task thất bại; xem lỗi server trong danh sách Task."
                    "queued" -> "Task đang chờ trên vCenter."
                    "running" -> "Task đang chạy trên vCenter."
                    else -> operation.status
                }, style = MaterialTheme.typography.bodySmall)
                operation.taskRef?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (operation.status in setOf("uncertain", "sent", "unavailable")) {
                    TextButton(onClick = refresh) { Text("Cập nhật kết quả") }
                }
            }
        }
    }
}
