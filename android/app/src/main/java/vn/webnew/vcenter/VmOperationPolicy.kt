package vn.webnew.vcenter

internal fun vmOperationBlockReason(vm: InventoryItem, operation: VmOperation): String? {
    if (vm.type != "VirtualMachine" || vm.ref != operation.vmRef) return "VM không khớp lệnh."
    val power = vm.get("runtime.powerState")
    val tools = vm.get("guest.toolsRunningStatus") == "guestToolsRunning"
    return when (operation.action) {
        VmOperationAction.POWER_ON -> if (power !in setOf("poweredOff", "suspended")) "VM phải tắt hoặc tạm dừng." else null
        VmOperationAction.POWER_OFF, VmOperationAction.RESET -> if (power != "poweredOn") "VM phải đang chạy." else null
        VmOperationAction.SHUTDOWN_GUEST, VmOperationAction.REBOOT_GUEST ->
            if (power != "poweredOn" || !tools) "Cần VM đang chạy và VMware Tools hoạt động." else null
        VmOperationAction.CREATE_SNAPSHOT -> when {
            power !in setOf("poweredOn", "poweredOff") -> "Không đủ trạng thái nguồn để tạo snapshot."
            operation.memory && power != "poweredOn" -> "Snapshot bộ nhớ cần VM đang chạy."
            operation.memory && operation.quiesce -> "Không kết hợp snapshot bộ nhớ và quiesce."
            operation.quiesce && (!tools || power != "poweredOn") -> "Quiesce cần VMware Tools và VM đang chạy."
            operation.snapshotName!!.length > 80 || operation.description.length > 4000 -> "Tên tối đa 80 ký tự; mô tả tối đa 4.000 ký tự."
            else -> null
        }
        VmOperationAction.REVERT_TO_SNAPSHOT, VmOperationAction.REMOVE_SNAPSHOT ->
            if ("snapshot" in vm.missing || vm.snapshotInfo?.snapshots?.none { it.ref == operation.snapshotRef } != false)
                "Snapshot không còn trong cây hiện tại; tải lại VM." else null
        VmOperationAction.REMOVE_ALL_SNAPSHOTS ->
            if ("snapshot" in vm.missing || vm.snapshotInfo?.snapshots.isNullOrEmpty()) "Không có cây snapshot hợp lệ." else null
        VmOperationAction.CONSOLIDATE_DISKS ->
            if (vm.get("runtime.consolidationNeeded") != "true") "Server chưa báo cần consolidation." else null
    }
}

internal fun vmActionLabel(action: VmOperationAction): String = when (action) {
    VmOperationAction.POWER_ON -> "Bật nguồn"
    VmOperationAction.POWER_OFF -> "Tắt nguồn cưỡng bức"
    VmOperationAction.RESET -> "Reset cưỡng bức"
    VmOperationAction.SHUTDOWN_GUEST -> "Tắt hệ điều hành"
    VmOperationAction.REBOOT_GUEST -> "Khởi động lại hệ điều hành"
    VmOperationAction.CREATE_SNAPSHOT -> "Tạo snapshot"
    VmOperationAction.REVERT_TO_SNAPSHOT -> "Revert snapshot"
    VmOperationAction.REMOVE_SNAPSHOT -> "Xóa snapshot"
    VmOperationAction.REMOVE_ALL_SNAPSHOTS -> "Xóa toàn bộ snapshot"
    VmOperationAction.CONSOLIDATE_DISKS -> "Consolidate disk"
}
