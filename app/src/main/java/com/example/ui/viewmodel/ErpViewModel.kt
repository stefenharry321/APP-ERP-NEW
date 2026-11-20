package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SupabasePreferencesStore
import com.example.data.model.AiAnalyticsReport
import com.example.data.model.AlertSeverity
import com.example.data.model.ChokarSaleEntry
import com.example.data.model.ChokarStockTotals
import com.example.data.model.CriticalAlert
import com.example.data.model.DayBookRow
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryCategory
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import com.example.data.model.InvoicePaymentStatus
import com.example.data.model.LedgerEntryType
import com.example.data.model.LiveSupabaseTableData
import com.example.data.model.MillEquipment
import com.example.data.model.MillStatus
import com.example.data.model.OfficeCustomer
import com.example.data.model.OfficeLedgerVoucher
import com.example.data.model.OfficeReceiptPayment
import com.example.data.model.OfficeSnapshotAuditState
import com.example.data.model.PartyBalanceSummary
import com.example.data.model.PartyLedgerEntry
import com.example.data.model.ProductionReportEntry
import com.example.data.model.RenderedPdfPreviewRequest
import com.example.data.model.SalesRegisterEntry
import com.example.data.model.Shipment
import com.example.data.model.ShipmentStatus
import com.example.data.model.SupabaseBridgeState
import com.example.data.model.SupabaseConnectionConfig
import com.example.data.model.SupabaseTableMapping
import com.example.data.model.SyncTelemetry
import com.example.data.remote.ParsedOfficeSnapshot
import com.example.data.remote.SupabaseErpClient
import com.example.data.remote.WindowsMirrorBridge
import com.example.data.repository.AiAnalyticsRepository
import com.example.data.repository.ErpRepository
import com.example.notifications.ErpNotificationManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ErpViewModel"
private const val STOP_TIMEOUT_MS = 5000L
private const val AUTO_SYNC_INTERVAL_MS = 20_000L

