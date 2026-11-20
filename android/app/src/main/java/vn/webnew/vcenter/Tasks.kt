package vn.webnew.vcenter

import java.time.Instant
import org.w3c.dom.Element

internal data class TaskEntity(val type: String?, val ref: String?, val name: String?)

internal data class TaskError(val type: String?, val message: String?)

internal data class VsphereTask(
    val ref: String,
    val state: String?,
    val entity: TaskEntity?,
    val user: String?,
    val queuedAt: String?,
    val startedAt: String?,
    val completedAt: String?,
    val progress: Int?,
    val error: TaskError?,
    val cancelable: Boolean?,
    val cancelled: Boolean?,
    val accessible: Boolean = true,
    val missing: Set<String> = emptySet(),
    val name: String? = null,
    val descriptionId: String? = null,
    val description: String? = null,
    val resultRef: String? = null, val resultType: String? = null
)

internal object TaskSoap {
    const val MAX_TASKS = 100
    const val HISTORY_WINDOW_HOURS = 24L
    private const val INFO_PATH = "info"

    private fun direct(parent: Element, name: String): List<Element> = (0 until parent.childNodes.length)
        .mapNotNull { parent.childNodes.item(it) as? Element }.filter { it.localName == name }

    private fun text(parent: Element?, name: String): String? = parent?.let { VsphereSoap.children(it, name) }
        ?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun mor(type: String, ref: String) =
        "<vim:_this type=\"$type\">${VsphereSoap.escape(ref)}</vim:_this>"

    fun createHistoryCollector(taskManager: String, rootFolder: String, now: Instant): String =
        VsphereSoap.envelope("CreateCollectorForTasks",
            mor("TaskManager", taskManager) +
                "<vim:filter><vim:entity><vim:entity type=\"Folder\">${VsphereSoap.escape(rootFolder)}</vim:entity>" +
                "<vim:recursion>all</vim:recursion></vim:entity><vim:time><vim:timeType>startedTime</vim:timeType>" +
                "<vim:beginTime>${now.minusSeconds(HISTORY_WINDOW_HOURS * 3600)}</vim:beginTime>" +
                "<vim:endTime>$now</vim:endTime></vim:time></vim:filter>")

    fun readNext(collector: String): String = VsphereSoap.envelope("ReadNextTasks",
        mor("TaskHistoryCollector", collector) + "<vim:maxCount>$MAX_TASKS</vim:maxCount>")

    fun destroyCollector(collector: String): String =
        VsphereSoap.envelope("DestroyCollector", mor("TaskHistoryCollector", collector))

    fun cancelTask(task: String): String = VsphereSoap.envelope("CancelTask", mor("Task", task))

    fun readTasks(collector: String, refs: List<String>): String {
        require(refs.size in 1..MAX_TASKS) { "Task lookup exceeds the 100-reference limit." }
        val unique = refs.distinct()
        require(unique.size == refs.size) { "Task references must be unique." }
        return VsphereSoap.envelope("RetrievePropertiesEx",
            mor("PropertyCollector", collector) + "<vim:specSet><vim:propSet><vim:type>Task</vim:type>" +
                "<vim:pathSet>$INFO_PATH</vim:pathSet></vim:propSet>" +
                unique.joinToString("") { "<vim:objectSet><vim:obj type=\"Task\">${VsphereSoap.escape(it)}</vim:obj></vim:objectSet>" } +
                "</vim:specSet><vim:options><vim:maxObjects>$MAX_TASKS</vim:maxObjects></vim:options>")
    }

    fun collectorRef(xml: String): String = VsphereSoap.children(
        VsphereSoap.result(xml, "CreateCollectorForTasks"), "returnval"
    )?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw SoapFailure("Không tạo được TaskHistoryCollector.")

    fun historyRefs(xml: String): List<String> {
        val refs = direct(VsphereSoap.result(xml, "ReadNextTasks"), "returnval").map { task ->
            val ref = text(task, "key") ?: task.textContent.trim().takeIf { it.isNotEmpty() }
            ref ?: throw SoapFailure("Lịch sử task thiếu object reference.")
        }
        if (refs.size > MAX_TASKS) throw SoapFailure("Server trả về quá giới hạn 100 task.")
        return refs.distinct()
    }

