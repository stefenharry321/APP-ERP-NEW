package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class InventoryCategory(val label: String) {
    FINISHED_ATTA("Finished Atta"),
    RAW_WHEAT("Raw Wheat"),
    BYPRODUCT("Bran & Chokar"),
    PACKAGING("Bags & Packaging");

    companion object {
        fun fromRaw(raw: String): InventoryCategory =
            entries.firstOrNull { it.name == raw } ?: FINISHED_ATTA
    }
}

enum class ShipmentStatus(val label: String, val stepIndex: Int) {
    LOADING("Loading", 0),
    IN_TRANSIT("In Transit", 1),
    OUT_FOR_DELIVERY("Out for Delivery", 2),
    DELIVERED("Delivered", 3);

    fun nextStatus(): ShipmentStatus? = when (this) {
        LOADING -> IN_TRANSIT
        IN_TRANSIT -> OUT_FOR_DELIVERY
        OUT_FOR_DELIVERY -> DELIVERED
        DELIVERED -> null
    }

    companion object {
        fun fromRaw(raw: String): ShipmentStatus =
            entries.firstOrNull { it.name == raw } ?: LOADING
    }
}

enum class MillMachineType(val label: String) {
    ROLLER_MILL("Roller Mill"),
    CHAKKI_STONE("Chakki Stone"),
    PLANSIFTER("Plansifter"),
    PACKAGING_LINE("Packaging Line"),
    SILO_CONVEYOR("Silo Conveyor");

    companion object {
        fun fromRaw(raw: String): MillMachineType =
            entries.firstOrNull { it.name == raw } ?: ROLLER_MILL
    }
}

enum class MillStatus(val label: String) {
    OPTIMAL("Optimal"),
    WARNING("Watch"),
    CRITICAL("Critical"),
    MAINTENANCE("Scheduled");

    companion object {
        fun fromRaw(raw: String): MillStatus =
            entries.firstOrNull { it.name == raw } ?: OPTIMAL
    }
}

