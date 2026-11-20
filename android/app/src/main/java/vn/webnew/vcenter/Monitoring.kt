package vn.webnew.vcenter

import java.time.Instant
import org.w3c.dom.Element

internal data class MonitoringEntity(val type: String?, val ref: String?, val name: String?)
internal data class MonitoringAlarm(
    val key: String, val alarmRef: String, val name: String?, val entity: MonitoringEntity,
    val status: String, val time: String?, val acknowledged: Boolean?
)
internal data class MonitoringEvent(
    val key: Int, val time: String?, val severity: String?, val entity: MonitoringEntity?,
    val content: String?, val eventType: String?
)
internal data class MonitoringSnapshot(
    val alarms: List<MonitoringAlarm> = emptyList(),
    val events: List<MonitoringEvent> = emptyList(),
    val alarmError: String? = null, val eventError: String? = null,
    val alarmLimitReached: Boolean = false, val eventLimitReached: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
)

internal object MonitoringSoap {
    const val MAX_ALARMS = 100
    const val MAX_EVENTS = 100
    const val EVENT_WINDOW_HOURS = 24L

    private fun direct(parent: Element, name: String): List<Element> = (0 until parent.childNodes.length)
        .mapNotNull { parent.childNodes.item(it) as? Element }.filter { it.localName == name }
    private fun text(parent: Element, name: String): String? = VsphereSoap.children(parent, name)
        ?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    private fun ref(parent: Element, name: String): MonitoringEntity? = VsphereSoap.children(parent, name)?.let {
        MonitoringEntity(it.getAttribute("type").takeIf(String::isNotBlank), it.textContent.trim().takeIf(String::isNotBlank), null)
    }
    private fun prop(collector: String, type: String, objects: List<String>, path: String, max: Int) =
        VsphereSoap.envelope("RetrievePropertiesEx",
            "<vim:_this type=\"PropertyCollector\">${VsphereSoap.escape(collector)}</vim:_this>" +
                "<vim:specSet><vim:propSet><vim:type>$type</vim:type><vim:pathSet>$path</vim:pathSet></vim:propSet>" +
                objects.joinToString("") { "<vim:objectSet><vim:obj type=\"$type\">${VsphereSoap.escape(it)}</vim:obj></vim:objectSet>" } +
                "</vim:specSet><vim:options><vim:maxObjects>$max</vim:maxObjects></vim:options>")

    fun alarms(collector: String, root: String) = prop(collector, "Folder", listOf(root), "triggeredAlarmState", 1)

    fun alarmNames(collector: String, refs: List<String>): String {
        require(refs.size in 1..MAX_ALARMS) { "Alarm name lookup exceeds the 100-reference limit." }
        return prop(collector, "Alarm", refs.distinct(), "info.name", MAX_ALARMS)
    }

    fun events(manager: String, now: Instant): String = VsphereSoap.envelope("QueryEvents",
        "<vim:_this type=\"EventManager\">${VsphereSoap.escape(manager)}</vim:_this>" +
            "<vim:filter><vim:time><vim:beginTime>${now.minusSeconds(EVENT_WINDOW_HOURS * 3600)}</vim:beginTime>" +
            "<vim:endTime>$now</vim:endTime></vim:time><vim:maxCount>$MAX_EVENTS</vim:maxCount></vim:filter>")

    data class AlarmPage(val alarms: List<MonitoringAlarm>, val alarmRefs: List<String>, val limitReached: Boolean)

