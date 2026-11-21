package vn.webnew.vcenter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VCenterApp() }
    }
}

private enum class Destination(val title: Int, val icon: Int? = null) {
    Overview(R.string.overview, R.drawable.ic_overview),
    Vms(R.string.vms, R.drawable.ic_vm),
    Hosts(R.string.hosts, R.drawable.ic_host),
    Tasks(R.string.tasks, R.drawable.ic_task),
    Datastores(R.string.datastores, R.drawable.ic_datastore),
    Network(R.string.network),
    Alarms(R.string.alarms),
    Connections(R.string.connections),
    Scope(R.string.scope)
}

private val primaryDestinations = Destination.entries.take(5)
private val secondaryDestinations = Destination.entries.drop(5)
private val canvas = Color(0xFF1B2A32)
private val panel = Color(0xFF21333B)
private val toolbar = Color(0xFF304250)
private val raised = Color(0xFF29414E)
private val accent = Color(0xFF4AAED9)
private val text = Color(0xFFF3F6F8)
private val muted = Color(0xFFBACBD6)

@Composable
internal fun HeaderUpdateStatus(fetchedAt: Long?, loading: Boolean) {
    Column(Modifier.widthIn(max = 150.dp).fillMaxWidth().testTag("header-update")) {
        Text(fetchedAt?.let {
            "Cập nhật " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it))
        } ?: "Chưa có dữ liệu", fontSize = 10.sp, color = muted, maxLines = 1, softWrap = false)
        Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp).testTag("header-loading-slot")) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxSize().testTag("header-loading"), color = accent)
        }
    }
}

