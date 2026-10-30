package com.example.applestockpulse

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface ChangeRow {
    data class Header(val label: String) : ChangeRow
    data class Event(val event: StockEventEntity) : ChangeRow
    data class Gap(val gap: CheckGap) : ChangeRow
}

@Composable
fun RecentChangesScreen(
    modifier: Modifier,
    events: List<StockEventEntity>,
    checks: List<CheckLogEntity>,
    rules: List<WatchRule>,
    initialStore: String?,
    lastSeenEventIdAtOpen: Long,
    historyDao: HistoryDao,
    onSeen: (Long) -> Unit,
    // Only events for these SKUs (the model shown on the Stock screen); null = all
    skuScope: Set<String>? = null
) {
    var watchedOnly by remember { mutableStateOf(false) }
    var storeFilter by remember(initialStore) { mutableStateOf(initialStore) }
    var showStores by remember { mutableStateOf(false) }
    val exportCsv = rememberCsvExporter(historyDao)
    val context = LocalContext.current
    val schedule = remember { TrackerPreferences(context).getCheckSchedule() }

    val watchedSkus = remember(rules) { rules.flatMap { it.skus() }.toSet() }
    val scopedEvents = remember(events, skuScope) { if (skuScope == null) events else events.filter { it.sku in skuScope } }
    val stores = remember(scopedEvents) { scopedEvents.associate { it.storeNumber to it.storeName }.toList().sortedBy { it.second } }

    // Opening the screen marks everything as seen; rows newer than the old mark keep their dot
    LaunchedEffect(events) {
        events.maxOfOrNull { it.id }?.let(onSeen)
    }

    val rows = remember(scopedEvents, checks, watchedOnly, storeFilter, watchedSkus) {
        val filtered = scopedEvents.filter { e ->
            (!watchedOnly || e.sku in watchedSkus) && (storeFilter == null || e.storeNumber == storeFilter)
        }
        val now = System.currentTimeMillis()
        val since = now - TrackerPreferences.HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        val gaps = CheckGaps.find(checks, since, now)
        val items = (filtered.map { it.timestamp to ChangeRow.Event(it) } + gaps.map { it.end to ChangeRow.Gap(it) })
            .sortedByDescending { it.first }
        buildList {
            var lastDay: String? = null
            items.forEach { (time, row) ->
                val day = TimeText.dayHeader(time, now)
                if (day != lastDay) {
                    add(ChangeRow.Header(day))
                    lastDay = day
                }
                add(row)
            }
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp + LocalDockPadding.current),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !watchedOnly, onClick = { watchedOnly = false }, label = { Text("All") })
                FilterChip(selected = watchedOnly, onClick = { watchedOnly = true }, label = { Text("Watched") })
                Box {
                    FilterChip(
                        selected = storeFilter != null,
                        onClick = { showStores = true },
                        label = { Text(stores.find { it.first == storeFilter }?.second ?: "Store") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    DropdownMenu(expanded = showStores, onDismissRequest = { showStores = false }) {
                        DropdownMenuItem(text = { Text("All stores") }, onClick = { storeFilter = null; showStores = false })
                        stores.forEach { (number, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { storeFilter = number; showStores = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = exportCsv) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                }
            }
        }

        if (scopedEvents.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.History,
                    "No changes yet",
                    "Changes show here when a device comes into stock or sells out. Keep monitoring on to collect them."
                )
            }
        } else if (rows.none { it is ChangeRow.Event }) {
            item { Text("No changes match these filters.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        items(rows, key = { row ->
            when (row) {
                is ChangeRow.Header -> "h_${row.label}"
                is ChangeRow.Event -> "e_${row.event.id}"
                is ChangeRow.Gap -> "g_${row.gap.start}"
            }
        }) { row ->
            when (row) {
                is ChangeRow.Header -> SectionLabel(row.label, modifier = Modifier.padding(top = 8.dp))
                is ChangeRow.Event -> StockEventRow(row.event, unread = row.event.id > lastSeenEventIdAtOpen)
                is ChangeRow.Gap -> GapRow(row.gap, schedule)
            }
        }
    }
}

@Composable
fun StockEventRow(event: StockEventEntity, unread: Boolean) {
    val context = LocalContext.current
    val inStock = event.kind != StockEventKind.SOLD_OUT
    Card(shape = RoundedCornerShape(14.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (inStock) StockColors.InStock else StockColors.Error)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${TimeText.time(event.timestamp)} · " + when (event.kind) {
                        StockEventKind.RESTOCK -> "In stock"
                        StockEventKind.FIRST_SEEN -> "In stock (first check)"
                        else -> "Sold out" + (event.durationMs?.let { " after ${TimeText.duration(it)}" } ?: "")
                    } + if (event.afterGap) " · time not exact" else "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (inStock) StockColors.InStock else MaterialTheme.colorScheme.onSurface
                )
                Text(ProductCatalog.getProductName(event.sku), fontSize = 13.sp)
                Text(event.storeName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (unread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (inStock) {
                TextButton(onClick = { openProductPage(context, event.sku) }) {
                    Text("Open", fontSize = 12.sp)
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun GapRow(gap: CheckGap, schedule: CheckSchedule) {
    // A gap that is (almost) all inside the night pause was planned, not lost
    val length = gap.end - gap.start
    val planned = schedule.pausedMsBetween(gap.start, gap.end) >= length - CheckGaps.MIN_GAP_MS
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                when {
                    planned -> "Paused by schedule"
                    gap.failedChecks > 0 -> "Checks failed"
                    else -> "No checks"
                } + " ${TimeText.time(gap.start)} – ${TimeText.time(gap.end)} (${TimeText.duration(length)}). " +
                    when {
                        planned -> ""
                        gap.failedChecks > 0 -> "Apple blocked them or the network was down. "
                        else -> "Monitoring was off or Android stopped it. "
                    } +
                    "Changes in this time are missing.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class PatternTarget(val key: String, val label: String, val skus: Set<String>?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatternsScreen(
    modifier: Modifier,
    events: List<StockEventEntity>,
    checks: List<CheckLogEntity>,
    rules: List<WatchRule>,
    // e.g. "rule_<id>", "model_iPhone Duo", "watched"
    initialTargetKey: String,
    onStoreClick: (String) -> Unit
) {
    val targets = remember(rules) {
        buildList {
            add(PatternTarget("all", "All devices", null))
            add(PatternTarget("watched", "All watched", rules.flatMap { it.skus() }.toSet()))
            rules.forEach { add(PatternTarget("rule_${it.id}", it.displayName(), it.skus().toSet())) }
            ProductCatalog.DEVICE_MODELS.forEach { model ->
                add(PatternTarget("model_${model.name}", model.name, ProductCatalog.getSkusForSelection(model.name, "All", "All").toSet()))
            }
        }
    }
    var targetKey by remember(initialTargetKey) { mutableStateOf(initialTargetKey) }
    val target = targets.find { it.key == targetKey } ?: targets.first()
    var storeNumber by remember { mutableStateOf<String?>(null) }
    var rangeDays by remember { mutableStateOf(30) }
    var showTargets by remember { mutableStateOf(false) }
    var showStores by remember { mutableStateOf(false) }
    val stores = remember(events) { events.associate { it.storeNumber to it.storeName }.toList().sortedBy { it.second } }

    val summary = remember(events, checks, target, storeNumber, rangeDays) {
        PatternsCalculator.compute(events, checks, target.skus, storeNumber, rangeDays, System.currentTimeMillis())
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp + LocalDockPadding.current),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { showTargets = true },
                        label = { Text(target.label) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    DropdownMenu(expanded = showTargets, onDismissRequest = { showTargets = false }) {
                        targets.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { targetKey = option.key; showTargets = false })
                        }
                    }
                }
                Box {
                    FilterChip(
                        selected = storeNumber != null,
                        onClick = { showStores = true },
                        label = { Text(stores.find { it.first == storeNumber }?.second ?: "All stores") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    DropdownMenu(expanded = showStores, onDismissRequest = { showStores = false }) {
                        DropdownMenuItem(text = { Text("All stores") }, onClick = { storeNumber = null; showStores = false })
                        stores.forEach { (number, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { storeNumber = number; showStores = false })
                        }
                    }
                }
                FilterChip(selected = rangeDays == 7, onClick = { rangeDays = 7 }, label = { Text("7 days") })
                FilterChip(selected = rangeDays == 30, onClick = { rangeDays = 30 }, label = { Text("30 days") })
            }
        }

        item { CoverageCard(summary) }

        if (!summary.hasEnoughData) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    EmptyState(
                        Icons.Default.Insights,
                        "Not enough data yet",
                        "Patterns need at least ${PatternSummary.MIN_RESTOCKS} restocks. So far: ${summary.restockCount}. Keep monitoring on; this fills in over a few days."
                    )
                }
            }
        } else {
            item {
                PatternCard("Usual restock time") {
                    HourBars(summary.restocksByHour)
                    summary.peakHour?.let {
                        Text(
                            "Most often ${TimeText.hourLabel(it)} – ${TimeText.hourLabel((it + 1) % 24)}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "Sells out in",
                        value = summary.medianSellOutMinutes?.let { "$it min" } ?: "–",
                        note = if (summary.sellOutSamples > 0) "median of ${summary.sellOutSamples}" else "no exact sell-outs yet",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Restocks",
                        value = "${summary.restocksLast7Days}",
                        note = "last 7 days · ${summary.restockCount} in $rangeDays",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                PatternCard("Best stores") {
                    summary.bestStores.take(5).forEachIndexed { index, store ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStoreClick(store.storeNumber) }
                                .padding(vertical = 6.dp)
                        ) {
                            Text("${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                            Text(store.storeName, modifier = Modifier.weight(1f))
                            Text("${store.restocks} restock(s) ›", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                PatternCard("Weekday × hour") {
                    WeekdayHeatmap(summary.restocksByWeekdayHour)
                }
            }
        }
    }
}

@Composable
private fun CoverageCard(summary: PatternSummary) {
    val percent = (summary.coverage * 100).toInt()
    val weak = summary.dataDays < 1 || percent < 60
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (weak) StockColors.WatchedSoft else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (weak) StockColors.Watched else MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                if (summary.dataDays <= 0.0) "No background checks yet. Turn monitoring on to collect data."
                else "Based on ${"%.1f".format(summary.dataDays)} day(s). The app was checking $percent% of that time." +
                    if (weak) " Results may miss restocks." else "",
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PatternCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(title)
            content()
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, note: String, modifier: Modifier) {
    Card(shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            SectionLabel(title)
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HourBars(counts: IntArray) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val barColor = StockColors.InStock
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(90.dp)) {
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * 23) / 24
        counts.forEachIndexed { hour, count ->
            val height = if (count == 0) 3.dp.toPx() else size.height * count / max
            drawRoundRect(
                color = if (count == 0) emptyColor else barColor,
                topLeft = Offset(hour * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(0, 6, 12, 18, 23).forEach {
            Text(TimeText.hourLabel(it), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekdayHeatmap(grid: Array<IntArray>) {
    val max = grid.maxOf { row -> row.maxOrNull() ?: 0 }.coerceAtLeast(1)
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        grid.forEachIndexed { dayIndex, hours ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(days[dayIndex], fontSize = 10.sp, modifier = Modifier.width(32.dp))
                hours.forEach { count ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(0.5.dp)
                            .background(
                                if (count == 0) MaterialTheme.colorScheme.surfaceVariant
                                else StockColors.InStock.copy(alpha = 0.25f + 0.75f * count / max),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
        Row(modifier = Modifier.padding(start = 32.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 23).forEach {
                Text(TimeText.hourLabel(it), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
