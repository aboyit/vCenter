package vn.webnew.vcenter

import java.util.Locale

internal data class CapacityDisplay(val value: String, val unit: String) {
    override fun toString() = "$value $unit"
}

internal fun capacityDisplay(bytes: Long?): CapacityDisplay {
    val gib = bytes?.div(1_073_741_824.0)
    val useTib = gib != null && gib >= 1000.0
    return CapacityDisplay(
        gib?.let { String.format(Locale.US, "%.1f", if (useTib) it / 1024.0 else it) } ?: "—",
        if (useTib) "TiB" else "GiB"
    )
}
