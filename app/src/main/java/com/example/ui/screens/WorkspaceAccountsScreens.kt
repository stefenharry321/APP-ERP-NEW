package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DayBookRow
import com.example.data.model.LedgerEntryType
import com.example.data.model.OfficeCustomer
import com.example.data.model.OfficeLedgerVoucher
import com.example.data.model.OfficeSnapshotAuditState
import com.example.data.model.PartyLedgerEntry
import com.example.data.model.SalesRegisterEntry
import com.example.data.model.SupabaseBridgeState
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
import com.example.util.ErpPdfGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

internal fun displayDateToIsoDay(displayDate: String): String {
    val clean = displayDate.trim()
    val dmy = Regex("^(\\d{2})-(\\d{2})-(\\d{4})").find(clean)
    if (dmy != null) {
        return "${dmy.groupValues[3]}-${dmy.groupValues[2]}-${dmy.groupValues[1]}"
    }
    val ymd = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(clean)
    if (ymd != null) {
        return "${ymd.groupValues[1]}-${ymd.groupValues[2]}-${ymd.groupValues[3]}"
    }
    return clean
}

internal fun millisToDisplayDate(millis: Long): String {
    val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(millis))
}

internal fun isDateWithinRange(displayDate: String, fromDisplay: String?, toDisplay: String?): Boolean {
    if (fromDisplay.isNullOrBlank() && toDisplay.isNullOrBlank()) return true
    val target = displayDateToIsoDay(displayDate)
    if (target.length < 10) return true
    if (!fromDisplay.isNullOrBlank() && target < displayDateToIsoDay(fromDisplay)) return false
    if (!toDisplay.isNullOrBlank() && target > displayDateToIsoDay(toDisplay)) return false
    return true
}

@Composable
internal fun ModuleTopHeader(
    title: String,
    syncTelemetry: SyncTelemetry,
    auditState: OfficeSnapshotAuditState = OfficeSnapshotAuditState(),
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("module_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onSyncNow,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("module_sync_button")
            ) {
                Icon(Icons.Filled.CloudSync, contentDescription = "Sync Office ERP", tint = TelemetryCyan)
            }
        }

        val bannerText = when {
            auditState.syncDriftWarning.isNotBlank() -> auditState.syncDriftWarning
            syncTelemetry.bridgeState != SupabaseBridgeState.CONNECTED_LIVE ->
                "Office Windows ERP not paired yet. Connect URL, Key & Pairing Code for automatic live sync."
            auditState.isAuditPassed && !auditState.isLiveFresh ->
                "Latest cloud upload is older than 3 min. Check Sync Now on OFFICE Windows ERP."
            else -> null
        }

        if (bannerText != null) {
            val isCritical = auditState.syncDriftWarning.isNotBlank()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background((if (isCritical) CrimsonCritical else AmberWarning).copy(alpha = 0.16f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = bannerText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCritical) CrimsonCritical else AmberWarning,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MoltenGoldGlow,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .liquidSpringClickable(onClick = onOpenSettings)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRangeAndSearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    fromDate: String?,
    toDate: String?,
    onFromDateSelected: (String?) -> Unit,
    onToDateSelected: (String?) -> Unit,
    onRefresh: () -> Unit,
    showSearch: Boolean = true
) {
    var pickingField by remember { mutableStateOf<String?>(null) }

    if (pickingField != null) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingField = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ms = pickerState.selectedDateMillis
                        if (ms != null) {
                            val formatted = millisToDisplayDate(ms)
                            if (pickingField == "FROM") onFromDateSelected(formatted) else onToDateSelected(formatted)
                        }
                        pickingField = null
                    }
                ) {
                    Text("Apply", color = MoltenGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (pickingField == "FROM") onFromDateSelected(null) else onToDateSelected(null)
                        pickingField = null
                    }
                ) {
                    Text("Clear", color = TelemetryCyan)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DatePill(
                label = fromDate ?: "From (All)",
                onClick = { pickingField = "FROM" },
                modifier = Modifier.weight(1f)
            )
            DatePill(
                label = toDate ?: "To (All)",
                onClick = { pickingField = "TO" },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (fromDate != null || toDate != null) {
                        onFromDateSelected(null)
                        onToDateSelected(null)
                    } else {
                        onRefresh()
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TitaniumSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset dates or refresh", tint = TelemetryCyan)
            }
        }
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search Account / Entry", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TelemetryCyan) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DatePill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TitaniumSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .liquidSpringClickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MoltenGold, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextPrimary, maxLines = 1)
    }
}

