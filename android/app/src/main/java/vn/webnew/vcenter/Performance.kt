package vn.webnew.vcenter

import org.w3c.dom.Element
import java.time.Instant

internal data class PerformancePoint(val time: Long, val interval: Int, val cpu: Double?, val memory: Double?)
internal data class HostPerformance(val ref: String, val points: List<PerformancePoint>)
internal data class PerformanceSnapshot(val start: Long, val end: Long, val hosts: List<HostPerformance>,
    val fetchedAt: Long = System.currentTimeMillis()) {
    fun points(inventory: InventorySnapshot, hostRef: String?): List<PerformancePoint> {
        if (hostRef != null) return hosts.firstOrNull { it.ref == hostRef }?.points ?: emptyList()
        val expected = inventory.hosts
        if (expected.isEmpty()) return emptyList()
        val indexed = hosts.associate { it.ref to it.points.associateBy { p -> p.time to p.interval } }
        val keys = hosts.flatMap { it.points }.map { it.time to it.interval }.distinct().sortedBy { it.first }
        return keys.map { key ->
            fun weighted(cpu: Boolean): Double? {
                var sum = 0.0
                var total = 0.0
                for (host in expected) {
                    val point = indexed[host.ref]?.get(key) ?: return null
                    val value = (if (cpu) point.cpu else point.memory) ?: return null
                    val weight = if (cpu) {
                        val cores = host.number("summary.hardware.numCpuCores") ?: return null
                        val mhz = host.number("summary.hardware.cpuMhz") ?: return null
                        cores.toDouble() * mhz
                    } else host.number("summary.hardware.memorySize")?.toDouble() ?: return null
                    if (weight <= 0) return null
                    sum += value * weight
                    total += weight
                }
                return (sum / total).takeIf { it.isFinite() }
            }
            PerformancePoint(key.first, key.second, weighted(true), weighted(false))
        }
    }
}

internal object PerformanceSoap {
    private fun elements(parent: Element, name: String): List<Element> = (0 until parent.childNodes.length)
        .mapNotNull { parent.childNodes.item(it) as? Element }.filter { it.localName == name }
    private fun text(parent: Element, name: String) = VsphereSoap.children(parent, name)?.textContent?.trim()
    private fun manager(ref: String) = "<vim:_this type=\"PerformanceManager\">${VsphereSoap.escape(ref)}</vim:_this>"

    fun metadata(collector: String, manager: String) = VsphereSoap.envelope("RetrievePropertiesEx",
        "<vim:_this type=\"PropertyCollector\">${VsphereSoap.escape(collector)}</vim:_this><vim:specSet>" +
            "<vim:propSet><vim:type>PerformanceManager</vim:type><vim:pathSet>perfCounter</vim:pathSet></vim:propSet>" +
            "<vim:objectSet><vim:obj type=\"PerformanceManager\">${VsphereSoap.escape(manager)}</vim:obj></vim:objectSet>" +
            "</vim:specSet><vim:options/>" )

    fun counters(xml: String): Pair<Int, Int> {
        val result = VsphereSoap.children(VsphereSoap.result(xml, "RetrievePropertiesEx"), "returnval")
            ?: throw SoapFailure("Không đọc được danh mục performance counter.")
        val counters = elements(result, "objects").flatMap { elements(it, "propSet") }
            .filter { text(it, "name") == "perfCounter" }
            .flatMap { prop -> VsphereSoap.children(prop, "val")?.let { elements(it, "PerfCounterInfo") } ?: emptyList() }
        fun id(group: String): Int = counters.firstOrNull { counter ->
            text(counter, "rollupType") == "average" &&
                VsphereSoap.children(counter, "groupInfo")?.let { text(it, "key") } == group &&
                VsphereSoap.children(counter, "nameInfo")?.let { text(it, "key") } == "usage" &&
                VsphereSoap.children(counter, "unitInfo")?.let { text(it, "key") } == "percent"
        }?.let { text(it, "key")?.toIntOrNull() } ?: throw SoapFailure("Server không cung cấp counter $group.usage.average (%).")
        return id("cpu") to id("mem")
    }

    fun currentTime() = VsphereSoap.envelope("CurrentTime", "<vim:_this type=\"ServiceInstance\">ServiceInstance</vim:_this>")
    fun serverTime(xml: String): Instant = try {
        Instant.parse(text(VsphereSoap.result(xml, "CurrentTime"), "returnval"))
    } catch (e: SoapFailure) { throw e }
    catch (_: Exception) { throw SoapFailure("Không đọc được thời gian vCenter.") }

    fun query(manager: String, hosts: List<String>, ids: Pair<Int, Int>, start: Instant, end: Instant) =
        VsphereSoap.envelope("QueryPerf", manager(manager) + hosts.joinToString("") { ref ->
            "<vim:querySpec><vim:entity type=\"HostSystem\">${VsphereSoap.escape(ref)}</vim:entity>" +
                "<vim:startTime>$start</vim:startTime><vim:endTime>$end</vim:endTime>" +
                listOf(ids.first, ids.second).joinToString("") { "<vim:metricId><vim:counterId>$it</vim:counterId><vim:instance></vim:instance></vim:metricId>" } +
                "<vim:intervalId>20</vim:intervalId><vim:format>normal</vim:format></vim:querySpec>"
        })

    fun parse(xml: String, ids: Pair<Int, Int>): List<HostPerformance> =
        elements(VsphereSoap.result(xml, "QueryPerf"), "returnval").map { entity ->
            val ref = text(entity, "entity")?.takeIf { it.isNotEmpty() } ?: throw SoapFailure("Hiệu năng thiếu host reference.")
            val samples = elements(entity, "sampleInfo").map { sample ->
                val time = try { Instant.parse(text(sample, "timestamp")).toEpochMilli() }
                catch (_: Exception) { throw SoapFailure("Mẫu hiệu năng có thời gian không hợp lệ.") }
                val interval = text(sample, "interval")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: throw SoapFailure("Mẫu hiệu năng thiếu chu kỳ.")
                time to interval
            }
            val series = elements(entity, "value").mapNotNull { value ->
                val id = VsphereSoap.children(value, "id") ?: return@mapNotNull null
                if (!text(id, "instance").isNullOrEmpty()) return@mapNotNull null
                val counter = text(id, "counterId")?.toIntOrNull() ?: return@mapNotNull null
                val values = elements(value, "value").map { it.textContent.trim().toLongOrNull()?.takeIf { n -> n in 0..10000 }?.div(100.0) }
                if (values.size != samples.size) throw SoapFailure("Số mẫu hiệu năng không khớp thời gian.")
                counter to values
            }.toMap()
            HostPerformance(ref, samples.mapIndexed { i, (time, interval) ->
                PerformancePoint(time, interval, series[ids.first]?.get(i), series[ids.second]?.get(i))
            })
        }
}
