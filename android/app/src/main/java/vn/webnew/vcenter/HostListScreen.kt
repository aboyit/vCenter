package vn.webnew.vcenter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val hostMuted = Color(0xFFBACBD6)
private val hostAccent = Color(0xFF4AAED9)
private val hostBorder = Color(0xFF495865)
private val hostTabs = listOf("Tổng quan", "Dịch vụ", "Network", "Storage", "DNS / NTP", "Firewall", "Nâng cao", "Truy cập", "PCI / Autostart", "Tasks")

@Composable
private fun HostPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFF21333B), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF0E161B)), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun HostListScreen(inventory: InventorySnapshot) {
    var selectedRef by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf("Tổng quan") }
    val hosts = inventory.hosts.sortedBy { it.get("name") ?: it.ref }
    val selected = hosts.firstOrNull { it.ref == selectedRef } ?: hosts.firstOrNull()
    val tree = vmInventoryTree(inventory, inventory.vms)
    val picker: @Composable () -> Unit = {
        HostPanel(Modifier.fillMaxWidth()) {
            tree.clusters.forEach { cluster ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cluster.cluster.get("name") ?: cluster.cluster.ref, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HealthIndicator(cluster.cluster.get("overallStatus"))
                    Text("${cluster.hosts.size} ESXi", color = hostMuted, fontSize = 11.sp)
                }
                HostPickerGrid(cluster.hosts.map { it.host }, selected?.ref) { selectedRef = it; tab = "Tổng quan" }
            }
            if (tree.standalone.isNotEmpty()) {
                Text("ESXi độc lập / Chưa xác định Cluster", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                HostPickerGrid(tree.standalone.map { it.host }, selected?.ref) { selectedRef = it; tab = "Tổng quan" }
            }
            if (hosts.isEmpty()) Text("Không có ESXi trong phạm vi dữ liệu trả về.", color = hostMuted)
        }
    }
    val detail: @Composable () -> Unit = {
        selected?.let { host ->
            HostPanel(Modifier.fillMaxWidth().testTag("host-detail-${host.ref}")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(host.get("name") ?: host.ref, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HostIndicators(host)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    hostTabs.forEach { label ->
                        OutlinedButton(onClick = { tab = label }, shape = RoundedCornerShape(7.dp),
                            border = BorderStroke(1.dp, if (tab == label) hostAccent else hostBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (tab == label) Color(0xFF29414E) else Color.Transparent),
                            modifier = Modifier.testTag("host-tab-$label")) { Text(label, fontSize = 11.sp) }
                    }
                }
                if (tab == "Tổng quan") {
                    Text("Quản trị host", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Column(Modifier.fillMaxWidth().testTag("host-summary")) {
                        HostInfoRow("Trạng thái kết nối", host.get("runtime.connectionState") ?: "Chưa có dữ liệu")
                        HostInfoRow("Health tổng thể", host.get("overallStatus") ?: if ("overallStatus" in host.missing) "Không đọc được (quyền/property)" else "Chưa có dữ liệu")
                        HostInfoRow("Maintenance", when (host.get("runtime.inMaintenanceMode")) { "true" -> "Đang bật"; "false" -> "Đang tắt"; else -> "Chưa có dữ liệu" })
                        val vmRefs = (host.links["vm"].orEmpty() + inventory.vms.filter { host.ref in it.links["runtime.host"].orEmpty() }.map { it.ref }).toSet()
                        val vms = inventory.vms.filter { it.ref in vmRefs }
                        val complete = vms.size == vmRefs.size && vms.all { it.get("runtime.powerState") != null } && ("vm" in host.links || vms.isNotEmpty())
                        HostInfoRow("VM đang chạy", if (complete) vms.count { it.get("runtime.powerState") == "poweredOn" }.toString() else "Chưa đủ dữ liệu")
                        HostInfoRow("CPU", "${host.get("summary.hardware.numCpuCores") ?: "—"} core × ${host.get("summary.hardware.cpuMhz") ?: "—"} MHz")
                        HostInfoRow("RAM", host.number("summary.hardware.memorySize")?.let { capacityDisplay(it).toString() } ?: "Chưa có dữ liệu")
                    }
                    Column(Modifier.fillMaxWidth().testTag("host-usage-${host.ref}"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ResourceBar("CPU", totalCpuUsage(InventorySnapshot(listOf(host)))) { value ->
                            value?.let { java.lang.String.format(java.util.Locale.US, "%.2f GHz", it / 1000.0) } ?: "—"
                        }
                        ResourceBar("RAM", ResourceUsage(
                            host.number("summary.quickStats.overallMemoryUsage")?.takeIf { it >= 0 }?.times(1024L * 1024L),
                            host.number("summary.hardware.memorySize")?.takeIf { it >= 0 }
                        )) { value -> value?.let { capacityDisplay(it).toString() } ?: "—" }
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        listOf(listOf(if (host.get("runtime.inMaintenanceMode") == "true") "Thoát maintenance" else "Vào maintenance", if (host.get("runtime.connectionState") == "connected") "Disconnect" else "Reconnect"), listOf("Reboot host", "Shutdown host")).forEach { actions ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                actions.forEach { label -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) { Text(label, fontSize = 11.sp) } }
                            }
                        }
                        }
                    Text("Chỉ đọc · Thao tác quản trị host chưa tích hợp.", color = hostMuted, fontSize = 11.sp)
                } else if (tab == "Network") {
                    HostNetworkDetails(host)
                } else {
                    Text(when (tab) {
                        "Dịch vụ" -> "Dịch vụ hệ thống"; "Network" -> "Standard switch / VMkernel"; "Storage" -> "Adapter & Datastore"
                        "DNS / NTP" -> "DNS và NTP"; "Firewall" -> "Ruleset có sẵn trên host"; "Nâng cao" -> "Advanced Settings"
                        "Truy cập" -> "Lockdown & quyền truy cập"; "PCI / Autostart" -> "PCI passthrough / VM Autostart"; else -> "Tasks của ${host.get("name") ?: host.ref}"
                    }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Chưa tích hợp đọc cấu hình $tab cho host này. Không có dữ liệu thực tế để hiển thị hoặc chỉnh sửa.", color = hostMuted, fontSize = 12.sp,
                        modifier = Modifier.testTag("host-tab-unavailable"))
                }
                if (host.missing.isNotEmpty()) Text("Property không trả về: ${host.missing.joinToString()}", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
        }
    }
    BoxWithConstraints {
        if (maxWidth >= 900.dp) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.width(215.dp)) { picker() }
            Box(Modifier.weight(1f)) { detail() }
        } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { picker(); detail() }
    }
}

