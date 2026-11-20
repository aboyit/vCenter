package vn.webnew.vcenter

internal object AdvancedExecutionPolicy {
    fun conflict(baseline: VmConfig, fresh: VmConfig): String? = when {
        baseline.vmRef != fresh.vmRef -> "Cấu hình không thuộc VM đã chọn."
        baseline.fingerprint() != fresh.fingerprint() ->
            "Cấu hình hoặc nguồn VM đã thay đổi; tải lại cấu hình và xem lại lựa chọn."
        else -> null
    }
}
