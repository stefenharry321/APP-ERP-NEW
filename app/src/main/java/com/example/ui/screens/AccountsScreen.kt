package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GstInvoice
import com.example.data.model.InvoicePaymentStatus
import com.example.data.model.LedgerEntryType
import com.example.data.model.LiveSupabaseTableData
import com.example.data.model.PartyBalanceSummary
import com.example.data.model.PartyLedgerEntry
import com.example.data.model.SupabaseTableMapping
import com.example.data.model.SyncTelemetry
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.liquidSpringClickable
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.EmeraldOptimal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MoltenGold
import com.example.ui.theme.MoltenGoldGlow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumElevated
import java.util.Locale

@Composable
fun AccountsScreen(
    invoices: List<GstInvoice>,
    ledgerEntries: List<PartyLedgerEntry>,
    partySummaries: List<PartyBalanceSummary>,
    syncTelemetry: SyncTelemetry,
    tableMapping: SupabaseTableMapping,
    liveTableData: LiveSupabaseTableData,
    isLoadingLiveTable: Boolean,
    onCreateInvoiceClick: () -> Unit,
    onMarkInvoicePaid: (GstInvoice) -> Unit,
    onAddLedgerEntryClick: () -> Unit,
    onDeleteLedgerEntry: (String) -> Unit,
    onOpenTableMapper: () -> Unit,
    onDiscoverTables: () -> Unit,
    onSelectLiveTable: (String) -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) } // 0 = GST Invoices, 1 = Party Khata, 2 = Live ERP Tables

    val totalInvoicedInr = invoices.sumOf { it.totalAmountInr }
    val totalGstInr = invoices.sumOf { it.cgstInr + it.sgstInr }
    val totalReceivableInr = partySummaries.sumOf { it.outstandingInr.coerceAtLeast(0.0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("accounts_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Contextual Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GST Billing & Khata",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tax invoices, party ledger & live Windows ERP tables",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                when (subTab) {
                    0 -> Button(
                        onClick = onCreateInvoiceClick,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("new_gst_invoice_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MoltenGold, contentColor = ObsidianVoid),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New GST Invoice", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GST Bill")
                    }
                    1 -> Button(
                        onClick = onAddLedgerEntryClick,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("new_ledger_entry_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldOptimal, contentColor = ObsidianVoid),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Khata Entry", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Entry")
                    }
                    else -> Button(
                        onClick = onOpenTableMapper,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("map_supabase_tables_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan, contentColor = ObsidianVoid),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = "Map Tables", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Map")
                    }
                }
            }
        }

        // Liquid 3-segment Mode Switcher
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModePill("GST Bills", subTab == 0, MoltenGold, "subtab_gst", Modifier.weight(1f)) { subTab = 0 }
                ModePill("Party Khata", subTab == 1, EmeraldOptimal, "subtab_khata", Modifier.weight(1f)) { subTab = 1 }
                ModePill("ERP Tables", subTab == 2, TelemetryCyan, "subtab_tables", Modifier.weight(1f)) {
                    subTab = 2
                    if (syncTelemetry.discoveredTables.isEmpty() && liveTableData.rows.isEmpty()) {
                        onDiscoverTables()
                    }
                }
            }
        }

        // Financial KPI Summary Ribbon
        item {
            GlassTelemetryCard(accentColor = MoltenGold) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FinStat("Total Billed", "₹${String.format(Locale.US, "%.1f", totalInvoicedInr / 100000.0)}L", MoltenGoldGlow)
                    FinStat("GST Tax", "₹${String.format(Locale.US, "%.1f", totalGstInr / 1000.0)}K", TelemetryCyan)
                    FinStat("Receivable", "₹${String.format(Locale.US, "%.1f", totalReceivableInr / 100000.0)}L", AmberWarning)
                }
            }
        }

        when (subTab) {
            // --- MODE 0: GST INVOICES ---
            0 -> {
                if (invoices.isEmpty()) {
                    item {
                        EmptyAccountsCard(
                            icon = Icons.Filled.ReceiptLong,
                            title = "No GST Tax Invoices yet",
                            subtitle = "Create a GST bill (HSN 1101 / 5% Atta GST) or sync your Windows ERP Supabase billing table."
                        )
                    }
                } else {
                    items(invoices, key = { it.id }) { invoice ->
                        GstInvoiceCard(
                            invoice = invoice,
                            onMarkPaid = { onMarkInvoicePaid(invoice) },
                            onShare = {
                                val text = buildString {
                                    appendLine("KUMAR FOODS PROCESSING COMPANY - TAX INVOICE")
                                    appendLine("Invoice No: ${invoice.invoiceNumber} | Date: ${invoice.invoiceDate}")
                                    appendLine("Party: ${invoice.partyName}")
                                    if (invoice.partyGstin.isNotBlank()) appendLine("GSTIN: ${invoice.partyGstin}")
                                    appendLine("Item: ${invoice.itemDescription} (HSN ${invoice.hsnCode})")
                                    appendLine("Qty: ${invoice.quantityMt} MT")
                                    appendLine("Taxable: ₹${String.format(Locale.US, "%,.2f", invoice.taxableAmountInr)}")
                                    appendLine("CGST + SGST (${invoice.gstRatePct}%): ₹${String.format(Locale.US, "%,.2f", invoice.cgstInr + invoice.sgstInr)}")
                                    appendLine("Grand Total: ₹${String.format(Locale.US, "%,.2f", invoice.totalAmountInr)}")
                                    appendLine("Status: ${invoice.paymentStatusEnum.label}")
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Kumar Foods GST Invoice ${invoice.invoiceNumber}")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share GST Invoice"))
                            }
                        )
                    }
                }
            }

            // --- MODE 1: PARTY LEDGER (KHATA) ---
            1 -> {
                if (partySummaries.isNotEmpty()) {
                    item {
                        Text(
                            text = "Distributor Balances (Khata Summary)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    items(partySummaries, key = { it.partyName }) { summary ->
                        PartySummaryCard(
                            summary = summary,
                            onShareReminder = {
                                val msg = "Namaste ${summary.partyName}, Kumar Foods Processing Company Khata Update: " +
                                    "Total Billed ₹${String.format(Locale.US, "%,.0f", summary.totalBilledInr)}, " +
                                    "Received ₹${String.format(Locale.US, "%,.0f", summary.totalReceivedInr)}, " +
                                    "Outstanding Balance: ₹${String.format(Locale.US, "%,.0f", summary.outstandingInr)}."
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, msg)
                                }
                                context.startActivity(Intent.createChooser(intent, "Send Party Khata Statement"))
                            }
                        )
                    }
                }

                item {
                    Text(
                        text = "Recent Ledger Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                if (ledgerEntries.isEmpty()) {
                    item {
                        EmptyAccountsCard(
                            icon = Icons.Filled.AccountBalanceWallet,
                            title = "No Khata Transactions yet",
                            subtitle = "Tap 'Entry' above to log party sales or UPI/RTGS payment receipts."
                        )
                    }
                } else {
                    items(ledgerEntries, key = { it.id }) { entry ->
                        LedgerEntryCard(
                            entry = entry,
                            onDelete = { onDeleteLedgerEntry(entry.id) }
                        )
                    }
                }
            }

            // --- MODE 2: LIVE WINDOWS ERP SUPABASE TABLE EXPLORER ---
            else -> {
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
                                    Icon(Icons.Filled.Storage, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Live Windows ERP Supabase Tables",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                }
                                IconButton(
                                    onClick = onDiscoverTables,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Supabase Tables", tint = TelemetryCyan)
                                }
                            }
                            Text(
                                text = "Mapped: ${tableMapping.inventoryTable} • ${tableMapping.shipmentsTable} • ${tableMapping.invoicesTable} • ${tableMapping.ledgerTable}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )

                            val tableChoices = if (syncTelemetry.discoveredTables.isNotEmpty()) {
                                syncTelemetry.discoveredTables
                            } else {
                                listOf(
                                    tableMapping.inventoryTable,
                                    tableMapping.shipmentsTable,
                                    tableMapping.invoicesTable,
                                    tableMapping.ledgerTable,
                                    tableMapping.millsTable
                                )
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(tableChoices) { tbl ->
                                    FilterChip(
                                        selected = liveTableData.tableName == tbl,
                                        onClick = { onSelectLiveTable(tbl) },
                                        label = { Text(tbl) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TelemetryCyan.copy(alpha = 0.22f),
                                            selectedLabelColor = TelemetryCyan
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    if (isLoadingLiveTable) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = TelemetryCyan)
                        }
                    } else if (!liveTableData.errorMessage.isNullOrBlank()) {
                        GlassTelemetryCard(accentColor = AmberWarning) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Live Windows ERP Bridge", style = MaterialTheme.typography.titleSmall, color = AmberWarning)
                                Text(liveTableData.errorMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                OutlinedButton(
                                    onClick = onOpenTableMapper,
                                    modifier = Modifier.height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Configure Table Names", color = TelemetryCyan)
                                }
                            }
                        }
                    } else if (liveTableData.rows.isEmpty()) {
                        EmptyAccountsCard(
                            icon = Icons.Filled.Storage,
                            title = if (liveTableData.tableName.isBlank()) "Select a Supabase Table Above" else "Table '${liveTableData.tableName}' has 0 rows",
                            subtitle = "Tap any table chip above to stream live Windows ERP rows directly from Supabase."
                        )
                    } else {
                        LiveSupabaseGridCard(liveTableData)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.2f) else ObsidianSurface)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) accent.copy(alpha = 0.65f) else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag(tag)
            .liquidSpringClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun FinStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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