@Composable
private fun HealthIndicator(status: String?) {
    val color = when (status) {
        "green" -> Color(0xFF36B978)
        "yellow" -> Color(0xFFE6AB36)
        "red" -> Color(0xFFEB6262)
        else -> Color(0xFF8495A1)
    }
    Canvas(Modifier.size(9.dp).semantics { contentDescription = "Health: ${status ?: "Chưa có dữ liệu"}" }) {
        drawCircle(color)
    }
}

@Composable
private fun HostIndicators(host: InventoryItem) {
    val connection = host.get("runtime.connectionState")
    val color = when {
        connection == "connected" -> Color(0xFF36B978)
        connection == "disconnected" || connection == "notResponding" -> Color(0xFFEB6262)
        else -> Color(0xFF8495A1)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(16.dp).semantics {
            contentDescription = "Kết nối: ${connection ?: "Chưa có dữ liệu"}"
        }) {
            val stroke = 1.6.dp.toPx()
            val outline = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val linked = connection == "connected"
            drawArc(color, 60f, 240f, false,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.06f, size.height * 0.3f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.55f, size.height * 0.4f), style = outline)
            drawArc(color, 240f, 240f, false,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.39f, size.height * 0.3f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.55f, size.height * 0.4f), style = outline)
            if (linked) {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height / 2),
                    androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height / 2), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
            } else {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.57f, size.height * 0.12f),
                    androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.88f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        HealthIndicator(host.get("overallStatus"))
    }
}

@Composable
private fun HostPickerGrid(hosts: List<InventoryItem>, selectedRef: String?, select: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 260.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            hosts.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { host ->
                        HostPick(host, selectedRef == host.ref, Modifier.weight(1f)) { select(host.ref) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HostPick(host: InventoryItem, selected: Boolean, modifier: Modifier = Modifier, select: () -> Unit) {
    OutlinedButton(onClick = select, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (selected) Color(0xFFD8E3E9) else hostBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Color(0xFFD8E3E9) else Color.Transparent,
            contentColor = if (selected) Color(0xFF0E161B) else Color(0xFFF3F6F8)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = modifier.heightIn(min = 48.dp).testTag("host-pick-${host.ref}")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(host.get("name") ?: host.ref, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        HostIndicators(host)
                    }
    }
}

@Composable
private fun HostInfoRow(label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, color = hostMuted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        HorizontalDivider(color = hostBorder)
    }
}