    fun parseAlarms(xml: String): AlarmPage {
        val result = VsphereSoap.children(VsphereSoap.result(xml, "RetrievePropertiesEx"), "returnval")
            ?: throw SoapFailure("Phản hồi cảnh báo thiếu kết quả.")
        if (text(result, "token") != null) throw SoapFailure("Phản hồi cảnh báo có trang tiếp theo; không thể hiển thị đầy đủ.")
        val root = direct(result, "objects").singleOrNull()
            ?: throw SoapFailure("Phản hồi cảnh báo thiếu rootFolder.")
        if (ref(root, "obj")?.type != "Folder" || ref(root, "obj")?.ref == null)
            throw SoapFailure("Phản hồi cảnh báo không đúng rootFolder.")
        if (direct(root, "missingSet").any { text(it, "path") == "triggeredAlarmState" })
            throw SoapFailure("Không có quyền đọc trạng thái cảnh báo trên rootFolder.")
        val states = direct(root, "propSet").firstOrNull { text(it, "name") == "triggeredAlarmState" }
            ?.let { VsphereSoap.children(it, "val")?.let { valNode -> direct(valNode, "AlarmState") } }
            ?: emptyList()
        val alarms = states.take(MAX_ALARMS).map { state ->
            val key = text(state, "key") ?: throw SoapFailure("Trạng thái cảnh báo thiếu key.")
            val alarmRef = ref(state, "alarm")?.ref ?: throw SoapFailure("Trạng thái cảnh báo thiếu alarm reference.")
            val entity = ref(state, "entity") ?: throw SoapFailure("Trạng thái cảnh báo thiếu đối tượng.")
            MonitoringAlarm(key, alarmRef, null, entity, text(state, "overallStatus") ?: "unknown",
                text(state, "time"), text(state, "acknowledged")?.toBooleanStrictOrNull())
        }
        return AlarmPage(alarms, alarms.map { it.alarmRef }.distinct(), states.size > MAX_ALARMS)
    }

    fun parseAlarmNames(xml: String): Map<String, String> {
        val result = VsphereSoap.children(VsphereSoap.result(xml, "RetrievePropertiesEx"), "returnval")
            ?: throw SoapFailure("Phản hồi tên cảnh báo thiếu kết quả.")
        if (text(result, "token") != null) throw SoapFailure("Danh sách tên cảnh báo chưa đầy đủ.")
        if (direct(result, "objects").any { obj -> direct(obj, "missingSet").any { text(it, "path") == "info.name" } })
            throw SoapFailure("Không có quyền đọc một số tên cảnh báo.")
        return direct(result, "objects").mapNotNull { obj ->
            val alarmRef = ref(obj, "obj")?.ref ?: return@mapNotNull null
            val name = direct(obj, "propSet").firstOrNull { text(it, "name") == "info.name" }
                ?.let { text(it, "val") } ?: return@mapNotNull null
            alarmRef to name
        }.toMap()
    }

    fun parseEvents(xml: String): List<MonitoringEvent> {
        val response = VsphereSoap.result(xml, "QueryEvents")
        val events = direct(response, "returnval")
        if (events.size > MAX_EVENTS) throw SoapFailure("Server trả về quá giới hạn 100 sự kiện.")
        return events.map { event ->
            val key = text(event, "key")?.toIntOrNull() ?: throw SoapFailure("Sự kiện thiếu event ID.")
            val entity = listOf("vm", "host", "ds", "net", "dvs", "computeResource", "datacenter")
                .firstNotNullOfOrNull { field -> VsphereSoap.children(event, field)?.let { arg ->
                    val objectRef = VsphereSoap.children(arg, field)?.takeIf { it.hasAttribute("type") }
                        ?: (0 until arg.childNodes.length).mapNotNull { arg.childNodes.item(it) as? Element }
                            .firstOrNull { it.hasAttribute("type") }
                    MonitoringEntity(objectRef?.getAttribute("type")?.takeIf(String::isNotBlank),
                        objectRef?.textContent?.trim()?.takeIf(String::isNotBlank), text(arg, "name"))
                } }
            val eventType = event.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type")
                .takeIf(String::isNotBlank)?.substringAfter(':')
            val severity = if (eventType == "EventEx") text(event, "severity") else null
            MonitoringEvent(key, text(event, "createdTime"), severity, entity,
                text(event, "fullFormattedMessage") ?: if (eventType == "EventEx") text(event, "message") else null, eventType)
        }
    }
}
