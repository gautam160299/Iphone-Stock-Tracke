package com.example.applestockpulse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Watching : Screen
    data object Browse : Screen
    data class RecentChanges(val storeNumber: String? = null) : Screen
    data class Patterns(val ruleId: String? = null) : Screen
    data object Settings : Screen
    data object Help : Screen
    data class RuleDetail(val ruleId: String) : Screen
}

private val Screen.title: String
    get() = when (this) {
        Screen.Watching -> "Watching"
        Screen.Browse -> "Browse"
        is Screen.RecentChanges -> "Recent changes"
        is Screen.Patterns -> "Patterns"
        Screen.Settings -> "Settings"
        Screen.Help -> "Help"
        is Screen.RuleDetail -> "Device group"
    }

private val Screen.isTopLevel: Boolean get() = this !is Screen.RuleDetail

/**
 * Everything the screens share: live service state, saved rules, history and permissions.
 */
class AppActions(
    val navigate: (Screen) -> Unit,
    val openEditor: (WatchRule) -> Unit,
    val startOrStop: (Boolean) -> Unit,
    val requestNotifications: () -> Unit,
    val showUndoDelete: (WatchRule) -> Unit
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

    // Navigation: a small back stack; top-level screens sit on top of Watching
    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Watching)) }
    val current = backStack.last()
    fun navigate(screen: Screen) {
        backStack = when {
            screen == Screen.Watching -> listOf(Screen.Watching)
            screen.isTopLevel -> listOf(Screen.Watching, screen)
            else -> backStack + screen
        }
    }

    val pendingRule by openRuleRequest.collectAsState()
    LaunchedEffect(pendingRule) {
        pendingRule?.let { id ->
            if (ruleStore.get(id) != null) navigate(Screen.RuleDetail(id))
            openRuleRequest.value = null
        }
    }

    // Notifications: runtime permission (Android 13+) plus the app and alarm-channel switches
    var hasNotificationPermission by remember { mutableStateOf(AlertNotifier.canPostAlarms(context)) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(checkBatteryOptimization(context)) }
    // Set when "Start" opened the permission dialog, so monitoring starts once the user allows it
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

    // Re-check each time the screen comes back, e.g. from the Settings app
    LifecycleResumeEffect(Unit) {
        hasNotificationPermission = AlertNotifier.canPostAlarms(context)
        isIgnoringBatteryOptimizations = checkBatteryOptimization(context)
        onPauseOrDispose { }
    }

    // First launch of the process: show live data without waiting for monitoring
    LaunchedEffect(Unit) {
        if (uiState.snapshots.isEmpty() && rules.isNotEmpty()) requestPoll(context)
    }

    var editorDraft by remember { mutableStateOf<WatchRule?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val actions = AppActions(
        navigate = ::navigate,
        openEditor = { editorDraft = it },
        startOrStop = { start ->
            if (!start) {
                stopTrackerService(context)
            } else if (rules.isEmpty()) {
                Toast.makeText(context, "Add a device to watch first.", Toast.LENGTH_SHORT).show()
                editorDraft = WatchRule(selectors = listOf(DeviceSelector(TrackerPreferences.DEFAULT_MODEL)))
            } else if (hasNotificationPermission) {
                startTrackerService(context)
            } else {
                // Monitoring without notifications would detect stock but never alarm
                requestNotifications(startAfter = true)
            }
        },
        requestNotifications = { requestNotifications(startAfter = false) },
        showUndoDelete = { rule ->
            scope.launch {
                val result = snackbarHostState.showSnackbar("Deleted ${rule.displayName()}", actionLabel = "Undo")
                if (result == SnackbarResult.ActionPerformed) {
                    ruleStore.upsert(rule)
                    requestPoll(context)
                }
            }
        }
    )

    val statuses = remember(rules, uiState.snapshots, defaultLocation) {
        rules.associate { it.id to RuleStatusCalculator.statusFor(it, defaultLocation, uiState.snapshots) }
    }
    val inStockRuleCount = statuses.values.count { it.inStock }
    val unreadCount = events.count { it.id > lastSeenEventId && it.kind != StockEventKind.SOLD_OUT }

    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && backStack.size > 1) { backStack = backStack.dropLast(1) }

    val drawerContent: @Composable () -> Unit = {
        DrawerContent(
            current = current,
            isRunning = isRunning,
            hasError = uiState.error != null,
            lastChecked = uiState.lastCheckedTimestamp,
            location = defaultLocation,
            inStockRuleCount = inStockRuleCount,
            unreadCount = unreadCount,
            onToggleMonitoring = actions.startOrStop,
            onSelect = { screen ->
                navigate(screen)
                scope.launch { drawerState.close() }
            },
            onTestAlarm = {
                if (hasNotificationPermission) {
                    AlertNotifier.postTestAlarm(context)
                    Toast.makeText(context, "Test alarm sent", Toast.LENGTH_SHORT).show()
                } else {
                    requestNotifications(startAfter = false)
                }
                scope.launch { drawerState.close() }
            }
        )
    }

    val content: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(current.title, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            when {
                                !current.isTopLevel -> IconButton(onClick = { backStack = backStack.dropLast(1) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                !isWide -> IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open menu")
                                }
                            }
                        },
                        actions = {
                            PulseIndicator(isActive = isRunning, hasError = uiState.error != null)
                            Spacer(modifier = Modifier.width(4.dp))
                            if (current == Screen.Watching || current is Screen.RuleDetail) {
                                IconButton(onClick = {
                                    requestPoll(context)
                                    Toast.makeText(context, "Checking Apple stock…", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Check now")
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    )
                    if (uiState.isChecking && (current == Screen.Watching || current is Screen.RuleDetail)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        ) { innerPadding ->
            val screenModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            when (val screen = current) {
                Screen.Watching -> WatchingScreen(
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
                        LaunchedEffect(Unit) { backStack = listOf(Screen.Watching) }
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
                Screen.Browse -> BrowseScreen(
                    modifier = screenModifier,
                    preferences = preferences,
                    defaultLocation = defaultLocation,
                    rules = rules,
                    events = events,
                    actions = actions
                )
                is Screen.RecentChanges -> RecentChangesScreen(
                    modifier = screenModifier,
                    events = events,
                    checks = checks,
                    rules = rules,
                    initialStore = screen.storeNumber,
                    lastSeenEventIdAtOpen = remember(screen) { lastSeenEventId },
                    historyDao = historyDao,
                    onSeen = { maxId ->
                        if (maxId > lastSeenEventId) {
                            lastSeenEventId = maxId
                            preferences.setLastSeenEventId(maxId)
                        }
                    }
                )
                is Screen.Patterns -> PatternsScreen(
                    modifier = screenModifier,
                    events = events,
                    checks = checks,
                    rules = rules,
                    initialRuleId = screen.ruleId,
                    actions = actions
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

    if (isWide) {
        PermanentNavigationDrawer(drawerContent = {
            PermanentDrawerSheet(modifier = Modifier.width(280.dp)) { drawerContent() }
        }) {
            content()
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { ModalDrawerSheet(modifier = Modifier.width(300.dp)) { drawerContent() } }
        ) {
            content()
        }
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
                Toast.makeText(context, "Saved ${rule.displayName()}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun DrawerContent(
    current: Screen,
    isRunning: Boolean,
    hasError: Boolean,
    lastChecked: Long?,
    location: SearchLocation,
    inStockRuleCount: Int,
    unreadCount: Int,
    onToggleMonitoring: (Boolean) -> Unit,
    onSelect: (Screen) -> Unit,
    onTestAlarm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseIndicator(isActive = isRunning, hasError = hasError, size = 10)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isRunning) "MONITORING ON" else "MONITORING OFF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = if (isRunning) StockColors.InStock else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Near ${location.label()}" + (lastChecked?.let { " · ${TimeText.time(it)}" } ?: ""),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isRunning, onCheckedChange = onToggleMonitoring)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        DrawerSection("Monitor")
        DrawerItem("Watching", Icons.Default.Visibility, current == Screen.Watching, inStockRuleCount, StockColors.InStock) { onSelect(Screen.Watching) }
        DrawerItem("Browse", Icons.Default.GridView, current == Screen.Browse) { onSelect(Screen.Browse) }

        DrawerSection("Insights")
        DrawerItem("Recent changes", Icons.Default.History, current is Screen.RecentChanges, unreadCount, null) { onSelect(Screen.RecentChanges()) }
        DrawerItem("Patterns", Icons.Default.Insights, current is Screen.Patterns) { onSelect(Screen.Patterns()) }

        DrawerSection("App")
        DrawerItem("Settings", Icons.Default.Settings, current == Screen.Settings) { onSelect(Screen.Settings) }
        DrawerItem("Test alarm", Icons.Default.NotificationsActive, false) { onTestAlarm() }
        DrawerItem("Help", Icons.Default.Info, current == Screen.Help) { onSelect(Screen.Help) }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun DrawerSection(title: String) {
    SectionLabel(title, modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int = 0,
    badgeColor: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        badge = if (badgeCount > 0) {
            {
                if (badgeColor != null) {
                    Badge(containerColor = badgeColor) { Text("$badgeCount") }
                } else {
                    Badge { Text("$badgeCount") }
                }
            }
        } else null,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HelpItem("How it works", "The app asks Apple's in-store pickup service about the devices you watch, about every 60 seconds while monitoring is on. When a device comes into stock at a store within your radius, an alarm sounds.")
        HelpItem("Device groups", "One group can hold exact devices (iPhone Duo 256GB Night Sky) and wide choices (any colour, any storage). One group can also hold more than one model.")
        HelpItem("Location and radius", "Each group uses the default location from Settings, or its own. Apple decides how many stores it returns for a postal code, so a large radius cannot show stores Apple does not send.")
        HelpItem("Keep it running", "Allow notifications and unrestricted battery use. Without them, Android can stop the checks while the screen is off.")
        HelpItem("Recent changes and Patterns", "These use only what this phone saw. Gaps show when the app was not checking. Patterns need a few days of data before they mean much.")
        HelpItem("If a cell shows SKU?", "Apple does not know that part number. Correct it in ProductCatalog.kt.")
    }
}

@Composable
private fun HelpItem(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}
