package com.example.applestockpulse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data class Stock(val tab: Int = StockTab.STOCK, val storeNumber: String? = null) : Screen
    data object Alerts : Screen
    data object Settings : Screen
    data object Help : Screen
    data class RuleDetail(val ruleId: String) : Screen
    data class Patterns(val ruleId: String) : Screen
}

private val Screen.title: String
    get() = when (this) {
        is Screen.Stock -> "Stock"
        Screen.Alerts -> "My alerts"
        Screen.Settings -> "Settings"
        Screen.Help -> "Help"
        is Screen.RuleDetail -> "Alert"
        is Screen.Patterns -> "Patterns"
    }

// Screens in the dock; the others open on top of one of them and show a back arrow
private val Screen.isTopLevel: Boolean get() = this !is Screen.RuleDetail && this !is Screen.Patterns

// Space reserved under scrolling content for the floating dock
private val DOCK_SPACE = 112.dp

/**
 * Everything the screens share: navigation, the alert editor, monitoring and alerts.
 */
class AppActions(
    val navigate: (Screen) -> Unit,
    val openEditor: (WatchRule) -> Unit,
    val startOrStop: (Boolean) -> Unit,
    val requestNotifications: () -> Unit,
    val showUndoDelete: (WatchRule) -> Unit,
    // One tap on a Notify cell: alert on, alert off, or point to the group that covers it
    val toggleNotify: (String) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(openRuleRequest: MutableStateFlow<String?>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { TrackerPreferences(context) }
    val ruleStore = remember { WatchRuleStore.get(context) }
    val historyDao = remember { HistoryDatabase.get(context).historyDao() }

    val isRunning by StockTrackerService.isRunning.collectAsStateWithLifecycle()
    val uiState by StockTrackerService.uiState.collectAsStateWithLifecycle()
    val rules by ruleStore.rules.collectAsStateWithLifecycle()
    var defaultLocation by remember { mutableStateOf(preferences.getDefaultLocation()) }

    val historySince = remember { System.currentTimeMillis() - TrackerPreferences.HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000L }
    val events by remember { historyDao.observeEventsSince(historySince) }.collectAsState(initial = emptyList())
    val checks by remember { historyDao.observeChecksSince(historySince) }.collectAsState(initial = emptyList())
    var lastSeenEventId by remember { mutableLongStateOf(preferences.getLastSeenEventId()) }

    // Navigation: a small back stack; the dock screens sit on top of Stock
    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Stock())) }
    val current = backStack.last()
    fun navigate(screen: Screen) {
        backStack = when {
            screen is Screen.Stock -> listOf(screen)
            screen.isTopLevel -> listOf(Screen.Stock(), screen)
            else -> backStack + screen
        }
    }

    val pendingRule by openRuleRequest.collectAsState()
    LaunchedEffect(pendingRule) {
        pendingRule?.let { id ->
            if (ruleStore.get(id) != null) backStack = listOf(Screen.Stock(), Screen.Alerts, Screen.RuleDetail(id))
            openRuleRequest.value = null
        }
    }

    // Notifications: runtime permission (Android 13+) plus the app and alarm-channel switches
    var hasNotificationPermission by remember { mutableStateOf(AlertNotifier.canPostAlarms(context)) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(checkBatteryOptimization(context)) }
    // Set when starting monitoring opened the permission dialog, so it starts once the user allows it
    var startAfterPermission by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = AlertNotifier.canPostAlarms(context)
        if (isGranted && startAfterPermission) {
            startTrackerService(context)
        } else if (!isGranted) {
            Toast.makeText(
                context,
                "Alarms need notifications. Monitoring did not start. Turn on notifications in Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
        startAfterPermission = false
    }

    fun requestNotifications(startAfter: Boolean) {
        val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (permissionMissing) {
            startAfterPermission = startAfter
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Permission is granted, but notifications or the alarm channel are switched off
            openNotificationSettings(context)
        }
    }

    // Monitoring without notifications would detect stock but never alarm
    fun startMonitoring() {
        if (hasNotificationPermission) startTrackerService(context) else requestNotifications(startAfter = true)
    }

    // Re-check each time the screen comes back, e.g. from the Settings app
    LifecycleResumeEffect(Unit) {
        hasNotificationPermission = AlertNotifier.canPostAlarms(context)
        isIgnoringBatteryOptimizations = checkBatteryOptimization(context)
        onPauseOrDispose { }
    }

    // First launch of the process: show live alert status without waiting for monitoring
    LaunchedEffect(Unit) {
        if (uiState.snapshots.isEmpty() && rules.isNotEmpty()) requestPoll(context)
    }

    var editorDraft by remember { mutableStateOf<WatchRule?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun snackbar(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(message, actionLabel = action, withDismissAction = action == null)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    val actions = AppActions(
        navigate = ::navigate,
        openEditor = { editorDraft = it },
        startOrStop = { start ->
            when {
                !start -> stopTrackerService(context)
                ruleStore.rules.value.isEmpty() -> {
                    navigate(Screen.Stock())
                    snackbar("Tap Notify on a phone first, so the app knows what to watch.")
                }
                else -> startMonitoring()
            }
        },
        requestNotifications = { requestNotifications(startAfter = false) },
        showUndoDelete = { rule ->
            snackbar("Deleted ${rule.displayName()}", "Undo") {
                ruleStore.upsert(rule)
                requestPoll(context)
            }
        },
        toggleNotify = { sku ->
            when (val decision = NotifyToggle.decide(ruleStore.rules.value, sku)) {
                is NotifyDecision.Create -> {
                    ruleStore.upsert(decision.rule)
                    requestPoll(context)
                    if (!isRunning) startMonitoring()
                    snackbar("Alert on: ${decision.rule.displayName()}")
                }
                is NotifyDecision.Remove -> {
                    decision.rules.forEach { ruleStore.delete(it.id) }
                    requestPoll(context)
                    snackbar("Alert off: ${ProductCatalog.getProductName(sku)}", "Undo") {
                        decision.rules.forEach { ruleStore.upsert(it) }
                        requestPoll(context)
                    }
                }
                is NotifyDecision.Covered -> snackbar(
                    "Part of your group “${decision.rule.displayName()}”",
                    "Edit"
                ) { editorDraft = decision.rule }
                null -> Unit
            }
        }
    )

    val statuses = remember(rules, uiState.snapshots, defaultLocation) {
        rules.associate { it.id to RuleStatusCalculator.statusFor(it, defaultLocation, uiState.snapshots) }
    }
    val inStockRuleCount = statuses.values.count { it.inStock }

    BackHandler(enabled = backStack.size > 1) { backStack = backStack.dropLast(1) }

    val hazeState = remember { HazeState() }
    val dockTab = backStack.lastOrNull { it.isTopLevel } ?: Screen.Stock()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = DOCK_SPACE - 16.dp))
            },
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(current.title, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) },
                        navigationIcon = {
                            if (!current.isTopLevel) {
                                IconButton(onClick = { backStack = backStack.dropLast(1) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            MonitoringPill(isRunning = isRunning, hasError = uiState.error != null) { actions.startOrStop(!isRunning) }
                            if (current is Screen.Stock || current == Screen.Alerts || current is Screen.RuleDetail) {
                                IconButton(onClick = {
                                    if (current is Screen.Stock) requestBrowsePoll(context, preferences.getBrowseModel()) else requestPoll(context)
                                    Toast.makeText(context, "Checking Apple stock…", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Check now")
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                    if (uiState.isChecking && (current == Screen.Alerts || current is Screen.RuleDetail)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        ) { innerPadding ->
            val screenModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            CompositionLocalProvider(LocalDockPadding provides DOCK_SPACE) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .haze(hazeState)) {
                    when (val screen = current) {
                        is Screen.Stock -> StockScreen(
                            modifier = screenModifier,
                            initialTab = screen.tab,
                            initialStore = screen.storeNumber,
                            preferences = preferences,
                            defaultLocation = defaultLocation,
                            rules = rules,
                            events = events,
                            checks = checks,
                            uiState = uiState,
                            isRunning = isRunning,
                            hasNotificationPermission = hasNotificationPermission,
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                            historyDao = historyDao,
                            lastSeenEventId = lastSeenEventId,
                            onSeen = { maxId ->
                                if (maxId > lastSeenEventId) {
                                    lastSeenEventId = maxId
                                    preferences.setLastSeenEventId(maxId)
                                }
                            },
                            actions = actions
                        )
                        Screen.Alerts -> WatchingScreen(
                            modifier = screenModifier,
                            rules = rules,
                            statuses = statuses,
                            uiState = uiState,
                            isRunning = isRunning,
                            defaultLocation = defaultLocation,
                            latestEvent = events.firstOrNull { it.kind != StockEventKind.SOLD_OUT },
                            hasNotificationPermission = hasNotificationPermission,
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                            ruleStore = ruleStore,
                            actions = actions
                        )
                        is Screen.RuleDetail -> {
                            val rule = rules.find { it.id == screen.ruleId }
                            if (rule == null) {
                                LaunchedEffect(Unit) { backStack = backStack.dropLast(1) }
                            } else {
                                RuleDetailScreen(
                                    modifier = screenModifier,
                                    rule = rule,
                                    status = statuses[rule.id],
                                    events = events,
                                    ruleStore = ruleStore,
                                    actions = actions,
                                    onDeleted = { backStack = backStack.dropLast(1) }
                                )
                            }
                        }
                        is Screen.Patterns -> PatternsScreen(
                            modifier = screenModifier,
                            events = events,
                            checks = checks,
                            rules = rules,
                            initialTargetKey = "rule_${screen.ruleId}",
                            onStoreClick = { store -> navigate(Screen.Stock(tab = StockTab.CHANGES, storeNumber = store)) }
                        )
                        Screen.Settings -> SettingsScreen(
                            modifier = screenModifier,
                            preferences = preferences,
                            historyDao = historyDao,
                            isRunning = isRunning,
                            hasNotificationPermission = hasNotificationPermission,
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                            eventCount = events.size,
                            unknownSkus = uiState.snapshots.values.flatMap { it.unknownSkus }.distinct(),
                            actions = actions,
                            onLocationSaved = { location ->
                                defaultLocation = location
                                requestPoll(context)
                            }
                        )
                        Screen.Help -> HelpScreen(modifier = screenModifier)
                    }
                }
            }
        }

        FloatingDock(
            hazeState = hazeState,
            selected = dockTab,
            alertBadge = when {
                inStockRuleCount > 0 -> inStockRuleCount
                else -> rules.size
            },
            alertBadgeInStock = inStockRuleCount > 0,
            onSelect = ::navigate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
        )
    }

    editorDraft?.let { draft ->
        RuleEditorSheet(
            initial = draft,
            isNew = ruleStore.get(draft.id) == null,
            defaultLocation = defaultLocation,
            onDismiss = { editorDraft = null },
            onSave = { rule ->
                ruleStore.upsert(rule)
                editorDraft = null
                requestPoll(context)
                if (!isRunning) startMonitoring()
                snackbar("Saved ${rule.displayName()}")
            }
        )
    }
}

@Composable
private fun MonitoringPill(isRunning: Boolean, hasError: Boolean, onClick: () -> Unit) {
    val color = when {
        hasError && isRunning -> StockColors.Error
        isRunning -> StockColors.InStock
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isRunning) color.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (isRunning) color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .selectable(selected = isRunning, role = Role.Switch, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag("monitoring_pill")
    ) {
        PulseIndicator(isActive = isRunning, hasError = hasError, size = 8)
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (isRunning) "Monitoring" else "Paused", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private data class DockItem(val screen: Screen, val label: String, val icon: ImageVector)

/**
 * Apple-style floating dock: frosted glass pill, the active tile raised and amber with a dot below.
 */
@Composable
private fun FloatingDock(
    hazeState: HazeState,
    selected: Screen,
    alertBadge: Int,
    alertBadgeInStock: Boolean,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        DockItem(Screen.Stock(), "Stock", Icons.Default.GridView),
        DockItem(Screen.Alerts, "Alerts", Icons.Default.NotificationsActive),
        DockItem(Screen.Settings, "Settings", Icons.Default.Tune),
        DockItem(Screen.Help, "Help", Icons.AutoMirrored.Filled.HelpOutline)
    )
    val shape = RoundedCornerShape(30.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .hazeChild(
                hazeState,
                HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    tint = HazeTint(Color(0xB81A1E2A)),
                    blurRadius = 24.dp,
                    noiseFactor = 0.05f,
                    fallbackTint = HazeTint(Color(0xF01A1E2A))
                )
            )
            .border(1.dp, Color(0x1AFFFFFF), shape)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)
            .testTag("floating_dock")
    ) {
        items.forEach { item ->
            val active = when (item.screen) {
                is Screen.Stock -> selected is Screen.Stock
                else -> selected == item.screen
            }
            val badge = if (item.screen == Screen.Alerts && alertBadge > 0) alertBadge else null
            DockTile(item, active, badge, badgeInStock = alertBadgeInStock) { onSelect(item.screen) }
        }
    }
}

@Composable
private fun DockTile(item: DockItem, active: Boolean, badge: Int?, badgeInStock: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (active) 1.12f else 1f, label = "dock_scale")
    val lift by animateDpAsState(if (active) (-6).dp else 0.dp, label = "dock_lift")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(62.dp)
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .testTag("dock_${item.label}")
    ) {
        Box(modifier = Modifier
            .offset(y = lift)
            .scale(scale)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .shadow(if (active) 10.dp else 0.dp, RoundedCornerShape(15.dp), spotColor = StockColors.Watched)
                    .background(if (active) StockColors.Watched else Color(0x12FFFFFF), RoundedCornerShape(15.dp))
            ) {
                Icon(item.icon, contentDescription = null, tint = if (active) StockColors.OnWatched else Color(0xFFEEF1F6), modifier = Modifier.size(24.dp))
            }
            if (badge != null) {
                Text(
                    "$badge",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (badgeInStock) StockColors.OnInStock else Color(0xFF0B0D12),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .border(2.dp, Color(0xFF1A1E2A), CircleShape)
                        .background(if (badgeInStock) StockColors.InStock else Color(0xFFEEF1F6), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            item.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Color(0xFFEEF1F6) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(if (active) StockColors.Watched else Color.Transparent, CircleShape)
        )
    }
}

@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HelpItem("Notify", "Tap Notify on any phone in Stock. The app watches that exact phone and starts monitoring. Tap On again to stop.")
        HelpItem("Groups", "In My alerts, one group can mean any colour, any storage, or more than one model, with its own location and radius. Tap a storage or colour heading in Stock to start one.")
        HelpItem("Restock times", "“Last restock”, “usually around”, “times this week” and “sells out in” are counted from the changes this phone saw. While monitoring is on, the app checks every colour and storage of the models you watch, so these fill in over a few days.")
        HelpItem("Location and radius", "Each alert uses the default location from Settings, or its own. Apple decides how many stores it returns for a postal code, so a large radius cannot show stores Apple does not send.")
        HelpItem("Keep it running", "Allow notifications and unrestricted battery use. Without them, Android can stop the checks while the screen is off.")
        HelpItem("If a cell shows SKU?", "Apple does not know that part number. Correct it in ProductCatalog.kt.")
        Spacer(modifier = Modifier.height(LocalDockPadding.current))
    }
}

@Composable
private fun HelpItem(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}
