package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.InventoryItem
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.KUMAR_WAREHOUSES
import com.example.ui.components.liquidSpringClickable
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.EmeraldOptimal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MoltenGold
import com.example.ui.theme.MoltenGoldGlow
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumElevated
import java.util.Locale

@Composable
fun InventoryScreen(
    items: List<InventoryItem>,
    selectedWarehouse: String,
    onSelectWarehouse: (String) -> Unit,
    onAddClick: () -> Unit,
    onEditItem: (InventoryItem) -> Unit,
    onAdjustStock: (InventoryItem, Double) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    val warehouses = listOf("All Warehouses") + KUMAR_WAREHOUSES
    val filtered = if (selectedWarehouse == "All Warehouses") {
        items
    } else {
        items.filter { it.warehouse.equals(selectedWarehouse, ignoreCase = true) }
    }

    val totalTonnes = filtered.sumOf { it.quantityMt }
    val totalCapacity = filtered.sumOf { it.capacityMt }.coerceAtLeast(1.0)
    val totalValueLakhs = filtered.sumOf { it.quantityMt * it.unitPriceInr } / 100_000.0
    val lowCount = filtered.count { it.belowThreshold }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("inventory_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Inventory & Warehouses",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Real-time stock sync & custom alert thresholds",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Button(
                    onClick = onAddClick,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_inventory_fab"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MoltenGold,
                        contentColor = ObsidianVoid
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Stock", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add SKU")
                }
            }
        }

        // Warehouse Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(warehouses) { wh ->
                    val selected = wh == selectedWarehouse
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectWarehouse(wh) },
                        label = { Text(wh) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Warehouse,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MoltenGold.copy(alpha = 0.22f),
                            selectedLabelColor = MoltenGoldGlow,
                            selectedLeadingIconColor = MoltenGoldGlow
                        )
                    )
                }
            }
        }

        // Multi-Warehouse Summary Strip
        item {
            GlassTelemetryCard(accentColor = TelemetryCyan) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryStat("Stock Volume", "${String.format(Locale.US, "%.1f", totalTonnes)} MT", MoltenGoldGlow)
                    SummaryStat("Est. Valuation", "₹${String.format(Locale.US, "%.1f", totalValueLakhs)}L", TelemetryCyan)
                    SummaryStat(
                        "Utilization",
                        "${((totalTonnes / totalCapacity) * 100).toInt()}%",
                        if (lowCount > 0) AmberWarning else EmeraldOptimal
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                GlassTelemetryCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warehouse,
                            contentDescription = "Empty Warehouse",
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No stock items in $selectedWarehouse",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tap 'Add SKU' above to log flour batches, raw wheat silos, or sync from Windows ERP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                InventoryItemCard(
                    item = item,
                    onAdjust = { delta -> onAdjustStock(item, delta) },
                    onEdit = { onEditItem(item) },
                    onDelete = { onDeleteItem(item.id) }
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryItem,
    onAdjust: (Double) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val animatedFill by animateFloatAsState(
        targetValue = item.fillFraction,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 200f),
        label = "stock_bar_${item.id}"
    )
    val statusColor = when {
        item.belowThreshold -> CrimsonCritical
        item.fillFraction < 0.35f -> AmberWarning
        else -> EmeraldOptimal
    }

    GlassTelemetryCard(
        accentColor = if (item.belowThreshold) CrimsonCritical else GlassBorder,
        modifier = Modifier
            .fillMaxWidth()
            .liquidSpringClickable(onClick = onEdit)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = item.sku,
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan
                        )
                        Text(
                            text = "• ${item.warehouse}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit ${item.name}",
                            tint = MoltenGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete ${item.name}",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Animated liquid progress bar with threshold marker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TitaniumElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFill.coerceIn(0.02f, 1f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(statusColor.copy(alpha = 0.75f), statusColor)
                            )
                        )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", item.quantityMt)} / ${String.format(Locale.US, "%.0f", item.capacityMt)} MT",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = statusColor
                    )
                    Text(
                        text = "Alert < ${String.format(Locale.US, "%.0f", item.lowThresholdMt)} MT • Batch ${item.batchCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                // Quick gesture/tap +/- 5 MT stock controls
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianVoid)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .liquidSpringClickable { onAdjust(-5.0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Decrease stock by 5 MT",
                            tint = AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MoltenGold.copy(alpha = 0.16f))
                            .border(1.dp, MoltenGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .liquidSpringClickable { onAdjust(5.0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Increase stock by 5 MT",
                            tint = MoltenGoldGlow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