@Composable
fun VCenterApp() {
    var currentName by rememberSaveable { mutableStateOf(Destination.Overview.name) }
    var previousPrimary by rememberSaveable { mutableStateOf(Destination.Overview.name) }
    var menuExpanded by remember { mutableStateOf(false) }
    val connectionModel: ConnectionViewModel = viewModel()
    val connection by connectionModel.state.collectAsStateWithLifecycle()
    val authenticated = connection.info != null
    val current = if (authenticated) Destination.valueOf(currentName) else Destination.Connections
    LaunchedEffect(authenticated) {
        currentName = Destination.Overview.name
        previousPrimary = Destination.Overview.name
        menuExpanded = false
    }
    fun navigate(destination: Destination) {
        if (current in primaryDestinations) previousPrimary = current.name
        currentName = destination.name
        menuExpanded = false
    }
    BackHandler(enabled = authenticated && current != Destination.Overview && !menuExpanded) {
        currentName = if (current in secondaryDestinations) previousPrimary else Destination.Overview.name
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(authenticated, current, lifecycleOwner) {
        if (authenticated && current == Destination.Overview) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                try {
                    while (true) {
                        connectionModel.refreshPerformanceIfIdle()
                        kotlinx.coroutines.delay(20_000)
                    }
                } finally { connectionModel.stopPerformanceRefresh() }
            }
        }
    }
    LaunchedEffect(authenticated, current, lifecycleOwner) {
        if (authenticated && current == Destination.Alarms) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                try {
                    while (true) {
                        connectionModel.refreshMonitoring()
                        kotlinx.coroutines.delay(20_000)
                    }
                } finally { connectionModel.stopMonitoringRefresh() }
            }
        }
    }
    LaunchedEffect(authenticated, current, lifecycleOwner) {
        if (authenticated) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    connectionModel.refreshTasks()
                    kotlinx.coroutines.delay(if (connectionModel.state.value.pendingOperations.any { it.status !in OperationPolicy.terminal }) 3_000 else 20_000)
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent, onPrimary = Color(0xFF0E161B), background = canvas,
            surface = panel, onSurface = text, onBackground = text,
            onSurfaceVariant = muted, secondaryContainer = raised,
            onSecondaryContainer = text, outline = Color(0xFF495865)
        )
    ) {
        Scaffold(
            containerColor = canvas,
            topBar = {
                Surface(color = toolbar) {
                    Row(
                        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                            .heightIn(min = 65.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_name), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (!authenticated) {
                                Text(stringResource(R.string.brand), color = muted, fontSize = 12.sp)
                                Box(Modifier.fillMaxWidth().height(3.dp)) {
                                    if (connection.busy) LinearProgressIndicator(Modifier.fillMaxSize().testTag("connecting"))
                                }
                            } else {
                                val fetchedAt = when (current) {
                                    Destination.Tasks -> connection.tasksFetchedAt
                                    Destination.Alarms -> connection.monitoring?.fetchedAt
                                    Destination.Scope, Destination.Connections -> null
                                    else -> connection.inventory?.fetchedAt
                                }
                                val loading = when (current) {
                                    Destination.Tasks -> connection.tasksLoading
                                    Destination.Alarms -> connection.monitoringLoading
                                    Destination.Scope, Destination.Connections -> false
                                    else -> connection.inventoryLoading
                                }
                                HeaderUpdateStatus(fetchedAt, loading)
                            }
                        }
                        if (authenticated && current !in listOf(Destination.Scope, Destination.Connections)) {
                            IconButton(onClick = {
                                when (current) {
                                    Destination.Tasks -> connectionModel.refreshTasks()
                                    Destination.Alarms -> connectionModel.refreshMonitoring()
                                    else -> connectionModel.refreshInventory()
                                }
                            }, enabled = when (current) {
                                Destination.Tasks -> !connection.tasksLoading
                                Destination.Alarms -> !connection.monitoringLoading
                                else -> !connection.inventoryLoading
                            }, modifier = Modifier.testTag("header-refresh").semantics { contentDescription = "Làm mới" }) {
                                Text("↻", color = accent, fontSize = 22.sp)
                            }
                        }
                        if (authenticated) {
                            Surface(color = panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
                                border = BorderStroke(1.dp, Color(0xFF495865)),
                                modifier = Modifier.widthIn(max = 170.dp).padding(end = 6.dp)) {
                                Text(connection.active?.name ?: "vCenter", fontSize = 10.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
                            }
                        }
                        if (authenticated) Box {
                            val menuLabel = stringResource(R.string.menu)
                            OutlinedButton(onClick = { menuExpanded = true },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = panel),
                                border = BorderStroke(1.dp, Color(0xFF495865)), contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(44.dp).testTag("more-menu").semantics { contentDescription = menuLabel }) {
                                Text("☰", fontSize = 22.sp, color = text)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                secondaryDestinations.filterNot { it == Destination.Connections }.forEach { destination ->
                                    DropdownMenuItem(text = { Text(stringResource(destination.title)) },
                                        onClick = { navigate(destination) },
                                        modifier = Modifier.testTag("menu-${destination.name}"))
                                }
                                DropdownMenuItem(text = { Text("Đăng xuất") },
                                    onClick = { menuExpanded = false; connectionModel.logout() },
                                    modifier = Modifier.testTag("menu-Logout"))
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (authenticated) Surface(color = canvas) {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = Color(0xFF495865))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)
                            .selectableGroup()) {
                            primaryDestinations.forEach { destination ->
                                val selected = current == destination
                                Column(Modifier.weight(1f)
                                    .background(if (selected) raised else Color.Transparent)
                                    .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab,
                                        onClick = { navigate(destination) })
                                    .testTag("tab-${destination.name}"),
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) accent else Color.Transparent))
                                    Icon(painterResource(requireNotNull(destination.icon)), contentDescription = null,
                                        tint = if (selected) text else muted,
                                        modifier = Modifier.padding(top = 5.dp).size(24.dp).testTag("icon-${destination.name}"))
                                    Text(stringResource(destination.title), fontSize = 9.sp, color = if (selected) text else muted,
                                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 3.dp, bottom = 7.dp))
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                Column(
                    Modifier.widthIn(max = 840.dp).fillMaxWidth()
                        .align(androidx.compose.ui.Alignment.TopCenter)
                        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (current == Destination.Connections) {
                        ConnectionScreen(connection, connectionModel)
                    } else if (current == Destination.Tasks) {
                        TasksScreen(connection, connectionModel::refreshTasks, connectionModel::cancelTask,
                            connectionModel::refreshTasks)
                    } else if (current in listOf(Destination.Overview, Destination.Vms, Destination.Hosts,
                            Destination.Datastores, Destination.Network, Destination.Alarms)) {
                        InventoryScreen(InventorySection.valueOf(current.name), connection,
                            if (current == Destination.Alarms) connectionModel::refreshMonitoring else connectionModel::refreshInventory,
                            navigate = { section -> navigate(Destination.valueOf(section.name)) },
                            operate = connectionModel::executeOperation,
                            loadConfig = connectionModel::loadVmConfig, advanced = connectionModel::executeAdvanced)
                    } else {
                        val title = if (current == Destination.Scope) R.string.scope_title else R.string.connected_title
                        val body = when (current) {
                            Destination.Scope -> R.string.scope_body
                            Destination.Tasks -> R.string.tasks_body
                            else -> R.string.disconnected_body
                        }
                        OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = panel),
                            border = BorderStroke(1.dp, Color(0xFF495865)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                                if (current == Destination.Overview) {
                                    connection.info?.let { info ->
                                        Text(info.name)
                                        Text("vCenter ${info.version}", color = muted)
                                        Text("Instance: ${info.instanceId}", color = muted)
                                    }
                                }
                                Text(stringResource(body),
                                    color = muted, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