/**
 * 1. ACCOUNTS & 3. LEDGER (Customer List -> Exact customerId Party Ledger with Period Opening & Running Balance)
 */
@Composable
fun AccountsAndLedgerModuleScreen(
    modeTitle: String,
    customers: List<OfficeCustomer>,
    ledgerEntries: List<PartyLedgerEntry>,
    officeLedgerVouchers: List<OfficeLedgerVoucher>,
    syncTelemetry: SyncTelemetry,
    auditState: OfficeSnapshotAuditState,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var selectedCustomer by remember { mutableStateOf<OfficeCustomer?>(null) }
    var search by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf<String?>(null) }
    var toDate by remember { mutableStateOf<String?>(null) }

    if (selectedCustomer != null) {
        val cust = selectedCustomer!!
        val matchedOfficeVouchers = remember(cust, officeLedgerVouchers) {
            if (cust.numericId >= 0) {
                officeLedgerVouchers.filter {
                    it.accountType.equals("customer", ignoreCase = true) && it.accountId == cust.numericId
                }
            } else {
                emptyList()
            }
        }
        val matchedFallbackEntries = remember(cust, ledgerEntries) {
            ledgerEntries.filter { it.partyName.equals(cust.name, ignoreCase = true) }
        }
        PartyLedgerDetailScreen(
            customer = cust,
            officeVouchers = matchedOfficeVouchers,
            fallbackEntries = matchedFallbackEntries,
            syncTelemetry = syncTelemetry,
            auditState = auditState,
            onBack = { selectedCustomer = null },
            onSyncNow = onSyncNow,
            onOpenSettings = onOpenSettings
        )
        return
    }

    BackHandler(onBack = onBack)
    val filtered = remember(customers, search) {
        if (search.isBlank()) customers
        else customers.filter {
            it.name.contains(search, true) ||
                it.mobile.contains(search, true) ||
                it.city.contains(search, true) ||
                it.code.contains(search, true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("accounts_ledger_module")
    ) {
        ModuleTopHeader(
            title = modeTitle,
            syncTelemetry = syncTelemetry,
            auditState = auditState,
            onBack = onBack,
            onSyncNow = onSyncNow,
            onOpenSettings = onOpenSettings
        )
        DateRangeAndSearchBar(
            searchQuery = search,
            onSearchChange = { search = it },
            fromDate = fromDate,
            toDate = toDate,
            onFromDateSelected = { fromDate = it },
            onToDateSelected = { toDate = it },
            onRefresh = onSyncNow
        )
        Text(
            text = "Tap a customer to open their strict ID Ledger, Period Opening Balance & Print/WhatsApp PDF.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    GlassTelemetryCard(accentColor = TelemetryCyan) {
                        Text(
                            text = "No customer accounts synced yet. Pair Supabase URL, Key & 32-char Pairing Code in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { cust ->
                    val suffix = if (cust.closingBalanceDr >= 0.0) "Dr" else "Cr"
                    val balColor = if (cust.closingBalanceDr >= 0.0) EmeraldOptimal else TelemetryCyan
                    GlassTelemetryCard(
                        accentColor = balColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidSpringClickable { selectedCustomer = cust }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(TelemetryCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cust.name.firstOrNull()?.uppercase() ?: "C",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TelemetryCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cust.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        if (cust.numericId >= 0) append("ID #${cust.numericId} • ")
                                        append(cust.mobile.ifBlank { cust.city })
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${cust.totalEntries} vouchers in ledger",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MoltenGold
                                )
                            }
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", abs(cust.closingBalanceDr))} $suffix",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = balColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class LedgerRowWithBalance(
    val id: String,
    val date: String,
    val typeLabel: String,
    val referenceTitle: String,
    val narration: String,
    val debitInr: Double,
    val creditInr: Double,
    val runningBalanceDr: Double
)

@Composable
private fun PartyLedgerDetailScreen(
    customer: OfficeCustomer,
    officeVouchers: List<OfficeLedgerVoucher>,
    fallbackEntries: List<PartyLedgerEntry>,
    syncTelemetry: SyncTelemetry,
    auditState: OfficeSnapshotAuditState,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var fromDate by remember { mutableStateOf<String?>(null) }
    var toDate by remember { mutableStateOf<String?>(null) }
    val today = remember { SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date()) }

    val useOffice = officeVouchers.isNotEmpty()
    val fromIso = remember(fromDate) { fromDate?.let(::displayDateToIsoDay) }
    val toIso = remember(toDate) { toDate?.let(::displayDateToIsoDay) }

    val computed = remember(useOffice, officeVouchers, fallbackEntries, fromIso, toIso, customer.openingBalanceDr) {
        if (useOffice) {
            val beforePeriod = if (fromIso != null) {
                officeVouchers.filter { it.voucherDay < fromIso }.sumOf { it.debitInr - it.creditInr }
            } else 0.0
            val inRange = officeVouchers.filter { v ->
                (fromIso == null || v.voucherDay >= fromIso) && (toIso == null || v.voucherDay <= toIso)
            }
            var running = beforePeriod
            val rows = inRange.map { v ->
                running += (v.debitInr - v.creditInr)
                LedgerRowWithBalance(
                    id = v.id,
                    date = v.date,
                    typeLabel = v.referenceType,
                    referenceTitle = if (v.referenceId != null) "${v.referenceType} #${v.referenceId}" else v.referenceType,
                    narration = v.narration,
                    debitInr = v.debitInr,
                    creditInr = v.creditInr,
                    runningBalanceDr = running
                )
            }
            Triple(beforePeriod, rows, inRange)
        } else {
            val sortedFallback = fallbackEntries.sortedBy { displayDateToIsoDay(it.entryDate) }
            val beforePeriod = customer.openingBalanceDr + if (fromIso != null) {
                sortedFallback.filter { displayDateToIsoDay(it.entryDate) < fromIso }.sumOf {
                    if (it.entryTypeEnum == LedgerEntryType.DEBIT_SALE) it.amountInr else -it.amountInr
                }
            } else 0.0
            val inRange = sortedFallback.filter { e ->
                val day = displayDateToIsoDay(e.entryDate)
                (fromIso == null || day >= fromIso) && (toIso == null || day <= toIso)
            }
            var running = beforePeriod
            val rows = inRange.map { e ->
                val dr = if (e.entryTypeEnum == LedgerEntryType.DEBIT_SALE) e.amountInr else 0.0
                val cr = if (e.entryTypeEnum == LedgerEntryType.CREDIT_PAYMENT) e.amountInr else 0.0
                running += (dr - cr)
                LedgerRowWithBalance(
                    id = e.id,
                    date = e.entryDate,
                    typeLabel = if (cr > 0.0) "Receipt" else "Standard Sale",
                    referenceTitle = "${if (cr > 0.0) "Receipt" else "Bill"} ${e.referenceNo}",
                    narration = e.notes,
                    debitInr = dr,
                    creditInr = cr,
                    runningBalanceDr = running
                )
            }
            Triple(beforePeriod, rows, emptyList<OfficeLedgerVoucher>())
        }
    }

    val periodOpening = computed.first
    val displayRows = computed.second
    val filteredOfficeVouchers = computed.third
    val totalDebit = displayRows.sumOf { it.debitInr }
    val totalCredit = displayRows.sumOf { it.creditInr }
    val closing = periodOpening + totalDebit - totalCredit
    val closingSuffix = if (closing >= 0.0) "Dr" else "Cr"
    val opSuffix = if (periodOpening >= 0.0) "Dr" else "Cr"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("party_ledger_detail_screen")
    ) {
        ModuleTopHeader(
            title = "Ledger",
            syncTelemetry = syncTelemetry,
            auditState = auditState,
            onBack = onBack,
            onSyncNow = onSyncNow,
            onOpenSettings = onOpenSettings
        )

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

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassTelemetryCard(accentColor = MoltenGold) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MoltenGoldGlow,
                                modifier = Modifier.weight(1f)
                            )
                            if (useOffice) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified Office Ledger",
                                    tint = EmeraldOptimal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = buildString {
                                if (customer.numericId >= 0) append("Customer ID #${customer.numericId} • ")
                                append("Mobile: ${customer.mobile.ifBlank { "Not Provided" }} • ${customer.city}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "Displayed vouchers: ${displayRows.size} / ${maxOf(displayRows.size, if (useOffice) officeVouchers.size else fallbackEntries.size)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "Period Op Bal: ₹${String.format(Locale.US, "%.2f", abs(periodOpening))} $opSuffix",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldOptimal
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                runCatching {
                                    val pdf = if (useOffice) {
                                        ErpPdfGenerator.generateOfficeLedgerPdf(
                                            context = context,
                                            customer = customer,
                                            vouchers = filteredOfficeVouchers,
                                            periodOpeningDr = periodOpening,
                                            fromDate = fromDate ?: "Beginning",
                                            toDate = toDate ?: today
                                        )
                                    } else {
                                        ErpPdfGenerator.generatePartyLedgerPdf(
                                            context = context,
                                            customer = customer,
                                            entries = fallbackEntries,
                                            fromDate = fromDate ?: "Beginning",
                                            toDate = toDate ?: today
                                        )
                                    }
                                    ErpPdfGenerator.sharePdfFile(context, pdf, "Ledger Statement - ${customer.name}")
                                }.onFailure {
                                    Toast.makeText(context, "PDF error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ledger_print_whatsapp_pdf"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TelemetryCyan,
                                contentColor = ObsidianVoid
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Print / WhatsApp PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            val groupedByDate = displayRows.groupBy { it.date }
            groupedByDate.forEach { (dateStr, dayTxns) ->
                val dayDr = dayTxns.sumOf { it.debitInr }
                val dayCr = dayTxns.sumOf { it.creditInr }
                item(key = "hdr_$dateStr") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TelemetryCyan.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(dateStr, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = TelemetryCyan)
                        Text(
                            text = "Dr ₹${String.format(Locale.US, "%.2f", dayDr)}   Cr ₹${String.format(Locale.US, "%.2f", dayCr)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
                items(dayTxns, key = { it.id }) { txn ->
                    val isReceipt = txn.creditInr > 0.0 && txn.debitInr == 0.0
                    val accent = if (isReceipt) EmeraldOptimal else MoltenGold
                    val runSuffix = if (txn.runningBalanceDr >= 0.0) "Dr" else "Cr"
                    GlassTelemetryCard(accentColor = accent) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accent.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = txn.typeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = accent
                                    )
                                }
                                Text(
                                    text = txn.referenceTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                if (txn.narration.isNotBlank()) {
                                    Text(txn.narration, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Text(
                                    text = "Bal: ₹${String.format(Locale.US, "%.2f", abs(txn.runningBalanceDr))} $runSuffix",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TelemetryCyan
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isReceipt) "Credit" else "Debit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", if (isReceipt) txn.creditInr else txn.debitInr)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = accent
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Debit / Credit / Balance Summary Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SummaryMetricRow("Period Debit", "₹${String.format(Locale.US, "%.2f", totalDebit)}", MoltenGoldGlow)
            SummaryMetricRow("Period Credit", "₹${String.format(Locale.US, "%.2f", totalCredit)}", EmeraldOptimal)
            HorizontalDivider(color = GlassBorder)
            SummaryMetricRow(
                "Closing Balance",
                "₹${String.format(Locale.US, "%.2f", abs(closing))} $closingSuffix",
                TelemetryCyan,
                bold = true
            )
        }
    }
}

/**
 * 2. DAY BOOK MODULE (With exact Office DayBook rows + DatePicker range filter)
 */
@Composable
fun DayBookModuleScreen(
    rows: List<DayBookRow>,
    salesRegister: List<SalesRegisterEntry>,
    syncTelemetry: SyncTelemetry,
    auditState: OfficeSnapshotAuditState,
    onOpenInvoice: (SalesRegisterEntry) -> Unit,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BackHandler(onBack = onBack)
    var search by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf<String?>(null) }
    var toDate by remember { mutableStateOf<String?>(null) }

    val filtered = remember(rows, search, fromDate, toDate) {
        rows.filter { r ->
            val matchesDate = isDateWithinRange(r.date, fromDate, toDate)
            val matchesQuery = search.isBlank() ||
                r.accountName.contains(search, true) ||
                r.description.contains(search, true) ||
                r.voucherType.contains(search, true) ||
                r.voucherNo.contains(search, true)
            matchesDate && matchesQuery
        }
    }
    val totalDr = filtered.sumOf { it.debitInr }
    val totalCr = filtered.sumOf { it.creditInr }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("day_book_module")
    ) {
        ModuleTopHeader("Day Book", syncTelemetry, auditState, onBack, onSyncNow, onOpenSettings)
        Text(
            text = auditState.companyName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MoltenGoldGlow,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        DateRangeAndSearchBar(
            searchQuery = search,
            onSearchChange = { search = it },
            fromDate = fromDate,
            toDate = toDate,
            onFromDateSelected = { fromDate = it },
            onToDateSelected = { toDate = it },
            onRefresh = onSyncNow
        )

        // Sticky Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TelemetryCyan.copy(alpha = 0.22f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("Type", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary, modifier = Modifier.weight(0.95f))
            Text("Acc Name", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary, modifier = Modifier.weight(1.3f))
            Text("Description / Amt", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            val grouped = filtered.groupBy { it.date }
            grouped.forEach { (dateStr, list) ->
                item(key = "db_date_$dateStr") {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MoltenGold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TitaniumSurface)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                items(list, key = { it.id }) { row ->
                    val linkedSale = row.linkedSaleId?.let { id -> salesRegister.firstOrNull { it.id == id } }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidSpringClickable { linkedSale?.let(onOpenInvoice) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(0.95f)) {
                            Text(
                                text = row.voucherType,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (row.creditInr > 0.0) EmeraldOptimal else TelemetryCyan
                            )
                            Text(row.voucherNo, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Text(
                            text = row.accountName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary,
                            modifier = Modifier.weight(1.3f)
                        )
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = row.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val amt = if (row.debitInr > 0.0) row.debitInr else row.creditInr
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", amt)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (row.creditInr > 0.0) EmeraldOptimal else MoltenGoldGlow
                            )
                        }
                    }
                    HorizontalDivider(color = GlassBorder)
                }
            }
        }

        // Day Book Dr / Cr Summary Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Summary (${filtered.size} vouchers)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            SummaryMetricRow("Dr (Sales / Debits)", "₹${String.format(Locale.US, "%.2f", totalDr)}", MoltenGoldGlow)
            SummaryMetricRow("Cr (Receipts / Credits)", "₹${String.format(Locale.US, "%.2f", totalCr)}", EmeraldOptimal)
        }
    }
}

/**
 * 4. OUTSTANDINGS MODULE (Sundry Debtors & Arrears with strict customerId drill-down to Ledger)
 */
@Composable
fun OutstandingsModuleScreen(
    customers: List<OfficeCustomer>,
    ledgerEntries: List<PartyLedgerEntry>,
    officeLedgerVouchers: List<OfficeLedgerVoucher>,
    syncTelemetry: SyncTelemetry,
    auditState: OfficeSnapshotAuditState,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var selectedCustomer by remember { mutableStateOf<OfficeCustomer?>(null) }
    var arrearsOnly by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    if (selectedCustomer != null) {
        val cust = selectedCustomer!!
        val matchedOfficeVouchers = remember(cust, officeLedgerVouchers) {
            if (cust.numericId >= 0) {
                officeLedgerVouchers.filter {
                    it.accountType.equals("customer", ignoreCase = true) && it.accountId == cust.numericId
                }
            } else emptyList()
        }
        PartyLedgerDetailScreen(
            customer = cust,
            officeVouchers = matchedOfficeVouchers,
            fallbackEntries = ledgerEntries.filter { it.partyName.equals(cust.name, ignoreCase = true) },
            syncTelemetry = syncTelemetry,
            auditState = auditState,
            onBack = { selectedCustomer = null },
            onSyncNow = onSyncNow,
            onOpenSettings = onOpenSettings
        )
        return
    }

    BackHandler(onBack = onBack)
    val filtered = remember(customers, search, arrearsOnly) {
        customers.filter { c ->
            val matchesSearch = search.isBlank() || c.name.contains(search, true) || c.mobile.contains(search, true)
            val matchesArrears = !arrearsOnly || c.closingBalanceDr >= 25000.0
            matchesSearch && matchesArrears
        }.sortedByDescending { it.closingBalanceDr }
    }
    val totalDues = filtered.filter { it.closingBalanceDr > 0.0 }.sumOf { it.closingBalanceDr }
    val totalAdvances = filtered.filter { it.closingBalanceDr < 0.0 }.sumOf { abs(it.closingBalanceDr) }
    val netOutstanding = totalDues - totalAdvances

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("outstandings_module")
    ) {
        ModuleTopHeader("Outstandings", syncTelemetry, auditState, onBack, onSyncNow, onOpenSettings)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Outstanding Accounts", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !arrearsOnly,
                    onClick = { arrearsOnly = false },
                    label = { Text("Sundry Debtors") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelemetryCyan,
                        selectedLabelColor = ObsidianVoid
                    )
                )
                FilterChip(
                    selected = arrearsOnly,
                    onClick = { arrearsOnly = true },
                    label = { Text("Arrears") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrimsonCritical,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search Account", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TelemetryCyan) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                GlassTelemetryCard(accentColor = EmeraldOptimal) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SummaryMetricRow(
                            "Total Outstanding",
                            "₹${String.format(Locale.US, "%.2f", abs(netOutstanding))} ${if (netOutstanding >= 0) "Dr" else "Cr"}",
                            MoltenGoldGlow,
                            bold = true
                        )
                        HorizontalDivider(color = GlassBorder)
                        SummaryMetricRow("Customer Dues", "₹${String.format(Locale.US, "%.2f", totalDues)}", EmeraldOptimal)
                        SummaryMetricRow("Customer Advances", "₹${String.format(Locale.US, "%.2f", totalAdvances)}", TelemetryCyan)
                        Text(
                            text = "${filtered.size} accounts • ${if (auditState.isAuditPassed) "Reconciled with Ledger (PASS)" else "Live Cloud Snapshot"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            items(filtered, key = { it.id }) { cust ->
                val isDue = cust.closingBalanceDr >= 0.0
                GlassTelemetryCard(
                    accentColor = if (isDue) EmeraldOptimal else TelemetryCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidSpringClickable { selectedCustomer = cust }
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
                                .clip(CircleShape)
                                .background(EmeraldOptimal.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cust.name.firstOrNull()?.uppercase() ?: "A",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldOptimal
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cust.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            Text(
                                text = if (cust.numericId >= 0) "ID #${cust.numericId} • ${cust.category}" else cust.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", abs(cust.closingBalanceDr))} ${if (isDue) "Dr" else "Cr"}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (isDue) EmeraldOptimal else TelemetryCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SummaryMetricRow(
    label: String,
    value: String,
    valueColor: Color,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
            else MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}
