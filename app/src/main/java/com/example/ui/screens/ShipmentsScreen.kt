package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Shipment
import com.example.data.model.ShipmentStatus
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.theme.AmberWarning
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
fun ShipmentsScreen(
    shipments: List<Shipment>,
    onAddShipment: () -> Unit,
    onAdvanceStatus: (Shipment) -> Unit
) {
    val activeLoadMt = shipments
        .filter { it.statusEnum != ShipmentStatus.DELIVERED }
        .sumOf { it.weightMt }
    val deliveredCount = shipments.count { it.statusEnum == ShipmentStatus.DELIVERED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("shipments_list"),
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
                        text = "Dispatch & Shipments",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Live outbound atta fleet status & ETA pipeline",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Button(
                    onClick = onAddShipment,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_shipment_fab"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TelemetryCyan,
                        contentColor = ObsidianVoid
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Dispatch", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dispatch")
                }
            }
        }

        // Dispatch Telemetry Ribbon
        item {
            GlassTelemetryCard(accentColor = MoltenGold) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Active Fleet Load", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            "${String.format(Locale.US, "%.1f", activeLoadMt)} MT",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MoltenGoldGlow
                        )
                    }
                    Column {
                        Text("In Pipeline", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            "${shipments.size - deliveredCount} Trucks",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = TelemetryCyan
                        )
                    }
                    Column {
                        Text("Completed", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            "$deliveredCount Trips",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = EmeraldOptimal
                        )
                    }
                }
            }
        }

        if (shipments.isEmpty()) {
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
                            imageVector = Icons.Filled.LocalShipping,
                            contentDescription = "No Shipments",
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text("No outbound dispatches scheduled", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            "Tap 'Dispatch' above to assign a truck from any Kumar Foods warehouse.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(shipments, key = { it.id }) { shipment ->
                ShipmentPipelineCard(
                    shipment = shipment,
                    onAdvance = { onAdvanceStatus(shipment) }
                )
            }
        }
    }
}

@Composable
private fun ShipmentPipelineCard(
    shipment: Shipment,
    onAdvance: () -> Unit
) {
    val status = shipment.statusEnum
    val nextStatus = status.nextStatus()
    val accent = when (status) {
        ShipmentStatus.LOADING -> AmberWarning
        ShipmentStatus.IN_TRANSIT -> TelemetryCyan
        ShipmentStatus.OUT_FOR_DELIVERY -> MoltenGold
        ShipmentStatus.DELIVERED -> EmeraldOptimal
    }

    GlassTelemetryCard(accentColor = accent, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = shipment.trackingCode,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = shipment.truckNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = shipment.clientName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${shipment.warehouseOrigin} → ${shipment.destination}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", shipment.weightMt)} MT",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                }
            }

            // 4-step animated pipeline bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShipmentStatus.entries.forEachIndexed { idx, step ->
                    val reached = idx <= status.stepIndex
                    val dotColor by animateColorAsState(
                        targetValue = if (reached) accent else TitaniumElevated,
                        label = "step_dot_$idx"
                    )
                    Box(
                        modifier = Modifier
                            .size(if (idx == status.stepIndex) 14.dp else 10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    if (idx < ShipmentStatus.entries.lastIndex) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(if (idx < status.stepIndex) accent else GlassBorder)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "ETA",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = shipment.productName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${status.label} • ${shipment.etaText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                if (nextStatus != null) {
                    Button(
                        onClick = onAdvance,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("advance_shipment_${shipment.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent.copy(alpha = 0.2f),
                            contentColor = accent
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(
                            text = nextStatus.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Advance status",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Delivered",
                            tint = EmeraldOptimal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Completed",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldOptimal
                        )
                    }
                }
            }
        }
    }
}