    fun parseTasks(xml: String, requestedRefs: List<String>): List<VsphereTask> {
        require(requestedRefs.size in 1..MAX_TASKS) { "Task lookup exceeds the 100-reference limit." }
        val result = VsphereSoap.children(VsphereSoap.result(xml, "RetrievePropertiesEx"), "returnval")
            ?: return requestedRefs.map { unavailable(it, setOf("object")) }
        if (text(result, "token") != null) throw SoapFailure("Danh sách task chưa đầy đủ.")
        val parsed = direct(result, "objects").associate { obj ->
            val objectRef = VsphereSoap.children(obj, "obj")
                ?: throw SoapFailure("Task thiếu object reference.")
            if (objectRef.getAttribute("type") != "Task") throw SoapFailure("Task response chứa object không hợp lệ.")
            val ref = objectRef.textContent.trim().takeIf { it.isNotEmpty() }
                ?: throw SoapFailure("Task reference không hợp lệ.")
            ref to parseObject(ref, obj)
        }
        return requestedRefs.map { parsed[it] ?: unavailable(it, setOf("object")) }
    }

    private fun unavailable(ref: String, missing: Set<String>) = VsphereTask(
        ref, null, null, null, null, null, null, null, null, null, null, accessible = false, missing = missing
    )

    private fun parseObject(ref: String, obj: Element): VsphereTask {
        val props = direct(obj, "propSet")
        val info = props.firstOrNull { text(it, "name") == INFO_PATH }
            ?.let { VsphereSoap.children(it, "val") }
        val missing = direct(obj, "missingSet").mapNotNull { text(it, "path") }.toSet()
        if (info == null) return unavailable(ref, missing.ifEmpty { setOf(INFO_PATH) })
        fun scalar(name: String) = text(info, name)
        val entityRef = VsphereSoap.children(info, "entity")?.let { entity ->
            TaskEntity(entity.getAttribute("type").takeIf { it.isNotBlank() },
                entity.textContent.trim().takeIf { it.isNotEmpty() }, scalar("entityName"))
        }
        val reason = VsphereSoap.children(info, "reason")
        val user = reason?.let { text(it, "userName") ?: text(it, "user") }
        val errorNode = VsphereSoap.children(info, "error")
        val error = errorNode?.let { node ->
            val type = node.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type")
                .takeIf { it.isNotBlank() }?.substringAfter(':')
            val messages = direct(node, "localizedMessage").mapNotNull { it.textContent.trim().takeIf(String::isNotEmpty) }
            TaskError(type, messages.firstOrNull())
        }
        return VsphereTask(ref, scalar("state"), entityRef, user, scalar("queueTime"),
            scalar("startTime"), scalar("completeTime"), scalar("progress")?.toIntOrNull()
                ?.takeIf { it in 0..100 }, error, scalar("cancelable")?.toBooleanStrictOrNull(),
            scalar("cancelled")?.toBooleanStrictOrNull(), accessible = true, missing = missing,
            name = scalar("name"), descriptionId = scalar("descriptionId"),
            description = VsphereSoap.children(info, "description")?.let { text(it, "message") },
            resultRef = VsphereSoap.children(info, "result")?.takeIf { it.hasAttribute("type") }?.textContent?.trim(),
            resultType = VsphereSoap.children(info, "result")?.getAttribute("type")?.takeIf { it.isNotBlank() })
    }

    fun parseHistory(xml: String): List<VsphereTask> {
        val response = VsphereSoap.result(xml, "ReadNextTasks")
        val refs = direct(response, "returnval").map { info ->
            text(info, "key") ?: throw SoapFailure("Lịch sử task thiếu object reference.")
        }
        if (refs.size > MAX_TASKS) throw SoapFailure("Server trả về quá giới hạn 100 task.")
        return direct(response, "returnval").mapIndexed { index, info -> parseInfo(refs[index], info) }
    }

    private fun parseInfo(ref: String, info: Element): VsphereTask {
        fun scalar(name: String) = text(info, name)
        val entity = VsphereSoap.children(info, "entity")?.let {
            TaskEntity(it.getAttribute("type").takeIf(String::isNotBlank), it.textContent.trim().takeIf(String::isNotEmpty), scalar("entityName"))
        }
        val reason = VsphereSoap.children(info, "reason")
        val errorNode = VsphereSoap.children(info, "error")
        val error = errorNode?.let {
            TaskError(it.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type")
                .takeIf(String::isNotBlank)?.substringAfter(':'), text(it, "localizedMessage"))
        }
        return VsphereTask(ref, scalar("state"), entity, reason?.let { text(it, "userName") ?: text(it, "user") },
            scalar("queueTime"), scalar("startTime"), scalar("completeTime"),
            scalar("progress")?.toIntOrNull()?.takeIf { it in 0..100 }, error,
            scalar("cancelable")?.toBooleanStrictOrNull(), scalar("cancelled")?.toBooleanStrictOrNull(),
            name = scalar("name"), descriptionId = scalar("descriptionId"),
            description = VsphereSoap.children(info, "description")?.let { text(it, "message") })
    }
}
