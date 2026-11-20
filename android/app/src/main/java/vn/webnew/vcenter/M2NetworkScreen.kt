package vn.webnew.vcenter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private fun shown(value: String?) = value ?: "Chưa có dữ liệu"
private fun unavailable(item: InventoryItem, path: String, present: Boolean): String? = when {
    path in item.missing -> "Không đọc được $path (quyền/property không khả dụng)."
    !present -> "$path chưa được trả về; không thể xác nhận cấu hình."
    else -> null
}

@Composable
internal fun HostNetworkDetails(host: InventoryItem) {
    val network = host.hostNetwork
    if (network == null) {
        Text("Chưa có dữ liệu network của host.")
        return
    }
    val paths = listOf("config.network.vswitch", "config.network.portgroup", "config.network.vnic", "config.network.pnic")
    paths.forEach { path -> unavailable(host, path, path in network.available)?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    } }
    if ("config.network.vswitch" in network.available) {
        Text("Standard vSwitch (${network.switches.size})", style = MaterialTheme.typography.titleMedium)
        network.switches.forEach { sw -> Text("${shown(sw.name)} · MTU ${shown(sw.mtu)} · uplinks: ${sw.uplinks.joinToString().ifEmpty { "Chưa có dữ liệu" }}") }
    }
    if ("config.network.portgroup" in network.available) {
        Text("Standard portgroup (${network.portgroups.size})", style = MaterialTheme.typography.titleMedium)
        network.portgroups.forEach { pg -> Text("${shown(pg.name)} · ${shown(pg.switchName)} · VLAN ${shown(pg.vlanId)}") }
    }
    if ("config.network.vnic" in network.available) {
        Text("VMkernel (${network.vmks.size})", style = MaterialTheme.typography.titleMedium)
        network.vmks.forEach { nic -> Text("${shown(nic.device)} · ${shown(nic.portgroup)} · IP ${shown(nic.ip)}") }
    }
    if ("config.network.pnic" in network.available) {
        Text("Physical uplinks (${network.uplinks.size})", style = MaterialTheme.typography.titleMedium)
        network.uplinks.forEach { nic -> Text("${shown(nic.device)} · link ${nic.linkSpeedMb?.let { "$it Mbps" } ?: "Chưa có dữ liệu"}") }
    }
}

@Composable
internal fun M2NetworkScreen(inventory: InventorySnapshot) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Network · Chỉ đọc", style = MaterialTheme.typography.headlineSmall)
        val items = inventory.networks
        if (items.isEmpty() && inventory.hosts.isEmpty()) Text("Không có network hoặc host trong phạm vi dữ liệu trả về.")
        items.forEach { item ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.get("name") ?: item.ref, style = MaterialTheme.typography.titleMedium)
                    Text("${item.type} · ${item.ref}", style = MaterialTheme.typography.bodySmall)
                    if (item.type == "VmwareDistributedVirtualSwitch") {
                        Text("UUID: ${shown(item.get("uuid"))} · LACP API: ${shown(item.get("config.lacpApiVersion"))}")
                        unavailable(item, "config.lacpGroupConfig", item.lacpGroups != null)?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        item.lacpGroups?.let { groups ->
                            Text("LAG / LACP (${groups.size})")
                            groups.forEach { group -> Text("${shown(group.name)} · key ${shown(group.key)} · mode ${shown(group.mode)} · ports ${shown(group.uplinkNum)} · uplinks ${group.uplinks.joinToString().ifEmpty { "Chưa có dữ liệu" }} · balance ${shown(group.loadBalance)}") }
                        }
                    }
                    if (item.type == "DistributedVirtualPortgroup") {
                        Text("vDS: ${item.links["config.distributedVirtualSwitch"]?.firstOrNull()?.let(inventory::name) ?: "Chưa có dữ liệu"}")
                    }
                    if (item.missing.isNotEmpty()) Text("Property không trả về: ${item.missing.joinToString()}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        inventory.hosts.forEach { host ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Host: ${host.get("name") ?: host.ref}", style = MaterialTheme.typography.titleMedium)
                    HostNetworkDetails(host)
                }
            }
        }
    }
}
