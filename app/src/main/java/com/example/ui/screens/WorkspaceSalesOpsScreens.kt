package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ChokarSaleEntry
import com.example.data.model.ChokarStockTotals
import com.example.data.model.InventoryItem
import com.example.data.model.OfficeReceiptPayment
import com.example.data.model.OfficeSnapshotAuditState
import com.example.data.model.ProductionReportEntry
import com.example.data.model.SalesRegisterEntry
import com.example.data.model.SyncTelemetry
import com.example.ui.components.GlassTelemetryCard
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

/**
 * 5. SALES REGISTER MODULE (Original Windows Office PDF pull via erp_mobile_invoice_pull + DatePicker)
 */
@Composable
fun SalesRegisterModuleScreen(
    sales: List<SalesRegisterEntry>,
    syncTelemetry: SyncTelemetry,
    auditState: OfficeSnapshotAuditState,
    isLoadingOfficePdf: Boolean,
    onOpenOfficePdf: (SalesRegisterEntry) -> Unit,
    onCreateNewInvoice: () -> Unit,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BackHandler(onBack = onBack)
    var search by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf<String?>(null) }
    var toDate by remember { mutableStateOf<String?>(null) }

    val filtered = remember(sales, search, fromDate, toDate) {
        sales.filter { s ->
            val matchesDate = isDateWithinRange(s.date, fromDate, toDate)
            val matchesQuery = search.isBlank() ||
                s.partyName.contains(search, true) ||
                s.saleNo.contains(search, true) ||
                s.items.any { it.productName.contains(search, true) }
            matchesDate && matchesQuery
        }
    }
    val totalKg = filtered.sumOf { it.totalWeightKg }
    val totalSales = filtered.sumOf { it.totalAmountInr }
    val pointerCount = filtered.count { it.hasOfficePdfPointer }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("sales_register_module")
    ) {
        ModuleTopHeader("Sales Register", syncTelemetry, auditState, onBack, onSyncNow, onOpenSettings)
        DateRangeAndSearchBar(
            searchQuery = search,
            onSearchChange = { search = it },
            fromDate = fromDate,
            toDate = toDate,
            onFromDateSelected = { fromDate = it },
            onToDateSelected = { toDate = it },
            onRefresh = onSyncNow
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Windows Sales Register • ${filtered.size} entries • ${syncTelemetry.lastSyncLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onCreateNewInvoice,
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Bill", style = MaterialTheme.typography.labelSmall)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    GlassTelemetryCard(accentColor = MoltenGold) {
                        Text(
                            text = "No sales register bills match this filter. Clear date range or pair your Office ERP in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { sale ->
                    GlassTelemetryCard(
                        accentColor = if (sale.hasOfficePdfPointer) EmeraldOptimal else TelemetryCyan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidSpringClickable { onOpenOfficePdf(sale) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = sale.saleNo,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = TelemetryCyan
                                )
                                Text(
                                    text = sale.date,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = sale.partyName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                sale.items.forEach { line ->
                                    Text(
                                        text = "${line.productName} • ${String.format(Locale.US, "%.1f", line.packKg)} Kg × ${line.bags} bags • Rate ${String.format(Locale.US, "%.0f", line.ratePerKgInclGst)}/${line.rateUnit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Total ₹${String.format(Locale.US, "%.2f", sale.totalAmountInr)}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MoltenGoldGlow
                            )
                            OutlinedButton(
                                onClick = { onOpenOfficePdf(sale) },
                                enabled = !isLoadingOfficePdf,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_open_pdf_${sale.saleNo}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (sale.hasOfficePdfPointer) EmeraldOptimal else TelemetryCyan
                                )
                            ) {
                                if (isLoadingOfficePdf) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = TelemetryCyan
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("FETCHING ORIGINAL OFFICE PDF...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("VERIFY / OPEN OFFICE PDF", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Totals Bar (Matches Screenshot #3)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SummaryMetricRow("Total Quantity", "${String.format(Locale.US, "%.3f", totalKg)} Kg", TextPrimary, bold = true)
            SummaryMetricRow("Sales Total", "₹${String.format(Locale.US, "%.2f", totalSales)}", MoltenGoldGlow, bold = true)
            Text(
                text = if (auditState.isAuditPassed) {
                    "PDF pointers: $pointerCount/${filtered.size}. Original Windows PDFs are verified via erp_mobile_invoice_pull when opened."
                } else {
                    "PDF pointers: ${filtered.size}/${filtered.size} On-Device Tax Invoice PDF Ready"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

/**
 * 6. RECEIPT & PAYMENT MODULE (Exact ↓ Receipt / ↑ Payment toggles, Mode, Discount, Totals)
 */
@Composable
fun ReceiptPaymentModuleScreen(
    entries: List<OfficeReceiptPayment>,
    syncTelemetry: SyncTelemetry,
    auditState: OfficeSnapshotAuditState,
    onAddVoucherClick: () -> Unit,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BackHandler(onBack = onBack)
    var search by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf<String?>(null) }
    var toDate by remember { mutableStateOf<String?>(null) }
    var filterKind by remember { mutableStateOf("ALL") } // ALL, RECEIPT, PAYMENT

    val filtered = remember(entries, search, fromDate, toDate, filterKind) {
        entries.filter { e ->
            val matchesDate = isDateWithinRange(e.date, fromDate, toDate)
            val matchesKind = when (filterKind) {
                "RECEIPT" -> e.kind.equals("Receipt", ignoreCase = true)
                "PAYMENT" -> e.kind.equals("Payment", ignoreCase = true)
                else -> true
            }
            val matchesSearch = search.isBlank() ||
                e.partyName.contains(search, true) ||
                e.referenceNo.contains(search, true) ||
                e.mode.contains(search, true) ||
                e.narration.contains(search, true)
            matchesDate && matchesKind && matchesSearch
        }
    }
    val totalReceipts = filtered.filter { it.kind.equals("Receipt", true) }.sumOf { it.amountInr }
    val totalPayments = filtered.filter { it.kind.equals("Payment", true) }.sumOf { it.amountInr }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("receipt_payment_module")
    ) {
        ModuleTopHeader("Receipt & Payment", syncTelemetry, auditState, onBack, onSyncNow, onOpenSettings)
        DateRangeAndSearchBar(
            searchQuery = search,
            onSearchChange = { search = it },
            fromDate = fromDate,
            toDate = toDate,
            onFromDateSelected = { fromDate = it },
            onToDateSelected = { toDate = it },
            onRefresh = onSyncNow
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filterKind == "ALL",
                    onClick = { filterKind = "ALL" },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filterKind == "RECEIPT",
                    onClick = { filterKind = "RECEIPT" },
                    label = { Text("↓ Receipt") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldOptimal,
                        selectedLabelColor = ObsidianVoid
                    )
                )
                FilterChip(
                    selected = filterKind == "PAYMENT",
                    onClick = { filterKind = "PAYMENT" },
                    label = { Text("↑ Payment") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberWarning,
                        selectedLabelColor = ObsidianVoid
                    )
                )
            }
            IconButton(
                onClick = onAddVoucherClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MoltenGold)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Voucher", tint = ObsidianVoid)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { txn ->
                val isReceipt = txn.kind.equals("Receipt", ignoreCase = true)
                val accent = if (isReceipt) EmeraldOptimal else AmberWarning
                GlassTelemetryCard(accentColor = accent) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${if (isReceipt) "↓ Receipt" else "↑ Payment"} • ${txn.mode} • ${txn.referenceNo}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = accent
                            )
                            Text(txn.partyName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            Text(
                                text = buildString {
                                    append(txn.date)
                                    if (txn.discountInr > 0.0) append(" • Disc ₹${String.format(Locale.US, "%.2f", txn.discountInr)}")
                                    if (txn.narration.isNotBlank()) append(" • ${txn.narration}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", txn.amountInr)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = accent
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SummaryMetricRow("Receipt Total", "₹${String.format(Locale.US, "%.2f", totalReceipts)}", EmeraldOptimal, bold = true)
            SummaryMetricRow("Payment Total", "₹${String.format(Locale.US, "%.2f", totalPayments)}", AmberWarning)
        }
    }
}

/**
 * 7. PRODUCTION REPORTS MODULE (ERP 3.5: All/Atta/Chokar/Wastage filters + Chokar packed/loose + remarks + issues)
 */
@Composable
fun ProductionReportsModuleScreen(
    reports: List<ProductionReportEntry>,
    chokarStockTotals: ChokarStockTotals,
    syncTelemetry: SyncTelemetry,
    auditState: OfficeSnapshotAuditState,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BackHandler(onBack = onBack)
    var fromDate by remember { mutableStateOf<String?>(null) }
    var toDate by remember { mutableStateOf<String?>(null) }
    var metricFilter by remember { mutableStateOf("ALL") } // ALL, ATTA, CHOKAR, WASTAGE

    val filtered = remember(reports, fromDate, toDate, metricFilter) {
        reports.filter { rep ->
            val inRange = isDateWithinRange(rep.date, fromDate, toDate)
            val matchesMetric = when (metricFilter) {
                "ATTA" -> rep.attaProducedKg > 0.0
                "CHOKAR" -> rep.chokarProducedKg > 0.0
                "WASTAGE" -> rep.wastageKg > 0.0
                else -> true
            }
            inRange && matchesMetric
        }
    }
    val totalWheat = filtered.sumOf { it.wheatProcessedKg }
    val totalAtta = filtered.sumOf { it.attaProducedKg }
    val totalChokar = filtered.sumOf { it.chokarProducedKg }
    val totalWastage = filtered.sumOf { it.wastageKg }
    val avgExtract = if (totalWheat > 0.0) (totalAtta / totalWheat) * 100.0 else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("production_reports_module")
    ) {
        ModuleTopHeader("Production Reports", syncTelemetry, auditState, onBack, onSyncNow, onOpenSettings)
        DateRangeAndSearchBar(
            searchQuery = "",
            onSearchChange = {},
            fromDate = fromDate,
            toDate = toDate,
            onFromDateSelected = { fromDate = it },
            onToDateSelected = { toDate = it },
            onRefresh = onSyncNow,
            showSearch = false
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL" to "All", "ATTA" to "Atta", "CHOKAR" to "Chokar", "WASTAGE" to "Wastage").forEach { (key, label) ->
                FilterChip(
                    selected = metricFilter == key,
                    onClick = { metricFilter = key },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MoltenGold,
                        selectedLabelColor = ObsidianVoid
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chokarStockTotals.issues.isNotEmpty()) {
                item {
                    GlassTelemetryCard(accentColor = AmberWarning) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Office Production & Chokar Notices", style = MaterialTheme.typography.labelLarge, color = AmberWarning)
                            }
                            chokarStockTotals.issues.forEach { msg ->
                                Text("• $msg", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            item {
                GlassTelemetryCard(accentColor = MoltenGold) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Plant Extraction Summary (${filtered.size} batches)", style = MaterialTheme.typography.titleMedium, color = MoltenGoldGlow)
                        SummaryMetricRow("Wheat Milled", "${String.format(Locale.US, "%.2f", totalWheat)} Kg", TextPrimary)
                        SummaryMetricRow("Atta Output", "${String.format(Locale.US, "%.2f", totalAtta)} Kg", EmeraldOptimal, bold = true)
                        SummaryMetricRow("Chokar Output", "${String.format(Locale.US, "%.2f", totalChokar)} Kg", TelemetryCyan)
                        SummaryMetricRow("Wastage", "${String.format(Locale.US, "%.2f", totalWastage)} Kg", AmberWarning)
                        HorizontalDivider(color = GlassBorder)
                        SummaryMetricRow("Avg Extraction Rate", "${String.format(Locale.US, "%.1f", avgExtract)}%", MoltenGoldGlow, bold = true)
                    }
                }
            }

            items(filtered, key = { it.id }) { rep ->
                GlassTelemetryCard(accentColor = TelemetryCyan) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Factory, contentDescription = null, tint = MoltenGold, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(rep.batchCode, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            }
                            Text(rep.date, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianVoid)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Wheat", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("${String.format(Locale.US, "%.1f", rep.wheatProcessedKg)} Kg", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            }
                            Column {
                                Text("Atta", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("${String.format(Locale.US, "%.1f", rep.attaProducedKg)} Kg", style = MaterialTheme.typography.labelLarge, color = EmeraldOptimal)
                            }
                            Column {
                                Text("Chokar", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("${String.format(Locale.US, "%.1f", rep.chokarProducedKg)} Kg", style = MaterialTheme.typography.labelLarge, color = TelemetryCyan)
                            }
                            Column {
                                Text("Wastage", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("${String.format(Locale.US, "%.1f", rep.wastageKg)} Kg", style = MaterialTheme.typography.labelLarge, color = AmberWarning)
                            }
                        }
                        if (rep.chokarPackedKg > 0.0 || rep.chokarLooseKg > 0.0) {
                            Text(
                                text = "Chokar packed at production: ${String.format(Locale.US, "%.2f", rep.chokarPackedKg)} Kg  •  Loose after production: ${String.format(Locale.US, "%.2f", rep.chokarLooseKg)} Kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        if (rep.remarks.isNotBlank()) {
                            Text(
                                text = "Remarks: ${rep.remarks}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MoltenGoldGlow
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 8. CHOKAR STOCK MODULE (ERP 3.5 exact read-only Office Chokar mass-conservation report + Firestore bins)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChokarStockModuleScreen(
    chokarItems: List<InventoryItem>,
    chokarStockTotals: ChokarStockTotals,
    syncTelemetry: SyncTelemetry,
    auditState: OfficeSnapshotAuditState,
    onAdjustStock: (InventoryItem, Double) -> Unit,
    onAddChokarClick: () -> Unit,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BackHandler(onBack = onBack)
    val totalKg = chokarItems.sumOf { it.quantityMt * 1000.0 }
    val totalBags = (totalKg / 40.0).toInt()
    val totalValue = chokarItems.sumOf { it.quantityMt * it.unitPriceInr }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("chokar_stock_module")
    ) {
        ModuleTopHeader("Chokar Stock", syncTelemetry, auditState, onBack, onSyncNow, onOpenSettings)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chokarStockTotals.hasOfficeChokarReport) {
                item {
                    GlassTelemetryCard(accentColor = EmeraldOptimal) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Verified Office Chokar Balance (Read-Only)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldOptimal
                            )
                            SummaryMetricRow("Total Produced", "${String.format(Locale.US, "%.2f", chokarStockTotals.producedKg)} Kg", TextPrimary)
                            SummaryMetricRow("Packed at Production", "${String.format(Locale.US, "%.2f", chokarStockTotals.productionPackedKg)} Kg", TelemetryCyan)
                            SummaryMetricRow("Packed Later", "${String.format(Locale.US, "%.2f", chokarStockTotals.laterPackedKg)} Kg", TelemetryCyan)
                            SummaryMetricRow("Sold Chokar", "${String.format(Locale.US, "%.2f", chokarStockTotals.soldKg)} Kg", AmberWarning)
                            HorizontalDivider(color = GlassBorder)
                            SummaryMetricRow("Loose Chokar Remaining", "${String.format(Locale.US, "%.2f", chokarStockTotals.looseRemainingKg)} Kg", MoltenGoldGlow, bold = true)
                            SummaryMetricRow("Packed Chokar Remaining", "${String.format(Locale.US, "%.2f", chokarStockTotals.packedRemainingKg)} Kg", EmeraldOptimal, bold = true)
                        }
                    }
                }

                if (chokarStockTotals.packBalances.isNotEmpty()) {
                    item {
                        GlassTelemetryCard(accentColor = TelemetryCyan) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Bag Size-Wise Remaining", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TelemetryCyan)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    chokarStockTotals.packBalances.forEach { pb ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(ObsidianVoid)
                                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f", pb.packKg)} Kg × ${String.format(Locale.US, "%.0f", pb.bags)} bags",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MoltenGoldGlow
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (chokarStockTotals.packingVouchers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Later Chokar Packing Vouchers (${chokarStockTotals.packingVouchers.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(chokarStockTotals.packingVouchers, key = { it.id }) { pv ->
                        GlassTelemetryCard(accentColor = MoltenGold) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Pack #${pv.id} • ${pv.date}", style = MaterialTheme.typography.labelMedium, color = TelemetryCyan)
                                    Text(
                                        "${String.format(Locale.US, "%.1f", pv.packKg)} Kg × ${pv.bags} bags",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", pv.totalKg)} Kg",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MoltenGoldGlow
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    GlassTelemetryCard(accentColor = MoltenGold) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SummaryMetricRow("Total Chokar Weight", "${String.format(Locale.US, "%.1f", totalKg)} Kg", MoltenGoldGlow, bold = true)
                            SummaryMetricRow("Estimated 40Kg Bags", "$totalBags Bags", TelemetryCyan)
                            SummaryMetricRow("Stock Valuation", "₹${String.format(Locale.US, "%.0f", totalValue)}", EmeraldOptimal)
                        }
                    }
                }
            }

            if (chokarItems.isEmpty() && !chokarStockTotals.hasOfficeChokarReport) {
                item {
                    GlassTelemetryCard(accentColor = AmberWarning) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("No Chokar / Bran inventory bins found yet.", color = TextSecondary)
                            Button(
                                onClick = onAddChokarClick,
                                colors = ButtonDefaults.buttonColors(containerColor = MoltenGold, contentColor = ObsidianVoid)
                            ) {
                                Text("Add Chokar Stock Bin")
                            }
                        }
                    }
                }
            } else if (chokarItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Warehouse Floor Bins",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(chokarItems, key = { it.id }) { item ->
                    val kg = item.quantityMt * 1000.0
                    val bags = (kg / 40.0).toInt()
                    val accent = if (item.belowThreshold) CrimsonCritical else EmeraldOptimal
                    GlassTelemetryCard(accentColor = accent) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Inventory2, contentDescription = null, tint = MoltenGold, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                                        Text("${item.warehouse} • ${item.sku}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                }
                                Text(
                                    text = "$bags Bags",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = accent
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", item.quantityMt)} MT (${String.format(Locale.US, "%.0f", kg)} Kg)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TelemetryCyan
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { onAdjustStock(item, -0.4) },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(TitaniumSurface)
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Dispatch 10 Bags", tint = CrimsonCritical)
                                    }
                                    IconButton(
                                        onClick = { onAdjustStock(item, 0.4) },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(TitaniumSurface)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Add 10 Bags", tint = EmeraldOptimal)
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

/**
 * 9. CHOKAR CUSTOMER SALES MODULE (Strictly grouped by customerId + invoice-wise lines)
 */
@Composable
fun ChokarCustomerSalesModuleScreen(
    chokarSales: List<ChokarSaleEntry>,
    syncTelemetry: SyncTelemetry,
    auditState: OfficeSnapshotAuditState,
    onCreateChokarBill: () -> Unit,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BackHandler(onBack = onBack)
    var search by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf<String?>(null) }
    var toDate by remember { mutableStateOf<String?>(null) }

    val filtered = remember(chokarSales, search, fromDate, toDate) {
        chokarSales.filter { s ->
            val matchesDate = isDateWithinRange(s.date, fromDate, toDate)
            val matchesQuery = search.isBlank() ||
                s.partyName.contains(search, true) ||
                s.billNo.contains(search, true) ||
                s.customerId.contains(search, true)
            matchesDate && matchesQuery
        }
    }
    // Strictly group by customerId when available (never merging different customer IDs by name)
    val groupedCustomers = remember(filtered) {
        filtered.groupBy { entry ->
            if (entry.customerId.isNotBlank()) "cid_${entry.customerId}"
            else "name_${entry.partyName.trim().lowercase(Locale.US)}"
        }.values.sortedByDescending { lines -> lines.sumOf { it.totalAmountInr } }
    }
    val totalKg = filtered.sumOf { it.totalWeightKg }
    val totalInr = filtered.sumOf { it.totalAmountInr }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("chokar_sales_module")
    ) {
        ModuleTopHeader("Chokar Customer Sales", syncTelemetry, auditState, onBack, onSyncNow, onOpenSettings)
        DateRangeAndSearchBar(
            searchQuery = search,
            onSearchChange = { search = it },
            fromDate = fromDate,
            toDate = toDate,
            onFromDateSelected = { fromDate = it },
            onToDateSelected = { toDate = it },
            onRefresh = onSyncNow
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (groupedCustomers.isEmpty()) {
                item {
                    GlassTelemetryCard(accentColor = TelemetryCyan) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "No Chokar customer sales entries match this period. Sync Office ERP or record a Chokar bill.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Button(
                                onClick = onCreateChokarBill,
                                colors = ButtonDefaults.buttonColors(containerColor = MoltenGold, contentColor = ObsidianVoid),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Record Chokar Sale", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(groupedCustomers, key = { it.first().id }) { partyLines ->
                    val first = partyLines.first()
                    val partyKg = partyLines.sumOf { it.totalWeightKg }
                    val partyBags = partyLines.sumOf { it.bags }
                    val partyAmt = partyLines.sumOf { it.totalAmountInr }

                    GlassTelemetryCard(accentColor = MoltenGold) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = first.partyName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = buildString {
                                            if (first.customerId.isNotBlank()) append("ID #${first.customerId} • ")
                                            append("${partyLines.size} bill(s) • $partyBags bags • ${String.format(Locale.US, "%.2f", partyKg)} Kg")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TelemetryCyan
                                    )
                                }
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", partyAmt)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MoltenGoldGlow
                                )
                            }
                            HorizontalDivider(color = GlassBorder)
                            partyLines.forEach { sale ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${sale.billNo} (${sale.date}) • ${String.format(Locale.US, "%.1f", sale.packKg)}Kg × ${sale.bags}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (sale.legacyUnmatched) AmberWarning else TextSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.US, "%.2f", sale.totalAmountInr)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SummaryMetricRow("Total Chokar Dispatched", "${String.format(Locale.US, "%.2f", totalKg)} Kg", TelemetryCyan)
            SummaryMetricRow("Chokar Sales Total", "₹${String.format(Locale.US, "%.2f", totalInr)}", MoltenGoldGlow, bold = true)
        }
    }
}
