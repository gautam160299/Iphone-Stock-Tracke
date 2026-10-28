package com.example.applestockpulse

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AppleStockPulseApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleStockPulseApp() {
    val context = LocalContext.current
    val preferences = remember { TrackerPreferences(context) }

    // Observe service state
    val isRunning by StockTrackerService.isRunning.collectAsStateWithLifecycle()
    val uiState by StockTrackerService.uiState.collectAsStateWithLifecycle()

    // Form inputs initialized from SharedPreferences
    var skusInput by remember { mutableStateOf(preferences.getSkus()) }
    var postalCodeInput by remember { mutableStateOf(preferences.getPostalCode()) }
    var storeFilterInput by remember { mutableStateOf(preferences.getStoreFilter()) }

    var configSavedBanner by remember { mutableStateOf(false) }

    // Notification Permission State
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!isGranted) {
            Toast.makeText(
                context,
                "Notification permission is required to receive instant stock alarms!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Battery Optimization State
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(checkBatteryOptimization(context))
    }

    // Re-check battery optimization on resume
    DisposableEffect(Unit) {
        isIgnoringBatteryOptimizations = checkBatteryOptimization(context)
        onDispose { }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        PulseIndicator(isActive = isRunning)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AppleStockPulse",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            openAppleBag(context)
                        },
                        modifier = Modifier.testTag("action_open_bag")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = "Open Apple Store Bag",
                            tint = Color(0xFF38BDF8)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission & Battery notices if needed
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    NotificationPermissionBanner(
                        onRequestPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }
            }

            if (!isIgnoringBatteryOptimizations) {
                item {
                    BatteryOptimizationBanner(
                        onRequestExemption = {
                            requestBatteryExemption(context)
                        }
                    )
                }
            }

            // Live Service Status Card
            item {
                StatusDashboardCard(
                    isRunning = isRunning,
                    uiState = uiState,
                    onForcePoll = {
                        val forceIntent = Intent(context, StockTrackerService::class.java).apply {
                            action = StockTrackerService.ACTION_FORCE_POLL
                        }
                        context.startService(forceIntent)
                    }
                )
            }

            // Controls Section (Start / Stop / Test Alert)
            item {
                ActionControlsRow(
                    isRunning = isRunning,
                    onStartTracker = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        preferences.saveConfiguration(skusInput, postalCodeInput, storeFilterInput)
                        startTrackerService(context)
                    },
                    onStopTracker = {
                        stopTrackerService(context)
                    },
                    onTestAlert = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        triggerTestAlert(context)
                    }
                )
            }

            // Wide Multi-Product Configuration Form Card
            item {
                WideProductConfigurationCard(
                    skus = skusInput,
                    onSkusChange = {
                        skusInput = it
                        configSavedBanner = false
                    },
                    postalCode = postalCodeInput,
                    onPostalCodeChange = {
                        postalCodeInput = it
                        configSavedBanner = false
                    },
                    storeFilter = storeFilterInput,
                    onStoreFilterChange = {
                        storeFilterInput = it
                        configSavedBanner = false
                    },
                    configSaved = configSavedBanner,
                    onSave = {
                        preferences.saveConfiguration(skusInput, postalCodeInput, storeFilterInput)
                        configSavedBanner = true
                        Toast.makeText(context, "Configuration Saved!", Toast.LENGTH_SHORT).show()
                        if (isRunning) {
                            val forceIntent = Intent(context, StockTrackerService::class.java).apply {
                                action = StockTrackerService.ACTION_FORCE_POLL
                            }
                            context.startService(forceIntent)
                        }
                    }
                )
            }

            // Store Results Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Store Inventory (${uiState.storesResults.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.totalAvailableCount > 0) {
                        Surface(
                            color = Color(0xFF10B981),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${uiState.totalAvailableCount} IN STOCK",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.storesResults.isEmpty()) {
                item {
                    EmptyResultsCard(isRunning = isRunning)
                }
            } else {
                items(
                    items = uiState.storesResults,
                    key = { "${it.storeNumber}:${it.sku}" }
                ) { storeResult ->
                    StoreAvailabilityCard(
                        result = storeResult,
                        onCheckoutClick = {
                            openAppleBag(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusDashboardCard(
    isRunning: Boolean,
    uiState: TrackerUiState,
    onForcePoll: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_dashboard_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isRunning) "MONITORING ACTIVE" else "MONITORING IDLE",
                        color = if (isRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isRunning) "Scanning active items every ~60s" else "Service stopped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isRunning) {
                    IconButton(
                        onClick = onForcePoll,
                        modifier = Modifier.testTag("force_poll_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Poll Immediately",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val lastCheckedFormatted = remember(uiState.lastCheckedTimestamp) {
                uiState.lastCheckedTimestamp?.let {
                    SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(it))
                } ?: "Never"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DashboardMetric(
                    label = "Last Checked",
                    value = lastCheckedFormatted
                )
                DashboardMetric(
                    label = "HTTP Status",
                    value = uiState.httpStatusCode?.toString() ?: "--"
                )
                DashboardMetric(
                    label = "In Stock",
                    value = "${uiState.totalAvailableCount} item(s)"
                )
            }

            if (!uiState.error.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22EF4444), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Notice",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.error,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ActionControlsRow(
    isRunning: Boolean,
    onStartTracker: () -> Unit,
    onStopTracker: () -> Unit,
    onTestAlert: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!isRunning) {
            Button(
                onClick = onStartTracker,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("start_tracker_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Tracker", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onStopTracker,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("stop_tracker_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Tracker", fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = onTestAlert,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .testTag("test_alert_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Test Alert", fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WideProductConfigurationCard(
    skus: String,
    onSkusChange: (String) -> Unit,
    postalCode: String,
    onPostalCodeChange: (String) -> Unit,
    storeFilter: String,
    onStoreFilterChange: (String) -> Unit,
    configSaved: Boolean,
    onSave: () -> Unit
) {
    var showCatalogPicker by remember { mutableStateOf(false) }
    var showManualSkuEditor by remember { mutableStateOf(false) }

    // Parse current active SKUs
    val currentSkuList = remember(skus) {
        skus.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    }

    // Catalog filtering state
    var selectedModelFilter by remember { mutableStateOf("All") }
    var selectedStorageFilter by remember { mutableStateOf("All") }
    var selectedColorFilter by remember { mutableStateOf("All") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("configuration_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Wide Alert Product Targets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${currentSkuList.size} product SKU(s) actively monitored",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (configSaved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Saved",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Quick Preset Bundles Section
            Text(
                text = "⚡ Quick Preset Bundles (One-Tap Wide Alert)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ProductCatalog.PRESET_GROUPS) { preset ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable {
                                onSkusChange(preset.skus.joinToString(", "))
                            }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = preset.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${preset.skus.size} models combined",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Currently Selected Products Chips
            Text(
                text = "Selected Products for Restock Alarm:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                currentSkuList.forEach { sku ->
                    val product = ProductCatalog.getProduct(sku)
                    val label = product?.name ?: sku

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            if (product != null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(product.colorHex)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        val remaining = currentSkuList.filter { it != sku }
                                        onSkusChange(remaining.joinToString(", "))
                                    }
                            )
                        }
                    }
                }
            }

            // Buttons to open Catalog Picker and Manual Editor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { showCatalogPicker = !showCatalogPicker },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (showCatalogPicker) Icons.Default.ExpandLess else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showCatalogPicker) "Hide Catalog" else "+ Add from Catalog",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = { showManualSkuEditor = !showManualSkuEditor },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (showManualSkuEditor) "Hide SKUs" else "Edit SKUs Raw",
                        fontSize = 12.sp
                    )
                }
            }

            // Expandable Catalog Browser & Multi-Selector
            AnimatedVisibility(visible = showCatalogPicker) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Browse Apple Canada Catalog",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        // Model Filter Row
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("All", "iPhone Duo", "iPhone 18 Pro", "iPhone 18 Pro Max", "iPhone Air", "iPhone 16")) { model ->
                                FilterChip(
                                    selected = selectedModelFilter == model,
                                    onClick = { selectedModelFilter = model },
                                    label = { Text(model, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Storage Filter Row
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("All", "256GB", "512GB", "1TB", "2TB", "128GB")) { storage ->
                                FilterChip(
                                    selected = selectedStorageFilter == storage,
                                    onClick = { selectedStorageFilter = storage },
                                    label = { Text(storage, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Color Filter Row
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("All", "Star White", "Silver", "Night Sky", "Black", "Burgundy", "Glacier", "Cloud White", "Light Gold", "Sky Blue")) { color ->
                                FilterChip(
                                    selected = selectedColorFilter == color,
                                    onClick = { selectedColorFilter = color },
                                    label = { Text(color, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Filtered catalog items
                        val filteredCatalog = remember(selectedModelFilter, selectedStorageFilter, selectedColorFilter) {
                            ProductCatalog.CATALOG.filter { item ->
                                (selectedModelFilter == "All" || item.model == selectedModelFilter) &&
                                (selectedStorageFilter == "All" || item.storage == selectedStorageFilter) &&
                                (selectedColorFilter == "All" || item.color.contains(selectedColorFilter, ignoreCase = true))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Found ${filteredCatalog.size} matching model(s)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (filteredCatalog.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        val combined = (currentSkuList + filteredCatalog.map { it.sku }).distinct()
                                        onSkusChange(combined.joinToString(", "))
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Select All Matches", fontSize = 11.sp)
                                }
                            }
                        }

                        // Individual Item Toggle List
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredCatalog.take(10).forEach { item ->
                                val isSelected = currentSkuList.contains(item.sku)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = CardDefaults.outlinedCardBorder(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = if (isSelected) {
                                                currentSkuList.filter { it != item.sku }
                                            } else {
                                                currentSkuList + item.sku
                                            }
                                            onSkusChange(updated.joinToString(", "))
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(item.colorHex)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "SKU: ${item.sku}",
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Add,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Raw SKU Input (Manual / Comma-Separated)
            AnimatedVisibility(visible = showManualSkuEditor) {
                OutlinedTextField(
                    value = skus,
                    onValueChange = onSkusChange,
                    label = { Text("Comma-Separated Apple Part Numbers") },
                    placeholder = { Text("e.g. MYDR3VC/A, MYDQ3VC/A, MYDX3VC/A") },
                    supportingText = { Text("Direct SKU batch input. Automatically syncs with product catalog.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skus_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = false,
                    maxLines = 4
                )
            }

            // Canadian Postal Code
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = postalCode,
                    onValueChange = onPostalCodeChange,
                    label = { Text("Canadian Postal Code") },
                    placeholder = { Text("e.g. V3T2W1") },
                    supportingText = { Text("Used to locate nearby Apple retail stores") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("postal_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Quick Postal Code Helper Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        "V3T2W1" to "Surrey",
                        "M5V2T6" to "Toronto",
                        "H3B2Y5" to "Montreal",
                        "T2P1J9" to "Calgary"
                    ).forEach { (code, city) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onPostalCodeChange(code) }
                        ) {
                            Text(
                                text = "$city ($code)",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Store Filter Input
            OutlinedTextField(
                value = storeFilter,
                onValueChange = onStoreFilterChange,
                label = { Text("Target Store Numbers (Optional)") },
                placeholder = { Text("e.g. R601, R488, R280") },
                supportingText = { Text("Leave blank to monitor all returned nearby stores") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store_filter_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Save Configuration Button
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_config_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Configuration", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StoreAvailabilityCard(
    result: StoreSkuAvailability,
    onCheckoutClick: () -> Unit
) {
    val isAvailable = result.isAvailable
    val badgeColor = if (isAvailable) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
    val badgeTextColor = if (isAvailable) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    val product = ProductCatalog.getProduct(result.sku)
    val displayName = result.productTitle.ifBlank { product?.name ?: result.sku }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store_item_${result.storeNumber}_${result.sku}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) Color(0x1810B981) else MaterialTheme.colorScheme.surface
        ),
        border = if (isAvailable) {
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF10B981)))
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Apple ${result.storeName} (${result.city}) • Store ${result.storeNumber}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isAvailable) "AVAILABLE TODAY" else "UNAVAILABLE",
                        color = badgeTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SKU and Pickup Quote
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SKU: ${result.sku}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (result.pickupQuote.isNotBlank()) {
                    Text(
                        text = result.pickupQuote,
                        fontSize = 13.sp,
                        fontWeight = if (isAvailable) FontWeight.Bold else FontWeight.Normal,
                        color = if (isAvailable) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onCheckoutClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("checkout_button_${result.storeNumber}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Checkout Bag Now",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyResultsCard(isRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Store,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isRunning) "Scanning inventory in background..." else "No polling data yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isRunning)
                    "Apple retail inventory results will populate here as store data arrives."
                else
                    "Click 'Start Tracker' above to begin continuous 60s background monitoring.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun PulseIndicator(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val color = if (isActive) Color(0xFF10B981) else Color(0xFF94A3B8)

    Box(
        modifier = Modifier
            .size(14.dp)
            .scale(if (isActive) scale else 1f)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun NotificationPermissionBanner(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x22F59E0B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = Color(0xFFD97706)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notification Permission",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Required for instant restock alarms when items become available.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
            ) {
                Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BatteryOptimizationBanner(onRequestExemption: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1F0284C7))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = Color(0xFF0284C7)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background Execution",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Exempt app from battery optimization to keep 60s polling alive while screen is off.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onRequestExemption,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Setup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun checkBatteryOptimization(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
}

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(fallback)
        } catch (_: Exception) {
            Toast.makeText(context, "Please allow unrestricted battery usage in App Settings.", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun startTrackerService(context: Context) {
    val intent = Intent(context, StockTrackerService::class.java).apply {
        action = StockTrackerService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
    Toast.makeText(context, "AppleStockPulse Tracker started", Toast.LENGTH_SHORT).show()
}

private fun stopTrackerService(context: Context) {
    val intent = Intent(context, StockTrackerService::class.java).apply {
        action = StockTrackerService.ACTION_STOP
    }
    context.startService(intent)
    Toast.makeText(context, "AppleStockPulse Tracker stopped", Toast.LENGTH_SHORT).show()
}

private fun triggerTestAlert(context: Context) {
    val intent = Intent(context, StockTrackerService::class.java).apply {
        action = StockTrackerService.ACTION_TRIGGER_TEST
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
    Toast.makeText(context, "Test alarm notification fired!", Toast.LENGTH_SHORT).show()
}

private fun openAppleBag(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apple.com/ca/shop/bag")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open browser: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
