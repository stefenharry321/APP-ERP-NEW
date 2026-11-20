package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AiAnalyticsReport
import com.example.data.model.MillEquipment
import com.example.data.model.MillStatus
import com.example.ui.components.AnimatedHealthRing
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.LiquidForecastChart
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.CrimsonDeep
import com.example.ui.theme.EmeraldOptimal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MoltenGold
import com.example.ui.theme.MoltenGoldGlow
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumElevated
import com.example.ui.viewmodel.AiTaskState
import java.util.Locale

@Composable
fun AiMaintenanceScreen(
    mills: List<MillEquipment>,
    reports: List<AiAnalyticsReport>,
    forecastState: AiTaskState,
    streamingReportState: AiTaskState,
    diagnosingMillId: String?,
    onRunSalesForecast: () -> Unit,
    onRunExecutiveReport: () -> Unit,
    onAddMill: () -> Unit,
    onDiagnoseMill: (MillEquipment) -> Unit,
    onScheduleMaintenance: (MillEquipment) -> Unit
) {
    val latestForecast = reports.firstOrNull { it.reportType == "SALES_FORECAST" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_maintenance_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        item {
            Column {
                Text(
                    text = "AI Forecasting & Mill Health",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "Predictive sales curves, automated reporting & milling diagnostics",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // AI Sales Trend Forecast Card
        item {
            GlassTelemetryCard(accentColor = MoltenGold) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header artwork
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(106.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_ai_forecast_bg),
                            contentDescription = "AI Demand Forecast Visualization",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            ObsidianVoid.copy(alpha = 0.25f),
                                            ObsidianVoid.copy(alpha = 0.92f)
                                        )
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = MoltenGoldGlow,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AI SALES TREND ENGINE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MoltenGoldGlow
                                    )
                                }
                                Text(
                                    text = latestForecast?.title ?: "7-Day Atta Demand Projection",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }

                            if (latestForecast != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(EmeraldOptimal.copy(alpha = 0.2f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    val prefix = if (latestForecast.predictedGrowthPct >= 0) "+" else ""
                                    Text(
                                        text = "$prefix${String.format(Locale.US, "%.1f", latestForecast.predictedGrowthPct)}%",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = EmeraldOptimal
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LiquidForecastChart(
                            labels = latestForecast?.forecastLabels.orEmpty(),
                            values = latestForecast?.forecastValues.orEmpty()
                        )

                        if (latestForecast != null) {
                            Text(
                                text = latestForecast.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "Recommended Wheat / Atta Restock: ${String.format(Locale.US, "%.1f", latestForecast.recommendedRestockMt)} MT",
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryCyan
                            )
                        } else {
                            Text(
                                text = "Run AI Sales Forecasting to analyze live Kumar Foods stock velocity and predict 7-day flour demand.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        if (forecastState is AiTaskState.Error) {
                            ErrorPill(forecastState.message)
                        }

                        Button(
                            onClick = onRunSalesForecast,
                            enabled = forecastState !is AiTaskState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("run_ai_forecast_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MoltenGold,
                                contentColor = ObsidianVoid
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (forecastState is AiTaskState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = ObsidianVoid,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Predicting Demand Curve...")
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Live AI Sales Forecast")
                            }
                        }
                    }
                }
            }
        }

        // Automated Streaming Executive Supply Chain Report
        item {
            GlassTelemetryCard(accentColor = TelemetryCyan) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = "Automated Report",
                                tint = TelemetryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Automated Supply Chain Report",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    when (streamingReportState) {
                        is AiTaskState.Idle -> {
                            val savedReport = reports.firstOrNull { it.reportType == "SUPPLY_CHAIN_REPORT" }
                            Text(
                                text = savedReport?.summary
                                    ?: "Generate a streaming executive report covering warehouse imbalances, dispatch velocity, and milling efficiency.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        is AiTaskState.Loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = TelemetryCyan,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Streaming real-time operations report...", style = MaterialTheme.typography.bodySmall, color = TelemetryCyan)
                            }
                        }
                        is AiTaskState.Streaming -> {
                            Text(
                                text = streamingReportState.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        is AiTaskState.Success -> {
                            Text(
                                text = streamingReportState.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        is AiTaskState.Error -> {
                            ErrorPill(streamingReportState.message)
                        }
                    }

                    OutlinedButton(
                        onClick = onRunExecutiveReport,
                        enabled = streamingReportState !is AiTaskState.Loading && streamingReportState !is AiTaskState.Streaming,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("stream_supply_report_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stream Executive Supply Chain Report", color = TelemetryCyan)
                    }
                }
            }
        }

        // Predictive Maintenance Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PrecisionManufacturing,
                        contentDescription = "Milling Equipment",
                        tint = MoltenGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Predictive Mill Maintenance",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                OutlinedButton(
                    onClick = onAddMill,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_mill_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Mill")
                }
            }
        }

        if (mills.isEmpty()) {
            item {
                GlassTelemetryCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No milling equipment registered yet.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        } else {
            items(mills, key = { it.id }) { mill ->
                MillTelemetryCard(
                    mill = mill,
                    isDiagnosing = diagnosingMillId == mill.id,
                    onDiagnose = { onDiagnoseMill(mill) },
                    onSchedule = { onScheduleMaintenance(mill) }
                )
            }
        }
    }
}

@Composable
private fun MillTelemetryCard(
    mill: MillEquipment,
    isDiagnosing: Boolean,
    onDiagnose: () -> Unit,
    onSchedule: () -> Unit
) {
    val accent = when (mill.statusEnum) {
        MillStatus.OPTIMAL -> EmeraldOptimal
        MillStatus.WARNING -> AmberWarning
        MillStatus.CRITICAL -> CrimsonCritical
        MillStatus.MAINTENANCE -> TelemetryCyan
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedHealthRing(score = mill.healthScore)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = mill.machineName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${mill.machineTypeEnum.label} • ${mill.zone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "Service Due: ${mill.nextServiceDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = mill.statusEnum.label.uppercase(Locale.US),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                }
            }

            // Sensor Telemetry Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianVoid)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SensorReadout(Icons.Filled.Thermostat, "${String.format(Locale.US, "%.1f", mill.temperatureC)}°C", "Temp")
                SensorReadout(Icons.Filled.Speed, "${String.format(Locale.US, "%.1f", mill.vibrationMmS)} mm/s", "Vibration")
                SensorReadout(Icons.Filled.Build, "${mill.runningHours.toInt()}h", "Runtime")
            }

            AnimatedVisibility(visible = !mill.lastAiAdvice.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TitaniumElevated)
                        .border(1.dp, TelemetryCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "AI Diagnostics: ${mill.lastAiAdvice.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDiagnose,
                    enabled = !isDiagnosing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("diagnose_mill_${mill.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TelemetryCyan.copy(alpha = 0.2f),
                        contentColor = TelemetryCyan
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isDiagnosing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TelemetryCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Diagnose")
                    }
                }
                OutlinedButton(
                    onClick = onSchedule,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("schedule_mill_${mill.id}"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Schedule Service", color = MoltenGoldGlow)
                }
            }
        }
    }
}

@Composable
private fun SensorReadout(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = MoltenGold, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun ErrorPill(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CrimsonDeep)
            .border(1.dp, CrimsonCritical.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(text = message, style = MaterialTheme.typography.bodySmall, color = CrimsonCritical)
    }
}
