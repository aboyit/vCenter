package vn.webnew.vcenter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val dashboardPanel = Color(0xFF21333B)
private val dashboardMuted = Color(0xFFBACBD6)
private val dashboardAccent = Color(0xFF4AAED9)
private val dashboardBorder = Color(0xFF495865)

private fun capacityValue(value: Long?) = capacityDisplay(value).value
private fun capacityUnit(value: Long?) = capacityDisplay(value).unit
private fun percentage(used: Long?, total: Long?): Float? =
    if (used != null && used >= 0 && total != null && total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else null

@Composable
internal fun DashboardScreen(inventory: InventorySnapshot, state: ConnectionState = ConnectionState(), navigate: (InventorySection) -> Unit) {
    val running = inventory.vms.count { it.get("runtime.powerState") == "poweredOn" }
    val off = inventory.vms.count { it.get("runtime.powerState") == "poweredOff" }
    val cpuUsed = inventory.hosts.map { it.number("summary.quickStats.overallCpuUsage") }
        .takeIf { it.isNotEmpty() && it.all { n -> n != null && n >= 0 } }?.sumOf { it!! }
    val cpuRatio = percentage(cpuUsed, inventory.cpuMhz)
    val memoryRatio = percentage(inventory.memoryUsedBytes, inventory.memoryBytes)
    val storageUsed = inventory.capacityBytes?.let { total -> inventory.freeBytes?.let { total - it } }
    val storageRatio = percentage(storageUsed, inventory.capacityBytes)
    BoxWithConstraints {
        val columns = if (maxWidth >= 640.dp) 4 else 2
        val stats: List<@Composable () -> Unit> = listOf(
            { DashboardStat("MÁY ẢO", inventory.vms.size.toString(), "VM",
                percentage(running.toLong(), inventory.vms.size.toLong()), "$running đang chạy · $off đã tắt") },
            { DashboardStat("CPU SỬ DỤNG", cpuRatio?.let { "${(it * 100).toInt()}" } ?: "—", "%", cpuRatio,
                if (cpuUsed != null && inventory.cpuMhz != null) String.format(Locale.US, "%.1f / %.1f GHz", cpuUsed / 1000.0, inventory.cpuMhz!! / 1000.0) else "Chưa có số liệu CPU") },
            { DashboardStat("BỘ NHỚ", capacityValue(inventory.memoryUsedBytes), capacityUnit(inventory.memoryUsedBytes), memoryRatio,
                "${memoryRatio?.let { "${(it * 100).toInt()}% / " } ?: ""}${capacityDisplay(inventory.memoryBytes)} tổng") },
            { DashboardStat("LƯU TRỮ", capacityValue(storageUsed), capacityUnit(storageUsed), storageRatio,
                "${storageRatio?.let { "${(it * 100).toInt()}% / " } ?: ""}${capacityDisplay(inventory.capacityBytes)} tổng") }
        )
        Column(verticalArrangement =         Arrangement.spacedBy(10.dp)) {
            stats.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { stat -> Box(Modifier.weight(1f).fillMaxHeight()) { stat() } }
                }
            }
        }
    }
    BoxWithConstraints {
        if (maxWidth >= 640.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1.65f)) { EsxiPerformancePanel(inventory, state) }
                Box(Modifier.weight(1f)) { HostsPanel(inventory, navigate) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EsxiPerformancePanel(inventory, state)
                HostsPanel(inventory, navigate)
            }
        }
    }
    VmTable(inventory, navigate)
    BoxWithConstraints {
        val tasks: @Composable () -> Unit = {
            DashboardPanel {
                PanelHeader("Tác vụ gần đây")
                Text("Chưa tích hợp TaskManager.", color = dashboardMuted, fontSize = 12.sp)
            }
        }
        val attention: @Composable () -> Unit = {
            DashboardPanel {
                PanelHeader("Cần chú ý", "Xem alarm/event →") { navigate(InventorySection.Alarms) }
                Text("Mở Cảnh báo / Sự kiện để đọc trạng thái từ vCenter.", color = dashboardMuted, fontSize = 12.sp)
                inventory.datastores.filter { it.get("summary.accessible") == "false" }.forEach {
                    Text("${it.get("name") ?: it.ref}: datastore không truy cập được", color = Color(0xFFF5CF84), fontSize = 12.sp)
                }
            }
        }
        if (maxWidth >= 640.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1.65f)) { tasks() }
                Box(Modifier.weight(1f)) { attention() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { tasks(); attention() }
        }
    }
    Text("Phạm vi tài khoản hiện tại · Số liệu tại lần cập nhật, không phải lịch sử hiệu năng.", color = dashboardMuted, fontSize = 10.sp)
}

@Composable
private fun VmTable(inventory: InventorySnapshot, navigate: (InventorySection) -> Unit) {
    DashboardPanel {
        PanelHeader("Máy ảo", "Xem tất cả →") { navigate(InventorySection.Vms) }
        if (inventory.vms.isEmpty()) Text("Không có VM trong phạm vi truy cập.", color = dashboardMuted, fontSize = 12.sp)
        else VmInventoryTable(inventory, inventory.vms.take(5)) { navigate(InventorySection.Vms) }
    }
}

