package vn.webnew.vcenter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

internal enum class InventorySection { Overview, Vms, Hosts, Datastores, Network, Alarms }

private fun bytes(value: Long?) = value?.let { capacityDisplay(it).toString() } ?: "Chưa có dữ liệu"
private fun field(item: InventoryItem, path: String) = item.get(path) ?: if (path in item.missing) "Thiếu quyền / property" else "Chưa có dữ liệu"

@Composable
internal fun InventoryScreen(section: InventorySection, state: ConnectionState, refresh: () -> Unit,
    navigate: (InventorySection) -> Unit = {}, operate: ((VmOperation) -> Unit)? = null,
    loadConfig: ((String) -> Unit)? = null, advanced: ((AdvancedOperation) -> Unit)? = null) {
    if (section == InventorySection.Alarms) {
        Box(Modifier.fillMaxWidth().height(580.dp)) {
            M2MonitoringScreen(state.monitoring, state.monitoringLoading, state.monitoringError)
        }
        return
    }
    state.inventoryError?.let {
        Text("${if (state.inventory != null) "Dữ liệu cũ — " else ""}$it", color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("inventory-error"))
    }
    val inventory = state.inventory
    if (inventory == null) {
        if (!state.inventoryLoading && state.inventoryError == null) Text("Chưa có dữ liệu; bấm Làm mới.")
        return
    }
    if (section == InventorySection.Network) {
        M2NetworkScreen(inventory)
        return
    }
    if (section == InventorySection.Overview) {
        DashboardScreen(inventory, state, navigate)
        return
    }
    if (section == InventorySection.Hosts) {
        HostListScreen(inventory)
        return
    }
    val selected = when (section) {
        InventorySection.Vms -> inventory.vms
        InventorySection.Hosts -> inventory.clusters + inventory.hosts
        InventorySection.Datastores -> inventory.datastores
        else -> inventory.networks
    }
    if (section == InventorySection.Vms) {
        VmListScreen(inventory, state, operate, loadConfig, advanced)
        return
    }
    val shown = selected
    shown.forEach { item ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.get("name") ?: item.ref, style = MaterialTheme.typography.titleMedium)
                Text("${item.type} · ${item.ref}", style = MaterialTheme.typography.bodySmall)
                when (item.type) {
                    "VirtualMachine" -> {
                        Text("Nguồn: ${field(item, "runtime.powerState")} · CPU: ${field(item, "config.hardware.numCPU")} vCPU · RAM: ${field(item, "config.hardware.memoryMB")} MiB")
                        Text("OS: ${field(item, "config.guestFullName")} · IP: ${field(item, "guest.ipAddress")}")
                        Text("Host: ${item.links["runtime.host"]?.firstOrNull()?.let(inventory::name) ?: "Chưa có dữ liệu"}")
                        Text("Datastore: ${item.links["datastore"]?.joinToString { inventory.name(it) } ?: "Chưa có dữ liệu"}")
                        VmSnapshots(item)
                    }
                    "HostSystem" -> {
                        Text("Kết nối: ${field(item, "runtime.connectionState")} · Maintenance: ${field(item, "runtime.inMaintenanceMode")}")
                        Text("CPU: ${field(item, "summary.hardware.numCpuCores")} core × ${field(item, "summary.hardware.cpuMhz")} MHz")
                        Text("RAM: ${bytes(item.number("summary.hardware.memorySize"))} · VM: ${item.links["vm"]?.size ?: "Chưa có dữ liệu"}")
                    }
                    "ClusterComputeResource" -> Text("Host: ${item.links["host"]?.joinToString { inventory.name(it) } ?: "Chưa có dữ liệu"}")
                    "Datastore" -> {
                        Text("Tổng: ${bytes(item.number("summary.capacity"))} · Trống: ${bytes(item.number("summary.freeSpace"))}")
                        val capacity = item.number("summary.capacity")
                        val free = item.number("summary.freeSpace")
                        Text("Đã dùng: ${bytes(if (capacity != null && free != null && free in 0..capacity) capacity - free else null)}")
                        Text("Truy cập: ${field(item, "summary.accessible")}")
                    }
                    "DistributedVirtualPortgroup" -> Text("vDS: ${item.links["config.distributedVirtualSwitch"]?.firstOrNull()?.let(inventory::name) ?: "Chưa có dữ liệu"}")
                    "VmwareDistributedVirtualSwitch" -> Text("UUID: ${field(item, "uuid")}")
                }
                if (item.missing.isNotEmpty()) Text("Property không trả về: ${item.missing.joinToString()}",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (section == InventorySection.Network) Text("Chỉ đọc Network, vDS, distributed portgroup; vSwitch, LAG/LACP chưa tích hợp.")
}

@Composable
internal fun VmSnapshots(item: InventoryItem) {
    var expanded by remember(item.ref) { mutableStateOf(false) }
    val info = item.snapshotInfo
    if ("snapshot" in item.missing) {
        Text("Snapshot: không đọc được property (có thể thiếu quyền).")
        return
    }
    if (info == null || info.snapshots.isEmpty()) {
        Text("Snapshot: không có snapshot được server trả về.")
        return
    }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("snapshots-${item.ref}")) {
        Text("${if (expanded) "Ẩn" else "Xem"} cây snapshot (${info.snapshots.size})")
    }
    if (expanded) {
        Text("Chỉ đọc; thời gian theo server. Trạng thái nguồn là tại lúc tạo snapshot.",
            style = MaterialTheme.typography.bodySmall)
        info.snapshots.forEach { snapshot ->
            Column(Modifier.fillMaxWidth().padding(start = (snapshot.depth.coerceAtMost(6) * 12).dp, bottom = 8.dp)) {
                Text("${snapshot.name ?: snapshot.ref}${if (snapshot.ref == info.currentRef) " · Hiện tại" else ""}",
                    style = MaterialTheme.typography.titleSmall)
                Text("${snapshot.ref} · Cấp ${snapshot.depth} · ${snapshot.createdAt ?: "Chưa có thời gian"}",
                    style = MaterialTheme.typography.bodySmall)
                Text("Nguồn: ${snapshot.powerState ?: "Chưa có dữ liệu"} · Quiesced: ${snapshot.quiesced ?: "Chưa có dữ liệu"}",
                    style = MaterialTheme.typography.bodySmall)
                snapshot.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (info.currentRef == null) Text("Server không chỉ định snapshot hiện tại.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun MetricCard(label: String, value: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
