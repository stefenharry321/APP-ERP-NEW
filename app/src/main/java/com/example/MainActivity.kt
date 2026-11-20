package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.SupabasePreferencesStore
import com.example.data.model.InventoryItem
import com.example.data.model.InvoicePaymentStatus
import com.example.data.model.SalesRegisterEntry
import com.example.data.model.WorkspaceModule
import com.example.data.remote.SupabaseErpClient
import com.example.data.remote.WindowsMirrorBridge
import com.example.data.repository.AiAnalyticsRepository
import com.example.data.repository.ErpRepository
import com.example.firebase.configureAppCheck
import com.example.firebase.signOutUser
import com.example.notifications.ErpNotificationManager
import com.example.ui.components.AddLedgerEntrySheet
import com.example.ui.components.AddMillEquipmentSheet
import com.example.ui.components.AddShipmentSheet
import com.example.ui.components.CreateGstInvoiceSheet
import com.example.ui.components.InventoryEditorSheet
import com.example.ui.components.LiquidFloatingNavBar
import com.example.ui.components.LiquidNavItem
import com.example.ui.components.SupabaseTableMapperSheet
import com.example.ui.components.SyncControlHubSheet
import com.example.ui.screens.AccountsAndLedgerModuleScreen
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.AiMaintenanceScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChokarCustomerSalesModuleScreen
import com.example.ui.screens.ChokarStockModuleScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DayBookModuleScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.InvoicePreviewScreen
import com.example.ui.screens.OutstandingsModuleScreen
import com.example.ui.screens.PdfBytesPreviewScreen
import com.example.ui.screens.ProductionReportsModuleScreen
import com.example.ui.screens.ReceiptPaymentModuleScreen
import com.example.ui.screens.SalesRegisterModuleScreen
import com.example.ui.screens.ShipmentsScreen
import com.example.ui.theme.KumarFoodsErpTheme
import com.example.ui.theme.MoltenGold
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.ErpViewModel
import com.example.ui.viewmodel.StreamState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent before UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            KumarFoodsErpTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianVoid
                ) {
                    KumarFoodsRootGate()
                }
            }
        }
    }
}

