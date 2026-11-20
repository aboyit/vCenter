package vn.webnew.vcenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class MonitoringLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun networkDoesNotTreatMissingHostConfigAsEmptySwitchList() {
        val host = InventoryItem("host-1", "HostSystem", mapOf("name" to "Lab ESXi"), emptyMap(), setOf("config.network.vswitch"))
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { M2NetworkScreen(InventorySnapshot(listOf(host))) }
            }
        }
        compose.onNodeWithText("Host: Lab ESXi").assertExists()
        compose.onNodeWithText("Chưa có dữ liệu network của host.").assertExists()
        compose.onNodeWithText("Standard vSwitch (0)").assertDoesNotExist()
    }

    @Test fun monitoringLoadsWithoutInventoryAndSeparatesPermissionErrors() {
        val snapshot = MonitoringSnapshot(eventError = "Không có quyền đọc sự kiện")
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InventoryScreen(InventorySection.Alarms, ConnectionState(monitoring = snapshot), {})
                }
            }
        }
        compose.onNodeWithText("Cảnh báo đang kích hoạt (0)").assertExists()
        compose.onNodeWithText("Không có quyền đọc sự kiện").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Không có sự kiện trong khoảng thời gian truy vấn.").assertDoesNotExist()
        compose.onNodeWithText("Chưa có dữ liệu; bấm Làm mới.").assertDoesNotExist()
    }
}