private fun GstInvoiceCard(
    invoice: GstInvoice,
    onMarkPaid: () -> Unit,
    onShare: () -> Unit
) {
    val status = invoice.paymentStatusEnum
    val accent = when (status) {
        InvoicePaymentStatus.PAID -> EmeraldOptimal
        InvoicePaymentStatus.PARTIAL -> AmberWarning
        InvoicePaymentStatus.UNPAID -> CrimsonCritical
    }

    GlassTelemetryCard(accentColor = accent, modifier = Modifier.fillMaxWidth()) {
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
                    Text(
                        text = "${invoice.invoiceNumber} • ${invoice.invoiceDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryCyan
                    )
                    Text(
                        text = invoice.partyName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (invoice.partyGstin.isNotBlank()) {
                        Text("GSTIN: ${invoice.partyGstin}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = status.label.uppercase(Locale.US),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                }
            }

            Text(
                text = "${invoice.itemDescription} • HSN ${invoice.hsnCode} • ${invoice.quantityMt} MT",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianVoid)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Taxable", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text("₹${String.format(Locale.US, "%,.0f", invoice.taxableAmountInr)}", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                }
                Column {
                    Text("GST (${invoice.gstRatePct.toInt()}%)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text("₹${String.format(Locale.US, "%,.0f", invoice.cgstInr + invoice.sgstInr)}", style = MaterialTheme.typography.labelMedium, color = TelemetryCyan)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Grand Total", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        "₹${String.format(Locale.US, "%,.0f", invoice.totalAmountInr)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MoltenGoldGlow
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Share Invoice", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Bill")
                }
                if (status != InvoicePaymentStatus.PAID) {
                    Button(
                        onClick = onMarkPaid,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("mark_paid_${invoice.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldOptimal, contentColor = ObsidianVoid),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark Paid")
                    }
                }
            }
        }
    }
}

