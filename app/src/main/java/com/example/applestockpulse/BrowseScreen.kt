package com.example.applestockpulse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

private const val BROWSE_REFRESH_MS = 60_000L

@Composable
fun BrowseScreen(
    modifier: Modifier,
    preferences: TrackerPreferences,
    defaultLocation: SearchLocation,
    rules: List<WatchRule>,
    events: List<StockEventEntity>,
    actions: AppActions
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val browseState by StockTrackerService.browseState.collectAsStateWithLifecycle()
    var model by remember { mutableStateOf(preferences.getBrowseModel()) }
    var showModels by remember { mutableStateOf(false) }
    var selectedSku by remember { mutableStateOf<String?>(null) }
    val deviceModel = ProductCatalog.getDeviceModel(model)
    val watchedSkus = remember(rules) { rules.flatMap { it.skus() }.toSet() }

    // Check when the screen opens, then every ~60 s while it is visible
    LaunchedEffect(model, defaultLocation) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                requestBrowsePoll(context, model)
                delay(BROWSE_REFRESH_MS)
            }
        }
    }

    // Results of an older request for another model or location would show wrong data
    val snapshot = browseState.snapshot?.takeIf {
        browseState.model == model && it.postalCode == defaultLocation.postalCode
    }
    val stores = snapshot?.storesWithin(defaultLocation.radiusKm).orEmpty()

    Column(modifier = modifier) {
        if (browseState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showModels = true }
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(deviceModel.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text(deviceModel.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose model", modifier = Modifier.size(28.dp))
                            }
                            DropdownMenu(expanded = showModels, onDismissRequest = { showModels = false }) {
                                ProductCatalog.DEVICE_MODELS.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.name, fontWeight = if (option.name == model) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            model = option.name
                                            preferences.setBrowseModel(option.name)
                                            showModels = false
                                        }
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Near ${defaultLocation.label()} · " +
                                    (snapshot?.checkedAt?.let { "checked ${TimeText.timeWithSeconds(it)}" } ?: "checking…"),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { requestBrowsePoll(context, model) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Check now")
                            }
                        }
                        Text(
                            "Tap a cell for details. Tap a storage or colour heading to watch that whole column or row.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        snapshot?.let { snap ->
                            val farthest = snap.farthestKm
                            if (farthest != null && farthest < defaultLocation.radiusKm) {
                                Text(
                                    "Apple returned ${snap.stores.size} stores; the farthest is ${"%.1f".format(farthest)} km away.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            snapshot?.error?.let { item { ErrorNoticeCard(it) } }
            snapshot?.unknownSkus?.takeIf { it.isNotEmpty() }?.let {
                item { ErrorNoticeCard("Apple does not recognize: ${it.joinToString()}. Those cells show SKU?.") }
            }

            if (snapshot == null) {
                item { EmptyState(Icons.Default.GridView, "Checking Apple stock…", "Results for ${deviceModel.name} show here in a few seconds.") }
            } else if (stores.isEmpty() && snapshot.error == null) {
                item { EmptyState(Icons.Default.GridView, "No stores within ${defaultLocation.radiusKm} km", "Choose a larger radius in Settings.") }
            } else {
                items(stores, key = { it.storeNumber }) { store ->
                    BrowseStoreCard(
                        store = store,
                        model = deviceModel,
                        watchedSkus = watchedSkus,
                        onCell = { sku -> selectedSku = sku },
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

    selectedSku?.let { sku ->
        VariantSheet(
            sku = sku,
            stores = stores,
            rules = rules.filter { sku in it.skus() },
            events = events,
            onDismiss = { selectedSku = null },
            onWatch = {
                selectedSku = null
                val product = ProductCatalog.getProduct(sku)
                if (product != null) {
                    actions.openEditor(
                        WatchRule(selectors = listOf(DeviceSelector(product.model, setOf(product.storage), setOf(product.color))))
                    )
                }
            },
            onOpen = { openProductPage(context, sku) }
        )
    }
}

@Composable
private fun BrowseStoreCard(
    store: StoreInventoryCard,
    model: DeviceModel,
    watchedSkus: Set<String>,
    onCell: (String) -> Unit,
    onWatchStorage: (String) -> Unit,
    onWatchColor: (String) -> Unit
) {
    val hasStock = store.variants.any { it.isAvailable && ProductCatalog.getProduct(it.sku)?.model == model.name }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = if (hasStock) {
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockColors.InStock))
        } else CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(store.storeName, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (store.addressText.isNotBlank()) {
                        Text(store.addressText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    if (store.phoneText.isNotBlank()) {
                        Text(store.phoneText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (store.distanceText.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(store.distanceText, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
            }
            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(92.dp))
                model.storages.forEach { storage ->
                    Text(
                        storage,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onWatchStorage(storage) }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            model.colors.forEach { color ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .width(92.dp)
                            .clickable { onWatchColor(color) }
                    ) {
                        Text(color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
                    }
                    model.storages.forEach { storage ->
                        val sku = ProductCatalog.getSku(model.name, storage, color)
                        val variant = sku?.let { s -> store.variants.find { it.sku == s } }
                        Box(modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)) {
                            MatrixCell(
                                sku = sku,
                                variant = variant,
                                watched = sku != null && sku in watchedSkus,
                                onClick = { if (sku != null) onCell(sku) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixCell(sku: String?, variant: VariantAvailability?, watched: Boolean, onClick: () -> Unit) {
    val (label, background, textColor) = when {
        sku == null -> Triple("—", MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.colorScheme.onSurfaceVariant)
        variant?.isKnown == false -> Triple("SKU?", MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.colorScheme.onSurfaceVariant)
        variant?.isAvailable == true -> Triple("✓", StockColors.InStock, Color.White)
        else -> Triple("·", MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = background,
        border = if (watched) {
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockColors.Watched))
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = sku != null, onClick = onClick)
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            if (watched) {
                Icon(Icons.Default.NotificationsActive, contentDescription = "Watched", tint = if (variant?.isAvailable == true) Color.White else StockColors.Watched, modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantSheet(
    sku: String,
    stores: List<StoreInventoryCard>,
    rules: List<WatchRule>,
    events: List<StockEventEntity>,
    onDismiss: () -> Unit,
    onWatch: () -> Unit,
    onOpen: () -> Unit
) {
    val lastRestock = remember(events, sku) { events.firstOrNull { it.sku == sku && it.kind != StockEventKind.SOLD_OUT } }
    val lastSellOut = remember(events, sku) { events.firstOrNull { it.sku == sku && it.kind == StockEventKind.SOLD_OUT && it.durationMs != null } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
            if (stores.isEmpty()) {
                Text("No store results yet.", fontSize = 13.sp)
            }
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
            lastSellOut?.let {
                Text("Last sold out after ${TimeText.duration(it.durationMs ?: 0)} at ${it.storeName}", fontSize = 13.sp)
            }

            if (rules.isNotEmpty()) {
                Text("Watched by: ${rules.joinToString { it.displayName() }}", fontSize = 12.sp, color = StockColors.Watched, fontWeight = FontWeight.SemiBold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onWatch, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (rules.isEmpty()) "Watch this" else "Watch again")
                }
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = StockColors.InStock)
                ) {
                    Text("Apple.ca")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
