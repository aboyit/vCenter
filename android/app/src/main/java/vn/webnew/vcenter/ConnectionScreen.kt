package vn.webnew.vcenter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.VisualTransformation

@Composable
internal fun ConnectionScreen(state: ConnectionState, model: ConnectionViewModel) {
    val p = state.active ?: Profile()
    val c = state.credentials
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    var profilesExpanded by remember { mutableStateOf(false) }
    Text("Hồ sơ đã lưu", style = MaterialTheme.typography.labelMedium)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { profilesExpanded = true }, enabled = state.profiles.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("saved-profiles"),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
            Text(state.profiles.firstOrNull { it.id == state.active?.id }?.name
                ?: if (state.profiles.isEmpty()) "Chưa có hồ sơ đã lưu" else "Chọn hồ sơ",
                modifier = Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded = profilesExpanded, onDismissRequest = { profilesExpanded = false }) {
            state.profiles.forEach { profile ->
                DropdownMenuItem(text = {
                    Column {
                        Text(profile.name)
                        Text("${profile.host}:${profile.port}", style = MaterialTheme.typography.bodySmall)
                    }
                }, onClick = { model.select(profile.id); profilesExpanded = false },
                    modifier = Modifier.testTag("profile-${profile.id}"))
            }
        }
    }
    ConnectionField(value = p.name, onValueChange = { model.edit(p.copy(name = it), c) },
        label = { Text("Tên kết nối") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("profile-name"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    ConnectionField(value = p.host, onValueChange = { model.edit(p.copy(host = it), c) },
        label = { Text("Host / IP") }, singleLine = true,
        modifier = Modifier.weight(1f).testTag("profile-host"))
    ConnectionField(value = p.port, onValueChange = { model.edit(p.copy(port = it), c) },
        label = { Text("Port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(88.dp).testTag("profile-port"))
    }
    ConnectionField(value = p.user, onValueChange = { model.edit(p.copy(user = it), c) },
        label = { Text("Tài khoản vCenter") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("vcenter-user"))
    ConnectionField(value = c.vcenterPassword,
        onValueChange = { model.edit(p, c.copy(vcenterPassword = it)) }, label = { Text("Mật khẩu vCenter") },
        visualTransformation = PasswordVisualTransformation(), singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("vcenter-password"))
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = p.npmEnabled, onCheckedChange = { model.edit(p.copy(npmEnabled = it), c) },
            modifier = Modifier.testTag("npm-toggle"))
        Text("HTTP Auth qua NPM")
    }
    if (p.npmEnabled) {
        ConnectionField(value = p.npmUser, onValueChange = { model.edit(p.copy(npmUser = it), c) },
            label = { Text("Tài khoản HTTP Auth NPM") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("npm-user"))
        ConnectionField(value = c.npmPassword, onValueChange = { model.edit(p, c.copy(npmPassword = it)) },
            label = { Text("Mật khẩu HTTP Auth NPM") }, visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("npm-password"))
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = p.remember, onCheckedChange = { model.edit(p.copy(remember = it), c) },
            modifier = Modifier.testTag("remember-passwords"))
        Text("Nhớ mật khẩu")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = model::connect, enabled = !state.busy,
            modifier = Modifier.weight(1f).testTag("connect")) { Text("Đăng nhập") }
        OutlinedButton(onClick = model::save, enabled = !state.busy,
            modifier = Modifier.testTag("save-profile")) { Text("Lưu hồ sơ") }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("connection-error")) }
    state.info?.let { info ->
        OutlinedCard(Modifier.fillMaxWidth().testTag("connected-server")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Đã đăng nhập vCenter", style = MaterialTheme.typography.titleMedium)
                Text("Server: ${info.name}")
                Text("Phiên bản: ${info.version}")
                Text("Instance: ${info.instanceId}")
                Button(onClick = model::logout, modifier = Modifier.testTag("logout")) { Text("Đăng xuất") }
            }
        }
    }
    if (state.active != null && state.profiles.any { it.id == state.active.id }) {
        TextButton(onClick = model::delete, modifier = Modifier.testTag("delete-profile")) {
            Text("Quên / xóa hồ sơ và mật khẩu đã nhớ")
        }
    }
}
}

@Composable
private fun ConnectionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        label = { androidx.compose.runtime.CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodySmall, content = label) },
        singleLine = singleLine, keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    )
}
