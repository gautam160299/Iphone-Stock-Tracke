package com.example.applestockpulse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

object StockTab {
    const val STOCK = 0
    const val CHANGES = 1
    const val PATTERNS = 2
}

private const val STOCK_REFRESH_MS = 60_000L
private const val STOCK_REFRESH_EXTRA_MS = 3 * 60_000L
private const val INSIGHT_DAYS = 30

/**
 * Home screen: one model at a time, with Stock, Recent changes and Patterns tabs.
 */
@Composable
fun StockScreen(
    modifier: Modifier,
    initialTab: Int,
    initialStore: String?,
    preferences: TrackerPreferences,
    defaultLocation: SearchLocation,
    rules: List<WatchRule>,
    events: List<StockEventEntity>,
    checks: List<CheckLogEntity>,
    uiState: TrackerUiState,
    isRunning: Boolean,
    hasNotificationPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    historyDao: HistoryDao,
    lastSeenEventId: Long,
    onSeen: (Long) -> Unit,
    actions: AppActions
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val browseState by StockTrackerService.browseState.collectAsStateWithLifecycle()
    var model by remember { mutableStateOf(preferences.getBrowseModel()) }
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    var changesStore by remember(initialStore) { mutableStateOf(initialStore) }
    var detailSku by remember { mutableStateOf<String?>(null) }
    val deviceModel = ProductCatalog.getDeviceModel(model)
    val modelSkus = remember(model) { ProductCatalog.getSkusForSelection(model, "All", "All").toSet() }

    // A model that monitoring does not check costs Apple requests of its own; while monitoring
    // runs, refresh it less often so the two together are less likely to get blocked
    val monitoredHere = remember(rules, defaultLocation) {
        rules.filter { it.effectiveLocation(defaultLocation).postalCode == defaultLocation.postalCode }
            .flatMap { rule -> rule.selectors.map { it.model } }
            .toSet()
    }
    val refreshMs = if (isRunning && model !in monitoredHere) STOCK_REFRESH_EXTRA_MS else STOCK_REFRESH_MS

    // Check when the screen opens, then regularly while it is visible
    LaunchedEffect(model, defaultLocation, refreshMs) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                requestBrowsePoll(context, model)
                delay(refreshMs)
            }
        }
    }

    // Newest data for this model: the Stock check, or the monitor's check of a watched model
    val browseSnapshot = browseState.snapshot?.takeIf { browseState.model == model && it.postalCode == defaultLocation.postalCode }
    val watchSnapshot = uiState.snapshots[defaultLocation.postalCode]?.takeIf { snap ->
        snap.stores.any { store -> store.variants.any { it.sku in modelSkus } }
    }
    val snapshot = listOfNotNull(browseSnapshot, watchSnapshot).maxByOrNull { it.checkedAt ?: 0L }
    val stores = snapshot?.storesWithin(defaultLocation.radiusKm).orEmpty()
    val unread = events.count { it.id > lastSeenEventId && it.sku in modelSkus && it.kind != StockEventKind.SOLD_OUT }

    Column(modifier = modifier) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            items(ProductCatalog.DEVICE_MODELS, key = { it.name }) { option ->
                FilterChip(
                    selected = option.name == model,
                    onClick = {
                        model = option.name
                        preferences.setBrowseModel(option.name)
                        changesStore = null
                    },
                    label = { Text(option.name, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(18.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StockColors.WatchedSoft,
                        selectedLabelColor = StockColors.Watched
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = option.name == model,
                        selectedBorderColor = StockColors.Watched,
                        borderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { actions.navigate(Screen.Settings) }
                    .padding(vertical = 6.dp)
            ) {
                Icon(Icons.Default.Place, contentDescription = null, tint = StockColors.Link, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Near ${defaultLocation.label()}", fontSize = 12.sp, color = StockColors.Link)
            }
            Text(
                snapshot?.checkedAt?.let { " · checked ${TimeText.timeWithSeconds(it)}" } ?: " · checking…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = StockColors.Watched)
            }
        ) {
            listOf("Stock", "Recent changes", "Patterns").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            if (index == StockTab.CHANGES && unread > 0 && tab != StockTab.CHANGES) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge(containerColor = StockColors.Watched, contentColor = StockColors.OnWatched) { Text("$unread") }
                            }
                        }
                    }
                )
            }
        }

        if (browseState.isLoading && tab == StockTab.STOCK) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                StockTab.STOCK -> StockTabContent(
                    deviceModel = deviceModel,
                    modelSkus = modelSkus,
                    snapshot = snapshot,
                    stores = stores,
                    defaultLocation = defaultLocation,
                    rules = rules,
                    events = events,
                    isRunning = isRunning,
                    hasNotificationPermission = hasNotificationPermission,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    actions = actions,
                    onDetails = { detailSku = it }
                )
                StockTab.CHANGES -> RecentChangesScreen(
                    modifier = Modifier.fillMaxSize(),
                    events = events,
                    checks = checks,
                    rules = rules,
                    initialStore = changesStore,
                    lastSeenEventIdAtOpen = remember(model) { lastSeenEventId },
                    historyDao = historyDao,
                    onSeen = onSeen,
                    skuScope = modelSkus
                )
                else -> PatternsScreen(
                    modifier = Modifier.fillMaxSize(),
                    events = events,
                    checks = checks,
                    rules = rules,
                    initialTargetKey = "model_$model",
                    onStoreClick = { store ->
                        changesStore = store
                        tab = StockTab.CHANGES
                    }
                )
            }
        }
    }

    detailSku?.let { sku ->
        VariantSheet(
            sku = sku,
            stores = stores,
            rules = rules.filter { sku in it.skus() },
            events = events,
            onDismiss = { detailSku = null },
            onToggleNotify = {
                detailSku = null
                actions.toggleNotify(sku)
            },
            onOpen = { openProductPage(context, sku) }
        )
    }
}

