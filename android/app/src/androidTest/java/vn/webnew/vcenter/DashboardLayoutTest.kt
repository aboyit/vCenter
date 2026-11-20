package vn.webnew.vcenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class DashboardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun vmFilterCombinesSearchAndShowsReadOnlySnapshotTree() {
        val root = VmSnapshot("snapshot-1", "Before update", "Lab baseline", "2026-11-18T10:00:00Z", "poweredOff", "true", 0)
        val child = VmSnapshot("snapshot-2", "After update", null, "2026-11-18T11:00:00Z", "poweredOn", "false", 1)
        val on = InventoryItem("vm-1", "VirtualMachine", mapOf("name" to "Lab Alpha", "runtime.powerState" to "poweredOn"),
            emptyMap(), emptySet(), VmSnapshotInfo(listOf(root, child), "snapshot-2"))
        val off = InventoryItem("vm-2", "VirtualMachine", mapOf("name" to "Lab Beta", "runtime.powerState" to "poweredOff"), emptyMap(), emptySet())
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InventoryScreen(InventorySection.Vms, ConnectionState(inventory = InventorySnapshot(listOf(on, off))), {})
                }
            }
        }
        compose.onNodeWithTag("vm-power-filter").performClick()
        compose.onNodeWithText("Đang bật").performClick()
        compose.onNodeWithText("Lab Beta").assertDoesNotExist()
        compose.onNodeWithText("Hiển thị 1/2 máy ảo").assertExists()
        compose.onNodeWithTag("vm-row-vm-1").performScrollTo().performClick()
        compose.onNodeWithTag("snapshots-vm-1").performScrollTo().performClick()
        compose.onNodeWithText("Before update").assertExists()
        compose.onNodeWithText("After update · Hiện tại").assertExists()
        compose.onNodeWithText("Đóng").performScrollTo().performClick()
        compose.onNodeWithTag("vm-search").performScrollTo().performTextInput("Beta")
        compose.onNodeWithText("Không có máy ảo phù hợp.").assertExists()
        compose.onNodeWithTag("vm-power-filter").performClick()
        compose.onNodeWithText("Tất cả").performClick()
        compose.onNodeWithText("Lab Beta").assertExists()
        compose.onNodeWithTag("vm-row-vm-2").performScrollTo().performClick()
        compose.onNodeWithText("Snapshot: không có snapshot được server trả về.").assertExists()
    }

    @Test fun hostPageSelectsEsxiAndShowsReferenceTabsWithoutFakeSettings() {
        val cluster = InventoryItem("cluster-1", "ClusterComputeResource", mapOf("name" to "Lab Cluster"), mapOf("host" to listOf("host-1", "host-2")), emptySet())
        val host = InventoryItem("host-1", "HostSystem", mapOf("name" to "Alpha ESXi", "runtime.connectionState" to "connected", "runtime.inMaintenanceMode" to "false"), emptyMap(), emptySet())
        val second = host.copy(ref = "host-2", values = host.values + mapOf("name" to "Beta ESXi",
            "summary.hardware.numCpuCores" to "4", "summary.hardware.cpuMhz" to "2500",
            "summary.quickStats.overallCpuUsage" to "2000", "summary.hardware.memorySize" to "17179869184",
            "summary.quickStats.overallMemoryUsage" to "4096"))
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InventoryScreen(InventorySection.Hosts, ConnectionState(inventory = InventorySnapshot(listOf(cluster, host, second,
                        InventoryItem("pool-1", "ResourcePool", mapOf("name" to "Hidden pool"), emptyMap(), emptySet())))), {})
                }
            }
        }
        compose.onNodeWithTag("screen-title").assertDoesNotExist()
        compose.onNodeWithText("Lab Cluster").assertExists()
        compose.onNodeWithText("Resource pools").assertDoesNotExist()
        compose.onNodeWithText("Hidden pool").assertDoesNotExist()
        compose.onNode(hasText("connected") and hasAnyAncestor(hasTestTag("host-pick-host-1")), useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Kết nối: connected", useUnmergedTree = true).assertCountEquals(3)
        val firstBounds = compose.onNodeWithTag("host-pick-host-1").fetchSemanticsNode().boundsInRoot
        val secondBounds = compose.onNodeWithTag("host-pick-host-2").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(firstBounds.width, secondBounds.width, 1f)
        org.junit.Assert.assertEquals(firstBounds.top, secondBounds.top, 1f)
        compose.onNodeWithTag("host-pick-host-2").performClick()
        compose.onNodeWithTag("host-detail-host-2").assertExists()
        compose.onNodeWithText("Đã dùng: 2.00 GHz").assertExists()
        compose.onNodeWithText("Trống: 8.00 GHz").assertExists()
        compose.onNodeWithText("Đã dùng: 4.0 GiB").assertExists()
        compose.onNodeWithText("Trống: 12.0 GiB").assertExists()
        compose.onNodeWithText("Reboot host").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("host-tab-Dịch vụ").performScrollTo().performClick()
        compose.onNodeWithText("Dịch vụ hệ thống").assertExists()
        compose.onNodeWithTag("host-tab-unavailable").assertExists()
        compose.onNodeWithTag("host-pick-host-1").performScrollTo().performClick()
        compose.onNodeWithTag("host-detail-host-1").assertExists()
        compose.onNodeWithText("Quản trị host").assertExists()
        compose.onNodeWithText("Chưa đủ dữ liệu CPU").assertExists()
        compose.onNodeWithText("Chưa đủ dữ liệu RAM").assertExists()
        compose.onNodeWithText("Đã dùng: 2.00 GHz").assertDoesNotExist()
        val summaryBounds = compose.onNodeWithTag("host-summary").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("Host summary should stay compact", summaryBounds.bottom - summaryBounds.top < 240.dp)
    }

    @Test fun vmTreeExpandsClusterAndHostAndKeepsStandaloneVisible() {
        val cluster = InventoryItem("cluster-1", "ClusterComputeResource", mapOf("name" to "Lab Cluster"),
            mapOf("host" to listOf("host-1")), emptySet())
        val host = InventoryItem("host-1", "HostSystem", mapOf("name" to "Cluster Host"), emptyMap(), emptySet())
        val standalone = InventoryItem("host-2", "HostSystem", mapOf("name" to "Standalone"), emptyMap(), emptySet())
        val vm = InventoryItem("vm-1", "VirtualMachine", mapOf("name" to "Nested VM"),
            mapOf("runtime.host" to listOf("host-1")), emptySet())
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    VmListScreen(InventorySnapshot(listOf(cluster, host, standalone, vm)))
                }
            }
        }
        compose.onNodeWithText("Cluster · Lab Cluster").assertExists()
        compose.onNodeWithTag("vm-tree-host-2").assertExists()
        compose.onNodeWithTag("vm-tree-cluster-1").performClick()
        compose.onNodeWithTag("vm-row-vm-1").assertDoesNotExist()
        compose.onNodeWithTag("vm-tree-host-1").assertDoesNotExist()
        compose.onNodeWithTag("vm-tree-cluster-1").performClick()
        compose.onNodeWithTag("vm-tree-host-1").performScrollTo().performClick()
        compose.onNodeWithTag("vm-row-vm-1").assertDoesNotExist()
        compose.onNodeWithTag("vm-tree-host-1").performClick()
        compose.onNodeWithTag("vm-row-vm-1").assertExists()
        val rowBounds = compose.onNodeWithTag("vm-row-vm-1").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("VM rows should stay compact", rowBounds.bottom - rowBounds.top <= 64.dp)
    }

    @Test fun overviewMatchesReferenceStructureWithScrollableVmTable() {
        val host = InventoryItem("host-1", "HostSystem", mapOf("name" to "Lab Host", "runtime.connectionState" to "connected",
            "summary.hardware.memorySize" to "17179869184", "summary.hardware.numCpuCores" to "4",
            "summary.hardware.cpuMhz" to "2500", "summary.quickStats.overallCpuUsage" to "2000",
            "summary.quickStats.overallMemoryUsage" to "4096"), emptyMap(), emptySet())
        val vm = InventoryItem("vm-1", "VirtualMachine", mapOf("name" to "Lab VM", "runtime.powerState" to "poweredOn",
            "config.guestFullName" to "Linux", "config.hardware.numCPU" to "2", "config.hardware.memoryMB" to "2048",
            "guest.ipAddress" to "192.0.2.10"), mapOf("runtime.host" to listOf("host-1")), emptySet())
        val state = ConnectionState(inventory = InventorySnapshot(listOf(host, vm)))
        compose.setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    InventoryScreen(InventorySection.Overview, state, {})
                }
            }
        }
        compose.onNodeWithText("Tổng quan hạ tầng").assertDoesNotExist()
        compose.onNodeWithText("Mọi tài nguyên. Một góc nhìn.").assertDoesNotExist()
        compose.onNodeWithTag("refresh-inventory").assertDoesNotExist()
        compose.onAllNodesWithTag("dashboard-stat").assertCountEquals(4)
        compose.onNodeWithText("Hosts & Cluster").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("resource-CPU").assertExists()
        compose.onNodeWithTag("dashboard-vm-table").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("TÊN MÁY ẢO").assertExists()
        compose.onNodeWithText("Lab VM").assertExists()
        compose.onNodeWithText("ĐỊA CHỈ IP").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("192.0.2.10").assertExists()
    }

    @Test fun resourceSummaryCombinesFourHostsWithoutIndividualCharts() {
        val hosts = (1..4).map { index ->
            InventoryItem("host-$index", "HostSystem", mapOf("name" to "ESXi $index",
                "summary.hardware.numCpuCores" to "4", "summary.hardware.cpuMhz" to "2500",
                "summary.quickStats.overallCpuUsage" to "2000", "summary.hardware.memorySize" to "17179869184",
                "summary.quickStats.overallMemoryUsage" to "4096"), emptyMap(), emptySet())
        }
        compose.setContent {
            MaterialTheme { EsxiPerformancePanel(InventorySnapshot(hosts), ConnectionState(inventoryError = "Mất kết nối")) }
        }
        listOf("CPU", "RAM", "Storage").forEach { compose.onNodeWithTag("resource-$it").assertIsDisplayed() }
        compose.onNodeWithText("Đã dùng: 8.00 GHz").assertExists()
        compose.onNodeWithText("Tổng: 40.00 GHz").assertExists()
        compose.onNodeWithText("Chưa đủ dữ liệu Storage").assertExists()
        compose.onNodeWithText("ESXi 1").assertDoesNotExist()
        compose.onNodeWithText("Dữ liệu cũ · Mất kết nối").assertExists()
    }
    @Test fun overviewKeepsFourCardsAndNavigatesToVmList() {
        var selected: InventorySection? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DashboardScreen(InventorySnapshot(emptyList())) { selected = it }
                }
            }
        }
        compose.onAllNodesWithTag("dashboard-stat").assertCountEquals(4)
        compose.onNodeWithText("Tổng tài nguyên ESXi").assertExists()
        compose.onNodeWithText("Không có ESXi trong phạm vi dữ liệu trả về.").assertExists()
        compose.onNodeWithText("Xem tất cả →").performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(InventorySection.Vms, selected) }
    }
}
