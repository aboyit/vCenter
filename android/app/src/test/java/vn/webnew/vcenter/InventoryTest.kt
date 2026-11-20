package vn.webnew.vcenter

import org.junit.Assert.*
import org.junit.Test

class InventoryTest {
    private fun response(method: String, content: String) = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:v="urn:vim25"><s:Body><v:${method}Response><v:returnval>$content</v:returnval></v:${method}Response></s:Body></s:Envelope>"""

    @Test fun retrievesDvsConfigWithoutTraversingSubtypeProperties() {
        val request = InventorySoap.retrieve("collector", "view")
        assertFalse(request.contains("<vim:pathSet>config.lacp"))
        val xml = response("RetrievePropertiesEx", """<v:objects><v:obj type="VmwareDistributedVirtualSwitch">dvs-1</v:obj><v:propSet><v:name>config</v:name><v:val><v:lacpApiVersion>multipleLag</v:lacpApiVersion><v:lacpGroupConfig><v:name>LAG-A</v:name><v:key>lag-1</v:key><v:mode>active</v:mode><v:uplinkName>uplink-1</v:uplinkName></v:lacpGroupConfig></v:val></v:propSet></v:objects>""")
        val item = InventorySoap.page(xml, "RetrievePropertiesEx").items.single()
        assertEquals("multipleLag", item.get("config.lacpApiVersion"))
        assertEquals("LAG-A", item.lacpGroups!!.single().name)
        assertEquals(listOf("uplink-1"), item.lacpGroups!!.single().uplinks)
    }

    @Test fun parsesCloneFolderEligibilityAndDistributedNetworkIdentity() {
        val xml = response("RetrievePropertiesEx", """<v:objects><v:obj type="Folder">group-v1</v:obj><v:propSet><v:name>childType</v:name><v:val><v:string>VirtualMachine</v:string><v:string>Folder</v:string></v:val></v:propSet></v:objects><v:objects><v:obj type="DistributedVirtualPortgroup">dvpg-1</v:obj><v:propSet><v:name>key</v:name><v:val>dvportgroup-21</v:val></v:propSet><v:propSet><v:name>config.distributedVirtualSwitch</v:name><v:val type="VmwareDistributedVirtualSwitch">dvs-2</v:val></v:propSet></v:objects>""")
        val items = InventorySoap.page(xml, "RetrievePropertiesEx").items
        assertEquals(listOf("VirtualMachine", "Folder"), items[0].links["childType"])
        assertEquals("dvportgroup-21", items[1].get("key"))
        assertEquals(listOf("dvs-2"), items[1].links["config.distributedVirtualSwitch"])
    }

    @Test fun parsesPagedValuesReferencesAndMissingProperties() {
        val xml = response("RetrievePropertiesEx", """<v:objects><v:obj type="VirtualMachine">vm-12</v:obj><v:propSet><v:name>name</v:name><v:val>Alpha &amp; Beta</v:val></v:propSet><v:propSet><v:name>datastore</v:name><v:val><v:ManagedObjectReference type="Datastore">datastore-1</v:ManagedObjectReference><v:ManagedObjectReference type="Datastore">datastore-2</v:ManagedObjectReference></v:val></v:propSet><v:missingSet><v:path>guest.ipAddress</v:path><v:fault><v:NoPermissionFault/></v:fault></v:missingSet></v:objects><v:token>next-2</v:token>""")
        val page = InventorySoap.page(xml, "RetrievePropertiesEx")
        assertEquals("next-2", page.token)
        assertEquals("Alpha & Beta", page.items.single().get("name"))
        assertEquals(listOf("datastore-1", "datastore-2"), page.items.single().links["datastore"])
        assertEquals(setOf("guest.ipAddress"), page.items.single().missing)
        assertTrue(InventorySoap.continuePage("collector", "a<&").contains("a&lt;&amp;"))
    }

    @Test fun filtersVmPowerAndSearchTogetherIncludingUnknownProperties() {
        fun vm(ref: String, name: String, power: String?) = InventoryItem(ref, "VirtualMachine",
            mapOf("name" to name) + (power?.let { mapOf("runtime.powerState" to it) } ?: emptyMap()), emptyMap(), emptySet())
        val items = listOf(vm("1", "Lab Alpha", "poweredOn"), vm("2", "Lab Beta", "poweredOff"),
            vm("3", "Other", "suspended"), vm("4", "Lab Unknown", null), vm("5", "Lab Future", "futureState"))
        assertEquals(listOf("1"), filterVms(items, "  LAB  ", VmPowerFilter.PoweredOn).map { it.ref })
        assertEquals(listOf("2"), filterVms(items, "", VmPowerFilter.PoweredOff).map { it.ref })
        assertEquals(listOf("3"), filterVms(items, "", VmPowerFilter.Suspended).map { it.ref })
        assertEquals(listOf("4", "5"), filterVms(items, "lab", VmPowerFilter.Unknown).map { it.ref })
        assertEquals(items, filterVms(items, "", VmPowerFilter.All))
        assertTrue(filterVms(items, "other", VmPowerFilter.PoweredOn).isEmpty())
    }

    @Test fun parsesSnapshotBranchesAndCurrentReferenceWithoutMixingVmLinks() {
        fun node(ref: String, name: String, children: String = "") = """<v:snapshot type="VirtualMachineSnapshot">$ref</v:snapshot><v:name>$name</v:name><v:description>Before upgrade</v:description><v:createTime>2026-11-18T10:00:00Z</v:createTime><v:state>poweredOff</v:state><v:quiesced>true</v:quiesced>$children"""
        val tree = """<v:currentSnapshot type="VirtualMachineSnapshot">snapshot-2</v:currentSnapshot><v:rootSnapshotList>${node("snapshot-1", "Root &amp; base", "<v:childSnapshotList>${node("snapshot-2", "Child")}</v:childSnapshotList><v:childSnapshotList>${node("snapshot-3", "Sibling")}</v:childSnapshotList>")}</v:rootSnapshotList><v:rootSnapshotList>${node("snapshot-4", "Second root")}</v:rootSnapshotList>"""
        val xml = response("RetrievePropertiesEx", """<v:objects><v:obj type="VirtualMachine">vm-1</v:obj><v:propSet><v:name>snapshot</v:name><v:val>$tree</v:val></v:propSet></v:objects>""")
        val item = InventorySoap.page(xml, "RetrievePropertiesEx").items.single()
        val info = item.snapshotInfo!!
        assertEquals("snapshot-2", info.currentRef)
        assertEquals(listOf("snapshot-1", "snapshot-2", "snapshot-3", "snapshot-4"), info.snapshots.map { it.ref })
        assertEquals(listOf(0, 1, 1, 0), info.snapshots.map { it.depth })
        assertEquals("Root & base", info.snapshots.first().name)
        assertEquals("2026-11-18T10:00:00Z", info.snapshots.first().createdAt)
        assertEquals("Before upgrade", info.snapshots.first().description)
        assertEquals("poweredOff", info.snapshots.first().powerState)
        assertEquals("true", info.snapshots.first().quiesced)
        assertFalse(item.links.containsKey("snapshot"))
    }

    @Test fun distinguishesUnavailableSnapshotsFromAbsentOptionalProperty() {
        val xml = response("RetrievePropertiesEx", """<v:objects><v:obj type="VirtualMachine">vm-empty</v:obj></v:objects><v:objects><v:obj type="VirtualMachine">vm-denied</v:obj><v:missingSet><v:path>snapshot</v:path><v:fault><v:NoPermissionFault/></v:fault></v:missingSet></v:objects><v:objects><v:obj type="VirtualMachine">vm-nil</v:obj><v:propSet><v:name>snapshot</v:name><v:val xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/></v:propSet></v:objects>""")
        val items = InventorySoap.page(xml, "RetrievePropertiesEx").items
        assertNull(items[0].snapshotInfo)
        assertTrue(items[0].missing.isEmpty())
        assertNull(items[1].snapshotInfo)
        assertTrue("snapshot" in items[1].missing)
        assertNull(items[2].snapshotInfo)
    }

    @Test fun rejectsSnapshotNodesWithoutReferenceRatherThanInventingEmptyTree() {
        val xml = response("RetrievePropertiesEx", """<v:objects><v:obj type="VirtualMachine">vm-1</v:obj><v:propSet><v:name>snapshot</v:name><v:val><v:rootSnapshotList><v:name>Broken</v:name></v:rootSnapshotList></v:val></v:propSet></v:objects>""")
        try { InventorySoap.page(xml, "RetrievePropertiesEx"); fail("expected invalid snapshot") }
        catch (e: SoapFailure) { assertTrue(e.message!!.contains("reference")) }
    }

    @Test fun totalsDeduplicateSharedDatastoresAndDoNotInventMissingValues() {
        val shared = InventoryItem("ds-1", "Datastore", mapOf("summary.capacity" to "10737418240", "summary.freeSpace" to "2147483648"), emptyMap(), emptySet())
        val second = InventoryItem("ds-2", "Datastore", emptyMap(), emptyMap(), setOf("summary.capacity"))
        val snapshot = InventorySnapshot(listOf(shared, shared, second))
        assertEquals(2, snapshot.datastores.size)
        assertNull(snapshot.capacityBytes)
        assertNull(snapshot.freeBytes)
        assertEquals(10737418240L, InventorySnapshot(listOf(shared)).capacityBytes)
        assertNull(InventorySnapshot(emptyList()).capacityBytes)
        assertNull(InventorySnapshot(emptyList()).cpuMhz)
    }

    @Test fun parsesHostNetworkWithoutTreatingNestedObjectsAsReferences() {
        val xml = response("RetrievePropertiesEx", """<v:objects><v:obj type="HostSystem">host-1</v:obj><v:propSet><v:name>overallStatus</v:name><v:val>yellow</v:val></v:propSet><v:propSet><v:name>config.network.vswitch</v:name><v:val><v:HostVirtualSwitch><v:name>vSwitch0</v:name><v:mtu>9000</v:mtu><v:spec><v:policy><v:nicTeaming><v:nicOrder><v:activeNic>vmnic0</v:activeNic><v:standbyNic>vmnic1</v:standbyNic></v:nicOrder></v:nicTeaming></v:policy></v:spec></v:HostVirtualSwitch></v:val></v:propSet><v:propSet><v:name>config.network.portgroup</v:name><v:val><v:HostPortGroup><v:spec><v:name>Management</v:name><v:vswitchName>vSwitch0</v:vswitchName><v:vlanId>10</v:vlanId></v:spec></v:HostPortGroup></v:val></v:propSet><v:propSet><v:name>config.network.vnic</v:name><v:val><v:HostVirtualNic><v:device>vmk0</v:device><v:portgroup>Management</v:portgroup><v:spec><v:ip><v:ipAddress>10.0.0.1</v:ipAddress></v:ip></v:spec></v:HostVirtualNic></v:val></v:propSet><v:missingSet><v:path>config.network.pnic</v:path></v:missingSet></v:objects>""")
        val item = InventorySoap.page(xml, "RetrievePropertiesEx").items.single()
        assertEquals("yellow", item.get("overallStatus"))
        assertEquals(HostSwitch("vSwitch0", "9000", listOf("vmnic0", "vmnic1")), item.hostNetwork!!.switches.single())
        assertEquals(HostPortgroup("Management", "vSwitch0", "10"), item.hostNetwork!!.portgroups.single())
        assertEquals(HostVmk("vmk0", "Management", "10.0.0.1"), item.hostNetwork!!.vmks.single())
        assertTrue("config.network.pnic" in item.missing)
        assertFalse("config.network.pnic" in item.hostNetwork!!.available)
        assertTrue(item.links.isEmpty())
    }

    @Test fun parsesLacpAndResourcePoolWithMissingDistinction() {
        val xml = response("RetrievePropertiesEx", """<v:objects><v:obj type="VmwareDistributedVirtualSwitch">dvs-1</v:obj><v:propSet><v:name>config.lacpApiVersion</v:name><v:val>multipleLag</v:val></v:propSet><v:propSet><v:name>config.lacpGroupConfig</v:name><v:val><v:VMwareDvsLacpGroupConfig><v:name>LAG-A</v:name><v:key>lag-1</v:key><v:mode>active</v:mode><v:uplinkName>uplink-1</v:uplinkName><v:uplinkName>uplink-2</v:uplinkName><v:uplinkNum>2</v:uplinkNum><v:loadbalanceAlgorithm>srcDestIp</v:loadbalanceAlgorithm></v:VMwareDvsLacpGroupConfig></v:val></v:propSet></v:objects><v:objects><v:obj type="ResourcePool">pool-1</v:obj><v:propSet><v:name>name</v:name><v:val>Production</v:val></v:propSet><v:propSet><v:name>parent</v:name><v:val type="ComputeResource">domain-c1</v:val></v:propSet></v:objects><v:objects><v:obj type="VmwareDistributedVirtualSwitch">dvs-2</v:obj><v:missingSet><v:path>config.lacpGroupConfig</v:path></v:missingSet></v:objects>""")
        val items = InventorySoap.page(xml, "RetrievePropertiesEx").items
        assertEquals(LacpGroup("LAG-A", "lag-1", "active", listOf("uplink-1", "uplink-2"), "2", "srcDestIp"), items[0].lacpGroups!!.single())
        assertEquals("multipleLag", items[0].get("config.lacpApiVersion"))
        assertEquals(listOf("pool-1"), InventorySnapshot(items).resourcePools.map { it.ref })
        assertEquals(listOf("domain-c1"), items[1].links["parent"])
        assertNull(items[2].lacpGroups)
        assertTrue("config.lacpGroupConfig" in items[2].missing)
    }

    @Test fun expiredSessionFaultIsIdentifiedWithoutExposingServerText() {
        val xml = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault><faultstring>private value</faultstring><detail><NotAuthenticatedFault xmlns="urn:vim25"/></detail></s:Fault></s:Body></s:Envelope>"""
        try { InventorySoap.page(xml, "RetrievePropertiesEx"); fail("expected expiry") }
        catch (e: SoapFailure) { assertTrue(e.expired); assertFalse(e.message!!.contains("private value")) }
    }

    @Test fun retrievalUsesViewTraversalAndBoundedPages() {
        val xml = InventorySoap.retrieve("collector", "view-1")
        assertTrue(xml.contains("vim:TraversalSpec"))
        assertTrue(xml.contains("<vim:path>view</vim:path>"))
        assertTrue(xml.contains("<vim:maxObjects>100</vim:maxObjects>"))
        assertFalse(xml.contains("PowerOnVM_Task"))
    }
}