@Composable
private fun StockTabContent(
    deviceModel: DeviceModel,
    modelSkus: Set<String>,
    snapshot: LocationSnapshot?,
    stores: List<StoreInventoryCard>,
    defaultLocation: SearchLocation,
    rules: List<WatchRule>,
    events: List<StockEventEntity>,
    isRunning: Boolean,
    hasNotificationPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    actions: AppActions,
    onDetails: (String) -> Unit
) {
    val context = LocalContext.current
    val now = remember(events, snapshot) { System.currentTimeMillis() }
    val storeInsights = remember(events, stores, modelSkus) {
        stores.associate { store ->
            store.storeNumber to PatternsCalculator.compute(events, emptyList(), modelSkus, store.storeNumber, INSIGHT_DAYS, now)
        }
    }
    val cellStates = remember(rules, modelSkus) { modelSkus.associateWith { NotifyCellState.of(rules, it) } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp + LocalDockPadding.current),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hasNotificationPermission) {
            item { NotificationPermissionBanner(onRequestPermission = actions.requestNotifications) }
        }
        if (!isIgnoringBatteryOptimizations) {
            item { BatteryOptimizationBanner(onRequestExemption = { requestBatteryExemption(context) }) }
        }
        if (!isRunning && rules.isNotEmpty()) {
            item { PausedStrip(onStart = { actions.startOrStop(true) }) }
        }

        item {
            NearbyBanner(
                modelSkus = modelSkus,
                stores = stores,
                events = events,
                storeInsights = storeInsights,
                now = now
            )
        }

        item {
            Text(
                "Tap Notify to get an alarm the moment that exact phone lands. Tap a storage or colour heading to watch the whole column or row.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        snapshot?.error?.let { item { ErrorNoticeCard(it) } }
        snapshot?.unknownSkus?.filter { it in modelSkus }?.takeIf { it.isNotEmpty() }?.let {
            item { ErrorNoticeCard("Apple does not recognize: ${it.joinToString()}. Those cells show SKU?.") }
        }

        when {
            snapshot == null -> item {
                EmptyState(Icons.Default.GridView, "Checking Apple stock…", "Results for ${deviceModel.name} show here in a few seconds.")
            }
            stores.isEmpty() && snapshot.error == null -> item {
                EmptyState(Icons.Default.GridView, "No stores within ${defaultLocation.radiusKm} km", "Choose a larger radius in Settings.")
            }
            else -> {
                val farthest = snapshot.farthestKm
                if (farthest != null && farthest < defaultLocation.radiusKm) {
                    item {
                        Text(
                            "Apple returned ${snapshot.stores.size} stores; the farthest is ${"%.1f".format(farthest)} km away.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(stores, key = { it.storeNumber }) { store ->
                    StoreStockCard(
                        store = store,
                        model = deviceModel,
                        insight = storeInsights[store.storeNumber],
                        now = now,
                        cellStates = cellStates,
                        onCell = { sku, available ->
                            if (available) onDetails(sku) else actions.toggleNotify(sku)
                        },
                        onLongCell = onDetails,
                        onWatchStorage = { storage ->
                            actions.openEditor(WatchRule(selectors = listOf(DeviceSelector(deviceModel.name, storages = setOf(storage)))))
                        },
                        onWatchColor = { color ->
                            actions.openEditor(WatchRule(selectors = listOf(DeviceSelector(deviceModel.name, colors = setOf(color)))))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PausedStrip(onStart: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StockColors.WatchedSoft,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StockColors.Watched.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Monitoring is off. Your alerts will not sound.", fontSize = 12.sp, color = Color(0xFFF6D79A), modifier = Modifier.weight(1f))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = StockColors.Watched, contentColor = StockColors.OnWatched),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Start", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
    }
}

/**
 * "Metrotown last restocked today 6:53 PM · usually around 10 AM · sells out in about 9 minutes"
 */
@Composable
private fun NearbyBanner(
    modelSkus: Set<String>,
    stores: List<StoreInventoryCard>,
    events: List<StockEventEntity>,
    storeInsights: Map<String, PatternSummary>,
    now: Long
) {
    val inStock = stores.flatMap { store -> store.variants.filter { it.isAvailable && it.sku in modelSkus }.map { store to it } }
    val nearby = remember(events, stores, modelSkus) {
        val storeNumbers = stores.map { it.storeNumber }.toSet()
        PatternsCalculator.compute(events.filter { it.storeNumber in storeNumbers }, emptyList(), modelSkus, null, INSIGHT_DAYS, now)
    }
    val latestStore = stores
        .mapNotNull { store -> storeInsights[store.storeNumber]?.lastRestockAt?.let { store to it } }
        .maxByOrNull { it.second }

    val green = inStock.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (green) StockColors.InStockSoft else Color(0xFF17140D), RoundedCornerShape(16.dp))
            .border(1.dp, if (green) StockColors.InStock else Color(0xFF3A3220), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        when {
            green -> {
                val (store, variant) = inStock.first()
                Text(
                    "In stock now: ${variantShortName(variant.sku)} at ${store.storeName}" +
                        if (inStock.size > 1) " (+${inStock.size - 1} more)" else "",
                    fontWeight = FontWeight.Bold,
                    color = StockColors.InStock
                )
                Text(
                    "${store.distanceText.ifBlank { "Nearby" }} · ${variant.pickupQuote}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            latestStore != null -> {
                val (store, time) = latestStore
                val usual = storeInsights[store.storeNumber]?.takeIf { it.hasEnoughData }?.peakHour
                Text("${store.storeName} last restocked ${relativeDayTime(time, now)}", fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(store.distanceText.takeIf { it.isNotBlank() }, usual?.let { "usually around ${TimeText.hourLabel(it)}" }).joinToString(" · "),
                    fontSize = 12.sp,
                    color = Color(0xFFC9D0DC)
                )
            }
            else -> {
                Text("No restocks seen near you yet", fontWeight = FontWeight.Bold)
                Text(
                    "Keep monitoring on. Restock times and sell-out speed fill in over a few days.",
                    fontSize = 12.sp,
                    color = Color(0xFFC9D0DC)
                )
            }
        }
        nearby.medianSellOutMinutes?.let {
            Text("Restocks near you usually sell out in about $it minutes.", fontSize = 12.sp, color = Color(0xFFC9D0DC))
        }
    }
}

/**
 * How a Notify cell looks for one phone, from the saved alerts.
 */
enum class NotifyCellState {
    NOTIFY, ON, MUTED, GROUP;

    companion object {
        fun of(rules: List<WatchRule>, sku: String): NotifyCellState {
            val covering = rules.filter { sku in it.skus() }
            val single = covering.filter { it.isSinglePhone }
            return when {
                single.any { it.alarmOn } -> ON
                covering.any { !it.isSinglePhone && it.alarmOn } -> GROUP
                covering.isNotEmpty() -> MUTED
                else -> NOTIFY
            }
        }
    }
}

@Composable
private fun StoreStockCard(
    store: StoreInventoryCard,
    model: DeviceModel,
    insight: PatternSummary?,
    now: Long,
    cellStates: Map<String, NotifyCellState>,
    onCell: (String, Boolean) -> Unit,
    onLongCell: (String) -> Unit,
    onWatchStorage: (String) -> Unit,
    onWatchColor: (String) -> Unit
) {
    val modelVariants = store.variants.filter { ProductCatalog.getProduct(it.sku)?.model == model.name }
    val available = modelVariants.count { it.isAvailable }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StockColors.Card, RoundedCornerShape(18.dp))
            .border(1.dp, if (available > 0) StockColors.InStock else StockColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("store_card_${store.storeNumber}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(store.storeName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (store.addressText.isNotBlank()) Text(store.addressText, fontSize = 11.sp, color = StockColors.Link)
                if (store.phoneText.isNotBlank()) Text(store.phoneText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (store.distanceText.isNotBlank()) Pill(store.distanceText, filled = false)
                Pill(if (available > 0) "$available available" else "None available", filled = available > 0)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10141C), RoundedCornerShape(10.dp))
                .border(1.dp, StockColors.CardBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(6.dp))
            Text(storeInsightLine(insight, now), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(modifier = Modifier.width(82.dp))
            model.storages.forEach { storage ->
                Text(
                    storage,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 32.dp)
                        .clickable { onWatchStorage(storage) }
                        .padding(vertical = 8.dp)
                        .semantics { contentDescription = "Watch every $storage ${model.name}" }
                )
            }
        }

        model.colors.forEach { color ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0B0D12),
                    modifier = Modifier
                        .width(82.dp)
                        .border(1.dp, Color(0xFF2A3142), RoundedCornerShape(8.dp))
                        .clickable { onWatchColor(color) }
                        .semantics { contentDescription = "Watch every $color ${model.name}" }
                ) {
                    Text(
                        color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                    )
                }
                model.storages.forEach { storage ->
                    val sku = ProductCatalog.getSku(model.name, storage, color)
                    val variant = sku?.let { s -> store.variants.find { it.sku == s } }
                    Box(modifier = Modifier.weight(1f)) {
                        NotifyCell(
                            sku = sku,
                            variant = variant,
                            state = sku?.let { cellStates[it] } ?: NotifyCellState.NOTIFY,
                            description = "${model.name} $storage $color at ${store.storeName}",
                            onClick = { if (sku != null) onCell(sku, variant?.isAvailable == true) },
                            onLongClick = { if (sku != null) onLongCell(sku) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, filled: Boolean) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (filled) StockColors.OnInStock else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(if (filled) StockColors.InStock else Color.Transparent, RoundedCornerShape(10.dp))
            .border(1.dp, if (filled) StockColors.InStock else Color(0xFF2A3142), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotifyCell(
    sku: String?,
    variant: VariantAvailability?,
    state: NotifyCellState,
    description: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    data class Look(val label: String, val background: Color, val border: Color, val text: Color, val prefix: String)

    val look = when {
        sku == null -> Look("—", Color(0x14FFFFFF), Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant, "Not sold: ")
        variant?.isKnown == false -> Look("SKU?", Color(0x14FFFFFF), Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant, "Unknown part number: ")
        variant?.isAvailable == true -> Look("Pickup", StockColors.InStock, StockColors.InStock, StockColors.OnInStock, "In stock: ")
        state == NotifyCellState.ON -> Look("On", StockColors.Watched, StockColors.Watched, StockColors.OnWatched, "Alert on: ")
        state == NotifyCellState.GROUP -> Look("Group", Color(0x38F6B445), StockColors.Watched, StockColors.Watched, "In a group alert: ")
        state == NotifyCellState.MUTED -> Look("Muted", Color.Transparent, Color(0xFF3A4357), MaterialTheme.colorScheme.onSurfaceVariant, "Alert muted: ")
        else -> Look("Notify", Color(0x0FF6B445), Color(0xFF5A4A26), StockColors.Watched, "Notify me: ")
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(look.background, RoundedCornerShape(8.dp))
            .border(1.dp, look.border, RoundedCornerShape(8.dp))
            .combinedClickable(enabled = sku != null, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = look.prefix + description }
    ) {
        Text(look.label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = look.text, maxLines = 1)
    }
}

/**
 * "Last restock today 6:53 PM · usually around 10 AM · 13 times this week", like the site's store line.
 */
fun storeInsightLine(summary: PatternSummary?, now: Long): String {
    if (summary == null || summary.lastRestockAt == null) {
        return "No restocks seen yet · history fills in while monitoring is on"
    }
    val parts = mutableListOf("Last restock ${relativeDayTime(summary.lastRestockAt, now)}")
    if (summary.hasEnoughData) summary.peakHour?.let { parts.add("usually around ${TimeText.hourLabel(it)}") }
    parts.add("${summary.restocksLast7Days} times this week")
    return parts.joinToString(" · ")
}

fun relativeDayTime(time: Long, now: Long): String =
    "${TimeText.dayHeader(time, now).replaceFirstChar { it.lowercase() }.let { if (it == "today" || it == "yesterday") it else "on $it" }} ${TimeText.time(time)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantSheet(
    sku: String,
    stores: List<StoreInventoryCard>,
    rules: List<WatchRule>,
    events: List<StockEventEntity>,
    onDismiss: () -> Unit,
    onToggleNotify: () -> Unit,
    onOpen: () -> Unit
) {
    val lastRestock = remember(events, sku) { events.firstOrNull { it.sku == sku && it.kind != StockEventKind.SOLD_OUT } }
    val lastSellOut = remember(events, sku) { events.firstOrNull { it.sku == sku && it.kind == StockEventKind.SOLD_OUT && it.durationMs != null } }
    val hasSingleAlert = rules.any { it.isSinglePhone }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF11151D)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(ProductCatalog.getProductName(sku), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Part number $sku", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionLabel("Stock nearby")
            if (stores.isEmpty()) Text("No store results yet.", fontSize = 13.sp)
            stores.forEach { store ->
                val variant = store.variants.find { it.sku == sku }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${store.storeName}${store.distanceText.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        when {
                            variant == null || !variant.isKnown -> "Unknown"
                            variant.isAvailable -> variant.pickupQuote
                            else -> "None"
                        },
                        fontSize = 12.sp,
                        fontWeight = if (variant?.isAvailable == true) FontWeight.Bold else FontWeight.Normal,
                        color = if (variant?.isAvailable == true) StockColors.InStock else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionLabel("History")
            Text(
                lastRestock?.let { "Last in stock: ${TimeText.dayTime(it.timestamp)} at ${it.storeName}" } ?: "Not seen in stock in the last 30 days.",
                fontSize = 13.sp
            )
            lastSellOut?.let { Text("Last sold out after ${TimeText.duration(it.durationMs ?: 0)} at ${it.storeName}", fontSize = 13.sp) }
            if (rules.isNotEmpty()) {
                Text("Watched by: ${rules.joinToString { it.displayName() }}", fontSize = 12.sp, color = StockColors.Watched, fontWeight = FontWeight.SemiBold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onToggleNotify, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (hasSingleAlert) "Stop alert" else "Notify me")
                }
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = StockColors.InStock, contentColor = StockColors.OnInStock)
                ) {
                    Text("Apple.ca", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
