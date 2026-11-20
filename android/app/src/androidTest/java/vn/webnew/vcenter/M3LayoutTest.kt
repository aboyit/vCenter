package vn.webnew.vcenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class M3LayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerLoadingKeepsLayoutStableAndTaskSearchDoesNotMoveOnRefresh() {
        val loading = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent { MaterialTheme {
            Column {
                HeaderUpdateStatus(1_790_000_000_000, loading.value)
                TasksScreen(ConnectionState(tasksLoading = loading.value), {}, {}, {})
            }
        } }
        val header = compose.onNodeWithTag("header-update").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithTag("task-search").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("header-loading").assertDoesNotExist()
        compose.runOnIdle { loading.value = true }
        compose.onNodeWithTag("header-loading").assertExists()
        assertEquals(header, compose.onNodeWithTag("header-update").fetchSemanticsNode().boundsInRoot)
        assertEquals(search, compose.onNodeWithTag("task-search").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Hoạt động vCenter").assertDoesNotExist()
        compose.onNodeWithTag("refresh-tasks").assertDoesNotExist()
    }

    @Test fun powerCommandRequiresConfirmationAndGuestRequiresTools() {
        val vm = InventoryItem("vm-1", "VirtualMachine", mapOf("name" to "Lab", "runtime.powerState" to "poweredOn"), emptyMap(), emptySet())
        var command: VmOperation? = null
        compose.setContent { MaterialTheme { VmActionsPanel(vm, false, false) { command = it } } }
        compose.onNodeWithTag("vm-actions-vm-1").performClick()
        compose.onNodeWithTag("vm-action-SHUTDOWN_GUEST").assertIsNotEnabled()
        compose.onNodeWithTag("vm-action-POWER_ON").assertIsNotEnabled()
        compose.onNodeWithTag("vm-action-POWER_OFF").performClick()
        assertNull(command)
        compose.onNodeWithText("Hủy").performClick()
        assertNull(command)
        compose.onNodeWithTag("vm-actions-vm-1").performClick()
        compose.onNodeWithTag("vm-action-POWER_OFF").performClick()
        compose.onNodeWithTag("confirm-vm-operation").performClick()
        compose.runOnIdle { assertEquals(VmOperationAction.POWER_OFF, command?.action) }
    }

    @Test fun vmDetailUsesTwoColumnsAndKeepsOperationsBehindConfirmation() {
        val vm = InventoryItem("vm-1", "VirtualMachine", mapOf("name" to "web-prod-01",
            "runtime.powerState" to "poweredOn", "config.hardware.numCPU" to "8",
            "config.hardware.memoryMB" to "16384"), emptyMap(), emptySet())
        var command: VmOperation? = null
        var closed = false
        compose.setContent { MaterialTheme {
            VmDetailDialog(InventorySnapshot(listOf(vm)), vm, ConnectionState(), { command = it }) { closed = true }
        } }
        compose.onNodeWithText("Đang chạy").assertExists()
        compose.onNodeWithText("8 vCPU / 16 GiB").assertExists()
        val resources = compose.onNodeWithTag("vm-detail-resources").fetchSemanticsNode().boundsInRoot
        val placement = compose.onNodeWithTag("vm-detail-placement").fetchSemanticsNode().boundsInRoot
        assertEquals(resources.top, placement.top, 1f)
        assertTrue(resources.right < placement.left)
        compose.onNodeWithText("⇄ Migration").assertIsNotEnabled()
        compose.onNodeWithText("⚙ Cấu hình").assertIsNotEnabled()
        compose.onNodeWithText("▣ Clone VM").assertIsNotEnabled()
        compose.onNodeWithText("⌨ Console").assertIsNotEnabled()
        compose.onNodeWithTag("vm-actions-vm-1").performClick()
        compose.onNodeWithText("Điều khiển nguồn").assertIsDisplayed()
        compose.onNodeWithText("Quay lại").performScrollTo().performClick()
        compose.runOnIdle { assertNull(command) }
        compose.onNodeWithTag("vm-actions-vm-1").performClick()
        compose.onNodeWithTag("vm-action-POWER_OFF").performScrollTo().performClick()
        compose.runOnIdle { assertNull(command) }
        compose.onNodeWithText("Hủy").performClick()
        compose.onNodeWithTag("vm-detail-snapshots").performClick()
        compose.onNodeWithTag("vm-action-CREATE_SNAPSHOT").assertIsEnabled()
        compose.onNodeWithTag("vm-action-POWER_OFF").assertDoesNotExist()
        compose.onNodeWithTag("vm-action-CREATE_SNAPSHOT").performClick()
        compose.onNodeWithText("Hủy").performClick()
        compose.onNodeWithTag("vm-detail-close").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(closed); assertNull(command) }
    }

    @Test fun unresolvedOperationRequiresExplicitManualReview() {
        val pending = PendingOperation("op", "scope", "vm-1", "RESET", 1)
        var reviewed: String? = null
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PendingOperationsPanel(listOf(pending)) { reviewed = it }
            }
        } }
        compose.onNodeWithText("Đối soát thủ công").performClick()
        assertNull(reviewed)
        compose.onNodeWithTag("confirm-operation-review").performClick()
        compose.runOnIdle { assertEquals("op", reviewed) }
    }

    @Test fun taskCancellationNeedsConfirmationAndDoesNotInventCompletion() {
        val task = VsphereTask("task-1", "running", null, "lab-user", null, null, null, 40, null, true, false)
        var cancelled: String? = null
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TasksScreen(ConnectionState(tasks = listOf(task)), {}, { cancelled = it }, {})
            }
        } }
        compose.onNodeWithTag("cancel-task-1").performScrollTo().performClick()
        assertNull(cancelled)
        compose.onNodeWithTag("confirm-cancel-task").performClick()
        compose.runOnIdle { assertEquals("task-1", cancelled) }
        compose.onNodeWithText("Server xác nhận cancelled = true").assertDoesNotExist()
        compose.onNodeWithText("Đang chạy").assertExists()
    }

    @Test fun taskDetailsAndFiltersExposeServerOperationWithoutChangingTask() {
        val running = VsphereTask("task-running", "running", TaskEntity("VirtualMachine", "vm-1", "Lab VM"),
            "lab-user", null, "2026-11-20T01:00:00Z", null, 40, null, false, false,
            name = "PowerOnVM_Task", descriptionId = "VirtualMachine.powerOn", description = "Power on virtual machine")
        val failed = running.copy(ref = "task-failed", state = "error", description = "Create snapshot",
            error = TaskError("NoPermission", "Permission denied"))
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TasksScreen(ConnectionState(tasks = listOf(running, failed)), {}, {}, {})
            }
        } }
        val screenWidth = compose.onNodeWithTag("task-search").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithTag("detail-task-running").performScrollTo().performClick()
        compose.onNodeWithTag("task-detail").assertExists()
        val dialogWidth = compose.onNodeWithTag("task-detail").fetchSemanticsNode().boundsInRoot.width
        assertTrue("Task popup should span the screen with narrow side margins", dialogWidth >= screenWidth * 0.9f)
        compose.onAllNodes(hasText("Power on virtual machine") and hasAnyAncestor(hasTestTag("task-detail"))).onFirst().assertIsDisplayed()
        compose.onNodeWithText("Task reference").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PowerOnVM_Task").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("close-task-detail").performClick()
        compose.onNodeWithTag("task-filter-error").performScrollTo().performClick()
        compose.onNodeWithTag("task-task-running").assertDoesNotExist()
        compose.onNodeWithTag("task-task-failed").assertExists()
        compose.onNodeWithTag("task-search").performScrollTo().performTextInput("missing-vm")
        compose.onNodeWithText("Không có task khớp tìm kiếm hoặc bộ lọc.").assertExists()
    }

    @Test fun journalSurvivesRecreationAndFiltersOnlyDeletedProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val profile = "m3-test-${java.util.UUID.randomUUID()}"
        val other = "$profile-other"
        val journal = OperationJournal(context)
        try {
            journal.put(PendingOperation("$profile-op", "$profile|server|user|instance", "vm-1", "RESET", 1, "task-1", "running"))
            journal.put(PendingOperation("$other-op", "$other|server|user|instance", "vm-1", "RESET", 2))
            val restored = OperationJournal(context).all().first { it.id == "$profile-op" }
            assertEquals("task-1", restored.taskRef)
            assertTrue(OperationPolicy.blocks(restored, restored.scope, "vm-1"))
            journal.removeScope(profile)
            assertFalse(journal.all().any { it.id == "$profile-op" })
            assertTrue(journal.all().any { it.id == "$other-op" })
        } finally { journal.removeScope(profile); journal.removeScope(other) }
    }
}
