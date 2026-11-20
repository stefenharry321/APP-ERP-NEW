package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.GstInvoice
import com.example.data.model.InvoicePaymentStatus
import com.example.data.model.LedgerEntryType
import com.example.data.model.PartyLedgerEntry
import com.example.data.model.SupabaseTableMapping
import com.example.ui.theme.EmeraldOptimal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MoltenGold
import com.example.ui.theme.MoltenGoldGlow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateGstInvoiceSheet(
    onDismiss: () -> Unit,
    onCreate: (GstInvoice) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var invoiceNo by remember { mutableStateOf("KF/26-27/${(1000..9999).random()}") }
    var partyName by remember { mutableStateOf("") }
    var partyGstin by remember { mutableStateOf("09AAACK1234F1Z5") }
    var hsnCode by remember { mutableStateOf("1101") }
    var itemDesc by remember { mutableStateOf("Chakki Fresh Whole Wheat Atta 10kg") }
    var qtyText by remember { mutableStateOf("15.0") }
    var rateText by remember { mutableStateOf("35000") }
    var gstRate by remember { mutableDoubleStateOf(5.0) }

    val qty = qtyText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val rate = rateText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val taxable = qty * rate
    val cgst = taxable * gstRate / 200.0
    val sgst = taxable * gstRate / 200.0
    val grandTotal = taxable + cgst + sgst

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
                text = "Generate GST Tax Invoice",
                style = MaterialTheme.typography.headlineSmall,
                color = MoltenGoldGlow
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = invoiceNo,
                    onValueChange = { invoiceNo = it },
                    label = { Text("Invoice No.") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = hsnCode,
                    onValueChange = { hsnCode = it },
                    label = { Text("HSN") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }
            OutlinedTextField(
                value = partyName,
                onValueChange = { partyName = it },
                label = { Text("Buyer / Distributor Party Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gst_party_name_input")
            )
            OutlinedTextField(
                value = partyGstin,
                onValueChange = { partyGstin = it.uppercase(Locale.US) },
                label = { Text("Party GSTIN (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = itemDesc,
                onValueChange = { itemDesc = it },
                label = { Text("Flour Product & Pack Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Quantity (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Rate ₹ / MT") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("GST Slab (CGST + SGST)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.0, 5.0, 12.0, 18.0).forEach { slab ->
                    FilterChip(
                        selected = gstRate == slab,
                        onClick = { gstRate = slab },
                        label = { Text("${slab.toInt()}% GST") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MoltenGold.copy(alpha = 0.22f),
                            selectedLabelColor = MoltenGoldGlow
                        )
                    )
                }
            }

            // Live GST Tax Summary Preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TitaniumSurface)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaxRow("Taxable Value", taxable)
                TaxRow("CGST (${gstRate / 2}%)", cgst)
                TaxRow("SGST (${gstRate / 2}%)", sgst)
                HorizontalDivider(color = GlassBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Grand Total", style = MaterialTheme.typography.titleMedium, color = MoltenGoldGlow)
                    Text(
                        "₹${String.format(Locale.US, "%,.2f", grandTotal)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MoltenGoldGlow
                    )
                }
            }

            Button(
                onClick = {
                    onCreate(
                        GstInvoice(
                            invoiceNumber = invoiceNo.trim().ifBlank { "KF/26-27/${(1000..9999).random()}" },
                            partyName = partyName.trim(),
                            partyGstin = partyGstin.trim(),
                            hsnCode = hsnCode.trim().ifBlank { "1101" },
                            itemDescription = itemDesc.trim().ifBlank { "Whole Wheat Atta" },
                            quantityMt = qty.coerceAtLeast(0.1),
                            taxableAmountInr = taxable,
                            gstRatePct = gstRate,
                            cgstInr = cgst,
                            sgstInr = sgst,
                            totalAmountInr = grandTotal,
                            paymentStatus = InvoicePaymentStatus.UNPAID.name,
                            invoiceDate = today,
                            syncSource = "FIREBASE"
                        )
                    )
                },
                enabled = partyName.isNotBlank() && grandTotal > 0.0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_gst_invoice_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoltenGold,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Generate GST Bill & Post to Khata", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TaxRow(label: String, amount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            "₹${String.format(Locale.US, "%,.2f", amount)}",
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddLedgerEntrySheet(
    onDismiss: () -> Unit,
    onSave: (PartyLedgerEntry) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var partyName by remember { mutableStateOf("") }
    var partyCity by remember { mutableStateOf("Lucknow") }
    var partyPhone by remember { mutableStateOf("+91-") }
    var entryType by remember { mutableStateOf(LedgerEntryType.CREDIT_PAYMENT) }
    var amountText by remember { mutableStateOf("") }
    var referenceNo by remember { mutableStateOf("UPI-${(100000..999999).random()}") }
    var notes by remember { mutableStateOf("") }

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
                text = "Record Party Khata Entry",
                style = MaterialTheme.typography.headlineSmall,
                color = EmeraldOptimal
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LedgerEntryType.entries.forEach { t ->
                    FilterChip(
                        selected = entryType == t,
                        onClick = { entryType = t },
                        label = { Text(t.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (t == LedgerEntryType.CREDIT_PAYMENT) {
                                EmeraldOptimal.copy(alpha = 0.22f)
                            } else {
                                MoltenGold.copy(alpha = 0.22f)
                            }
                        )
                    )
                }
            }

            OutlinedTextField(
                value = partyName,
                onValueChange = { partyName = it },
                label = { Text("Party / Distributor Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ledger_party_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = partyCity,
                    onValueChange = { partyCity = it },
                    label = { Text("City / Mandi") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = partyPhone,
                    onValueChange = { partyPhone = it },
                    label = { Text("Phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹ INR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ledger_amount_input")
                )
                OutlinedTextField(
                    value = referenceNo,
                    onValueChange = { referenceNo = it },
                    label = { Text("UTR / Bill Ref") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Narration / Notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 0.0
                    onSave(
                        PartyLedgerEntry(
                            partyName = partyName.trim(),
                            partyPhone = partyPhone.trim(),
                            partyCity = partyCity.trim().ifBlank { "UP Mandi" },
                            entryType = entryType.name,
                            amountInr = amt,
                            referenceNo = referenceNo.trim().ifBlank { "REF-${System.currentTimeMillis() % 10000}" },
                            notes = notes.trim(),
                            entryDate = today,
                            syncSource = "FIREBASE"
                        )
                    )
                },
                enabled = partyName.isNotBlank() && (amountText.toDoubleOrNull() ?: 0.0) > 0.0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_ledger_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldOptimal,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save to Party Khata & Sync", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseTableMapperSheet(
    currentMapping: SupabaseTableMapping,
    discoveredTables: List<String>,
    onDismiss: () -> Unit,
    onSaveMapping: (SupabaseTableMapping) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var invTable by remember { mutableStateOf(currentMapping.inventoryTable) }
    var shpTable by remember { mutableStateOf(currentMapping.shipmentsTable) }
    var millTable by remember { mutableStateOf(currentMapping.millsTable) }
    var billTable by remember { mutableStateOf(currentMapping.invoicesTable) }
    var ledTable by remember { mutableStateOf(currentMapping.ledgerTable) }

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Map Windows ERP Supabase Tables",
                style = MaterialTheme.typography.headlineSmall,
                color = TelemetryCyan
            )
            Text(
                text = "Enter the exact table names from your existing Windows ERP Supabase schema so live records sync 1:1.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (discoveredTables.isNotEmpty()) {
                Text(
                    text = "Detected Live Tables: ${discoveredTables.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldOptimal
                )
            }

            OutlinedTextField(
                value = invTable,
                onValueChange = { invTable = it },
                label = { Text("Inventory / Stock Table Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = shpTable,
                onValueChange = { shpTable = it },
                label = { Text("Shipments / Dispatch Table Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = billTable,
                onValueChange = { billTable = it },
                label = { Text("GST Invoices / Bills Table Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = ledTable,
                onValueChange = { ledTable = it },
                label = { Text("Party Ledger / Khata Table Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = millTable,
                onValueChange = { millTable = it },
                label = { Text("Milling Equipment Table Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    onSaveMapping(
                        SupabaseTableMapping(
                            inventoryTable = invTable.trim().ifBlank { "inventory_items" },
                            shipmentsTable = shpTable.trim().ifBlank { "shipments" },
                            millsTable = millTable.trim().ifBlank { "mill_equipment" },
                            invoicesTable = billTable.trim().ifBlank { "gst_invoices" },
                            ledgerTable = ledTable.trim().ifBlank { "party_ledger" }
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_table_mapping_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TelemetryCyan,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Table Mapping & Sync Now", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
