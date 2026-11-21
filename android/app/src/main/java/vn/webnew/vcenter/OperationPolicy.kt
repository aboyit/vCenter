package vn.webnew.vcenter

internal object OperationPolicy {
    val terminal = setOf("success", "error", "rejected", "reviewed", "accepted", "observed")

    fun reconciledStatus(serverState: String?): String = when (serverState) {
        "queued", "running", "success", "error" -> serverState
        else -> "unavailable"
    }

    fun guestStatus(operation: PendingOperation, power: String?): String =
        when {
            operation.action == "REBOOT_GUEST" && operation.status == "sent" -> "accepted"
            operation.action == "SHUTDOWN_GUEST" && power == "poweredOff" -> "observed"
            else -> operation.status
        }
}