@Keep
@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val sku: String = "",
    val name: String = "",
    val category: String = InventoryCategory.FINISHED_ATTA.name,
    val warehouse: String = "Main Mill Plant",
    val quantityMt: Double = 0.0,
    val capacityMt: Double = 100.0,
    val lowThresholdMt: Double = 20.0,
    val unitPriceInr: Double = 34000.0,
    val batchCode: String = "",
    val syncSource: String = "FIREBASE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val fillFraction: Float
        get() = if (capacityMt > 0.0) (quantityMt / capacityMt).toFloat().coerceIn(0f, 1f) else 0f

    val belowThreshold: Boolean
        get() = quantityMt <= lowThresholdMt

    val categoryEnum: InventoryCategory
        get() = InventoryCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class Shipment(
    @DocumentId val id: String = "",
    val trackingCode: String = "",
    val clientName: String = "",
    val destination: String = "",
    val productName: String = "",
    val weightMt: Double = 10.0,
    val truckNumber: String = "",
    val status: String = ShipmentStatus.LOADING.name,
    val etaText: String = "",
    val warehouseOrigin: String = "Main Mill Plant",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: ShipmentStatus
        get() = ShipmentStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class MillEquipment(
    @DocumentId val id: String = "",
    val machineName: String = "",
    val machineType: String = MillMachineType.ROLLER_MILL.name,
    val zone: String = "Grinding Floor A",
    val healthScore: Int = 90,
    val temperatureC: Double = 48.0,
    val vibrationMmS: Double = 1.8,
    val runningHours: Double = 1200.0,
    val outputRateMtHr: Double = 5.5,
    val status: String = MillStatus.OPTIMAL.name,
    val nextServiceDate: String = "2026-12-01",
    val lastAiAdvice: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val machineTypeEnum: MillMachineType
        get() = MillMachineType.fromRaw(machineType)

    val statusEnum: MillStatus
        get() = MillStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AiAnalyticsReport(
    @DocumentId val id: String = "",
    val reportType: String = "SALES_FORECAST",
    val title: String = "",
    val summary: String = "",
    val predictedGrowthPct: Double = 0.0,
    val recommendedRestockMt: Double = 0.0,
    val forecastLabels: List<String> = emptyList(),
    val forecastValues: List<Double> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class InvoicePaymentStatus(val label: String) {
    UNPAID("Unpaid"),
    PARTIAL("Partial"),
    PAID("Paid");

    companion object {
        fun fromRaw(raw: String): InvoicePaymentStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNPAID
    }
}

enum class LedgerEntryType(val label: String) {
    DEBIT_SALE("Sale (Debit)"),
    CREDIT_PAYMENT("Payment (Credit)");

    companion object {
        fun fromRaw(raw: String): LedgerEntryType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEBIT_SALE
    }
}

@Keep
@IgnoreExtraProperties
data class GstInvoice(
    @DocumentId val id: String = "",
    val invoiceNumber: String = "",
    val partyName: String = "",
    val partyGstin: String = "",
    val hsnCode: String = "1101",
    val itemDescription: String = "",
    val quantityMt: Double = 1.0,
    val taxableAmountInr: Double = 0.0,
    val gstRatePct: Double = 5.0,
    val cgstInr: Double = 0.0,
    val sgstInr: Double = 0.0,
    val totalAmountInr: Double = 0.0,
    val paymentStatus: String = InvoicePaymentStatus.UNPAID.name,
    val invoiceDate: String = "2026-11-20",
    val syncSource: String = "FIREBASE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val paymentStatusEnum: InvoicePaymentStatus
        get() = InvoicePaymentStatus.fromRaw(paymentStatus)
}

@Keep
@IgnoreExtraProperties
data class PartyLedgerEntry(
    @DocumentId val id: String = "",
    val partyName: String = "",
    val partyPhone: String = "",
    val partyCity: String = "Lucknow",
    val entryType: String = LedgerEntryType.DEBIT_SALE.name,
    val amountInr: Double = 0.0,
    val referenceNo: String = "",
    val notes: String = "",
    val entryDate: String = "2026-11-20",
    val syncSource: String = "FIREBASE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val entryTypeEnum: LedgerEntryType
        get() = LedgerEntryType.fromRaw(entryType)
}

data class PartyBalanceSummary(
    val partyName: String,
    val partyCity: String,
    val partyPhone: String,
    val totalBilledInr: Double,
    val totalReceivedInr: Double,
    val outstandingInr: Double,
    val entryCount: Int
)

data class SupabaseTableMapping(
    val inventoryTable: String = "inventory_items",
    val shipmentsTable: String = "shipments",
    val millsTable: String = "mill_equipment",
    val invoicesTable: String = "gst_invoices",
    val ledgerTable: String = "party_ledger"
)

data class LiveSupabaseTableData(
    val tableName: String = "",
    val columns: List<String> = emptyList(),
    val rows: List<Map<String, String>> = emptyList(),
    val latencyMs: Long = 0L,
    val errorMessage: String? = null
)

enum class SupabaseBridgeState {
    CONNECTED_LIVE,
    SYNCING,
    AWAITING_CREDENTIALS,
    ERROR
}

data class SyncTelemetry(
    val bridgeState: SupabaseBridgeState = SupabaseBridgeState.AWAITING_CREDENTIALS,
    val lastSyncLabel: String = "Cloud Realtime Active",
    val latencyMs: Long = 0L,
    val syncedRowCount: Int = 0,
    val realtimeSocketActive: Boolean = false,
    val discoveredTables: List<String> = emptyList(),
    val statusMessage: String = "Firestore live sync active. Configure SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets for Windows ERP bridge."
)

data class CriticalAlert(
    val id: String,
    val title: String,
    val subtitle: String,
    val severity: AlertSeverity,
    val sourceType: String
)

enum class AlertSeverity {
    CRITICAL,
    WARNING
}
