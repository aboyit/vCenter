package vn.webnew.vcenter

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TasksTest {
    private fun response(method: String, content: String) =
        "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/' xmlns:v='urn:vim25'>" +
            "<s:Body><v:${method}Response>$content</v:${method}Response></s:Body></s:Envelope>"

    @Test fun historyRequestsAreRootRecursiveBoundedAndEscaped() {
        val create = TaskSoap.createHistoryCollector("task<&", "group<&", Instant.parse("2026-11-20T02:00:00Z"))
        assertTrue(create.contains("CreateCollectorForTasks"))
        assertTrue(create.contains("task&lt;&amp;"))
        assertTrue(create.contains("group&lt;&amp;"))
        assertTrue(create.contains("<vim:recursion>all</vim:recursion>"))
        assertTrue(create.contains("2026-11-19T02:00:00Z"))
        assertTrue(TaskSoap.readNext("collector<&").contains("<vim:maxCount>100</vim:maxCount>"))
        assertTrue(TaskSoap.destroyCollector("collector<&").contains("collector&lt;&amp;"))
        assertTrue(TaskSoap.cancelTask("task<&").contains("task&lt;&amp;"))
    }

    @Test fun retainsCloneResultReferenceSeparatelyFromSourceEntity() {
        val xml = response("RetrievePropertiesEx", "<v:returnval><v:objects><v:obj type='Task'>task-clone</v:obj>" +
            "<v:propSet><v:name>info</v:name><v:val><v:state>success</v:state>" +
            "<v:entity type='VirtualMachine'>vm-source</v:entity><v:result type='VirtualMachine'>vm-clone</v:result>" +
            "</v:val></v:propSet></v:objects></v:returnval>")
        val task = TaskSoap.parseTasks(xml, listOf("task-clone")).single()
        assertEquals("vm-source", task.entity?.ref)
        assertEquals("vm-clone", task.resultRef)
        assertEquals("VirtualMachine", task.resultType)
    }

    @Test fun parsesTaskInfoAndRetainsUnknownState() {
        val xml = response("RetrievePropertiesEx", "<v:returnval><v:objects><v:obj type='Task'>task-1</v:obj>" +
            "<v:propSet><v:name>info</v:name><v:val><v:state>futureState</v:state>" +
            "<v:entity type='VirtualMachine'>vm-1</v:entity><v:entityName>DB &amp; API</v:entityName>" +
            "<v:reason><v:userName>DOMAIN\\user</v:userName></v:reason>" +
            "<v:queueTime>2026-11-20T01:00:00Z</v:queueTime>" +
            "<v:startTime>2026-11-20T01:00:01Z</v:startTime>" +
            "<v:completeTime>2026-11-20T01:00:05Z</v:completeTime>" +
            "<v:progress>75</v:progress><v:cancelable>true</v:cancelable><v:cancelled>false</v:cancelled>" +
            "</v:val></v:propSet></v:objects></v:returnval>")
        val task = TaskSoap.parseTasks(xml, listOf("task-1")).single()
        assertEquals("futureState", task.state)
        assertEquals(TaskEntity("VirtualMachine", "vm-1", "DB & API"), task.entity)
        assertEquals("DOMAIN\\user", task.user)
        assertEquals(75, task.progress)
        assertEquals(true, task.cancelable)
        assertEquals(false, task.cancelled)
        assertTrue(task.accessible)
    }

    @Test fun missingTaskAndMissingPropertiesAreDistinctFromSuccessfulNulls() {
        val xml = response("RetrievePropertiesEx", "<v:returnval><v:objects><v:obj type='Task'>task-ok</v:obj>" +
            "<v:propSet><v:name>info</v:name><v:val><v:state>success</v:state></v:val></v:propSet>" +
            "<v:missingSet><v:path>info.progress</v:path><v:fault/></v:missingSet>" +
            "</v:objects></v:returnval>")
        val tasks = TaskSoap.parseTasks(xml, listOf("task-ok", "task-denied"))
        assertTrue(tasks[0].accessible)
        assertEquals("success", tasks[0].state)
        assertTrue("info.progress" in tasks[0].missing)
        assertFalse(tasks[1].accessible)
        assertEquals(setOf("object"), tasks[1].missing)
        assertNull(tasks[1].state)
    }

    @Test fun parsesLocalizedErrorAndHistoryReferences() {
        val xml = response("RetrievePropertiesEx", "<v:returnval><v:objects><v:obj type='Task'>task-2</v:obj>" +
            "<v:propSet><v:name>info</v:name><v:val><v:error xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " +
            "xsi:type='v:InvalidPowerState'><v:localizedMessage>Invalid state</v:localizedMessage></v:error></v:val></v:propSet>" +
            "</v:objects></v:returnval>")
        assertEquals(TaskError("InvalidPowerState", "Invalid state"), TaskSoap.parseTasks(xml, listOf("task-2")).single().error)
        val history = response("ReadNextTasks", "<v:returnval><v:key type='Task'>task-1</v:key><v:state>running</v:state></v:returnval>" +
            "<v:returnval><v:key type='Task'>task-2</v:key><v:state>success</v:state></v:returnval>")
        assertEquals(listOf("task-1", "task-2"), TaskSoap.historyRefs(history))
        assertEquals(listOf("running", "success"), TaskSoap.parseHistory(history).map { it.state })
        assertEquals("history-1", TaskSoap.collectorRef(response("CreateCollectorForTasks", "<v:returnval type='TaskHistoryCollector'>history-1</v:returnval>")))
    }

    @Test fun operationDetailsAreParsedFromHistoryAndTrackedTasks() {
        val info = "<v:state>success</v:state><v:name>PowerOnVM_Task</v:name>" +
            "<v:descriptionId>VirtualMachine.powerOn</v:descriptionId>" +
            "<v:description><v:message>Power on virtual machine</v:message></v:description>"
        val history = TaskSoap.parseHistory(response("ReadNextTasks",
            "<v:returnval><v:key>task-1</v:key>$info</v:returnval>")).single()
        val tracked = TaskSoap.parseTasks(response("RetrievePropertiesEx",
            "<v:returnval><v:objects><v:obj type='Task'>task-1</v:obj><v:propSet>" +
                "<v:name>info</v:name><v:val>$info</v:val></v:propSet></v:objects></v:returnval>"), listOf("task-1")).single()
        listOf(history, tracked).forEach {
            assertEquals("PowerOnVM_Task", it.name)
            assertEquals("VirtualMachine.powerOn", it.descriptionId)
            assertEquals("Power on virtual machine", it.description)
            assertEquals("Power on virtual machine", it.taskTitle())
        }
        assertEquals("VirtualMachine.powerOn", history.copy(description = null).taskTitle())
        assertEquals("PowerOnVM_Task", history.copy(description = null, descriptionId = null).taskTitle())
    }

    @Test fun taskTimesUseLocalZoneAndDurationDoesNotInventMissingCompletion() {
        val task = VsphereTask("task-1", "success", null, null, null,
            "2026-11-20T03:49:48.194Z", "2026-11-20T03:49:48.369999Z", null, null, false, false)
        assertEquals("20/11/2026 10:49:48", taskTime(task.startedAt, java.time.ZoneId.of("Asia/Ho_Chi_Minh")))
        assertEquals("175 ms", taskDuration(task))
        assertEquals("Chưa có thời lượng", taskDuration(task.copy(completedAt = null)))
        assertEquals("Chưa kết thúc", taskDuration(task.copy(state = "running", completedAt = null)))
        assertEquals("Thời gian không hợp lệ", taskDuration(task.copy(completedAt = "2026-11-20T03:00:00Z")))
        assertEquals("invalid", taskTime("invalid"))
        assertEquals("Chưa có dữ liệu", taskTime(null))
    }

    @Test fun trackedLookupIsBoundedAndRequestsAllInfoFields() {
        val request = TaskSoap.readTasks("collector", listOf("task<&"))
        assertTrue(request.contains("task&lt;&amp;"))
        assertTrue(request.contains("<vim:pathSet>info</vim:pathSet>"))
        assertThrows(IllegalArgumentException::class.java) {
            TaskSoap.readTasks("collector", (0..100).map { "task-$it" })
        }
    }
}