@Composable
private fun KumarFoodsRootGate() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            KumarErpWorkspace(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@Composable
private fun KumarErpWorkspace(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val notificationManager = remember(context) { ErpNotificationManager(context.applicationContext) }

    var hasNotifPermission by remember { mutableStateOf(notificationManager.hasNotificationPermission()) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    val viewModel: ErpViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ErpViewModel(
                    erpRepository = ErpRepository(db),
                    aiRepository = AiAnalyticsRepository(),
                    supabaseClient = SupabaseErpClient(),
                    windowsMirrorBridge = WindowsMirrorBridge(app.applicationContext),
                    preferencesStore = SupabasePreferencesStore(app.applicationContext),
                    notificationManager = ErpNotificationManager(app.applicationContext),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val inventoryState by viewModel.inventoryState.collectAsStateWithLifecycle()
    val shipmentsState by viewModel.shipmentsState.collectAsStateWithLifecycle()
    val millsState by viewModel.millsState.collectAsStateWithLifecycle()
    val reportsState by viewModel.aiReportsState.collectAsStateWithLifecycle()
    val invoicesState by viewModel.gstInvoicesState.collectAsStateWithLifecycle()
    val ledgerState by viewModel.partyLedgerState.collectAsStateWithLifecycle()
    val partySummaries by viewModel.partySummaries.collectAsStateWithLifecycle()
    val officeCustomers by viewModel.officeCustomers.collectAsStateWithLifecycle()
    val officeLedgerVouchers by viewModel.officeLedgerVouchers.collectAsStateWithLifecycle()
    val officeAuditState by viewModel.officeAuditState.collectAsStateWithLifecycle()
    val salesRegister by viewModel.salesRegister.collectAsStateWithLifecycle()
    val dayBookRows by viewModel.dayBookRows.collectAsStateWithLifecycle()
    val receiptsAndPayments by viewModel.receiptsAndPayments.collectAsStateWithLifecycle()
    val productionReports by viewModel.productionReports.collectAsStateWithLifecycle()
    val chokarStockItems by viewModel.chokarStockItems.collectAsStateWithLifecycle()
    val chokarStockTotals by viewModel.chokarStockTotals.collectAsStateWithLifecycle()
    val chokarSales by viewModel.chokarSales.collectAsStateWithLifecycle()
    val renderedPdfPreview by viewModel.renderedPdfPreview.collectAsStateWithLifecycle()
    val isLoadingOfficePdf by viewModel.isLoadingOfficePdf.collectAsStateWithLifecycle()
    val criticalAlerts by viewModel.criticalAlerts.collectAsStateWithLifecycle()
    val syncTelemetry by viewModel.syncTelemetry.collectAsStateWithLifecycle()
    val connectionConfig by viewModel.connectionConfig.collectAsStateWithLifecycle()
    val tableMapping by viewModel.tableMapping.collectAsStateWithLifecycle()
    val liveTableData by viewModel.liveTableData.collectAsStateWithLifecycle()
    val isLoadingLiveTable by viewModel.isLoadingLiveTable.collectAsStateWithLifecycle()
    val selectedWarehouse by viewModel.selectedWarehouse.collectAsStateWithLifecycle()
    val alertsEnabled by viewModel.alertsEnabled.collectAsStateWithLifecycle()
    val forecastTaskState by viewModel.forecastTaskState.collectAsStateWithLifecycle()
    val streamingReportState by viewModel.streamingReportState.collectAsStateWithLifecycle()
    val diagnosingMillId by viewModel.diagnosingMillId.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val isSeedingData by viewModel.isSeedingData.collectAsStateWithLifecycle()

    val inventory = (inventoryState as? StreamState.Success)?.data.orEmpty()
    val shipments = (shipmentsState as? StreamState.Success)?.data.orEmpty()
    val mills = (millsState as? StreamState.Success)?.data.orEmpty()
    val reports = (reportsState as? StreamState.Success)?.data.orEmpty()
    val invoices = (invoicesState as? StreamState.Success)?.data.orEmpty()
    val ledgerEntries = (ledgerState as? StreamState.Success)?.data.orEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(bannerMessage) {
        bannerMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBanner()
        }
    }

    val streamError = (inventoryState as? StreamState.Error)?.message
        ?: (shipmentsState as? StreamState.Error)?.message
        ?: (millsState as? StreamState.Error)?.message
        ?: (invoicesState as? StreamState.Error)?.message
        ?: (ledgerState as? StreamState.Error)?.message
    LaunchedEffect(streamError) {
        streamError?.let { snackbarHostState.showSnackbar("Cloud sync alert: $it") }
    }

    var activeWorkspaceModule by remember { mutableStateOf<WorkspaceModule?>(null) }
    var previewInvoice by remember { mutableStateOf<SalesRegisterEntry?>(null) }
    var editingInventory by remember { mutableStateOf<InventoryItem?>(null) }
    var showInventoryEditor by remember { mutableStateOf(false) }
    var showAddShipment by remember { mutableStateOf(false) }
    var showAddMill by remember { mutableStateOf(false) }
    var showSyncHub by remember { mutableStateOf(false) }
    var showCreateInvoice by remember { mutableStateOf(false) }
    var showAddLedger by remember { mutableStateOf(false) }
    var showTableMapper by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 5 })

    val navItems = listOf(
        LiquidNavItem(
            label = stringResource(R.string.nav_overview),
            selectedIcon = Icons.Filled.Dashboard,
            unselectedIcon = Icons.Outlined.Dashboard,
            testTag = "nav_overview",
            badgeCount = criticalAlerts.size
        ),
        LiquidNavItem(
            label = stringResource(R.string.nav_inventory),
            selectedIcon = Icons.Filled.Inventory2,
            unselectedIcon = Icons.Outlined.Inventory2,
            testTag = "nav_inventory",
            badgeCount = inventory.count { it.belowThreshold }
        ),
        LiquidNavItem(
            label = stringResource(R.string.nav_logistics),
            selectedIcon = Icons.Filled.LocalShipping,
            unselectedIcon = Icons.Outlined.LocalShipping,
            testTag = "nav_shipments"
        ),
        LiquidNavItem(
            label = stringResource(R.string.nav_accounts),
            selectedIcon = Icons.Filled.AccountBalanceWallet,
            unselectedIcon = Icons.Outlined.AccountBalanceWallet,
            testTag = "nav_accounts",
            badgeCount = invoices.count { it.paymentStatusEnum != InvoicePaymentStatus.PAID }
        ),
        LiquidNavItem(
            label = stringResource(R.string.nav_ai_mills),
            selectedIcon = Icons.Filled.AutoGraph,
            unselectedIcon = Icons.Outlined.AutoGraph,
            testTag = "nav_ai_mills"
        )
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        val isExpanded = maxWidth >= 720.dp

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = if (isExpanded || activeWorkspaceModule != null) 16.dp else 90.dp)
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = ObsidianSurface,
                        contentColor = TextPrimary,
                        actionColor = MoltenGold
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (isExpanded) {
                        NavigationRail(
                            containerColor = ObsidianSurface,
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                        ) {
                            navItems.forEachIndexed { idx, item ->
                                NavigationRailItem(
                                    selected = pagerState.currentPage == idx && activeWorkspaceModule == null,
                                    onClick = {
                                        activeWorkspaceModule = null
                                        previewInvoice = null
                                        scope.launch { pagerState.animateScrollToPage(idx) }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (pagerState.currentPage == idx) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .statusBarsPadding()
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                            val scale = (1f - (pageOffset * 0.045f)).coerceIn(0.92f, 1f)
                            val alpha = (1f - (pageOffset * 0.28f)).coerceIn(0.65f, 1f)

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    }
                            ) {
                                when (page) {
                                    0 -> DashboardScreen(
                                        inventory = inventory,
                                        shipments = shipments,
                                        mills = mills,
                                        alerts = criticalAlerts,
                                        salesCount = salesRegister.size,
                                        customersCount = officeCustomers.size,
                                        syncTelemetry = syncTelemetry,
                                        isLoading = inventoryState is StreamState.Loading,
                                        isSeedingData = isSeedingData,
                                        onSeedStarterData = viewModel::seedKumarFoodsStarterData,
                                        onOpenSyncHub = { showSyncHub = true },
                                        onForceSyncNow = { viewModel.triggerSupabaseSync(silent = false) },
                                        onOpenWorkspaceModule = { module -> activeWorkspaceModule = module },
                                        onNavigateToTab = { target ->
                                            scope.launch { pagerState.animateScrollToPage(target.coerceIn(0, 4)) }
                                        },
                                        onEditInventory = { item ->
                                            editingInventory = item
                                            showInventoryEditor = true
                                        },
                                        onQuickAddInventory = {
                                            editingInventory = null
                                            showInventoryEditor = true
                                        }
                                    )
                                    1 -> InventoryScreen(
                                        items = inventory,
                                        selectedWarehouse = selectedWarehouse,
                                        onSelectWarehouse = viewModel::selectWarehouse,
                                        onAddClick = {
                                            editingInventory = null
                                            showInventoryEditor = true
                                        },
                                        onEditItem = { item ->
                                            editingInventory = item
                                            showInventoryEditor = true
                                        },
                                        onAdjustStock = viewModel::adjustInventoryQuantity,
                                        onDeleteItem = viewModel::deleteInventoryItem
                                    )
                                    2 -> ShipmentsScreen(
                                        shipments = shipments,
                                        onAddShipment = { showAddShipment = true },
                                        onAdvanceStatus = viewModel::advanceShipmentStatus
                                    )
                                    3 -> AccountsScreen(
                                        invoices = invoices,
                                        ledgerEntries = ledgerEntries,
                                        partySummaries = partySummaries,
                                        syncTelemetry = syncTelemetry,
                                        tableMapping = tableMapping,
                                        liveTableData = liveTableData,
                                        isLoadingLiveTable = isLoadingLiveTable,
                                        onCreateInvoiceClick = { showCreateInvoice = true },
                                        onMarkInvoicePaid = viewModel::markInvoicePaid,
                                        onAddLedgerEntryClick = { showAddLedger = true },
                                        onDeleteLedgerEntry = viewModel::deletePartyLedgerEntry,
                                        onOpenTableMapper = { showTableMapper = true },
                                        onDiscoverTables = viewModel::discoverSupabaseTablesNow,
                                        onSelectLiveTable = viewModel::browseLiveSupabaseTable
                                    )
                                    4 -> AiMaintenanceScreen(
                                        mills = mills,
                                        reports = reports,
                                        forecastState = forecastTaskState,
                                        streamingReportState = streamingReportState,
                                        diagnosingMillId = diagnosingMillId,
                                        onRunSalesForecast = viewModel::runAiSalesForecast,
                                        onRunExecutiveReport = viewModel::runStreamingSupplyChainReport,
                                        onAddMill = { showAddMill = true },
                                        onDiagnoseMill = viewModel::runAiMillDiagnosis,
                                        onScheduleMaintenance = viewModel::scheduleMaintenanceNow
                                    )
                                }
                            }
                        }

                        if (!isExpanded && activeWorkspaceModule == null && previewInvoice == null && renderedPdfPreview == null) {
                            LiquidFloatingNavBar(
                                items = navItems,
                                selectedIndex = pagerState.currentPage,
                                pagerOffsetFraction = pagerState.currentPageOffsetFraction,
                                onSelect = { idx ->
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }

                        // Full-screen smooth slide-in overlay for any of the 9 Workspace Modules
                        androidx.compose.animation.AnimatedVisibility(
                            visible = activeWorkspaceModule != null && previewInvoice == null && renderedPdfPreview == null,
                            enter = slideInHorizontally { it / 3 } + fadeIn(),
                            exit = slideOutHorizontally { it / 3 } + fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when (activeWorkspaceModule) {
                                WorkspaceModule.ACCOUNTS -> AccountsAndLedgerModuleScreen(
                                    modeTitle = "Accounts",
                                    customers = officeCustomers,
                                    ledgerEntries = ledgerEntries,
                                    officeLedgerVouchers = officeLedgerVouchers,
                                    syncTelemetry = syncTelemetry,
                                    auditState = officeAuditState,
                                    onBack = { activeWorkspaceModule = null },
                                    onSyncNow = { viewModel.triggerSupabaseSync(silent = false) },
                                    onOpenSettings = { showSyncHub = true }
                                )
                                WorkspaceModule.LEDGER -> AccountsAndLedgerModuleScreen(
                                    modeTitle = "Ledger",
                                    customers = officeCustomers,
                                    ledgerEntries = ledgerEntries,
                                    officeLedgerVouchers = officeLedgerVouchers,
                                    syncTelemetry = syncTelemetry,
                                    auditState = officeAuditState,
                                    onBack = { activeWorkspaceModule = null },
                                    onSyncNow = { viewModel.triggerSupabaseSync(silent = false) },
                                    onOpenSettings = { showSyncHub = true }
                                )
                                WorkspaceModule.DAY_BOOK -> DayBookModuleScreen(
                                    rows = dayBookRows,
                                    salesRegister = salesRegister,
                                    syncTelemetry = syncTelemetry,
                                    auditState = officeAuditState,
                                    onOpenInvoice = { sale ->
                                        viewModel.openOfficeInvoicePdf(sale) { fallback ->
                                            previewInvoice = fallback
                                        }
                                    },
                                    onBack = { activeWorkspaceModule = null },
                                    onSyncNow = { viewModel.triggerSupabaseSync(silent = false) },
                                    onOpenSettings = { showSyncHub = true }
                                )
                                WorkspaceModule.OUTSTANDINGS -> OutstandingsModuleScreen(
                                    customers = officeCustomers,
                                    ledgerEntries = ledgerEntries,
                                    officeLedgerVouchers = officeLedgerVouchers,
                                    syncTelemetry = syncTelemetry,
                                    auditState = officeAuditState,
                                    onBack = { activeWorkspaceModule = null },
                                    onSyncNow = { viewModel.triggerSupabaseSync(silent = false) },
                                    onOpenSettings = { showSyncHub = true }
                                )
                                WorkspaceModule.SALES_REGISTER -> SalesRegisterModuleScreen(
                                    sales = salesRegister,
                                    syncTelemetry = syncTelemetry,
                                    auditState = officeAuditState,
                                    isLoadingOfficePdf = isLoadingOfficePdf,
                                    onOpenOfficePdf = { sale ->
                                        viewModel.openOfficeInvoicePdf(sale) { fallback ->
                                            previewInvoice = fallback
                                        }
                                    },
                                    onCreateNewInvoice = { showCreateInvoice = true },
                                    onBack = { activeWorkspaceModule = null },
                                    onSyncNow = { viewModel.triggerSupabaseSync(silent = false) },
                                    onOpenSettings = { showSyncHub = true }
                                )
                                WorkspaceModule.RECEIPT_PAYMENT -> ReceiptPaymentModuleScreen(
                                    entries = receiptsAndPayments,
                                    syncTelemetry = syncTelemetry,
                                    auditState = officeAuditState,
                                    onAddVoucherClick = { showAddLedger = true },
                                    onBack = { activeWorkspaceModule = null },
                                    onSyncNow = { viewModel.triggerSupabaseSync(silent = false) },
                                    onOpenSettings = { showSyncHub = true }
                                )
                                WorkspaceModule.PRODUCTION_REPORTS -> ProductionReportsModuleScreen(
                                    reports = productionReports,
                                    chokarStockTotals = chokarStockTotals,
                                    syncTelemetry = syncTelemetry,
                                    auditState = officeAuditState,
                                    onBack = { activeWorkspaceModule = null },
                                    onSyncNow = { viewModel.triggerSupabaseSync(silent = false) },
                                    onOpenSettings = { showSyncHub = true }
                                )
                                WorkspaceModule.CHOKAR_STOCK -> ChokarStockModuleScreen(
                                    chokarItems = chokarStockItems,
                                    chokarStockTotals = chokarStockTotals,
                                    syncTelemetry = syncTelemetry,
                                    auditState = officeAuditState,
                                    onAdjustStock = viewModel::adjustInventoryQuantity,
                                    onAddChokarClick = {
                                        editingInventory = null
                                        showInventoryEditor = true
                                    },
                                    onBack = { activeWorkspaceModule = null },
                                    onSyncNow = { viewModel.triggerSupabaseSync(silent = false) },
                                    onOpenSettings = { showSyncHub = true }
                                )
                                WorkspaceModule.CHOKAR_SALES -> ChokarCustomerSalesModuleScreen(
                                    chokarSales = chokarSales,
                                    syncTelemetry = syncTelemetry,
                                    auditState = officeAuditState,
                                    onCreateChokarBill = { showCreateInvoice = true },
                                    onBack = { activeWorkspaceModule = null },
                                    onSyncNow = { viewModel.triggerSupabaseSync(silent = false) },
                                    onOpenSettings = { showSyncHub = true }
                                )
                                null -> Unit
                            }
                        }

                        // Full-screen Authentic Kumar Foods Tax Invoice Preview (Save/Share PDF + Print)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = previewInvoice != null && renderedPdfPreview == null,
                            enter = slideInHorizontally { it / 2 } + fadeIn(),
                            exit = slideOutHorizontally { it / 2 } + fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            previewInvoice?.let { sale ->
                                InvoicePreviewScreen(
                                    sale = sale,
                                    onBack = { previewInvoice = null }
                                )
                            }
                        }

                        // Full-screen Native PdfRenderer Viewer for Exact Original Windows ERP PDFs
                        androidx.compose.animation.AnimatedVisibility(
                            visible = renderedPdfPreview != null,
                            enter = slideInHorizontally { it / 2 } + fadeIn(),
                            exit = slideOutHorizontally { it / 2 } + fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            renderedPdfPreview?.let { pdfReq ->
                                PdfBytesPreviewScreen(
                                    request = pdfReq,
                                    onBack = viewModel::dismissRenderedPdfPreview
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInventoryEditor) {
        InventoryEditorSheet(
            existingItem = editingInventory,
            onDismiss = { showInventoryEditor = false },
            onSave = { item ->
                viewModel.saveInventoryItem(item)
                showInventoryEditor = false
            }
        )
    }

    if (showAddShipment) {
        AddShipmentSheet(
            onDismiss = { showAddShipment = false },
            onCreate = { shipment ->
                viewModel.createShipment(shipment)
                showAddShipment = false
            }
        )
    }

    if (showAddMill) {
        AddMillEquipmentSheet(
            onDismiss = { showAddMill = false },
            onCreate = { mill ->
                viewModel.addMillEquipment(mill)
                showAddMill = false
            }
        )
    }

    if (showCreateInvoice) {
        CreateGstInvoiceSheet(
            onDismiss = { showCreateInvoice = false },
            onCreate = { invoice ->
                viewModel.createGstInvoice(invoice)
                showCreateInvoice = false
            }
        )
    }

    if (showAddLedger) {
        AddLedgerEntrySheet(
            onDismiss = { showAddLedger = false },
            onSave = { entry ->
                viewModel.addPartyLedgerEntry(entry)
                showAddLedger = false
            }
        )
    }

    if (showTableMapper) {
        SupabaseTableMapperSheet(
            currentMapping = tableMapping,
            discoveredTables = syncTelemetry.discoveredTables,
            onDismiss = { showTableMapper = false },
            onSaveMapping = { newMap ->
                viewModel.updateTableMapping(newMap)
                showTableMapper = false
            }
        )
    }

    if (showSyncHub) {
        SyncControlHubSheet(
            userEmail = userEmail,
            syncTelemetry = syncTelemetry,
            auditState = officeAuditState,
            connectionConfig = connectionConfig,
            alertsEnabled = alertsEnabled,
            hasNotificationPermission = hasNotifPermission,
            onSaveSupabaseConnection = { url, key, pairCode, autoSync, bucket ->
                viewModel.saveSupabaseConnectionAndSync(url, key, pairCode, autoSync, bucket)
            },
            onDisconnectOffice = viewModel::disconnectOfficePairing,
            onToggleAutoSync = viewModel::setAutoSyncEnabled,
            onOpenTableMapper = { showTableMapper = true },
            onToggleAlerts = viewModel::setAlertsEnabled,
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onForceSyncSupabase = { viewModel.triggerSupabaseSync(silent = false) },
            onSignOut = {
                showSyncHub = false
                signOutUser(credentialManager, scope)
            },
            onDismiss = { showSyncHub = false }
        )
    }
}
