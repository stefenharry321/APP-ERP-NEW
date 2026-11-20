package com.example.data.model

enum class WorkspaceModule(val title: String, val subtitle: String) {
    ACCOUNTS("Accounts", "Customer directory & balances"),
    DAY_BOOK("Day Book", "Daily sales & receipt log"),
    LEDGER("Ledger", "Party khata & PDF statement"),
    OUTSTANDINGS("Outstandings", "Sundry debtors & arrears"),
    SALES_REGISTER("Sales Register", "Invoices & Office PDFs"),
    RECEIPT_PAYMENT("Receipt & Payment", "Cash, UPI & RTGS vouchers"),
    PRODUCTION_REPORTS("Production Reports", "Atta, Chokar & Wastage"),
    CHOKAR_STOCK("Chokar Stock", "Loose, packed & bag stock"),
    CHOKAR_SALES("Chokar Customer Sales", "Customer-wise Chokar bills")
}

data class SupabaseConnectionConfig(
    val supabaseUrl: String = "",
    val supabaseAnonKey: String = "",
    val mobilePairCode: String = "",
    val autoSyncEnabled: Boolean = true,
    val pdfStorageBucket: String = "invoices"
) {
    val hasEndpoint: Boolean
        get() = supabaseUrl.startsWith("https://") && supabaseAnonKey.length > 20

    val hasValidPairCode: Boolean
        get() = mobilePairCode.trim().length >= 32

    val isManuallyPaired: Boolean
        get() = hasEndpoint && hasValidPairCode
}

data class InvoiceLineItem(
    val productName: String = "Atta",
    val hsnCode: String = "1101",
    val packKg: Double = 47.0,
    val bags: Int = 10,
    val ratePerKgInclGst: Double = 33.0,
    val rateUnit: String = "Kg",
    val gstRatePct: Double = 5.0
) {
    val totalWeightKg: Double
        get() = packKg * bags

    val grossAmountInr: Double
        get() = totalWeightKg * ratePerKgInclGst

    val taxableAmountInr: Double
        get() = if (gstRatePct > 0.0) grossAmountInr / (1.0 + gstRatePct / 100.0) else grossAmountInr

    val ratePerPackExclGst: Double
        get() = if (bags > 0) taxableAmountInr / bags else 0.0
}

data class SalesRegisterEntry(
    val id: String,
    val saleNo: String,
    val invoiceNo: String,
    val date: String,
    val partyName: String,
    val partyAddress: String = "Mohanpura, Kasganj, Uttar Pradesh - 09",
    val partyMobile: String = "",
    val partyGstin: String = "Unregistered / Not Provided",
    val partyState: String = "Uttar Pradesh (09)",
    val vehicleNo: String = "",
    val paymentTerms: String = "As per sale entry",
    val items: List<InvoiceLineItem>,
    val totalQuantityKgOverride: Double? = null,
    val gstRatePct: Double = 5.0,
    val isInterState: Boolean = false,
    val totalAmountInr: Double,
    val balanceDueInr: Double = totalAmountInr,
    val paymentStatus: String = InvoicePaymentStatus.UNPAID.name,
    val invoiceRevision: String = "",
    val invoiceFilename: String = "",
    val inlinePdfBase64: String? = null,
    val officePdfUrl: String? = null,
    val syncSource: String = "FIREBASE"
) {
    val hasOfficePdfPointer: Boolean
        get() = !inlinePdfBase64.isNullOrBlank() || invoiceRevision.isNotBlank() || !officePdfUrl.isNullOrBlank()

    val totalBags: Int
        get() = items.sumOf { it.bags }

    val totalWeightKg: Double
        get() = totalQuantityKgOverride?.takeIf { it > 0.0 } ?: items.sumOf { it.totalWeightKg }

    val taxableValueInr: Double
        get() = items.sumOf { it.taxableAmountInr }
            .takeIf { it > 0.0 }
            ?: (if (gstRatePct > 0.0) totalAmountInr / (1.0 + gstRatePct / 100.0) else totalAmountInr)

    val totalGstInr: Double
        get() = (totalAmountInr - taxableValueInr).coerceAtLeast(0.0)

    val cgstInr: Double
        get() = if (isInterState) 0.0 else totalGstInr / 2.0

    val sgstInr: Double
        get() = if (isInterState) 0.0 else totalGstInr / 2.0

    val igstInr: Double
        get() = if (isInterState) totalGstInr else 0.0
}