sealed interface StreamState<out T> {
    data object Loading : StreamState<Nothing>
    data class Success<T>(val data: T) : StreamState<T>
    data class Error(val message: String) : StreamState<Nothing>
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data object Loading : AiTaskState
    data class Streaming(val text: String) : AiTaskState
    data class Success(val text: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

class ErpViewModel(
    private val erpRepository: ErpRepository,
    private val aiRepository: AiAnalyticsRepository,
    private val supabaseClient: SupabaseErpClient,
    private val windowsMirrorBridge: WindowsMirrorBridge,
    private val preferencesStore: SupabasePreferencesStore,
    private val notificationManager: ErpNotificationManager,
    private val currentUserId: String
) : ViewModel() {

    val inventoryState: StateFlow<StreamState<List<InventoryItem>>> =
        erpRepository.observeInventory(currentUserId)
            .toSafeStreamState("Failed to observe inventory")

    val shipmentsState: StateFlow<StreamState<List<Shipment>>> =
        erpRepository.observeShipments(currentUserId)
            .toSafeStreamState("Failed to observe shipments")

    val millsState: StateFlow<StreamState<List<MillEquipment>>> =
        erpRepository.observeMills(currentUserId)
            .toSafeStreamState("Failed to observe milling equipment")

    val aiReportsState: StateFlow<StreamState<List<AiAnalyticsReport>>> =
        erpRepository.observeAiReports(currentUserId)
            .toSafeStreamState("Failed to observe AI reports")

    val gstInvoicesState: StateFlow<StreamState<List<GstInvoice>>> =
        erpRepository.observeGstInvoices(currentUserId)
            .toSafeStreamState("Failed to observe GST invoices")

    val partyLedgerState: StateFlow<StreamState<List<PartyLedgerEntry>>> =
        erpRepository.observePartyLedger(currentUserId)
            .toSafeStreamState("Failed to observe party ledger")

    private val _supabaseSalesRegister = MutableStateFlow<List<SalesRegisterEntry>>(emptyList())
    private val _supabaseOfficeCustomers = MutableStateFlow<List<OfficeCustomer>>(emptyList())
    private val _supabaseProductionReports = MutableStateFlow<List<ProductionReportEntry>>(emptyList())
    private val _supabaseChokarSales = MutableStateFlow<List<ChokarSaleEntry>>(emptyList())

    // Verified Windows ERP RPC snapshot state
    private val _officeAuditState = MutableStateFlow(OfficeSnapshotAuditState())
    val officeAuditState: StateFlow<OfficeSnapshotAuditState> = _officeAuditState.asStateFlow()

    private val _officeLedgerVouchers = MutableStateFlow<List<OfficeLedgerVoucher>>(emptyList())
    val officeLedgerVouchers: StateFlow<List<OfficeLedgerVoucher>> = _officeLedgerVouchers.asStateFlow()

    private val _officeDayBookRows = MutableStateFlow<List<DayBookRow>>(emptyList())
    private val _officeReceiptsAndPayments = MutableStateFlow<List<OfficeReceiptPayment>>(emptyList())

    private val _chokarStockTotals = MutableStateFlow(ChokarStockTotals())
    val chokarStockTotals: StateFlow<ChokarStockTotals> = _chokarStockTotals.asStateFlow()

    private val _renderedPdfPreview = MutableStateFlow<RenderedPdfPreviewRequest?>(null)
    val renderedPdfPreview: StateFlow<RenderedPdfPreviewRequest?> = _renderedPdfPreview.asStateFlow()

    private val _isLoadingOfficePdf = MutableStateFlow(false)
    val isLoadingOfficePdf: StateFlow<Boolean> = _isLoadingOfficePdf.asStateFlow()

    private val _connectionConfig = MutableStateFlow(SupabaseConnectionConfig())
    val connectionConfig: StateFlow<SupabaseConnectionConfig> = _connectionConfig.asStateFlow()

    val partySummaries: StateFlow<List<PartyBalanceSummary>> = partyLedgerState
        .map { state ->
            val entries = (state as? StreamState.Success)?.data.orEmpty()
            entries.groupBy { it.partyName.trim().ifBlank { "Unnamed Party" } }
                .map { (party, txns) ->
                    val billed = txns.filter { it.entryTypeEnum == LedgerEntryType.DEBIT_SALE }.sumOf { it.amountInr }
                    val received = txns.filter { it.entryTypeEnum == LedgerEntryType.CREDIT_PAYMENT }.sumOf { it.amountInr }
                    val latest = txns.firstOrNull()
                    PartyBalanceSummary(
                        partyName = party,
                        partyCity = latest?.partyCity.orEmpty().ifBlank { "Kasganj, UP" },
                        partyPhone = latest?.partyPhone.orEmpty(),
                        totalBilledInr = billed,
                        totalReceivedInr = received,
                        outstandingInr = billed - received,
                        entryCount = txns.size
                    )
                }
                .sortedByDescending { it.outstandingInr }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Unified Office Customers list. When Windows ERP RPC snapshot is loaded, preserves strict
     * numericId separation (never merging two customers with the same name).
     */
    val officeCustomers: StateFlow<List<OfficeCustomer>> = combine(
        _supabaseOfficeCustomers,
        partySummaries
    ) { supaCustomers, summaries ->
        if (supaCustomers.any { it.numericId >= 0 }) {
            return@combine supaCustomers.sortedBy { it.name.lowercase(Locale.US) }
        }
        val byName = linkedMapOf<String, OfficeCustomer>()
        supaCustomers.forEach { c -> byName[c.name.lowercase(Locale.US)] = c }
        summaries.forEach { s ->
            val key = s.partyName.lowercase(Locale.US)
            if (!byName.containsKey(key)) {
                byName[key] = OfficeCustomer(
                    id = "cust_${key.replace(Regex("[^a-z0-9]"), "_")}",
                    name = s.partyName,
                    category = "Customer",
                    mobile = s.partyPhone,
                    city = s.partyCity,
                    closingBalanceDr = s.outstandingInr,
                    totalEntries = s.entryCount,
                    syncSource = "FIREBASE"
                )
            }
        }
        byName.values.sortedBy { it.name.lowercase(Locale.US) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Unified Sales Register combining live verified Windows ERP sales with Firestore GST invoices.
     */
    val salesRegister: StateFlow<List<SalesRegisterEntry>> = combine(
        _supabaseSalesRegister,
        gstInvoicesState
    ) { supaSales, invState ->
        val firestoreInvoices = (invState as? StreamState.Success)?.data.orEmpty()
        val bySaleNo = linkedMapOf<String, SalesRegisterEntry>()
        supaSales.forEach { s -> bySaleNo[s.invoiceNo.lowercase(Locale.US)] = s }
        firestoreInvoices.forEach { inv ->
            val key = inv.invoiceNumber.lowercase(Locale.US)
            if (!bySaleNo.containsKey(key)) {
                val weightKg = (inv.quantityMt * 1000.0).coerceAtLeast(10.0)
                val packKg = if (inv.itemDescription.contains("10kg", true)) 10.0
                else if (inv.itemDescription.contains("5kg", true)) 5.0
                else 47.0
                val bags = (weightKg / packKg).toInt().coerceAtLeast(1)
                val rateKg = if (weightKg > 0.0) inv.totalAmountInr / weightKg else 33.0
                bySaleNo[key] = SalesRegisterEntry(
                    id = inv.id,
                    saleNo = inv.invoiceNumber,
                    invoiceNo = inv.invoiceNumber,
                    date = inv.invoiceDate,
                    partyName = inv.partyName,
                    partyGstin = inv.partyGstin.ifBlank { "Unregistered / Not Provided" },
                    items = listOf(
                        InvoiceLineItem(
                            productName = inv.itemDescription.ifBlank { "Atta" },
                            hsnCode = inv.hsnCode.ifBlank { "1101" },
                            packKg = packKg,
                            bags = bags,
                            ratePerKgInclGst = rateKg,
                            gstRatePct = inv.gstRatePct
                        )
                    ),
                    gstRatePct = inv.gstRatePct,
                    totalAmountInr = inv.totalAmountInr,
                    balanceDueInr = if (inv.paymentStatusEnum == InvoicePaymentStatus.PAID) 0.0 else inv.totalAmountInr,
                    paymentStatus = inv.paymentStatus,
                    syncSource = inv.syncSource
                )
            }
        }
        bySaleNo.values.sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Unified Day Book: prioritizes exact audited Windows ERP Day Book rows when synced,
     * otherwise combines Sales Register and Firestore Party Ledger vouchers.
     */
    val dayBookRows: StateFlow<List<DayBookRow>> = combine(
        _officeDayBookRows,
        salesRegister,
        partyLedgerState
    ) { officeDb, sales, ledState ->
        if (officeDb.isNotEmpty()) {
            val salesByNo = sales.associateBy { it.saleNo.lowercase(Locale.US) }
            return@combine officeDb.map { row ->
                val linked = salesByNo[row.voucherNo.lowercase(Locale.US)]
                if (linked != null) row.copy(linkedSaleId = linked.id) else row
            }
        }
        val ledgers = (ledState as? StreamState.Success)?.data.orEmpty()
        val rows = mutableListOf<DayBookRow>()
        val usedRefs = mutableSetOf<String>()

        sales.forEach { s ->
            usedRefs.add(s.invoiceNo.lowercase(Locale.US))
            val prodSummary = s.items.map { it.productName }.distinct().joinToString(", ").ifBlank { "Atta" }
            rows.add(
                DayBookRow(
                    id = "db_sale_${s.id}",
                    date = s.date,
                    voucherNo = s.saleNo,
                    voucherType = if (prodSummary.contains("chokar", true)) "Chokar Sale" else "Standard Sale",
                    accountName = s.partyName,
                    description = prodSummary,
                    debitInr = s.totalAmountInr,
                    creditInr = 0.0,
                    linkedSaleId = s.id
                )
            )
        }
        ledgers.forEach { l ->
            val isCredit = l.entryTypeEnum == LedgerEntryType.CREDIT_PAYMENT
            if (!isCredit && usedRefs.contains(l.referenceNo.lowercase(Locale.US))) return@forEach
            rows.add(
                DayBookRow(
                    id = "db_led_${l.id}",
                    date = l.entryDate,
                    voucherNo = l.referenceNo,
                    voucherType = if (isCredit) "Receipt" else "Standard Sale",
                    accountName = l.partyName,
                    description = l.notes.ifBlank { if (isCredit) "Payment Received" else "Atta" },
                    debitInr = if (isCredit) 0.0 else l.amountInr,
                    creditInr = if (isCredit) l.amountInr else 0.0
                )
            )
        }
        rows.sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Exact Receipt & Payment entries (uses Windows RPC receipts + payments when available,
     * falling back to Firestore party ledger vouchers).
     */
    val receiptsAndPayments: StateFlow<List<OfficeReceiptPayment>> = combine(
        _officeReceiptsAndPayments,
        partyLedgerState
    ) { officeRp, ledState ->
        if (officeRp.isNotEmpty()) return@combine officeRp
        val ledgers = (ledState as? StreamState.Success)?.data.orEmpty()
        ledgers.map { l ->
            val isRec = l.entryTypeEnum == LedgerEntryType.CREDIT_PAYMENT
            OfficeReceiptPayment(
                id = l.id,
                kind = if (isRec) "Receipt" else "Payment",
                date = l.entryDate,
                partyName = l.partyName,
                amountInr = l.amountInr,
                mode = if (l.notes.contains("UPI", true)) "UPI"
                else if (l.notes.contains("RTGS", true) || l.notes.contains("NEFT", true)) "Bank"
                else "Cash",
                discountInr = 0.0,
                referenceNo = l.referenceNo,
                narration = l.notes
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Production Reports: uses live Office ERP Chokar/Production snapshot if available,
     * otherwise synthesizes live shift output from Kumar Foods active milling machines.
     */
    val productionReports: StateFlow<List<ProductionReportEntry>> = combine(
        _supabaseProductionReports,
        millsState
    ) { supaProd, mState ->
        if (supaProd.isNotEmpty()) return@combine supaProd
        val mills = (mState as? StreamState.Success)?.data.orEmpty()
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        mills.mapIndexed { idx, mill ->
            val wheatKg = (mill.outputRateMtHr * 8.0 * 1000.0).coerceAtLeast(4000.0)
            val attaKg = wheatKg * 0.785
            val chokarKg = wheatKg * 0.195
            val wastageKg = (wheatKg - attaKg - chokarKg).coerceAtLeast(0.0)
            ProductionReportEntry(
                id = "prod_${mill.id}",
                date = today,
                batchCode = "KF-PRD-${101 + idx}",
                millUnit = mill.machineName,
                wheatProcessedKg = wheatKg,
                attaProducedKg = attaKg,
                chokarProducedKg = chokarKg,
                wastageKg = wastageKg,
                chokarPackedKg = chokarKg * 0.75,
                chokarLooseKg = chokarKg * 0.25,
                shiftName = if (idx % 2 == 0) "Day Shift (A)" else "Night Shift (B)",
                syncSource = "MILL_TELEMETRY"
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val chokarStockItems: StateFlow<List<InventoryItem>> = inventoryState
        .map { state ->
            val items = (state as? StreamState.Success)?.data.orEmpty()
            items.filter {
                it.categoryEnum == InventoryCategory.BYPRODUCT ||
                    it.name.contains("chokar", ignoreCase = true) ||
                    it.name.contains("bran", ignoreCase = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val chokarSales: StateFlow<List<ChokarSaleEntry>> = combine(
        _supabaseChokarSales,
        salesRegister
    ) { supaChokar, sales ->
        if (supaChokar.isNotEmpty()) return@combine supaChokar
        sales.filter { s ->
            s.items.any {
                it.productName.contains("chokar", true) || it.productName.contains("bran", true)
            }
        }.map { s ->
            val first = s.items.firstOrNull()
            ChokarSaleEntry(
                id = "chks_${s.id}",
                billNo = s.saleNo,
                date = s.date,
                partyName = s.partyName,
                partyMobile = s.partyMobile,
                packKg = first?.packKg ?: 40.0,
                bags = s.totalBags,
                ratePerKg = first?.ratePerKgInclGst ?: 21.5,
                totalAmountInr = s.totalAmountInr,
                syncSource = s.syncSource
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _selectedWarehouse = MutableStateFlow("All Warehouses")
    val selectedWarehouse: StateFlow<String> = _selectedWarehouse.asStateFlow()

    private val _alertsEnabled = MutableStateFlow(true)
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    private val _tableMapping = MutableStateFlow(SupabaseTableMapping())
    val tableMapping: StateFlow<SupabaseTableMapping> = _tableMapping.asStateFlow()

    private val _liveTableData = MutableStateFlow(LiveSupabaseTableData())
    val liveTableData: StateFlow<LiveSupabaseTableData> = _liveTableData.asStateFlow()

    private val _isLoadingLiveTable = MutableStateFlow(false)
    val isLoadingLiveTable: StateFlow<Boolean> = _isLoadingLiveTable.asStateFlow()

    private val _syncTelemetry = MutableStateFlow(
        SyncTelemetry(
            bridgeState = SupabaseBridgeState.AWAITING_CREDENTIALS,
            statusMessage = "Open Settings -> Supabase Connection to paste your URL, Publishable Key & 32-char Mobile Pairing Code."
        )
    )
    val syncTelemetry: StateFlow<SyncTelemetry> = _syncTelemetry.asStateFlow()

    private val _forecastTaskState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val forecastTaskState: StateFlow<AiTaskState> = _forecastTaskState.asStateFlow()

    private val _streamingReportState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val streamingReportState: StateFlow<AiTaskState> = _streamingReportState.asStateFlow()

    private val _diagnosingMillId = MutableStateFlow<String?>(null)
    val diagnosingMillId: StateFlow<String?> = _diagnosingMillId.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _isSeedingData = MutableStateFlow(false)
    val isSeedingData: StateFlow<Boolean> = _isSeedingData.asStateFlow()

    private var realtimeBridgeJob: Job? = null
    private var autoSyncLoopJob: Job? = null
    private var lastAutoResumePullMs: Long = 0L
    private var knownSaleNumbers: Set<String> = emptySet()

    val criticalAlerts: StateFlow<List<CriticalAlert>> = combine(
        inventoryState,
        millsState,
        alertsEnabled
    ) { invState, millState, notifyOn ->
        val items = (invState as? StreamState.Success)?.data.orEmpty()
        val mills = (millState as? StreamState.Success)?.data.orEmpty()

        val alerts = buildList {
            items.filter { it.belowThreshold }.forEach { item ->
                add(
                    CriticalAlert(
                        id = "low_stock_${item.id}_${item.quantityMt.toInt()}",
                        title = "Low Stock: ${item.name}",
                        subtitle = "${item.warehouse} at ${String.format(Locale.US, "%.1f", item.quantityMt)} MT (Threshold: ${String.format(Locale.US, "%.0f", item.lowThresholdMt)} MT)",
                        severity = if (item.quantityMt <= item.lowThresholdMt * 0.6) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                        sourceType = "INVENTORY"
                    )
                )
            }
            mills.filter { it.statusEnum == MillStatus.CRITICAL || it.statusEnum == MillStatus.WARNING }.forEach { mill ->
                add(
                    CriticalAlert(
                        id = "mill_health_${mill.id}_${mill.status}",
                        title = "${mill.machineName} (${mill.healthScore}%)",
                        subtitle = "Temp ${mill.temperatureC}°C • Vib ${mill.vibrationMmS} mm/s • Service due ${mill.nextServiceDate}",
                        severity = if (mill.statusEnum == MillStatus.CRITICAL) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                        sourceType = "MAINTENANCE"
                    )
                )
            }
        }

        if (notifyOn) {
            alerts.filter { it.severity == AlertSeverity.CRITICAL }.forEach { alert ->
                notificationManager.dispatchAlertIfNew(alert)
            }
        }
        alerts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            // 1. Load cached verified Office ERP snapshot immediately for instant offline open
            windowsMirrorBridge.loadCachedSnapshot()?.let { cached ->
                applyParsedOfficeSnapshot(cached, notifyNewSales = false)
            }

            // 2. Load saved pairing credentials & table mapping
            val savedConfig = preferencesStore.connectionConfigFlow.first()
            val savedMapping = preferencesStore.tableMappingFlow.first()
            _connectionConfig.value = savedConfig
            _tableMapping.value = savedMapping

            windowsMirrorBridge.updateCredentials(
                url = savedConfig.supabaseUrl,
                key = savedConfig.supabaseAnonKey,
                mobilePairCode = savedConfig.mobilePairCode
            )
            supabaseClient.updateCredentials(
                url = savedConfig.supabaseUrl,
                key = savedConfig.supabaseAnonKey,
                bucket = savedConfig.pdfStorageBucket
            )

            if (windowsMirrorBridge.isReadyForOfficeRpc() || supabaseClient.hasValidCredentials()) {
                startSupabaseRealtimeBridge(savedConfig.autoSyncEnabled)
            }
        }
    }

    /**
     * Automatically pulls fresh Windows ERP data whenever the app returns to the foreground.
     */
    fun onAppResumed() {
        val now = System.currentTimeMillis()
        if (now - lastAutoResumePullMs < 5_000L) return
        if (!_connectionConfig.value.autoSyncEnabled) return
        if (windowsMirrorBridge.isReadyForOfficeRpc() || supabaseClient.hasValidCredentials()) {
            lastAutoResumePullMs = now
            triggerSupabaseSync(silent = true)
        }
    }

    /**
     * Pairs the app with the user's Windows ERP Supabase project + 32-char Mobile Pairing Code,
     * persists credentials in DataStore, pulls live verified data, and starts automatic 20s sync.
     */
    fun saveSupabaseConnectionAndSync(
        url: String,
        anonKey: String,
        mobilePairCode: String,
        autoSyncEnabled: Boolean,
        pdfBucket: String
    ) {
        viewModelScope.launch {
            preferencesStore.saveConnectionConfig(url, anonKey, mobilePairCode, autoSyncEnabled, pdfBucket)
            val updated = SupabaseConnectionConfig(
                supabaseUrl = url.trim().trimEnd('/'),
                supabaseAnonKey = anonKey.trim(),
                mobilePairCode = mobilePairCode.trim(),
                autoSyncEnabled = autoSyncEnabled,
                pdfStorageBucket = pdfBucket.trim().ifBlank { "invoices" }
            )
            _connectionConfig.value = updated
            windowsMirrorBridge.updateCredentials(updated.supabaseUrl, updated.supabaseAnonKey, updated.mobilePairCode)
            supabaseClient.updateCredentials(updated.supabaseUrl, updated.supabaseAnonKey, updated.pdfStorageBucket)

            if (windowsMirrorBridge.isReadyForOfficeRpc() || supabaseClient.hasValidCredentials()) {
                _bannerMessage.value = if (windowsMirrorBridge.isReadyForOfficeRpc()) {
                    "Office ERP Paired! Pulling verified Windows snapshot via erp_mobile_pull..."
                } else {
                    "Supabase URL & Key saved! Syncing public ERP tables..."
                }
                startSupabaseRealtimeBridge(autoSyncEnabled)
                triggerSupabaseSync(silent = false, isNewPairing = true)
            } else {
                stopAutoSyncWorkers()
                _syncTelemetry.update {
                    it.copy(
                        bridgeState = SupabaseBridgeState.AWAITING_CREDENTIALS,
                        realtimeSocketActive = false,
                        statusMessage = "Enter a valid https://...supabase.co URL, Publishable Key & 32-char Pairing Code."
                    )
                }
                _bannerMessage.value = "Please enter valid Supabase URL, Key, and Mobile Pairing Code."
            }
        }
    }

    fun disconnectOfficePairing() {
        viewModelScope.launch {
            stopAutoSyncWorkers()
            preferencesStore.clearConnectionConfig()
            windowsMirrorBridge.clearCachedSnapshot()
            windowsMirrorBridge.updateCredentials("", "", "")
            supabaseClient.updateCredentials("", "", "invoices")
            _connectionConfig.value = SupabaseConnectionConfig()
            _officeAuditState.value = OfficeSnapshotAuditState()
            _supabaseOfficeCustomers.value = emptyList()
            _officeLedgerVouchers.value = emptyList()
            _supabaseSalesRegister.value = emptyList()
            _officeDayBookRows.value = emptyList()
            _officeReceiptsAndPayments.value = emptyList()
            _supabaseProductionReports.value = emptyList()
            _chokarStockTotals.value = ChokarStockTotals()
            _supabaseChokarSales.value = emptyList()
            knownSaleNumbers = emptySet()
            _syncTelemetry.value = SyncTelemetry(
                bridgeState = SupabaseBridgeState.AWAITING_CREDENTIALS,
                statusMessage = "Office ERP disconnected and offline snapshot erased."
            )
            _bannerMessage.value = "Office ERP disconnected & cached office data cleared."
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesStore.setAutoSyncEnabled(enabled)
            _connectionConfig.update { it.copy(autoSyncEnabled = enabled) }
            if (enabled && (windowsMirrorBridge.isReadyForOfficeRpc() || supabaseClient.hasValidCredentials())) {
                startSupabaseRealtimeBridge(true)
                _bannerMessage.value = "Automatic Windows ERP live sync enabled (20s + on app resume)."
            } else {
                autoSyncLoopJob?.cancel()
                autoSyncLoopJob = null
                _bannerMessage.value = "Automatic background polling paused (manual Sync Now still active)."
            }
        }
    }

    private fun stopAutoSyncWorkers() {
        realtimeBridgeJob?.cancel()
        autoSyncLoopJob?.cancel()
        supabaseClient.stopRealtimeListener()
    }

    private fun startSupabaseRealtimeBridge(enablePeriodicAutoSync: Boolean) {
        stopAutoSyncWorkers()
        if (!windowsMirrorBridge.isReadyForOfficeRpc() && supabaseClient.hasValidCredentials()) {
            supabaseClient.startRealtimeListener { connected ->
                _syncTelemetry.update { current ->
                    current.copy(
                        realtimeSocketActive = connected,
                        bridgeState = if (connected) SupabaseBridgeState.CONNECTED_LIVE else current.bridgeState
                    )
                }
            }
            realtimeBridgeJob = viewModelScope.launch {
                triggerSupabaseSync(silent = true)
                supabaseClient.realtimeEvents.collect {
                    triggerSupabaseSync(silent = true)
                    val openTable = _liveTableData.value.tableName
                    if (openTable.isNotBlank()) {
                        browseLiveSupabaseTable(openTable)
                    }
                }
            }
        } else if (windowsMirrorBridge.isReadyForOfficeRpc()) {
            realtimeBridgeJob = viewModelScope.launch {
                triggerSupabaseSync(silent = true)
            }
        }

        if (enablePeriodicAutoSync) {
            autoSyncLoopJob = viewModelScope.launch {
                while (isActive) {
                    delay(AUTO_SYNC_INTERVAL_MS)
                    if (_connectionConfig.value.autoSyncEnabled &&
                        (windowsMirrorBridge.isReadyForOfficeRpc() || supabaseClient.hasValidCredentials())
                    ) {
                        triggerSupabaseSync(silent = true)
                    }
                }
            }
        }
    }

    fun triggerSupabaseSync(silent: Boolean = false, isNewPairing: Boolean = false) {
        val useOfficeRpc = windowsMirrorBridge.isReadyForOfficeRpc()
        if (!useOfficeRpc && !supabaseClient.hasValidCredentials()) {
            if (!silent) {
                _bannerMessage.value = "Open Settings -> Supabase Connection and enter URL, Key & Pairing Code."
            }
            return
        }
        viewModelScope.launch {
            _syncTelemetry.update { it.copy(bridgeState = SupabaseBridgeState.SYNCING) }

            if (useOfficeRpc) {
                val res = windowsMirrorBridge.pullVerifiedOfficeSnapshot(
                    previousSalesNumbers = knownSaleNumbers,
                    previousUploadedIso = _officeAuditState.value.cloudUploadedAt,
                    isNewPairing = isNewPairing
                )
                res.onSuccess { parsed ->
                    applyParsedOfficeSnapshot(parsed, notifyNewSales = !isNewPairing && knownSaleNumbers.isNotEmpty())
                    if (!silent) {
                        val driftNote = if (parsed.auditState.syncDriftWarning.isNotBlank()) " • Drift warning!" else ""
                        _bannerMessage.value = "Office ERP Verified PASS (${parsed.latencyMs}ms • ${parsed.customers.size} parties, ${parsed.salesRegister.size} bills$driftNote)"
                    }
                }.onFailure { err ->
                    _syncTelemetry.update {
                        it.copy(
                            bridgeState = SupabaseBridgeState.ERROR,
                            statusMessage = err.localizedMessage ?: "Office ERP RPC pull failed"
                        )
                    }
                    if (!silent) {
                        _bannerMessage.value = "Office sync error: ${err.localizedMessage}"
                    }
                }
                return@launch
            }

            // Fallback: Direct public REST table pull when user provided URL+Key without 32-char Pairing Code
            val result = supabaseClient.pullLiveErpSnapshot(_tableMapping.value)
            result.onSuccess { snap ->
                if (snap.salesRegister.isNotEmpty()) _supabaseSalesRegister.value = snap.salesRegister
                if (snap.officeCustomers.isNotEmpty()) _supabaseOfficeCustomers.value = snap.officeCustomers
                if (snap.productionReports.isNotEmpty()) _supabaseProductionReports.value = snap.productionReports
                if (snap.chokarSales.isNotEmpty()) _supabaseChokarSales.value = snap.chokarSales

                snap.inventory.forEach { erpRepository.upsertInventoryItem(it) }
                snap.shipments.forEach { erpRepository.createShipment(it) }
                snap.mills.forEach { erpRepository.upsertMillEquipment(it) }
                snap.invoices.forEach { erpRepository.upsertGstInvoice(it) }
                snap.ledger.forEach { erpRepository.addPartyLedgerEntry(it) }

                val totalRows = snap.inventory.size + snap.shipments.size + snap.mills.size +
                    snap.invoices.size + snap.ledger.size + snap.officeCustomers.size + snap.salesRegister.size
                val timeStr = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US).format(Date())
                _syncTelemetry.update {
                    it.copy(
                        bridgeState = SupabaseBridgeState.CONNECTED_LIVE,
                        lastSyncLabel = timeStr,
                        latencyMs = snap.latencyMs,
                        syncedRowCount = totalRows,
                        discoveredTables = snap.discoveredTables,
                        statusMessage = "Supabase REST Live • ${snap.discoveredTables.size} tables ($totalRows rows)"
                    )
                }
                if (_liveTableData.value.tableName.isBlank() && snap.discoveredTables.isNotEmpty()) {
                    browseLiveSupabaseTable(snap.discoveredTables.first())
                }
                if (!silent) {
                    _bannerMessage.value = "Windows ERP synced in ${snap.latencyMs}ms (${snap.discoveredTables.size} tables, $totalRows rows)."
                }
            }.onFailure { err ->
                _syncTelemetry.update {
                    it.copy(
                        bridgeState = SupabaseBridgeState.ERROR,
                        statusMessage = "Supabase sync error: ${err.localizedMessage ?: "Check URL/Key or network"}"
                    )
                }
                if (!silent) {
                    _bannerMessage.value = "Supabase sync failed: ${err.localizedMessage}"
                }
            }
        }
    }

    private fun applyParsedOfficeSnapshot(parsed: ParsedOfficeSnapshot, notifyNewSales: Boolean) {
        val incomingSaleNums = parsed.salesRegister.map { it.saleNo.trim() }.filter { it.isNotEmpty() }.toSet()
        if (notifyNewSales && _alertsEnabled.value) {
            val newlyAdded = incomingSaleNums - knownSaleNumbers
            if (newlyAdded.isNotEmpty()) {
                val firstNew = parsed.salesRegister.firstOrNull { it.saleNo in newlyAdded }
                if (firstNew != null) {
                    notificationManager.dispatchAlertIfNew(
                        CriticalAlert(
                            id = "new_office_sale_${firstNew.saleNo}",
                            title = "New Office Sale: ${firstNew.saleNo}",
                            subtitle = "${firstNew.partyName} • ₹${String.format(Locale.US, "%.2f", firstNew.totalAmountInr)}",
                            severity = AlertSeverity.WARNING,
                            sourceType = "SALES_REGISTER"
                        )
                    )
                }
            }
        }
        knownSaleNumbers = incomingSaleNums

        _officeAuditState.value = parsed.auditState
        _supabaseOfficeCustomers.value = parsed.customers
        _officeLedgerVouchers.value = parsed.ledgerVouchers
        _supabaseSalesRegister.value = parsed.salesRegister
        _officeDayBookRows.value = parsed.dayBookRows
        _officeReceiptsAndPayments.value = parsed.receiptsAndPayments
        _supabaseProductionReports.value = parsed.productionReports
        _chokarStockTotals.value = parsed.chokarStockTotals
        _supabaseChokarSales.value = parsed.chokarSales

        val totalRows = parsed.customers.size + parsed.salesRegister.size +
            parsed.ledgerVouchers.size + parsed.receiptsAndPayments.size + parsed.productionReports.size
        val timeStr = parsed.auditState.snapshotGeneratedAt.ifBlank {
            SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US).format(Date())
        }
        _syncTelemetry.update {
            it.copy(
                bridgeState = SupabaseBridgeState.CONNECTED_LIVE,
                realtimeSocketActive = true,
                lastSyncLabel = timeStr,
                latencyMs = parsed.latencyMs,
                syncedRowCount = totalRows,
                discoveredTables = listOf(
                    "erp_mobile_pull (RPC)",
                    "erp_mobile_invoice_pull (PDF)",
                    "customers (${parsed.customers.size})",
                    "salesRegister (${parsed.salesRegister.size})",
                    "ledger (${parsed.ledgerVouchers.size})",
                    "chokarReport (${parsed.productionReports.size})"
                ),
                statusMessage = "Office ERP Verified PASS • ${parsed.customers.size} parties • ${parsed.salesRegister.size} bills • Auto-sync 20s"
            )
        }
    }

    /**
     * Fetches the EXACT original Windows ERP PDF via `erp_mobile_invoice_pull` when paired & available,
     * opening `PdfBytesPreviewScreen`. Falls back cleanly to on-device Tax Invoice Preview otherwise.
     */
    fun openOfficeInvoicePdf(
        sale: SalesRegisterEntry,
        onFallbackToGeneratedPreview: (SalesRegisterEntry) -> Unit
    ) {
        val canPullOffice = sale.hasOfficePdfPointer &&
            (!sale.inlinePdfBase64.isNullOrBlank() || windowsMirrorBridge.isReadyForOfficeRpc())
        if (!canPullOffice) {
            onFallbackToGeneratedPreview(sale)
            return
        }
        viewModelScope.launch {
            _isLoadingOfficePdf.value = true
            val res = windowsMirrorBridge.pullOriginalWindowsInvoicePdf(sale)
            _isLoadingOfficePdf.value = false
            res.onSuccess { pdfBytes ->
                _renderedPdfPreview.value = RenderedPdfPreviewRequest(
                    title = "Invoice ${sale.saleNo} • Office PDF",
                    filename = sale.invoiceFilename.ifBlank { "Kumar_Foods_Invoice_${sale.saleNo}.pdf" },
                    pdfBytes = pdfBytes
                )
            }.onFailure { err ->
                _bannerMessage.value = "${err.localizedMessage} — showing live invoice preview."
                onFallbackToGeneratedPreview(sale)
            }
        }
    }

    fun dismissRenderedPdfPreview() {
        _renderedPdfPreview.value = null
    }

    fun updateTableMapping(newMapping: SupabaseTableMapping) {
        viewModelScope.launch {
            _tableMapping.value = newMapping
            preferencesStore.saveTableMapping(newMapping)
            _bannerMessage.value = "Windows ERP table mapping saved. Syncing..."
            triggerSupabaseSync(silent = false)
        }
    }

    fun browseLiveSupabaseTable(tableName: String) {
        viewModelScope.launch {
            _isLoadingLiveTable.value = true
            val data = supabaseClient.fetchRawTableRows(tableName)
            _liveTableData.value = data
            _isLoadingLiveTable.value = false
        }
    }

    fun discoverSupabaseTablesNow() {
        viewModelScope.launch {
            _isLoadingLiveTable.value = true
            val result = supabaseClient.discoverPublicTables()
            result.onSuccess { tables ->
                _syncTelemetry.update { it.copy(discoveredTables = tables) }
                if (tables.isNotEmpty()) {
                    val data = supabaseClient.fetchRawTableRows(tables.first())
                    _liveTableData.value = data
                } else {
                    _liveTableData.value = LiveSupabaseTableData(
                        errorMessage = "Office ERP uses secure RPCs (erp_mobile_pull) rather than public tables."
                    )
                }
            }.onFailure { err ->
                _liveTableData.value = LiveSupabaseTableData(
                    errorMessage = err.localizedMessage ?: "Pair your Supabase URL & Key in Settings first."
                )
            }
            _isLoadingLiveTable.value = false
        }
    }

    fun selectWarehouse(warehouse: String) {
        _selectedWarehouse.value = warehouse
    }

    fun setAlertsEnabled(enabled: Boolean) {
        _alertsEnabled.value = enabled
        if (!enabled) notificationManager.resetDispatchedAlerts()
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun seedKumarFoodsStarterData() {
        viewModelScope.launch {
            _isSeedingData.value = true
            val result = erpRepository.initializeKumarFoodsStarterData()
            _isSeedingData.value = false
            result.onSuccess {
                _bannerMessage.value = "Kumar Foods Mill, Sales Register, Chokar & Party Khata initialized."
            }.onFailure { err ->
                _bannerMessage.value = "Setup error: ${err.localizedMessage}"
            }
        }
    }

    fun saveInventoryItem(item: InventoryItem) {
        viewModelScope.launch {
            val result = erpRepository.upsertInventoryItem(item)
            result.onSuccess {
                supabaseClient.pushInventoryUpdate(item, _tableMapping.value.inventoryTable)
                _bannerMessage.value = "Saved ${item.name} (${String.format(Locale.US, "%.1f", item.quantityMt)} MT)."
            }.onFailure { err ->
                _bannerMessage.value = "Failed to save inventory: ${err.localizedMessage}"
            }
        }
    }

    fun adjustInventoryQuantity(item: InventoryItem, deltaMt: Double) {
        val updatedQty = (item.quantityMt + deltaMt).coerceIn(0.0, item.capacityMt)
        viewModelScope.launch {
            val result = erpRepository.adjustInventoryStock(
                itemId = item.id,
                newQuantityMt = updatedQty,
                newThresholdMt = item.lowThresholdMt,
                syncSource = if (supabaseClient.hasValidCredentials()) "SUPABASE_ERP" else "FIREBASE"
            )
            result.onSuccess {
                supabaseClient.pushInventoryUpdate(item.copy(quantityMt = updatedQty), _tableMapping.value.inventoryTable)
            }.onFailure { err ->
                _bannerMessage.value = "Stock update failed: ${err.localizedMessage}"
            }
        }
    }

    fun deleteInventoryItem(itemId: String) {
        viewModelScope.launch {
            erpRepository.deleteInventoryItem(itemId).onFailure { err ->
                _bannerMessage.value = "Delete failed: ${err.localizedMessage}"
            }
        }
    }

    fun createShipment(shipment: Shipment) {
        viewModelScope.launch {
            val result = erpRepository.createShipment(shipment)
            result.onSuccess {
                supabaseClient.pushShipmentStatus(shipment, _tableMapping.value.shipmentsTable)
                _bannerMessage.value = "Dispatched ${shipment.trackingCode} (${shipment.weightMt} MT)."
            }.onFailure { err ->
                _bannerMessage.value = "Shipment creation failed: ${err.localizedMessage}"
            }
        }
    }

    fun advanceShipmentStatus(shipment: Shipment) {
        val next = shipment.statusEnum.nextStatus() ?: return
        val nextEta = when (next) {
            ShipmentStatus.IN_TRANSIT -> "En Route • GPS Active"
            ShipmentStatus.OUT_FOR_DELIVERY -> "Arriving Today"
            ShipmentStatus.DELIVERED -> "Delivered & Signed"
            ShipmentStatus.LOADING -> shipment.etaText
        }
        viewModelScope.launch {
            val result = erpRepository.advanceShipmentStatus(shipment.id, next, nextEta)
            result.onSuccess {
                supabaseClient.pushShipmentStatus(shipment.copy(status = next.name, etaText = nextEta), _tableMapping.value.shipmentsTable)
                _bannerMessage.value = "${shipment.trackingCode} moved to ${next.label}."
            }.onFailure { err ->
                _bannerMessage.value = "Status update failed: ${err.localizedMessage}"
            }
        }
    }

    fun createGstInvoice(invoice: GstInvoice, alsoAddToKhata: Boolean = true) {
        viewModelScope.launch {
            val result = erpRepository.upsertGstInvoice(invoice)
            result.onSuccess {
                supabaseClient.pushGstInvoice(invoice, _tableMapping.value.invoicesTable)
                if (alsoAddToKhata) {
                    val ledgerEntry = PartyLedgerEntry(
                        partyName = invoice.partyName,
                        partyCity = "Kasganj, UP",
                        entryType = LedgerEntryType.DEBIT_SALE.name,
                        amountInr = invoice.totalAmountInr,
                        referenceNo = invoice.invoiceNumber,
                        notes = "Standard Sale (${invoice.itemDescription})",
                        entryDate = invoice.invoiceDate,
                        syncSource = invoice.syncSource
                    )
                    erpRepository.addPartyLedgerEntry(ledgerEntry)
                    supabaseClient.pushLedgerEntry(ledgerEntry, _tableMapping.value.ledgerTable)
                }
                _bannerMessage.value = "GST Invoice ${invoice.invoiceNumber} generated & synced."
            }.onFailure { err ->
                _bannerMessage.value = "Invoice failed: ${err.localizedMessage}"
            }
        }
    }

    fun markInvoicePaid(invoice: GstInvoice) {
        if (invoice.paymentStatusEnum == InvoicePaymentStatus.PAID) return
        viewModelScope.launch {
            val syncTag = if (supabaseClient.hasValidCredentials()) "SUPABASE_ERP" else "FIREBASE"
            val result = erpRepository.markInvoicePaid(invoice.id, syncTag)
            result.onSuccess {
                supabaseClient.pushGstInvoice(invoice.copy(paymentStatus = InvoicePaymentStatus.PAID.name), _tableMapping.value.invoicesTable)
                val receipt = PartyLedgerEntry(
                    partyName = invoice.partyName,
                    partyCity = "Kasganj, UP",
                    entryType = LedgerEntryType.CREDIT_PAYMENT.name,
                    amountInr = invoice.totalAmountInr,
                    referenceNo = "R-${invoice.invoiceNumber}",
                    notes = "Receipt for ${invoice.invoiceNumber}",
                    entryDate = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date()),
                    syncSource = syncTag
                )
                erpRepository.addPartyLedgerEntry(receipt)
                supabaseClient.pushLedgerEntry(receipt, _tableMapping.value.ledgerTable)
                _bannerMessage.value = "${invoice.invoiceNumber} marked PAID & posted to Ledger."
            }.onFailure { err ->
                _bannerMessage.value = "Update failed: ${err.localizedMessage}"
            }
        }
    }

    fun addPartyLedgerEntry(entry: PartyLedgerEntry) {
        viewModelScope.launch {
            val result = erpRepository.addPartyLedgerEntry(entry)
            result.onSuccess {
                supabaseClient.pushLedgerEntry(entry, _tableMapping.value.ledgerTable)
                _bannerMessage.value = "Voucher ${entry.referenceNo} saved for ${entry.partyName}."
            }.onFailure { err ->
                _bannerMessage.value = "Ledger save failed: ${err.localizedMessage}"
            }
        }
    }

    fun deletePartyLedgerEntry(entryId: String) {
        viewModelScope.launch {
            erpRepository.deletePartyLedgerEntry(entryId).onFailure { err ->
                _bannerMessage.value = "Delete failed: ${err.localizedMessage}"
            }
        }
    }

    fun addMillEquipment(mill: MillEquipment) {
        viewModelScope.launch {
            erpRepository.upsertMillEquipment(mill).onSuccess {
                _bannerMessage.value = "Registered ${mill.machineName} for telemetry monitoring."
            }.onFailure { err ->
                _bannerMessage.value = "Failed to add machine: ${err.localizedMessage}"
            }
        }
    }

    fun scheduleMaintenanceNow(mill: MillEquipment) {
        viewModelScope.launch {
            val nextScore = (mill.healthScore + 15).coerceAtMost(98)
            val result = erpRepository.updateMillMaintenanceSchedule(
                millId = mill.id,
                newStatus = MillStatus.MAINTENANCE,
                nextServiceDate = "Scheduled • Priority",
                healthScore = nextScore,
                aiAdvice = mill.lastAiAdvice ?: "Preventive maintenance slot booked via Kumar Foods ERP."
            )
            result.onSuccess {
                _bannerMessage.value = "Maintenance scheduled for ${mill.machineName}."
            }.onFailure { err ->
                _bannerMessage.value = "Scheduling failed: ${err.localizedMessage}"
            }
        }
    }

    fun runAiSalesForecast() {
        val inv = (inventoryState.value as? StreamState.Success)?.data.orEmpty()
        val shp = (shipmentsState.value as? StreamState.Success)?.data.orEmpty()
        val mills = (millsState.value as? StreamState.Success)?.data.orEmpty()

        viewModelScope.launch {
            _forecastTaskState.value = AiTaskState.Loading
            val result = aiRepository.generateSalesForecast(inv, shp, mills)
            result.onSuccess { report ->
                erpRepository.saveAiReport(report)
                _forecastTaskState.value = AiTaskState.Success(report.title)
                _bannerMessage.value = "AI 7-Day Sales Forecast generated and synced across devices."
            }.onFailure { err ->
                _forecastTaskState.value = AiTaskState.Error(err.localizedMessage ?: "Sales forecast failed.")
            }
        }
    }

    fun runStreamingSupplyChainReport() {
        val inv = (inventoryState.value as? StreamState.Success)?.data.orEmpty()
        val shp = (shipmentsState.value as? StreamState.Success)?.data.orEmpty()
        val mills = (millsState.value as? StreamState.Success)?.data.orEmpty()

        viewModelScope.launch {
            _streamingReportState.value = AiTaskState.Loading
            var finalReportText = ""
            try {
                aiRepository.streamExecutiveSupplyChainReport(inv, shp, mills).collect { partial ->
                    finalReportText = partial
                    _streamingReportState.value = AiTaskState.Streaming(partial)
                }
                if (finalReportText.isNotBlank()) {
                    _streamingReportState.value = AiTaskState.Success(finalReportText)
                    erpRepository.saveAiReport(
                        AiAnalyticsReport(
                            reportType = "SUPPLY_CHAIN_REPORT",
                            title = "Executive Supply Chain Audit",
                            summary = finalReportText.take(3900),
                            predictedGrowthPct = 0.0,
                            recommendedRestockMt = inv.filter { it.belowThreshold }
                                .sumOf { (it.capacityMt - it.quantityMt).coerceAtLeast(0.0) },
                            forecastLabels = emptyList(),
                            forecastValues = emptyList()
                        )
                    )
                } else {
                    _streamingReportState.value = AiTaskState.Error("AI returned an empty report.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Streaming executive report failed", e)
                _streamingReportState.value = AiTaskState.Error(e.localizedMessage ?: "Failed to stream report.")
            }
        }
    }

    fun runAiMillDiagnosis(mill: MillEquipment) {
        viewModelScope.launch {
            _diagnosingMillId.value = mill.id
            val result = aiRepository.diagnoseMillMaintenance(mill)
            _diagnosingMillId.value = null
            result.onSuccess { diagnosis ->
                val mappedStatus = MillStatus.fromRaw(diagnosis.riskLevel)
                val fullAdvice = "Window: ${diagnosis.predictedFailureWindow} • ${diagnosis.recommendedAction}"
                erpRepository.updateMillMaintenanceSchedule(
                    millId = mill.id,
                    newStatus = mappedStatus,
                    nextServiceDate = diagnosis.recommendedServiceDate,
                    healthScore = mill.healthScore,
                    aiAdvice = fullAdvice
                )
                _bannerMessage.value = "AI Maintenance Diagnosis updated for ${mill.machineName}."
            }.onFailure { err ->
                _bannerMessage.value = "AI Diagnosis error: ${err.localizedMessage}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoSyncWorkers()
    }

    private fun <T> Flow<T>.toSafeStreamState(fallbackError: String): StateFlow<StreamState<T>> =
        map<T, StreamState<T>> { StreamState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackError, rootError)
                emit(StreamState.Error(rootError.message ?: fallbackError))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StreamState.Loading)
}