@Composable
private fun PartySummaryCard(
    summary: PartyBalanceSummary,
    onShareReminder: () -> Unit
) {
    val dueColor = if (summary.outstandingInr > 0) AmberWarning else EmeraldOptimal
    GlassTelemetryCard(accentColor = dueColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.partyName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("${summary.partyCity} • ${summary.entryCount} entries", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Billed ₹${String.format(Locale.US, "%,.0f", summary.totalBilledInr)} • Recd ₹${String.format(Locale.US, "%,.0f", summary.totalReceivedInr)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (summary.outstandingInr > 0) "DUE" else "SETTLED",
                    style = MaterialTheme.typography.labelSmall,
                    color = dueColor
                )
                Text(
                    text = "₹${String.format(Locale.US, "%,.0f", kotlin.math.abs(summary.outstandingInr))}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = dueColor
                )
                IconButton(onClick = onShareReminder, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = "Share Khata Reminder", tint = TelemetryCyan, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun LedgerEntryCard(
    entry: PartyLedgerEntry,
    onDelete: () -> Unit
) {
    val isCredit = entry.entryTypeEnum == LedgerEntryType.CREDIT_PAYMENT
    val accent = if (isCredit) EmeraldOptimal else MoltenGold

    GlassTelemetryCard(accentColor = GlassBorder, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isCredit) "CR" else "DR",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accent
                        )
                    }
                    Text(entry.partyName, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${entry.entryDate} • ${entry.referenceNo}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                if (entry.notes.isNotBlank()) {
                    Text(entry.notes, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (isCredit) "+" else "-"}₹${String.format(Locale.US, "%,.0f", entry.amountInr)}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accent
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Entry", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveSupabaseGridCard(data: LiveSupabaseTableData) {
    GlassTelemetryCard(accentColor = TelemetryCyan, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${data.tableName} (${data.rows.size} live rows)",
                    style = MaterialTheme.typography.titleSmall,
                    color = TelemetryCyan
                )
                Text(
                    text = "${data.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldOptimal
                )
            }
            HorizontalDivider(color = GlassBorder)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TitaniumElevated)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    data.columns.take(8).forEach { col ->
                        Text(
                            text = col.uppercase(Locale.US),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MoltenGoldGlow,
                            modifier = Modifier.width(110.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Live rows
                data.rows.take(25).forEach { row ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianVoid)
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        data.columns.take(8).forEach { col ->
                            Text(
                                text = row[col] ?: "—",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                modifier = Modifier.width(110.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAccountsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    GlassTelemetryCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
