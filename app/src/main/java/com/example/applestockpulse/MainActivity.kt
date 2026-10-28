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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppleStockPulseApp() {
    val context = LocalContext.current
    val preferences = remember { TrackerPreferences(context) }

    // Observe service state
    val isRunning by StockTrackerService.isRunning.collectAsStateWithLifecycle()
    val uiState by StockTrackerService.uiState.collectAsStateWithLifecycle()

    // Model selection state
    var selectedModel by remember { mutableStateOf(preferences.getSelectedModel()) }
    var selectedStorage by remember { mutableStateOf(preferences.getSelectedStorage()) }
    var selectedColor by remember { mutableStateOf(preferences.getSelectedColor()) }
    var postalCode by remember { mutableStateOf(preferences.getPostalCode()) }

    var showModelDropdown by remember { mutableStateOf(false) }
    var showPostalDialog by remember { mutableStateOf(false) }

    // Re-check armed state dynamically
    var armedVersion by remember { mutableStateOf(0) }

    // Current device model definition
    val currentDeviceModel = remember(selectedModel) {
        ProductCatalog.getDeviceModel(selectedModel)
    }

    // Auto trigger initial poll if results are empty on first launch
    LaunchedEffect(Unit) {
        if (uiState.storeCards.isEmpty()) {
            val forceIntent = Intent(context, StockTrackerService::class.java).apply {
                action = StockTrackerService.ACTION_FORCE_POLL
            }
            context.startService(forceIntent)
        }
    }

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
                "Notification permission is needed for instant stock alarms!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Battery Optimization State
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(checkBatteryOptimization(context))
    }

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
                        onClick = { openAppleBag(context) },
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        onRequestExemption = { requestBatteryExemption(context) }
                    )
                }
            }

            // Results Header & Device Model Selector (styled like iphonestockchecker.com)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Country & Region header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RESULTS • 🇨🇦 CANADA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Postal Code Button / Chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { showPostalDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Near $postalCode",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Postal Code",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Big Device Model Selector with Dropdown
                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showModelDropdown = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = selectedModel,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = currentDeviceModel.subtitle,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Choose Device Model",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showModelDropdown,
                                onDismissRequest = { showModelDropdown = false }
                            ) {
                                ProductCatalog.DEVICE_MODELS.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = model.name,
                                                    fontWeight = if (model.name == selectedModel) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = model.subtitle,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedModel = model.name
                                            selectedStorage = "All"
                                            selectedColor = "All"
                                            preferences.saveSelection(model.name, "All", "All", postalCode)
                                            showModelDropdown = false
                                            // Force immediate poll with the newly selected device model
                                            val forceIntent = Intent(context, StockTrackerService::class.java).apply {
                                                action = StockTrackerService.ACTION_FORCE_POLL
                                            }
                                            context.startService(forceIntent)
                                        }
                                    )
                                }
                            }
                        }

                        // Specs Filter Row (Storage & Color)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Filter Matrix Specs:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Storage Filters
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedStorage == "All",
                                        onClick = {
                                            selectedStorage = "All"
                                            preferences.setSelectedStorage("All")
                                        },
                                        label = { Text("All Storages", fontSize = 11.sp) }
                                    )
                                }
                                items(currentDeviceModel.storages) { storage ->
                                    FilterChip(
                                        selected = selectedStorage == storage,
                                        onClick = {
                                            selectedStorage = storage
                                            preferences.setSelectedStorage(storage)
                                        },
                                        label = { Text(storage, fontSize = 11.sp) }
                                    )
                                }
                            }

                            // Color Filters
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedColor == "All",
                                        onClick = {
                                            selectedColor = "All"
                                            preferences.setSelectedColor("All")
                                        },
                                        label = { Text("All Colors", fontSize = 11.sp) }
                                    )
                                }
                                items(currentDeviceModel.colors) { color ->
                                    FilterChip(
                                        selected = selectedColor == color,
                                        onClick = {
                                            selectedColor = color
                                            preferences.setSelectedColor(color)
                                        },
                                        label = { Text(color, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Control Buttons Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isRunning) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        startTrackerService(context)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("start_tracker_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start 60s Monitoring", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = { stopTrackerService(context) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("stop_tracker_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                ) {
                                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stop Monitoring", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    val forceIntent = Intent(context, StockTrackerService::class.java).apply {
                                        action = StockTrackerService.ACTION_FORCE_POLL
                                    }
                                    context.startService(forceIntent)
                                    Toast.makeText(context, "Scanning Apple inventory...", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(46.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Check Now", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check Now", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { triggerTestAlert(context) },
                                modifier = Modifier.height(46.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Status Notice Banner
            item {
                val lastCheckedFormatted = remember(uiState.lastCheckedTimestamp) {
                    uiState.lastCheckedTimestamp?.let {
                        SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(it))
                    } ?: "Just now"
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (uiState.totalAvailableCount > 0) Color(0x1F10B981) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (uiState.totalAvailableCount > 0) {
                        CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF10B981)))
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (uiState.totalAvailableCount > 0)
                                    "🚨 ${uiState.totalAvailableCount} $selectedModel in stock right now!"
                                else
                                    "Nothing in stock nearby right now",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (uiState.totalAvailableCount > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap any [Notify] pill below to get an instant alarm when that exact spec lands.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = lastCheckedFormatted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Store Grid Cards (matching the screenshot!)
            if (uiState.storeCards.isEmpty()) {
                item {
                    EmptyStoreResultsCard(
                        isRunning = isRunning,
                        onScanNow = {
                            val forceIntent = Intent(context, StockTrackerService::class.java).apply {
                                action = StockTrackerService.ACTION_FORCE_POLL
                            }
                            context.startService(forceIntent)
                        }
                    )
                }
            } else {
                items(
                    items = uiState.storeCards,
                    key = { it.storeNumber }
                ) { store ->
                    StoreMatrixCard(
                        store = store,
                        currentDeviceModel = currentDeviceModel,
                        storageFilter = selectedStorage,
                        colorFilter = selectedColor,
                        armedVersion = armedVersion,
                        isSkuArmed = { sku -> preferences.isSkuArmed(sku) },
                        onToggleArmed = { sku ->
                            preferences.toggleArmedNotificationSku(sku)
                            armedVersion++
                            Toast.makeText(
                                context,
                                if (preferences.isSkuArmed(sku)) "Alarm armed for $sku!" else "Alarm disarmed for $sku",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onOpenBag = { openAppleBag(context) }
                    )
                }
            }
        }
    }

    // Postal Code Dialog
    if (showPostalDialog) {
        var tempPostal by remember { mutableStateOf(postalCode) }

        AlertDialog(
            onDismissRequest = { showPostalDialog = false },
            title = { Text("Search Location (Canada)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your Canadian Postal Code to find the nearest Apple Retail Stores:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = tempPostal,
                        onValueChange = { tempPostal = it.uppercase() },
                        label = { Text("Postal Code") },
                        placeholder = { Text("e.g. V6C1T2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Quick Cities:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "V6C1T2" to "Vancouver",
                            "V3T2W1" to "Surrey",
                            "M5V2T6" to "Toronto",
                            "H3B2Y5" to "Montreal",
                            "T2P1J9" to "Calgary"
                        ).forEach { (code, city) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { tempPostal = code }
                            ) {
                                Text(
                                    text = "$city ($code)",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        postalCode = tempPostal.trim()
                        preferences.setPostalCode(postalCode)
                        showPostalDialog = false
                        val forceIntent = Intent(context, StockTrackerService::class.java).apply {
                            action = StockTrackerService.ACTION_FORCE_POLL
                        }
                        context.startService(forceIntent)
                    }
                ) {
                    Text("Update Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StoreMatrixCard(
    store: StoreInventoryCard,
    currentDeviceModel: DeviceModel,
    storageFilter: String,
    colorFilter: String,
    armedVersion: Int,
    isSkuArmed: (String) -> Boolean,
    onToggleArmed: (String) -> Unit,
    onOpenBag: () -> Unit
) {
    val storagesToDisplay = remember(currentDeviceModel, storageFilter) {
        if (storageFilter == "All") currentDeviceModel.storages else listOf(storageFilter)
    }

    val colorsToDisplay = remember(currentDeviceModel, colorFilter) {
        if (colorFilter == "All") currentDeviceModel.colors else listOf(colorFilter)
    }

    val hasStock = store.totalAvailableInStore > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store_card_${store.storeNumber}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (hasStock) {
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF10B981)))
        } else {
            CardDefaults.outlinedCardBorder()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Store Title & Distance / Stock Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = store.storeName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (store.addressText.isNotBlank()) {
                        Text(
                            text = store.addressText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 15.sp
                        )
                    }
                    if (store.phoneText.isNotBlank()) {
                        Text(
                            text = store.phoneText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (store.distanceText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = store.distanceText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasStock) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (hasStock) "${store.totalAvailableInStore} Available" else "None available",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasStock) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Restock Banner Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasStock)
                            "🚨 Stock detected! Tap below to open Apple checkout bag."
                        else
                            "↺ Checked live · Tap [Notify] on any spec to set instant restock alarm",
                        fontSize = 11.sp,
                        color = if (hasStock) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // SPEC MATRIX TABLE (matching iphonestockchecker.com)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Table Columns Header (Storages)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left spacer for Color column
                    Spacer(modifier = Modifier.width(90.dp))

                    storagesToDisplay.forEach { storage ->
                        Text(
                            text = storage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Table Rows for each Color
                colorsToDisplay.forEach { color ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color Label Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (color.contains("White", ignoreCase = true) || color.contains("Silver", ignoreCase = true)) {
                                Color(0xFFF1F5F9)
                            } else {
                                Color(0xFF1E293B)
                            },
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.width(90.dp)
                        ) {
                            Text(
                                text = color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (color.contains("White", ignoreCase = true) || color.contains("Silver", ignoreCase = true)) {
                                    Color(0xFF0F172A)
                                } else {
                                    Color.White
                                },
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                            )
                        }

                        // Storage Cells for this Color
                        storagesToDisplay.forEach { storage ->
                            val sku = ProductCatalog.getSku(currentDeviceModel.name, storage, color)
                            val variant = store.variants.find { it.sku.equals(sku, ignoreCase = true) }
                            val isAvailable = variant?.isAvailable == true
                            val isArmed = if (sku != null) isSkuArmed(sku) else false

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isAvailable) {
                                    // Available in stock -> Green checkout button!
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF10B981),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenBag() }
                                    ) {
                                        Text(
                                            text = "Pickup!",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                } else {
                                    // Unavailable -> Notify toggle pill
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isArmed) Color(0x33F59E0B) else Color(0xFFF1F5F9),
                                        border = if (isArmed) {
                                            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFF59E0B)))
                                        } else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (sku != null) {
                                                    onToggleArmed(sku)
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isArmed) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                                contentDescription = null,
                                                tint = if (isArmed) Color(0xFFD97706) else Color(0xFF64748B),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = if (isArmed) "Armed" else "Notify",
                                                fontSize = 10.sp,
                                                fontWeight = if (isArmed) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isArmed) Color(0xFFD97706) else Color(0xFF475569)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStoreResultsCard(
    isRunning: Boolean,
    onScanNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Connecting to Apple Canada...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Querying live store inventory and pickup availability for nearby Apple Stores.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onScanNow,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Inventory Now")
            }
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
                    text = "Notification Permission Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Allow notifications so AppleStockPulse can trigger an alarm when stock lands.",
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
                    text = "Allow app to run in background so 60s inventory scanning stays active while screen is locked.",
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
