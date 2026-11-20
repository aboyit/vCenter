package vn.webnew.vcenter

internal enum class VmOperationAction(val taskBased: Boolean) {
    POWER_ON(true),
    POWER_OFF(true),
    RESET(true),
    SHUTDOWN_GUEST(false),
    REBOOT_GUEST(false),
    CREATE_SNAPSHOT(true),
    REVERT_TO_SNAPSHOT(true),
    REMOVE_SNAPSHOT(true),
    REMOVE_ALL_SNAPSHOTS(true),
    CONSOLIDATE_DISKS(true)
}

internal data class VmOperation(
    val action: VmOperationAction,
    val vmRef: String,
    val snapshotRef: String? = null,
    val snapshotName: String? = null,
    val description: String = "",
    val memory: Boolean = false,
    val quiesce: Boolean = false,
    val removeChildren: Boolean = false,
    val consolidate: Boolean = true,
    val suppressPowerOn: Boolean = false
) {
    init {
        require(vmRef.isNotBlank()) { "VM reference is required." }
        when (action) {
            VmOperationAction.CREATE_SNAPSHOT -> {
                require(!snapshotName.isNullOrBlank()) { "Snapshot name is required." }
                require(snapshotRef == null && !removeChildren && consolidate && !suppressPowerOn) {
                    "Options are not valid for snapshot creation."
                }
            }
            VmOperationAction.REVERT_TO_SNAPSHOT -> {
                require(!snapshotRef.isNullOrBlank()) { "Snapshot reference is required." }
                require(snapshotName == null && description.isEmpty() && !memory && !quiesce &&
                    !removeChildren && consolidate) { "Options are not valid for snapshot revert." }
            }
            VmOperationAction.REMOVE_SNAPSHOT -> {
                require(!snapshotRef.isNullOrBlank()) { "Snapshot reference is required." }
                require(snapshotName == null && description.isEmpty() && !memory && !quiesce && !suppressPowerOn) {
                    "Options are not valid for snapshot removal."
                }
            }
            VmOperationAction.REMOVE_ALL_SNAPSHOTS -> require(snapshotRef == null && snapshotName == null &&
                description.isEmpty() && !memory && !quiesce && !removeChildren && !suppressPowerOn) {
                "Options are not valid for removing all snapshots."
            }
            else -> require(snapshotRef == null && snapshotName == null && description.isEmpty() && !memory &&
                !quiesce && !removeChildren && consolidate && !suppressPowerOn) {
                "Options are not valid for this VM action."
            }
        }
    }
}

internal object VmOperationSoap {
    private fun mor(type: String, ref: String) =
        "<vim:_this type=\"$type\">${VsphereSoap.escape(ref)}</vim:_this>"
    private fun bool(value: Boolean) = if (value) "true" else "false"

    fun request(operation: VmOperation): String = when (operation.action) {
        VmOperationAction.POWER_ON -> task("PowerOnVM_Task", operation.vmRef)
        VmOperationAction.POWER_OFF -> task("PowerOffVM_Task", operation.vmRef)
        VmOperationAction.RESET -> task("ResetVM_Task", operation.vmRef)
        VmOperationAction.SHUTDOWN_GUEST -> sync("ShutdownGuest", operation.vmRef)
        VmOperationAction.REBOOT_GUEST -> sync("RebootGuest", operation.vmRef)
        VmOperationAction.CREATE_SNAPSHOT -> VsphereSoap.envelope("CreateSnapshot_Task",
            mor("VirtualMachine", operation.vmRef) +
                "<vim:name>${VsphereSoap.escape(operation.snapshotName!!)}</vim:name>" +
                "<vim:description>${VsphereSoap.escape(operation.description)}</vim:description>" +
                "<vim:memory>${bool(operation.memory)}</vim:memory>" +
                "<vim:quiesce>${bool(operation.quiesce)}</vim:quiesce>")
        VmOperationAction.REVERT_TO_SNAPSHOT -> VsphereSoap.envelope("RevertToSnapshot_Task",
            mor("VirtualMachineSnapshot", operation.snapshotRef!!) +
                "<vim:suppressPowerOn>${bool(operation.suppressPowerOn)}</vim:suppressPowerOn>")
        VmOperationAction.REMOVE_SNAPSHOT -> VsphereSoap.envelope("RemoveSnapshot_Task",
            mor("VirtualMachineSnapshot", operation.snapshotRef!!) +
                "<vim:removeChildren>${bool(operation.removeChildren)}</vim:removeChildren>" +
                "<vim:consolidate>${bool(operation.consolidate)}</vim:consolidate>")
        VmOperationAction.REMOVE_ALL_SNAPSHOTS -> task("RemoveAllSnapshots_Task", operation.vmRef,
            "<vim:consolidate>${bool(operation.consolidate)}</vim:consolidate>")
        VmOperationAction.CONSOLIDATE_DISKS -> task("ConsolidateVMDisks_Task", operation.vmRef)
    }

    private fun task(method: String, vmRef: String, fields: String = "") =
        VsphereSoap.envelope(method, mor("VirtualMachine", vmRef) + fields)

    private fun sync(method: String, vmRef: String) =
        VsphereSoap.envelope(method, mor("VirtualMachine", vmRef))

    fun result(xml: String, action: VmOperationAction): String? {
        val response = VsphereSoap.result(xml, method(action))
        if (!action.taskBased) return null
        val task = VsphereSoap.children(response, "returnval")
            ?: throw SoapFailure("vCenter không trả về task reference.")
        if (task.getAttribute("type").takeIf { it.isNotEmpty() } != "Task") {
            throw SoapFailure("vCenter trả về task reference không hợp lệ.")
        }
        return task.textContent.trim().takeIf { it.isNotEmpty() }
            ?: throw SoapFailure("vCenter trả về task reference rỗng.")
    }

    internal fun method(action: VmOperationAction) = when (action) {
        VmOperationAction.POWER_ON -> "PowerOnVM_Task"
        VmOperationAction.POWER_OFF -> "PowerOffVM_Task"
        VmOperationAction.RESET -> "ResetVM_Task"
        VmOperationAction.SHUTDOWN_GUEST -> "ShutdownGuest"
        VmOperationAction.REBOOT_GUEST -> "RebootGuest"
        VmOperationAction.CREATE_SNAPSHOT -> "CreateSnapshot_Task"
        VmOperationAction.REVERT_TO_SNAPSHOT -> "RevertToSnapshot_Task"
        VmOperationAction.REMOVE_SNAPSHOT -> "RemoveSnapshot_Task"
        VmOperationAction.REMOVE_ALL_SNAPSHOTS -> "RemoveAllSnapshots_Task"
        VmOperationAction.CONSOLIDATE_DISKS -> "ConsolidateVMDisks_Task"
    }
}
