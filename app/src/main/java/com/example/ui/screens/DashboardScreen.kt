package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AlertSeverity
import com.example.data.model.CriticalAlert
import com.example.data.model.InventoryItem
import com.example.data.model.MillEquipment
import com.example.data.model.Shipment
import com.example.data.model.ShipmentStatus
import com.example.data.model.SupabaseBridgeState
import com.example.data.model.SyncTelemetry
import com.example.data.model.WorkspaceModule
import com.example.ui.components.AnimatedMetricText
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.LiquidSiloTankGauge
import com.example.ui.components.LiveSyncPulseChip
import com.example.ui.components.liquidSpringClickable
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.EmeraldOptimal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MoltenGold
import com.example.ui.theme.MoltenGoldGlow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumSurface
import java.util.Locale

@Composable
fun DashboardScreen(
    inventory: List<InventoryItem>,
    shipments: List<Shipment>,
    mills: List<MillEquipment>,
    alerts: List<CriticalAlert>,
    salesCount: Int,
    customersCount: Int,
    syncTelemetry: SyncTelemetry,
    isLoading: Boolean,
    isSeedingData: Boolean,
    onSeedStarterData: () -> Unit,
    onOpenSyncHub: () -> Unit,
    onForceSyncNow: () -> Unit,
    onOpenWorkspaceModule: (WorkspaceModule) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onEditInventory: (InventoryItem) -> Unit,
    onQuickAddInventory: () -> Unit
) {
    val totalStockMt = inventory.sumOf { it.quantityMt }
    val activeShipments = shipments.count { it.statusEnum != ShipmentStatus.DELIVERED }
    val totalOutputMtHr = mills.sumOf { it.outputRateMtHr }
    val avgHealth = if (mills.isEmpty()) 0.0 else mills.map { it.healthScore }.average()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Brand & Sync Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_kumar_logo),
                        contentDescription = "Kumar Foods Emblem",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, MoltenGold.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "KUMAR FOODS",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MoltenGoldGlow
                        )
                        Text(
                            text = "FY 01-04-2026 - 31-03-2027 • Pure Food. Total Control.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveSyncPulseChip(
                        label = when (syncTelemetry.bridgeState) {
                            SupabaseBridgeState.CONNECTED_LIVE -> "ERP ${syncTelemetry.latencyMs}ms"
                            SupabaseBridgeState.SYNCING -> "SYNCING"
                            SupabaseBridgeState.AWAITING_CREDENTIALS -> "PAIR ERP"
                            SupabaseBridgeState.ERROR -> "SYNC ERR"
                        },
                        isLive = syncTelemetry.bridgeState != SupabaseBridgeState.ERROR,
                        accentColor = when (syncTelemetry.bridgeState) {
                            SupabaseBridgeState.CONNECTED_LIVE -> EmeraldOptimal
                            SupabaseBridgeState.SYNCING -> TelemetryCyan
                            SupabaseBridgeState.AWAITING_CREDENTIALS -> AmberWarning
                            SupabaseBridgeState.ERROR -> CrimsonCritical
                        },
                        onClick = onOpenSyncHub,
                        modifier = Modifier.testTag("sync_status_chip")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onOpenSyncHub,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_sync_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Sync and Settings",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        // Office Records Live Sync Strip (Matches Screenshot #10)
        item {
            GlassTelemetryCard(
                accentColor = if (syncTelemetry.bridgeState == SupabaseBridgeState.CONNECTED_LIVE) EmeraldOptimal else TelemetryCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidSpringClickable(onClick = onOpenSyncHub)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Office records",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Sales $salesCount • Customers $customersCount • ${syncTelemetry.lastSyncLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onForceSyncNow,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TelemetryCyan.copy(alpha = 0.16f))
                            .testTag("quick_sync_office_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = "Sync Office Records Now",
                            tint = TelemetryCyan
                        )
                    }
                }
            }
        }

        // Your Workspace — All 9 Exact Kumar Foods ERP Modules (Matches Screenshots #9 & #10)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Workspace",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Live Office ERP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MoltenGoldGlow
                    )
                }

                val workspacePairs = listOf(
                    Triple(WorkspaceModule.ACCOUNTS, Icons.Filled.Groups, TelemetryCyan) to
                        Triple(WorkspaceModule.DAY_BOOK, Icons.Filled.Book, MoltenGold),
                    Triple(WorkspaceModule.LEDGER, Icons.Filled.ReceiptLong, EmeraldOptimal) to
                        Triple(WorkspaceModule.OUTSTANDINGS, Icons.Filled.AccountBalanceWallet, AmberWarning),
                    Triple(WorkspaceModule.SALES_REGISTER, Icons.Filled.PointOfSale, TelemetryCyan) to
                        Triple(WorkspaceModule.RECEIPT_PAYMENT, Icons.Filled.Payments, CrimsonCritical),
                    Triple(WorkspaceModule.PRODUCTION_REPORTS, Icons.Filled.Factory, MoltenGold) to
                        Triple(WorkspaceModule.CHOKAR_STOCK, Icons.Filled.Inventory2, EmeraldOptimal)
                )

                workspacePairs.forEach { (left, right) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WorkspaceTileCard(
                            module = left.first,
                            icon = left.second,
                            accent = left.third,
                            onClick = { onOpenWorkspaceModule(left.first) },
                            modifier = Modifier.weight(1f)
                        )
                        WorkspaceTileCard(
                            module = right.first,
                            icon = right.second,
                            accent = right.third,
                            onClick = { onOpenWorkspaceModule(right.first) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Full-width 9th module: Chokar Customer Sales (matches Screenshot #9 bottom wide card)
                WorkspaceTileCard(
                    module = WorkspaceModule.CHOKAR_SALES,
                    icon = Icons.Filled.BarChart,
                    accent = TelemetryCyan,
                    onClick = { onOpenWorkspaceModule(WorkspaceModule.CHOKAR_SALES) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Futuristic Hero Mill Banner Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(listOf(MoltenGold.copy(alpha = 0.55f), TelemetryCyan.copy(alpha = 0.45f))),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .liquidSpringClickable { onNavigateToTab(4) }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_mill_hero),
                    contentDescription = "Flour Processing Plant",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ObsidianVoid.copy(alpha = 0.92f),
                                    ObsidianVoid.copy(alpha = 0.62f),
                                    ObsidianVoid.copy(alpha = 0.25f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MoltenGold.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "PLANT LIVE TELEMETRY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MoltenGoldGlow
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "AI Forecast",
                                tint = TelemetryCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AI Ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryCyan
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Flour Milling Command",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${inventory.size} Stock SKUs • ${mills.size} Active Machines • ${alerts.size} Alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // First-launch empty state with 1-tap Kumar Foods Starter Plant Setup
        if (!isLoading && inventory.isEmpty() && shipments.isEmpty() && mills.isEmpty()) {
            item {
                GlassTelemetryCard(accentColor = MoltenGold) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Factory,
                            contentDescription = "Setup Plant",
                            tint = MoltenGold,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Initialize Kumar Foods Workspace",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Your cloud ERP is connected and ready. Load starter Chakki Fresh Atta silos, trucks & Buhler mill telemetry or add your own records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onSeedStarterData,
                                enabled = !isSeedingData,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("seed_starter_data_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MoltenGold,
                                    contentColor = ObsidianVoid
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isSeedingData) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = ObsidianVoid,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Load Mill Plant Data")
                                }
                            }
                            OutlinedButton(
                                onClick = onQuickAddInventory,
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add SKU")
                            }
                        }
                    }
                }
            }
        }

        // 2x2 Animated KPI Telemetry Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        title = "Total Warehouse Stock",
                        value = totalStockMt,
                        suffix = " MT",
                        decimals = 1,
                        icon = Icons.Filled.Inventory2,
                        accent = MoltenGold,
                        onClick = { onNavigateToTab(1) },
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Mill Throughput",
                        value = totalOutputMtHr,
                        suffix = " MT/h",
                        decimals = 1,
                        icon = Icons.Filled.Bolt,
                        accent = TelemetryCyan,
                        onClick = { onNavigateToTab(3) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        title = "Active Shipments",
                        value = activeShipments.toDouble(),
                        suffix = " Trucks",
                        decimals = 0,
                        icon = Icons.Filled.LocalShipping,
                        accent = EmeraldOptimal,
                        onClick = { onNavigateToTab(2) },
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Avg Mill Health",
                        value = avgHealth,
                        suffix = "%",
                        decimals = 0,
                        icon = Icons.Filled.Factory,
                        accent = if (avgHealth < 75.0 && mills.isNotEmpty()) AmberWarning else EmeraldOptimal,
                        onClick = { onNavigateToTab(3) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Animated Liquid Silo & Atta Bins Monitor
        if (inventory.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Liquid Silo & Atta Levels",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Swipe to explore",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(inventory, key = { it.id }) { item ->
                            LiquidSiloTankGauge(
                                fillFraction = item.fillFraction,
                                belowThreshold = item.belowThreshold,
                                label = item.name,
                                quantityLabel = "${String.format(Locale.US, "%.1f", item.quantityMt)} / ${String.format(Locale.US, "%.0f", item.capacityMt)} MT",
                                modifier = Modifier
                                    .width(156.dp)
                                    .liquidSpringClickable { onEditInventory(item) }
                            )
                        }
                    }
                }
            }
        }

        // Critical Low-Stock & Maintenance Alerts Feed
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Alerts",
                        tint = if (alerts.isNotEmpty()) CrimsonCritical else EmeraldOptimal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Critical Operations Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Text(
                    text = if (alerts.isEmpty()) "All Clear" else "${alerts.size} Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (alerts.isEmpty()) EmeraldOptimal else CrimsonCritical
                )
            }
        }

        if (alerts.isEmpty()) {
            item {
                GlassTelemetryCard(accentColor = EmeraldOptimal) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All warehouse thresholds & roller mills are within safe parameters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(alerts, key = { it.id }) { alert ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 4 }
                ) {
                    val accent = if (alert.severity == AlertSeverity.CRITICAL) CrimsonCritical else AmberWarning
                    GlassTelemetryCard(
                        accentColor = accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidSpringClickable {
                                onNavigateToTab(if (alert.sourceType == "INVENTORY") 1 else 3)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = alert.title,
                                    tint = accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = alert.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = alert.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: Double,
    suffix: String,
    decimals: Int,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassTelemetryCard(
        accentColor = accent,
        modifier = modifier.liquidSpringClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            AnimatedMetricText(
                targetValue = value,
                suffix = suffix,
                decimals = decimals,
                color = TextPrimary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WorkspaceTileCard(
    module: WorkspaceModule,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassTelemetryCard(
        accentColor = accent,
        modifier = modifier
            .liquidSpringClickable(onClick = onClick)
            .testTag("workspace_tile_${module.name.lowercase(Locale.US)}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = module.title,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = module.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = module.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
