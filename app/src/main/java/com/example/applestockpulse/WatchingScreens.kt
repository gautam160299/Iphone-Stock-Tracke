package com.example.applestockpulse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WatchingScreen(
    modifier: Modifier,
    rules: List<WatchRule>,
    statuses: Map<String, RuleStatus>,
    uiState: TrackerUiState,
    isRunning: Boolean,
    defaultLocation: SearchLocation,
    latestEvent: StockEventEntity?,
    hasNotificationPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    ruleStore: WatchRuleStore,
    actions: AppActions
) {
    val context = LocalContext.current
    // In-stock groups first, then newest first
    val sortedRules = remember(rules, statuses) {
        rules.sortedWith(compareByDescending<WatchRule> { statuses[it.id]?.inStock == true }.thenByDescending { it.createdAt })
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp + LocalDockPadding.current),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasNotificationPermission) {
                item { NotificationPermissionBanner(onRequestPermission = actions.requestNotifications) }
            }
            if (!isIgnoringBatteryOptimizations) {
                item { BatteryOptimizationBanner(onRequestExemption = { requestBatteryExemption(context) }) }
            }

            item {
                MonitorStatusCard(
                    isRunning = isRunning,
                    uiState = uiState,
                    defaultLocation = defaultLocation,
                    onStartStop = actions.startOrStop,
                    onOpenSettings = { actions.navigate(Screen.Settings) }
                )
            }

            uiState.error?.let { error ->
                item { ErrorNoticeCard(error) }
            }

            latestEvent?.let { event ->
                item { LatestChangeStrip(event) { actions.navigate(Screen.Stock(tab = StockTab.CHANGES)) } }
            }

            if (rules.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        EmptyState(
                            icon = Icons.Default.Visibility,
                            title = "No alerts yet",
                            text = "Tap Notify on any phone in Stock, or build a group here: any colour, any storage, or several models."
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { actions.navigate(Screen.Stock()) }) { Text("Open Stock") }
                                OutlinedButton(onClick = { actions.openEditor(newRuleDraft()) }) { Text("Add group") }
                            }
                        }
                    }
                }
            } else {
                item {
                    SectionLabel("Your alerts (${rules.size})", modifier = Modifier.padding(top = 4.dp))
                }
                items(sortedRules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        status = statuses[rule.id],
                        defaultLocation = defaultLocation,
                        onOpen = { actions.navigate(Screen.RuleDetail(rule.id)) },
                        onToggleAlarm = { on -> ruleStore.setAlarm(listOf(rule.id), on) },
                        onEdit = { actions.openEditor(rule) },
                        onDelete = {
                            ruleStore.delete(rule.id)
                            actions.showUndoDelete(rule)
                        }
                    )
                }
                item {
                    Button(
                        onClick = { actions.openEditor(newRuleDraft()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("add_rule_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add group", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun newRuleDraft(): WatchRule = WatchRule(selectors = listOf(DeviceSelector(TrackerPreferences.DEFAULT_MODEL)))

@Composable
private fun MonitorStatusCard(
    isRunning: Boolean,
    uiState: TrackerUiState,
    defaultLocation: SearchLocation,
    onStartStop: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_dashboard_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isRunning) "MONITORING ON" else "MONITORING OFF",
                        color = if (isRunning) StockColors.InStock else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = uiState.lastCheckedTimestamp?.let { "Last check ${TimeText.timeWithSeconds(it)}" } ?: "Not checked yet",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isRunning) {
                    OutlinedButton(onClick = { onStartStop(false) }, modifier = Modifier.testTag("stop_tracker_button")) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = { onStartStop(true) },
                        modifier = Modifier.testTag("start_tracker_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start")
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onOpenSettings)
            ) {
                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Near ${defaultLocation.label()} · change",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (uiState.watchedSkuCount > 0) {
                val seconds = (uiState.requestsPerCheck - 1).coerceAtLeast(0) * 5
                Text(
                    "${uiState.watchedSkuCount} variants · ${uiState.requestsPerCheck} request(s) per check" +
                        if (uiState.requestsPerCheck > 6) " · each check takes about ${seconds}s" else "",
                    fontSize = 11.sp,
                    color = if (uiState.requestsPerCheck > 6) StockColors.Watched else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LatestChangeStrip(event: StockEventEntity, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StockColors.InStockSoft,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, contentDescription = null, tint = StockColors.InStock, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${TimeText.dayHeader(event.timestamp)} ${TimeText.time(event.timestamp)} · ${ProductCatalog.getProductName(event.sku)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("In stock at ${event.storeName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("All changes ›", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RuleCard(
    rule: WatchRule,
    status: RuleStatus?,
    defaultLocation: SearchLocation,
    onOpen: () -> Unit,
    onToggleAlarm: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val inStock = status?.inStock == true
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("rule_card_${rule.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (inStock) StockColors.InStockSoft else MaterialTheme.colorScheme.surface),
        border = if (inStock) {
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockColors.InStock))
        } else CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.displayName(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Switch(checked = rule.alarmOn, onCheckedChange = onToggleAlarm)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
            rule.selectors.forEach { selector ->
                Text(
                    selector.label(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "📍 ${rule.effectiveLocation(defaultLocation).label()}" + if (rule.location == null) " (default)" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!rule.alarmOn) {
                Text("Alarm muted", fontSize = 11.sp, color = StockColors.Watched, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            RuleStatusLine(status)
        }
    }
}

@Composable
private fun RuleStatusLine(status: RuleStatus?) {
    when {
        status == null || status.checkedAt == null && status.error == null -> Text(
            "Waiting for the first check…",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        status.inStock -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "🟢 IN STOCK · ${status.hits.size} store(s)",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = StockColors.InStock
            )
            status.hits.take(2).forEach { hit ->
                Text(
                    "${hit.store.storeName}${hit.store.distanceText.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}: " +
                        hit.available.joinToString { variantShortName(it.sku) },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (status.hits.size > 2) {
                Text("+ ${status.hits.size - 2} more store(s)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        status.error != null && status.storesInRange == 0 -> Text(
            status.error,
            fontSize = 12.sp,
            color = StockColors.Error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        else -> Text(
            "None within ${status.location.radiusKm} km · ${status.storesInRange} store(s) checked",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * "iPhone Duo 256GB Night Sky" -> "256GB Night Sky"; the model is already shown nearby.
 */
fun variantShortName(sku: String): String {
    val product = ProductCatalog.getProduct(sku) ?: return sku
    return "${product.storage} ${product.color}"
}

@Composable
fun RuleDetailScreen(
    modifier: Modifier,
    rule: WatchRule,
    status: RuleStatus?,
    events: List<StockEventEntity>,
    ruleStore: WatchRuleStore,
    actions: AppActions,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val ruleSkus = remember(rule) { rule.skus().toSet() }
    val ruleEvents = remember(events, ruleSkus) { events.filter { it.sku in ruleSkus }.take(5) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp + LocalDockPadding.current),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rule.displayName(), fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(checked = rule.alarmOn, onCheckedChange = { ruleStore.setAlarm(listOf(rule.id), it) })
                            Text(if (rule.alarmOn) "Alarm on" else "Muted", fontSize = 10.sp)
                        }
                    }
                    rule.selectors.forEach { Text(it.label(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    status?.let { Text("📍 ${it.location.label()}", fontSize = 12.sp) }
                    Text("${ruleSkus.size} variant(s) watched", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { actions.openEditor(rule) }) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }
                        OutlinedButton(onClick = {
                            ruleStore.delete(rule.id)
                            actions.showUndoDelete(rule)
                            onDeleted()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }

        status?.error?.let { item { ErrorNoticeCard(it) } }

        item { SectionLabel("Stores within ${status?.location?.radiusKm ?: "-"} km") }

        if (status == null || status.checkedAt == null) {
            item { Text("Waiting for the first check…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (!status.inStock) {
            item {
                Text(
                    "None in stock · ${status.storesInRange} store(s) checked at ${TimeText.timeWithSeconds(status.checkedAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            items(status.hits, key = { it.store.storeNumber }) { hit ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StockColors.InStockSoft)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(hit.store.storeName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            listOf(hit.store.distanceText, hit.store.addressText).filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        hit.available.forEach { variant ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ProductCatalog.getProductName(variant.sku), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(variant.pickupQuote, fontSize = 11.sp, color = StockColors.InStock)
                                }
                                Button(
                                    onClick = { openProductPage(context, variant.sku) },
                                    colors = ButtonDefaults.buttonColors(containerColor = StockColors.InStock),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Open", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item { SectionLabel("Last changes", modifier = Modifier.padding(top = 8.dp)) }
        if (ruleEvents.isEmpty()) {
            item { Text("No changes recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        } else {
            items(ruleEvents, key = { it.id }) { event -> StockEventRow(event, unread = false) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { actions.navigate(Screen.Stock(tab = StockTab.CHANGES)) }) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("All changes")
                }
                TextButton(onClick = { actions.navigate(Screen.Patterns(rule.id)) }) {
                    Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Patterns for this group")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorSheet(
    initial: WatchRule,
    isNew: Boolean,
    defaultLocation: SearchLocation,
    onDismiss: () -> Unit,
    onSave: (WatchRule) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial.name) }
    val selectors = remember { mutableStateListOf<DeviceSelector>().apply { addAll(initial.selectors) } }
    var alarmOn by remember { mutableStateOf(initial.alarmOn) }
    var useCustomLocation by remember { mutableStateOf(initial.location != null) }
    var customPostal by remember { mutableStateOf(initial.location?.postalCode ?: defaultLocation.postalCode) }
    var customRadius by remember { mutableStateOf(initial.location?.radiusKm ?: defaultLocation.radiusKm) }

    val draft = WatchRule(
        id = initial.id,
        name = name.trim(),
        selectors = selectors.toList(),
        alarmOn = alarmOn,
        location = if (useCustomLocation) SearchLocation(TrackerPreferences.cleanPostalCode(customPostal), customRadius) else null,
        createdAt = initial.createdAt
    )
    val skus = draft.skus()
    val postalValid = !useCustomLocation || TrackerPreferences.isValidPostalCode(customPostal)
    val emptySelector = selectors.any { it.skus().isEmpty() }
    val canSave = skus.isNotEmpty() && postalValid && !emptySelector

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (isNew) "Watch devices" else "Edit device group", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                placeholder = { Text(draft.copy(name = "").displayName()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            selectors.forEachIndexed { index, selector ->
                SelectorEditor(
                    index = index,
                    selector = selector,
                    canRemove = selectors.size > 1,
                    onChange = { selectors[index] = it },
                    onRemove = { selectors.removeAt(index) }
                )
            }

            OutlinedButton(
                onClick = { selectors.add(DeviceSelector(ProductCatalog.DEVICE_MODELS.first().name)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add another device to this group")
            }

            HorizontalDivider()

            SectionLabel("Location")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !useCustomLocation,
                    onClick = { useCustomLocation = false },
                    label = { Text("Default · ${defaultLocation.label()}") }
                )
                FilterChip(selected = useCustomLocation, onClick = { useCustomLocation = true }, label = { Text("Custom") })
            }
            if (useCustomLocation) {
                OutlinedTextField(
                    value = customPostal,
                    onValueChange = { customPostal = it.uppercase() },
                    label = { Text("Postal code") },
                    isError = !postalValid,
                    supportingText = if (!postalValid) {
                        { Text("Use a Canadian postal code, e.g. V6C 1T2") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                RadiusChips(selected = customRadius, onSelect = { customRadius = it })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Alarm", fontWeight = FontWeight.SemiBold)
                    Text("Sound an alarm when any of these come into stock", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = alarmOn, onCheckedChange = { alarmOn = it })
            }

            HorizontalDivider()

            // Preview: exactly what will be watched
            Text(
                if (skus.isEmpty()) "No variant matches these choices" else "This watches ${skus.size} variant(s):",
                fontWeight = FontWeight.SemiBold,
                color = if (skus.isEmpty()) StockColors.Error else MaterialTheme.colorScheme.onSurface
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                skus.take(12).forEach { sku ->
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            ProductCatalog.getProductName(sku),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (skus.size > 12) {
                    Text("and ${skus.size - 12} more", fontSize = 11.sp, modifier = Modifier.padding(4.dp))
                }
            }
            if (skus.size > 30) {
                Text(
                    "Many variants make each check slower. Each request covers ${StockTrackerService.MAX_SKUS_PER_REQUEST}.",
                    fontSize = 11.sp,
                    color = StockColors.Watched
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { onSave(draft) },
                    enabled = canSave,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_rule_button")
                ) { Text("Save") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorEditor(
    index: Int,
    selector: DeviceSelector,
    canRemove: Boolean,
    onChange: (DeviceSelector) -> Unit,
    onRemove: () -> Unit
) {
    val model = ProductCatalog.getDeviceModel(selector.model)
    var showModels by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Device ${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Remove device") }
                }
            }
            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showModels = true }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(model.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose model")
                    }
                }
                DropdownMenu(expanded = showModels, onDismissRequest = { showModels = false }) {
                    ProductCatalog.DEVICE_MODELS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                showModels = false
                                if (option.name != selector.model) onChange(DeviceSelector(option.name))
                            }
                        )
                    }
                }
            }

            Text("Storage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = selector.storages.isEmpty(), onClick = { onChange(selector.copy(storages = emptySet())) }, label = { Text("Any") })
                model.storages.forEach { storage ->
                    FilterChip(
                        selected = storage in selector.storages,
                        onClick = { onChange(selector.copy(storages = selector.storages.toggle(storage))) },
                        label = { Text(storage) }
                    )
                }
            }

            Text("Colour", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = selector.colors.isEmpty(), onClick = { onChange(selector.copy(colors = emptySet())) }, label = { Text("Any") })
                model.colors.forEach { color ->
                    val swatch = ProductCatalog.CATALOG.firstOrNull { it.model == model.name && it.color == color }?.colorHex ?: Color.Gray
                    FilterChip(
                        selected = color in selector.colors,
                        onClick = { onChange(selector.copy(colors = selector.colors.toggle(color))) },
                        label = { Text(color) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                            )
                        }
                    )
                }
            }
            if (selector.skus().isEmpty()) {
                Text("No ${model.name} exists with these choices.", fontSize = 11.sp, color = StockColors.Error)
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadiusChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SearchLocation.RADIUS_OPTIONS_KM.forEach { km ->
            FilterChip(selected = km == selected, onClick = { onSelect(km) }, label = { Text("$km km") })
        }
    }
}
