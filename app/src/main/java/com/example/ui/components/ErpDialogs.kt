package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.InventoryCategory
import com.example.data.model.InventoryItem
import com.example.data.model.MillEquipment
import com.example.data.model.MillMachineType
import com.example.data.model.MillStatus
import com.example.data.model.Shipment
import com.example.data.model.ShipmentStatus
import com.example.data.model.SupabaseBridgeState
import com.example.data.model.SyncTelemetry
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.EmeraldOptimal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MoltenGold
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumSurface
import java.util.Locale

val KUMAR_WAREHOUSES = listOf(
    "Main Mill Plant",
    "North Dispatch Hub",
    "Central Silo Yard",
    "East Dry Depot"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InventoryEditorSheet(
    existingItem: InventoryItem?,
    onDismiss: () -> Unit,
    onSave: (InventoryItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(existingItem?.name ?: "") }
    var sku by remember { mutableStateOf(existingItem?.sku ?: "KF-ATT-${(100..999).random()}") }
    var selectedCategory by remember {
        mutableStateOf(existingItem?.categoryEnum ?: InventoryCategory.FINISHED_ATTA)
    }
    var selectedWarehouse by remember {
        mutableStateOf(existingItem?.warehouse ?: KUMAR_WAREHOUSES.first())
    }
    var qtyText by remember { mutableStateOf(existingItem?.quantityMt?.toString() ?: "45.0") }
    var capText by remember { mutableStateOf(existingItem?.capacityMt?.toString() ?: "100.0") }
    var priceText by remember { mutableStateOf(existingItem?.unitPriceInr?.toString() ?: "35000") }
    var batchCode by remember { mutableStateOf(existingItem?.batchCode ?: "KF-2026-B1") }
    val capacityVal = capText.toFloatOrNull()?.coerceIn(10f, 1000f) ?: 100f
    var thresholdSlider by remember {
        mutableFloatStateOf((existingItem?.lowThresholdMt?.toFloat() ?: 20f).coerceIn(0f, capacityVal))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existingItem == null) "Add Flour / Grain Stock" else "Edit Stock & Alert Threshold",
                style = MaterialTheme.typography.headlineSmall,
                color = MoltenGold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Product Name (e.g. Chakki Fresh Atta)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inventory_name_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("ERP SKU") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = batchCode,
                    onValueChange = { batchCode = it },
                    label = { Text("Batch Code") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Category", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InventoryCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MoltenGold.copy(alpha = 0.22f),
                            selectedLabelColor = MoltenGold
                        )
                    )
                }
            }

            Text("Warehouse Location", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KUMAR_WAREHOUSES.forEach { wh ->
                    FilterChip(
                        selected = selectedWarehouse == wh,
                        onClick = { selectedWarehouse = wh },
                        label = { Text(wh) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                            selectedLabelColor = TelemetryCyan
                        )
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Stock (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = capText,
                    onValueChange = { capText = it },
                    label = { Text("Capacity (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("₹ / MT") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TitaniumSurface)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Custom Low-Stock Alert Threshold",
                        style = MaterialTheme.typography.labelLarge,
                        color = AmberWarning
                    )
                    Text(
                        "${String.format(Locale.US, "%.0f", thresholdSlider)} MT",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
                Slider(
                    value = thresholdSlider.coerceIn(0f, capacityVal),
                    onValueChange = { thresholdSlider = it },
                    valueRange = 0f..capacityVal,
                    colors = SliderDefaults.colors(
                        thumbColor = AmberWarning,
                        activeTrackColor = AmberWarning
                    )
                )
            }

            Button(
                onClick = {
                    val qty = qtyText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val cap = capText.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0
                    val price = priceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 34000.0
                    onSave(
                        InventoryItem(
                            id = existingItem?.id ?: "",
                            sku = sku.trim().ifBlank { "KF-SKU" },
                            name = name.trim().ifBlank { "Whole Wheat Atta" },
                            category = selectedCategory.name,
                            warehouse = selectedWarehouse,
                            quantityMt = qty,
                            capacityMt = cap,
                            lowThresholdMt = thresholdSlider.toDouble().coerceIn(0.0, cap),
                            unitPriceInr = price,
                            batchCode = batchCode.trim().ifBlank { "KF-BATCH" },
                            syncSource = existingItem?.syncSource ?: "FIREBASE"
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_inventory_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoltenGold,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (existingItem == null) "Save & Sync Stock" else "Update Stock & Threshold",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddShipmentSheet(
    onDismiss: () -> Unit,
    onCreate: (Shipment) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var clientName by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("Chakki Fresh Whole Wheat Atta 10kg") }
    var weightText by remember { mutableStateOf("18.0") }
    var truckNumber by remember { mutableStateOf("UP-32-KF-4820") }
    var etaText by remember { mutableStateOf("Today, 7:30 PM") }
    var origin by remember { mutableStateOf(KUMAR_WAREHOUSES.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Dispatch New Flour Shipment",
                style = MaterialTheme.typography.headlineSmall,
                color = TelemetryCyan
            )
            OutlinedTextField(
                value = clientName,
                onValueChange = { clientName = it },
                label = { Text("Distributor / Client Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shipment_client_input")
            )
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("Destination City / Mandi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = productName,
                onValueChange = { productName = it },
                label = { Text("Atta Product & Pack Size") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Load (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = truckNumber,
                    onValueChange = { truckNumber = it },
                    label = { Text("Truck Reg No.") },
                    singleLine = true,
                    modifier = Modifier.weight(1.3f)
                )
            }
            OutlinedTextField(
                value = etaText,
                onValueChange = { etaText = it },
                label = { Text("Estimated Arrival (ETA)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Dispatch Warehouse", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KUMAR_WAREHOUSES.forEach { wh ->
                    FilterChip(
                        selected = origin == wh,
                        onClick = { origin = wh },
                        label = { Text(wh) }
                    )
                }
            }
            Button(
                onClick = {
                    onCreate(
                        Shipment(
                            trackingCode = "KF-SHP-${(1000..9999).random()}",
                            clientName = clientName.trim(),
                            destination = destination.trim().ifBlank { "Regional Hub" },
                            productName = productName.trim().ifBlank { "Whole Wheat Atta" },
                            weightMt = weightText.toDoubleOrNull()?.coerceIn(0.5, 500.0) ?: 15.0,
                            truckNumber = truckNumber.trim().ifBlank { "UP-14-KF-2026" },
                            status = ShipmentStatus.LOADING.name,
                            etaText = etaText.trim().ifBlank { "Today" },
                            warehouseOrigin = origin
                        )
                    )
                },
                enabled = clientName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("create_shipment_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TelemetryCyan,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Confirm Dispatch & Sync", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMillEquipmentSheet(
    onDismiss: () -> Unit,
    onCreate: (MillEquipment) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("Grinding Floor A") }
    var type by remember { mutableStateOf(MillMachineType.ROLLER_MILL) }
    var tempText by remember { mutableStateOf("52.5") }
    var vibText by remember { mutableStateOf("1.9") }
    var hoursText by remember { mutableStateOf("2450") }
    var outputText by remember { mutableStateOf("6.5") }
    var nextService by remember { mutableStateOf("2026-12-15") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Register Milling Equipment",
                style = MaterialTheme.typography.headlineSmall,
                color = MoltenGold
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Machine Name (e.g. Buhler RM-02)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mill_name_input")
            )
            OutlinedTextField(
                value = zone,
                onValueChange = { zone = it },
                label = { Text("Plant Floor / Zone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MillMachineType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(t.label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = tempText,
                    onValueChange = { tempText = it },
                    label = { Text("Temp °C") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = vibText,
                    onValueChange = { vibText = it },
                    label = { Text("Vib mm/s") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text("Run Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = outputText,
                    onValueChange = { outputText = it },
                    label = { Text("MT / hr") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = nextService,
                onValueChange = { nextService = it },
                label = { Text("Next Scheduled Service (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val temp = tempText.toDoubleOrNull() ?: 50.0
                    val vib = vibText.toDoubleOrNull() ?: 1.8
                    val score = when {
                        temp > 72 || vib > 4.5 -> 58
                        temp > 62 || vib > 3.2 -> 75
                        else -> 92
                    }
                    val status = when {
                        score < 65 -> MillStatus.CRITICAL
                        score < 80 -> MillStatus.WARNING
                        else -> MillStatus.OPTIMAL
                    }
                    onCreate(
                        MillEquipment(
                            machineName = name.trim(),
                            machineType = type.name,
                            zone = zone.trim().ifBlank { "Grinding Floor" },
                            healthScore = score,
                            temperatureC = temp,
                            vibrationMmS = vib,
                            runningHours = hoursText.toDoubleOrNull() ?: 1500.0,
                            outputRateMtHr = outputText.toDoubleOrNull() ?: 5.5,
                            status = status.name,
                            nextServiceDate = nextService.trim().ifBlank { "2026-12-15" }
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_mill_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoltenGold,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Add Equipment Telemetry", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncControlHubSheet(
    userEmail: String,
    syncTelemetry: SyncTelemetry,
    auditState: com.example.data.model.OfficeSnapshotAuditState,
    connectionConfig: com.example.data.model.SupabaseConnectionConfig,
    alertsEnabled: Boolean,
    hasNotificationPermission: Boolean,
    onSaveSupabaseConnection: (url: String, key: String, pairCode: String, autoSync: Boolean, bucket: String) -> Unit,
    onDisconnectOffice: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onOpenTableMapper: () -> Unit,
    onToggleAlerts: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onForceSyncSupabase: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var supabaseUrl by remember(connectionConfig.supabaseUrl) { mutableStateOf(connectionConfig.supabaseUrl) }
    var supabaseKey by remember(connectionConfig.supabaseAnonKey) { mutableStateOf(connectionConfig.supabaseAnonKey) }
    var pairCode by remember(connectionConfig.mobilePairCode) { mutableStateOf(connectionConfig.mobilePairCode) }
    var pdfBucket by remember(connectionConfig.pdfStorageBucket) { mutableStateOf(connectionConfig.pdfStorageBucket) }
    var autoSync by remember(connectionConfig.autoSyncEnabled) { mutableStateOf(connectionConfig.autoSyncEnabled) }
    var showKeyPlain by remember { mutableStateOf(false) }
    var showPairPlain by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CloudSync,
                    contentDescription = "Settings & Supabase Pairing",
                    tint = TelemetryCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Kumar Foods ERP Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = userEmail.ifBlank { "Kumar Foods Processing Company • Mobile Bridge v3.5" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Verified Windows ERP Snapshot Audit Card
            GlassTelemetryCard(
                accentColor = when {
                    auditState.syncDriftWarning.isNotBlank() -> CrimsonCritical
                    auditState.isAuditPassed -> EmeraldOptimal
                    else -> AmberWarning
                }
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Snapshot Verification",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = if (auditState.isAuditPassed) "PASS • VERIFIED" else "NOT PAIRED",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (auditState.isAuditPassed) EmeraldOptimal else AmberWarning
                        )
                    }
                    if (auditState.isAuditPassed) {
                        Text(
                            text = "Office snapshot created: ${auditState.snapshotGeneratedAt.ifBlank { "-" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "Cloud upload received: ${com.example.data.remote.WindowsMirrorBridge.formatIsoToLocalLabel(auditState.cloudUploadedAt).ifBlank { "-" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "Original Windows PDF pointers: ${auditState.pdfPointerCount}/${auditState.totalSalesCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan
                        )
                    } else {
                        Text(
                            text = "Paste your Supabase URL, Publishable Key & 32-char Mobile Pairing Code from Windows ERP -> Settings once. Live sync then runs automatically every 20s and whenever you reopen the app.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    if (auditState.syncDriftWarning.isNotBlank()) {
                        Text(
                            text = auditState.syncDriftWarning,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = CrimsonCritical
                        )
                    }
                }
            }

            // Supabase Office ERP Pairing Card
            GlassTelemetryCard(
                accentColor = when (syncTelemetry.bridgeState) {
                    SupabaseBridgeState.CONNECTED_LIVE -> EmeraldOptimal
                    SupabaseBridgeState.SYNCING -> TelemetryCyan
                    SupabaseBridgeState.AWAITING_CREDENTIALS -> AmberWarning
                    SupabaseBridgeState.ERROR -> CrimsonCritical
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Office Supabase Connection",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = when (syncTelemetry.bridgeState) {
                                SupabaseBridgeState.CONNECTED_LIVE -> "OFFICE LIVE • ${syncTelemetry.latencyMs}ms"
                                SupabaseBridgeState.SYNCING -> "SYNCING..."
                                SupabaseBridgeState.AWAITING_CREDENTIALS -> "ENTER KEYS"
                                SupabaseBridgeState.ERROR -> "CHECK SYNC"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when (syncTelemetry.bridgeState) {
                                SupabaseBridgeState.CONNECTED_LIVE -> EmeraldOptimal
                                SupabaseBridgeState.SYNCING -> TelemetryCyan
                                SupabaseBridgeState.AWAITING_CREDENTIALS -> AmberWarning
                                SupabaseBridgeState.ERROR -> CrimsonCritical
                            }
                        )
                    }
                    Text(
                        text = syncTelemetry.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = supabaseUrl,
                        onValueChange = { supabaseUrl = it },
                        label = { Text("Supabase Project URL") },
                        placeholder = { Text("https://xxxx.supabase.co") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("supabase_url_input")
                    )

                    OutlinedTextField(
                        value = supabaseKey,
                        onValueChange = { supabaseKey = it },
                        label = { Text("Supabase Publishable / Anon Key") },
                        placeholder = { Text("sb_publishable_... or eyJhbGci...") },
                        singleLine = true,
                        visualTransformation = if (showKeyPlain) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            Text(
                                text = if (showKeyPlain) "HIDE" else "SHOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryCyan,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showKeyPlain = !showKeyPlain }
                                    .padding(4.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("supabase_key_input")
                    )

                    OutlinedTextField(
                        value = pairCode,
                        onValueChange = { pairCode = it },
                        label = { Text("Mobile Pairing Code (32+ chars from Windows ERP)") },
                        placeholder = { Text("Paste 32+ char code from Windows ERP Settings") },
                        singleLine = true,
                        visualTransformation = if (showPairPlain) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            Text(
                                text = if (showPairPlain) "HIDE" else "SHOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MoltenGold,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showPairPlain = !showPairPlain }
                                    .padding(4.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("supabase_pair_code_input")
                    )

                    OutlinedTextField(
                        value = pdfBucket,
                        onValueChange = { pdfBucket = it },
                        label = { Text("Fallback Storage Bucket (Optional)") },
                        placeholder = { Text("invoices") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic Live Sync",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Auto-syncs every 20s + whenever app opens",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = autoSync,
                            onCheckedChange = {
                                autoSync = it
                                onToggleAutoSync(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianVoid,
                                checkedTrackColor = EmeraldOptimal
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianVoid)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = "Synced Sources",
                            tint = MoltenGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (syncTelemetry.discoveredTables.isNotEmpty()) {
                                "Active Bridge: ${syncTelemetry.discoveredTables.take(4).joinToString(" • ")}"
                            } else {
                                "Uses secure RPCs: erp_mobile_pull & erp_mobile_invoice_pull"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = {
                            onSaveSupabaseConnection(supabaseUrl, supabaseKey, pairCode, autoSync, pdfBucket)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pair_save_supabase_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MoltenGold,
                            contentColor = ObsidianVoid
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pair Office ERP & Auto-Sync")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onForceSyncSupabase,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("force_supabase_sync_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Sync Now", color = TelemetryCyan)
                        }
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenTableMapper()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Map Tables", color = MoltenGold)
                        }
                    }

                    if (connectionConfig.hasEndpoint || auditState.isAuditPassed) {
                        OutlinedButton(
                            onClick = onDisconnectOffice,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("disconnect_office_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonCritical)
                        ) {
                            Text("Disconnect & Erase Offline Office Data")
                        }
                    }
                }
            }

            // Push Alerts Configuration
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(TitaniumSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Critical Alerts",
                        tint = AmberWarning,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Critical Stock, Mill & New Sale Alerts",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            if (hasNotificationPermission) "Android system notifications active"
                            else "Tap to grant Android notification permission",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = alertsEnabled && hasNotificationPermission,
                    onCheckedChange = { checked ->
                        if (checked && !hasNotificationPermission) {
                            onRequestNotificationPermission()
                        }
                        onToggleAlerts(checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianVoid,
                        checkedTrackColor = MoltenGold
                    )
                )
            }

            HorizontalDivider(color = GlassBorder)

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonCritical)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
            }
        }
    }
}
