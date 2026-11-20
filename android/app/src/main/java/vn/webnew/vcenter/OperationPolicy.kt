package vn.webnew.vcenter

internal object OperationPolicy {
    val terminal = setOf("success", "error", "rejected", "reviewed")

    fun blocks(operation: PendingOperation, scope: String, vmRef: String): Boolean =
        operation.scope == scope && operation.vmRef == vmRef && operation.status !in terminal

    fun reconciledStatus(serverState: String?): String = when (serverState) {
        "queued", "running", "success", "error" -> serverState
        else -> "unavailable"
    }
}