@Composable
private fun DashboardStat(label: String, value: String, unit: String, ratio: Float?, detail: String) {
    Surface(Modifier.fillMaxWidth().fillMaxHeight().testTag("dashboard-stat"), color = dashboardPanel,
        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF0E161B))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = dashboardMuted, fontSize = 12.sp)
                Text("↗", color = dashboardMuted, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Text(unit, color = dashboardMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (ratio != null) LinearProgressIndicator(progress = { ratio }, color = dashboardAccent,
                trackColor = Color(0xFF29414E), modifier = Modifier.fillMaxWidth().height(4.dp))
            else Spacer(Modifier.fillMaxWidth().height(4.dp))
            Text(detail, color = dashboardMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DashboardPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = dashboardPanel, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF0E161B))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun PanelHeader(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            Text(action, fontSize = 11.sp, color = dashboardAccent)
        }
    }
}

@Composable
private fun HostsPanel(inventory: InventorySnapshot, navigate: (InventorySection) -> Unit) {
    DashboardPanel {
        val online = inventory.hosts.count { it.get("runtime.connectionState") == "connected" }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hosts & Cluster", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusBadge("$online / ${inventory.hosts.size} online", online > 0)
        }
        if (inventory.hosts.isEmpty()) Text("Không có host trong phạm vi truy cập.", color = dashboardMuted)
        inventory.hosts.take(3).forEach { host ->
            HorizontalDivider(color = dashboardBorder.copy(alpha = .4f))
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Color(0xFF29414E), shape = RoundedCornerShape(8.dp)) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_host), null,
                        tint = dashboardAccent, modifier = Modifier.padding(11.dp).size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(host.get("name") ?: host.ref, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${capacityDisplay(host.number("summary.hardware.memorySize"))} RAM", color = dashboardMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val connected = host.get("runtime.connectionState") == "connected"
                    StatusBadge(if (connected) "Hoạt động" else host.get("runtime.connectionState") ?: "Chưa rõ", connected)
                    val cores = host.number("summary.hardware.numCpuCores")
                    val mhz = host.number("summary.hardware.cpuMhz")
                    val cpu = percentage(host.number("summary.quickStats.overallCpuUsage"),
                        if (cores != null && mhz != null) cores * mhz else null)
                    val ram = percentage(host.number("summary.quickStats.overallMemoryUsage"),
                        host.number("summary.hardware.memorySize")?.div(1024L * 1024L))
                    Text("CPU ${cpu?.let { "${(it * 100).toInt()}%" } ?: "—"} · RAM ${ram?.let { "${(it * 100).toInt()}%" } ?: "—"}",
                        color = dashboardMuted, fontSize = 10.sp)
                }
            }
        }
        TextButton(onClick = { navigate(InventorySection.Hosts) }, contentPadding = PaddingValues(0.dp)) {
            Text("Xem host →", color = dashboardAccent, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatusBadge(label: String, active: Boolean, compact: Boolean = false) {
    Surface(color = if (active) Color(0xFF284638) else Color(0xFF29414E), shape = RoundedCornerShape(5.dp)) {
        Text(label, color = if (active) Color(0xFF9DD18E) else dashboardMuted, fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 5.dp))
    }
}

@Composable
internal fun VmInventoryTable(inventory: InventorySnapshot, items: List<InventoryItem>, onSelect: (InventoryItem) -> Unit) {
    Column(Modifier.horizontalScroll(rememberScrollState()).testTag("dashboard-vm-table")) {
            Surface(color = Color(0xFF29414E)) {
                Row(Modifier.padding(vertical = 7.dp)) {
                    listOf("TÊN MÁY ẢO" to 170, "TRẠNG THÁI" to 100, "HOST" to 140,
                        "CPU" to 80, "RAM" to 100, "ĐỊA CHỈ IP" to 140, "" to 32).forEach { (label, width) ->
                        Text(label, fontSize = 10.sp, letterSpacing = .8.sp,
                            modifier = Modifier.width(width.dp).padding(horizontal = 8.dp))
                    }
                }
            }
            items.forEach { vm ->
                Row(Modifier.clickable { onSelect(vm) }.testTag("vm-row-${vm.ref}").heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(170.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_vm), null,
                            tint = dashboardAccent, modifier = Modifier.size(14.dp))
                        Column {
                            Text(vm.get("name") ?: vm.ref, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(vm.get("config.guestFullName") ?: "Chưa có OS", fontSize = 10.sp,
                                color = dashboardMuted, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(Modifier.width(100.dp).padding(horizontal = 8.dp)) {
                        StatusBadge(when (vm.get("runtime.powerState")) {
                            "poweredOn" -> "● Đang chạy"
                            "poweredOff" -> "○ Đã tắt"
                            "suspended" -> "Tạm dừng"
                            else -> "Chưa rõ"
                        }, vm.get("runtime.powerState") == "poweredOn", compact = true)
                    }
                    val host = vm.links["runtime.host"]?.firstOrNull()?.let(inventory::name) ?: "—"
                    listOf(host to 140, "${vm.get("config.hardware.numCPU") ?: "—"} vCPU" to 80,
                        "${vm.get("config.hardware.memoryMB") ?: "—"} MiB" to 100,
                        (vm.get("guest.ipAddress") ?: "—") to 140).forEach { (value, width) ->
                        Text(value, color = dashboardMuted, fontSize = 12.sp,
                            modifier = Modifier.width(width.dp).padding(horizontal = 8.dp))
                    }
                    Text("›", color = dashboardMuted, fontSize = 18.sp, modifier = Modifier.width(32.dp))
                }
                HorizontalDivider(color = dashboardBorder)
            }
        }
}
