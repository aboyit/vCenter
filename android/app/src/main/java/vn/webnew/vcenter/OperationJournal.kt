package vn.webnew.vcenter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class PendingOperation(
    val id: String, val scope: String, val vmRef: String, val action: String,
    val createdAt: Long, val taskRef: String? = null, val status: String = "uncertain"
)

internal class OperationJournal(context: Context) {
    private val prefs = context.getSharedPreferences("operations", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<PendingOperation> {
        val data = JSONArray(prefs.getString("items", "[]"))
        return (0 until data.length()).map { index ->
            val item = data.getJSONObject(index)
            PendingOperation(item.getString("id"), item.getString("scope"), item.getString("vm"),
                item.getString("action"), item.getLong("time"),
                item.optString("task").takeIf { it.isNotBlank() }, item.getString("status"))
        }
    }

    @Synchronized
    fun put(operation: PendingOperation) {
        write(all().filterNot { it.id == operation.id } + operation)
    }

    @Synchronized
    fun removeScope(profileId: String) {
        write(all().filterNot { it.scope.startsWith("$profileId|") })
    }

    private fun write(items: List<PendingOperation>) {
        val data = JSONArray().apply {
            items.forEach { item -> put(JSONObject().put("id", item.id).put("scope", item.scope)
                .put("vm", item.vmRef).put("action", item.action).put("time", item.createdAt)
                .put("task", item.taskRef.orEmpty()).put("status", item.status)) }
        }
        check(prefs.edit().putString("items", data.toString()).commit()) {
            "Cannot persist operation state"
        }
    }
}

internal fun operationScope(profile: Profile, info: ServerInfo): String =
    listOf(profile.id, Endpoint.url(profile.host, profile.port).toString(), profile.user, info.instanceId)
        .joinToString("|")
