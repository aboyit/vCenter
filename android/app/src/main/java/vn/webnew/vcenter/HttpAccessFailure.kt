package vn.webnew.vcenter

internal object HttpAccessFailure {
    fun operationMessage(error: Exception, rejected: Boolean, fallback: String): String {
        val prefix = when {
            rejected -> "Lệnh không được thực hiện. "
            error is SoapFailure && error.httpStatus != null ->
                "Server/proxy trả lỗi HTTP sau khi gửi; đang đối soát Task và trạng thái VM. "
            else -> "Mất phản hồi sau khi gửi; đang tải lại Task và trạng thái VM. "
        }
        return prefix + (if (error is SoapFailure) error.message else fallback)
    }

    fun describe(code: Int, stage: String, challenge: String?, npmEnabled: Boolean): String {
        val step = when (stage) {
            "RetrieveServiceContent" -> "đọc thông tin /sdk, chưa gửi tài khoản vCenter"
            "Login" -> "đăng nhập vCenter (đã đọc được /sdk)"
            "Logout" -> "đăng xuất"
            "ReconfigVM_Task" -> "cập nhật cấu hình VM (ReconfigVM_Task)"
            "CloneVM_Task" -> "nhân bản VM (CloneVM_Task)"
            "MigrateVM_Task", "RelocateVM_Task" -> "di chuyển VM ($stage)"
            "RetrievePropertiesEx", "ContinueRetrievePropertiesEx" -> "đọc trạng thái vCenter ($stage)"
            else -> "gọi API $stage"
        }
        val hint = when {
            challenge?.trim()?.startsWith("Basic", ignoreCase = true) == true ->
                "Endpoint yêu cầu HTTP Basic Auth. Kiểm tra tài khoản Access List NPM (không phải tài khoản quản trị port 81)."
            code == 403 && stage == "RetrieveServiceContent" ->
                "Yêu cầu bị chặn trước SOAP Login. Kiểm tra Access List/IP allow-deny, Satisfy Any nếu có và đường dẫn /sdk trên NPM/WAF/upstream; chưa thể quy lỗi cho mật khẩu vCenter."
            code == 403 ->
                "Kiểm tra Access List/IP, quy tắc proxy/WAF và upstream. HTTP 403 riêng lẻ không xác định chắc bên nào từ chối."
            else -> "Kiểm tra HTTP Auth của proxy/upstream; chưa có bằng chứng đây là lỗi mật khẩu vCenter."
        }
        return "HTTP $code ở bước $step. $hint " +
            if (npmEnabled) "App đã gửi NPM HTTP Auth." else "NPM HTTP Auth đang tắt trong hồ sơ."
    }
}