data class OfficeCustomer(
    val id: String,
    val numericId: Int = -1,
    val name: String,
    val code: String = "",
    val category: String = "Customer",
    val mobile: String = "",
    val city: String = "Kasganj, UP",
    val address: String = "",
    val gstin: String = "",
    val openingBalanceDr: Double = 0.0,
    val closingBalanceDr: Double = 0.0,
    val totalEntries: Int = 0,
    val syncSource: String = "FIREBASE"
) {
    val isAdvance: Boolean
        get() = closingBalanceDr < 0.0
}

data class OfficeLedgerVoucher(
    val id: String,
    val accountId: Int,
    val accountType: String,
    val partyName: String,
    val date: String,
    val voucherDay: String,
    val statementSequence: Int = 0,
    val referenceType: String,
    val referenceId: Int? = null,
    val narration: String,
    val debitInr: Double,
    val creditInr: Double,
    val syncSource: String = "WINDOWS_RPC"
)

data class OfficeReceiptPayment(
    val id: String,
    val kind: String, // "Receipt" or "Payment"
    val date: String,
    val partyName: String,
    val amountInr: Double,
    val mode: String = "Cash",
    val discountInr: Double = 0.0,
    val referenceNo: String = "",
    val narration: String = ""
)

data class DayBookRow(
    val id: String,
    val date: String,
    val voucherNo: String,
    val voucherType: String,
    val accountName: String,
    val description: String,
    val debitInr: Double,
    val creditInr: Double,
    val linkedSaleId: String? = null
)

data class ProductionReportEntry(
    val id: String,
    val date: String,
    val batchCode: String,
    val millUnit: String,
    val wheatProcessedKg: Double,
    val attaProducedKg: Double,
    val chokarProducedKg: Double,
    val wastageKg: Double = 0.0,
    val chokarPackedKg: Double = 0.0,
    val chokarLooseKg: Double = 0.0,
    val remarks: String = "",
    val shiftName: String = "Day Shift",
    val syncSource: String = "FIREBASE"
) {
    val extractionPct: Double
        get() = if (wheatProcessedKg > 0.0) ((attaProducedKg / wheatProcessedKg) * 100.0).coerceIn(0.0, 100.0) else 0.0
}

data class ChokarPackBalance(
    val packKg: Double,
    val bags: Double
)

data class ChokarPackingVoucher(
    val id: String,
    val date: String,
    val packKg: Double,
    val bags: Int,
    val totalKg: Double
)

data class ChokarStockTotals(
    val producedKg: Double = 0.0,
    val productionPackedKg: Double = 0.0,
    val laterPackedKg: Double = 0.0,
    val soldKg: Double = 0.0,
    val looseRemainingKg: Double = 0.0,
    val packedRemainingKg: Double = 0.0,
    val legacyUnknownPackKg: Double = 0.0,
    val packBalances: List<ChokarPackBalance> = emptyList(),
    val packingVouchers: List<ChokarPackingVoucher> = emptyList(),
    val issues: List<String> = emptyList(),
    val hasOfficeChokarReport: Boolean = false
)

data class ChokarSaleEntry(
    val id: String,
    val billNo: String,
    val date: String,
    val customerId: String = "",
    val partyName: String,
    val partyMobile: String = "",
    val packKg: Double = 40.0,
    val bags: Int = 25,
    val totalWeightKgOverride: Double? = null,
    val ratePerKg: Double = 21.5,
    val totalAmountInr: Double = packKg * bags * ratePerKg,
    val legacyUnmatched: Boolean = false,
    val syncSource: String = "FIREBASE"
) {
    val totalWeightKg: Double
        get() = totalWeightKgOverride ?: (packKg * bags)
}

data class OfficeSnapshotAuditState(
    val companyName: String = "KUMAR FOODS PROCESSING COMPANY",
    val snapshotGeneratedAt: String = "",
    val cloudUploadedAt: String = "",
    val isLiveFresh: Boolean = false,
    val isAuditPassed: Boolean = false,
    val auditIssues: List<String> = emptyList(),
    val syncDriftWarning: String = "",
    val pdfPointerCount: Int = 0,
    val totalSalesCount: Int = 0
)

data class RenderedPdfPreviewRequest(
    val title: String,
    val filename: String,
    val pdfBytes: ByteArray
)
