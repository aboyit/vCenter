package vn.webnew.vcenter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private val panel = Color(0xFF21333B)
private val muted = Color(0xFFBACBD6)
private val border = Color(0xFF495865)

@Composable
internal fun M2MonitoringScreen(snapshot: MonitoringSnapshot?, loading: Boolean, error: String?) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            if (error != null) Text(error, color = Color(0xFFFFB4AB))
            if (snapshot != null) Text("Truy vấn tối đa " +
                "${MonitoringSoap.MAX_EVENTS} sự kiện / ${MonitoringSoap.EVENT_WINDOW_HOURS} giờ, " +
                "${MonitoringSoap.MAX_ALARMS} cảnh báo.", color = muted)
        }
        if (snapshot == null && !loading && error == null) item { Text("Chưa tải dữ liệu giám sát.", color = muted) }
        if (snapshot != null) {
            item {
                Text("Cảnh báo đang kích hoạt (${snapshot.alarms.size})", fontWeight = FontWeight.Bold, color = Color.White)
                if (snapshot.alarmError != null) Text(snapshot.alarmError, color = Color(0xFFFFB4AB))
                if (snapshot.alarmLimitReached) Text("Danh sách cảnh báo đã bị cắt ở giới hạn ${MonitoringSoap.MAX_ALARMS}.", color = muted)
                if (snapshot.alarms.isEmpty() && snapshot.alarmError == null) Text("Không có cảnh báo đang kích hoạt trên rootFolder trong phạm vi quyền hiện tại.", color = muted)
            }
            items(snapshot.alarms, key = { "alarm:${it.key}:${it.entity.ref}" }) { alarm ->
                MonitorCard {
                    Text(alarm.name ?: "Alarm ${alarm.alarmRef}", fontWeight = FontWeight.Bold, color = Color.White)
                    Text("${alarm.status} · ${entityLabel(alarm.entity)}", color = muted)
                    Text("Kích hoạt: ${alarm.time ?: "Không có thời gian"} · Xác nhận: " +
                        when (alarm.acknowledged) { true -> "Có"; false -> "Chưa"; null -> "Không rõ" }, color = muted)
                }
            }
            item {
                Text("Sự kiện gần đây (${snapshot.events.size})", fontWeight = FontWeight.Bold, color = Color.White)
                if (snapshot.eventError != null) Text(snapshot.eventError, color = Color(0xFFFFB4AB))
                if (snapshot.eventLimitReached) Text("Có thể còn sự kiện khác; chỉ hiển thị ${MonitoringSoap.MAX_EVENTS} bản ghi.", color = muted)
                if (snapshot.events.isEmpty() && snapshot.eventError == null) Text("Không có sự kiện trong khoảng thời gian truy vấn.", color = muted)
            }
            items(snapshot.events, key = { "event:${it.key}" }) { event ->
                MonitorCard {
                    Text(event.content ?: "Sự kiện #${event.key} (${event.eventType ?: "không rõ loại"})", color = Color.White)
                    Text("${event.time ?: "Không có thời gian"} · ${event.entity?.let(::entityLabel) ?: "Không có đối tượng"}", color = muted)
                    Text("Mức độ: ${event.severity ?: "API không cung cấp"}", color = muted)
                }
            }
        }
    }
}

private fun entityLabel(entity: MonitoringEntity): String =
    entity.name ?: listOfNotNull(entity.type, entity.ref).joinToString(" · ").ifEmpty { "Không rõ đối tượng" }

private fun formatTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

@Composable
private fun MonitorCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), border = BorderStroke(1.dp, border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}
