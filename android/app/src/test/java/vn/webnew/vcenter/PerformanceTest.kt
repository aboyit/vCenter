package vn.webnew.vcenter

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PerformanceTest {
    private fun response(method: String, content: String) = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:v="urn:vim25"><s:Body><v:${method}Response>$content</v:${method}Response></s:Body></s:Envelope>"""

    @Test fun discoversCountersByMetadataInsteadOfHardcodedIds() {
        fun counter(id: Int, group: String, unit: String = "percent") = """<v:PerfCounterInfo><v:key>$id</v:key><v:nameInfo><v:key>usage</v:key></v:nameInfo><v:groupInfo><v:key>$group</v:key></v:groupInfo><v:unitInfo><v:key>$unit</v:key></v:unitInfo><v:rollupType>average</v:rollupType></v:PerfCounterInfo>"""
        val xml = response("RetrievePropertiesEx", """<v:returnval><v:objects><v:propSet><v:name>perfCounter</v:name><v:val>${counter(9, "cpu", "mhz")}${counter(17, "cpu")}${counter(28, "mem")}</v:val></v:propSet></v:objects></v:returnval>""")
        assertEquals(17 to 28, PerformanceSoap.counters(xml))
        val start = Instant.parse("2026-11-19T03:00:00Z")
        val query = PerformanceSoap.query("perf", listOf("host<&"), 17 to 28, start, start.plusSeconds(3600))
        assertTrue(query.contains("host&lt;&amp;"))
        assertTrue(query.contains("<vim:intervalId>20</vim:intervalId>"))
        assertTrue(query.contains("<vim:counterId>28</vim:counterId>"))
    }

    @Test fun parsesPercentUnitsMissingSamplesAndIgnoresDeviceInstances() {
        fun series(id: Int, instance: String, values: String) = """<v:value><v:id><v:counterId>$id</v:counterId><v:instance>$instance</v:instance></v:id>$values</v:value>"""
        val xml = response("QueryPerf", """<v:returnval><v:entity type="HostSystem">host-1</v:entity><v:sampleInfo><v:interval>300</v:interval><v:timestamp>2026-11-19T03:00:00Z</v:timestamp></v:sampleInfo><v:sampleInfo><v:interval>300</v:interval><v:timestamp>2026-11-19T03:05:00Z</v:timestamp></v:sampleInfo>${series(17, "", "<v:value>3250</v:value><v:value>-1</v:value>")}${series(28, "", "<v:value>6000</v:value><v:value>10001</v:value>")}${series(17, "0", "<v:value>9999</v:value>")}</v:returnval>""")
        val host = PerformanceSoap.parse(xml, 17 to 28).single()
        assertEquals("host-1", host.ref)
        assertEquals(32.5, host.points[0].cpu!!, .001)
        assertEquals(60.0, host.points[0].memory!!, .001)
        assertNull(host.points[1].cpu)
        assertNull(host.points[1].memory)
        val broken = xml.replace("<v:value>-1</v:value>", "")
        assertThrows(SoapFailure::class.java) { PerformanceSoap.parse(broken, 17 to 28) }
    }

    @Test fun aggregatesOnlyAlignedCompleteHostSamplesWithCapacityWeights() {
        fun host(ref: String, cores: String, memory: String) = InventoryItem(ref, "HostSystem", mapOf(
            "summary.hardware.numCpuCores" to cores, "summary.hardware.cpuMhz" to "1000",
            "summary.hardware.memorySize" to memory), emptyMap(), emptySet())
        val inventory = InventorySnapshot(listOf(host("a", "1", "100"), host("b", "3", "100")))
        val data = PerformanceSnapshot(0, 1000, listOf(
            HostPerformance("a", listOf(PerformancePoint(100, 300, 20.0, 40.0), PerformancePoint(200, 300, 0.0, 0.0))),
            HostPerformance("b", listOf(PerformancePoint(100, 300, 60.0, 80.0), PerformancePoint(200, 20, 50.0, 50.0)))))
        val points = data.points(inventory, null)
        assertEquals(50.0, points[0].cpu!!, .001)
        assertEquals(60.0, points[0].memory!!, .001)
        assertTrue(points.drop(1).all { it.cpu == null && it.memory == null })
        assertEquals(2, data.points(inventory, "a").size)
        assertTrue(data.points(inventory, "unknown").isEmpty())
        val incomplete = data.copy(hosts = data.hosts.take(1))
        assertTrue(incomplete.points(inventory, null).all { it.cpu == null && it.memory == null })
    }
}
