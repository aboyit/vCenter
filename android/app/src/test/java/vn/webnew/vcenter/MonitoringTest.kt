package vn.webnew.vcenter

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class MonitoringTest {
    private fun response(method: String, contents: String) =
        "<Envelope xmlns='http://schemas.xmlsoap.org/soap/envelope/'><Body>" +
            "<${method}Response xmlns='urn:vim25'>$contents</${method}Response></Body></Envelope>"

    @Test fun requestsAreReadOnlyBoundedAndEscaped() {
        val events = MonitoringSoap.events("event<&", Instant.parse("2026-11-19T12:00:00Z"))
        assertTrue(events.contains("<vim:maxCount>100</vim:maxCount>"))
        assertTrue(events.contains("2026-11-18T12:00:00Z"))
        assertTrue(events.contains("event&lt;&amp;"))
        val alarm = MonitoringSoap.alarms("collector", "group-d1")
        assertTrue(alarm.contains("triggeredAlarmState"))
        assertTrue(alarm.contains("<vim:maxObjects>1</vim:maxObjects>"))
        assertTrue(MonitoringSoap.alarmNames("collector", listOf("a<&")).contains("a&lt;&amp;"))
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringSoap.alarmNames("collector", (0..100).map { "alarm-$it" })
        }
    }

    @Test fun alarmsAreDistinctStatesWithNamesLookedUpSeparately() {
        val xml = response("RetrievePropertiesEx", "<returnval><objects><obj type='Folder'>group-d1</obj>" +
            "<propSet><name>triggeredAlarmState</name><val><AlarmState><key>state-1</key>" +
            "<alarm type='Alarm'>alarm-1</alarm><entity type='VirtualMachine'>vm-1</entity>" +
            "<overallStatus>red</overallStatus><time>2026-11-19T10:00:00Z</time>" +
            "<acknowledged>false</acknowledged></AlarmState></val></propSet></objects></returnval>")
        val page = MonitoringSoap.parseAlarms(xml)
        assertEquals("vm-1", page.alarms.single().entity.ref)
        assertEquals("red", page.alarms.single().status)
        assertEquals(false, page.alarms.single().acknowledged)
        assertEquals(listOf("alarm-1"), page.alarmRefs)
        val name = response("RetrievePropertiesEx", "<returnval><objects><obj type='Alarm'>alarm-1</obj>" +
            "<propSet><name>info.name</name><val>Production VM</val></propSet></objects></returnval>")
        assertEquals("Production VM", MonitoringSoap.parseAlarmNames(name)["alarm-1"])
    }

    @Test fun missingPermissionAndContinuationAreExplicit() {
        val missing = response("RetrievePropertiesEx", "<returnval><objects><obj type='Folder'>group-d1</obj>" +
            "<missingSet><path>triggeredAlarmState</path><fault/></missingSet></objects></returnval>")
        assertTrue(assertThrows(SoapFailure::class.java) { MonitoringSoap.parseAlarms(missing) }
            .message!!.contains("quyền"))
        val continued = response("RetrievePropertiesEx", "<returnval><token>opaque</token>" +
            "<objects><obj type='Folder'>group-d1</obj></objects></returnval>")
        assertThrows(SoapFailure::class.java) { MonitoringSoap.parseAlarms(continued) }
        assertTrue(MonitoringSoap.parseAlarms(response("RetrievePropertiesEx",
            "<returnval><objects><obj type='Folder'>group-d1</obj></objects></returnval>")).alarms.isEmpty())
    }

    @Test fun eventFieldsDoNotInventSeverity() {
        val xml = response("QueryEvents", "<returnval xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " +
            "xsi:type='VmPoweredOnEvent'><key>14</key><createdTime>2026-11-19T10:20:00Z</createdTime>" +
            "<vm><vm type='VirtualMachine'>vm-8</vm><name>SQL</name></vm>" +
            "<fullFormattedMessage>Powered on SQL</fullFormattedMessage></returnval>")
        val event = MonitoringSoap.parseEvents(xml).single()
        assertEquals(14, event.key)
        assertEquals("SQL", event.entity?.name)
        assertEquals("vm-8", event.entity?.ref)
        assertEquals("Powered on SQL", event.content)
        assertNull(event.severity)
        assertEquals("VmPoweredOnEvent", event.eventType)
        val extended = response("QueryEvents", "<returnval xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " +
            "xsi:type='EventEx'><key>15</key><severity>warning</severity><message>Warning from server</message></returnval>")
        assertEquals("warning", MonitoringSoap.parseEvents(extended).single().severity)
        assertEquals("Warning from server", MonitoringSoap.parseEvents(extended).single().content)
        assertTrue(MonitoringSoap.parseEvents(response("QueryEvents", "")).isEmpty())
    }
}
