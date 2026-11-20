package vn.webnew.vcenter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal data class ResourceUsage(val used: Long?, val capacity: Long?) {
    val free: Long? get() = capacity?.let { total -> used?.takeIf { it in 0..total }?.let { total - it } }
    val ratio: Float? get() = free?.let { if (capacity!! > 0) used!!.toFloat() / capacity else 0f }
}

internal fun totalCpuUsage(inventory: InventorySnapshot): ResourceUsage {
    val used = inventory.hosts.map { it.number("summary.quickStats.overallCpuUsage") }
        .takeIf { it.isNotEmpty() && it.all { value -> value != null && value >= 0 } }?.sumOf { it!! }
    return ResourceUsage(used, inventory.cpuMhz)
}

@Composable
internal fun EsxiPerformancePanel(inventory: InventorySnapshot, state: ConnectionState) {
    val muted = Color(0xFFBACBD6)
    Surface(color = Color(0xFF21333B), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF0E161B)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Tổng tài nguyên ESXi", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            state.inventoryError?.let { Text("Dữ liệu cũ · $it", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
            ResourceBar("CPU", totalCpuUsage(inventory)) { value ->
                value?.let { String.format(Locale.US, "%.2f GHz", it / 1000.0) } ?: "—"
            }
            ResourceBar("RAM", ResourceUsage(inventory.memoryUsedBytes, inventory.memoryBytes)) { value ->
                value?.let { capacityDisplay(it).toString() } ?: "—"
            }
            val capacity = inventory.capacityBytes
            val free = inventory.freeBytes
            val used = if (capacity != null && free != null && free in 0..capacity) capacity - free else null
            ResourceBar("Storage", ResourceUsage(used, capacity)) { value ->
                value?.let { capacityDisplay(it).toString() } ?: "—"
            }
            if (inventory.hosts.isEmpty()) Text("Không có ESXi trong phạm vi dữ liệu trả về.", color = muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun ResourceBar(label: String, usage: ResourceUsage, format: (Long?) -> String) {
    val muted = Color(0xFFBACBD6)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().testTag("resource-$label")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Trống: ${format(usage.free)}", color = muted, fontSize = 10.sp)
        }
        LinearProgressIndicator(progress = { usage.ratio ?: 0f }, modifier = Modifier.fillMaxWidth().height(12.dp),
            color = Color(0xFF4AAED9), trackColor = Color(0xFF304A56))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Đã dùng: ${format(usage.used?.takeIf { usage.free != null })}", color = muted, fontSize = 10.sp)
            Text("Tổng: ${format(usage.capacity)}", color = muted, fontSize = 10.sp)
        }
        if (usage.ratio == null) Text("Chưa đủ dữ liệu $label", color = muted, fontSize = 10.sp)
    }
}
