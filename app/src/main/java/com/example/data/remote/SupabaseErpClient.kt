package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ChokarSaleEntry
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import com.example.data.model.LiveSupabaseTableData
import com.example.data.model.MillEquipment
import com.example.data.model.OfficeCustomer
import com.example.data.model.PartyLedgerEntry
import com.example.data.model.ProductionReportEntry
import com.example.data.model.SalesRegisterEntry
import com.example.data.model.Shipment
import com.example.data.model.SupabaseTableMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "SupabaseErpClient"
private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

data class SupabaseSnapshot(
    val inventory: List<InventoryItem>,
    val shipments: List<Shipment>,
    val mills: List<MillEquipment>,
    val invoices: List<GstInvoice>,
    val ledger: List<PartyLedgerEntry>,
    val salesRegister: List<SalesRegisterEntry>,
    val officeCustomers: List<OfficeCustomer>,
    val productionReports: List<ProductionReportEntry>,
    val chokarSales: List<ChokarSaleEntry>,
    val discoveredTables: List<String>,
    val latencyMs: Long
)

/**
 * Production-grade Supabase PostgREST, OpenAPI Schema Auto-Discovery, Storage PDF Bridge,
 * and Realtime WebSocket sync client for Kumar Foods Windows ERP.
 * Supports both runtime user-entered pairing credentials (DataStore) and BuildConfig defaults.
 */
class SupabaseErpClient {

    @Volatile
    private var baseUrl: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    @Volatile
    private var anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    @Volatile
    private var pdfStorageBucket: String = "invoices"

    private val concurrencyGate = Semaphore(permits = 5)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _realtimeEvents = MutableSharedFlow<String>(extraBufferCapacity = 48)
    val realtimeEvents: Flow<String> = _realtimeEvents.asSharedFlow()

    @Volatile
    private var activeSocket: WebSocket? = null

    fun updateCredentials(url: String, key: String, bucket: String = "invoices") {
        val cleanUrl = url.trim().trimEnd('/')
        val cleanKey = key.trim()
        baseUrl = if (cleanUrl.isNotBlank()) cleanUrl else BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        anonKey = if (cleanKey.isNotBlank()) cleanKey else BuildConfig.SUPABASE_ANON_KEY.trim()
        pdfStorageBucket = bucket.trim().ifBlank { "invoices" }
    }

    fun hasValidCredentials(): Boolean {
        return baseUrl.startsWith("https://") &&
            !baseUrl.contains("YOUR_PROJECT_REF", ignoreCase = true) &&
            anonKey.length > 20 &&
            !anonKey.contains("YOUR_SUPABASE_ANON_KEY", ignoreCase = true)
    }

    fun resolveOfficePdfUrl(rawPointer: String?): String? {
        val ptr = rawPointer?.trim().orEmpty()
        if (ptr.isBlank() || ptr.equals("null", ignoreCase = true)) return null
        if (ptr.startsWith("https://") || ptr.startsWith("http://")) return ptr
        if (!hasValidCredentials()) return null
        val cleanedPath = ptr.removePrefix("/")
        return if (cleanedPath.startsWith("storage/v1/")) {
            "$baseUrl/$cleanedPath"
        } else {
            "$baseUrl/storage/v1/object/public/$pdfStorageBucket/$cleanedPath"
        }
    }

    fun startRealtimeListener(onSocketStateChanged: (Boolean) -> Unit) {
        if (!hasValidCredentials()) {
            onSocketStateChanged(false)
            return
        }
        stopRealtimeListener()

        val wsBase = baseUrl.replaceFirst("https://", "wss://")
        val wsUrl = "$wsBase/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
        val request = Request.Builder().url(wsUrl).build()

        activeSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Supabase Realtime WebSocket connected (schema: public).")
                    onSocketStateChanged(true)
                    val joinPayload = JSONObject().apply {
                        put("topic", "realtime:public")
                        put("event", "phx_join")
                        put("payload", JSONObject().apply {
                            put("config", JSONObject().apply {
                                put("postgres_changes", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("event", "*")
                                        put("schema", "public")
                                    })
                                })
                            })
                        })
                        put("ref", "1")
                    }
                    webSocket.send(joinPayload.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("postgres_changes") || text.contains("INSERT") || text.contains("UPDATE") || text.contains("DELETE")) {
                        _realtimeEvents.tryEmit(text)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Supabase Realtime socket error: ${t.message}")
                    onSocketStateChanged(false)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onSocketStateChanged(false)
                }
            }
        )
    }

    fun stopRealtimeListener() {
        activeSocket?.close(1000, "Client shutdown")
        activeSocket = null
    }

    suspend fun discoverPublicTables(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!hasValidCredentials()) {
            return@withContext Result.failure(IllegalStateException("Enter your Supabase URL & Key in Settings to connect."))
        }
        concurrencyGate.withPermit {
            try {
                val raw = executeGetWithRetry("$baseUrl/rest/v1/")
                val json = JSONObject(raw)
                val paths = json.optJSONObject("paths")
                val tables = mutableListOf<String>()
                if (paths != null) {
                    val keys = paths.keys()
                    while (keys.hasNext()) {
                        val key = keys.next().removePrefix("/")
                        if (key.isNotBlank() && !key.startsWith("rpc/")) {
                            tables.add(key)
                        }
                    }
                }
                Result.success(tables.sorted())
            } catch (e: Exception) {
                Log.w(TAG, "OpenAPI table discovery failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun fetchRawTableRows(tableName: String, limit: Int = 100): LiveSupabaseTableData =
        withContext(Dispatchers.IO) {
            if (!hasValidCredentials()) {
                return@withContext LiveSupabaseTableData(
                    tableName = tableName,
                    errorMessage = "Open Settings -> Supabase Connection and paste your Supabase URL & Key to browse live Windows ERP tables."
                )
            }
            val safeTable = tableName.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "")
            if (safeTable.isBlank()) {
                return@withContext LiveSupabaseTableData(tableName = tableName, errorMessage = "Invalid table name.")
            }
            concurrencyGate.withPermit {
                val startMs = System.currentTimeMillis()
                try {
                    val raw = executeGetWithRetry("$baseUrl/rest/v1/$safeTable?select=*&limit=$limit")
                    val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    val arr = JSONArray(raw)
                    val columnSet = linkedSetOf<String>()
                    val rows = mutableListOf<Map<String, String>>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val rowMap = linkedMapOf<String, String>()
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            columnSet.add(k)
                            val v = obj.opt(k)
                            rowMap[k] = if (v == null || v == JSONObject.NULL) "—" else v.toString().take(120)
                        }
                        rows.add(rowMap)
                    }
                    LiveSupabaseTableData(
                        tableName = safeTable,
                        columns = columnSet.toList(),
                        rows = rows,
                        latencyMs = latency
                    )
                } catch (e: Exception) {
                    LiveSupabaseTableData(
                        tableName = safeTable,
                        errorMessage = e.localizedMessage ?: "Failed to read table $safeTable"
                    )
                }
            }
        }

    suspend fun pullLiveErpSnapshot(mapping: SupabaseTableMapping): Result<SupabaseSnapshot> =
        withContext(Dispatchers.IO) {
            if (!hasValidCredentials()) {
                return@withContext Result.failure(
                    IllegalStateException("Supabase URL & Anon Key not configured yet.")
                )
            }
            concurrencyGate.withPermit {
                val startMs = System.currentTimeMillis()
                try {
                    val discovered = discoverPublicTables().getOrDefault(emptyList())
                    val invTable = resolveTableName(mapping.inventoryTable, discovered, listOf("inventory", "stock", "chokar_stock", "items"))
                    val shpTable = resolveTableName(mapping.shipmentsTable, discovered, listOf("shipment", "dispatch", "delivery", "trucks"))
                    val millTable = resolveTableName(mapping.millsTable, discovered, listOf("mill", "machine", "equipment", "maintenance"))
                    val billTable = resolveTableName(mapping.invoicesTable, discovered, listOf("sales_register", "sales", "invoice", "bill", "gst"))
                    val ledTable = resolveTableName(mapping.ledgerTable, discovered, listOf("ledger", "voucher", "khata", "receipt", "payment", "day_book"))
                    val custTable = resolveOptionalTable(discovered, listOf("customer", "account", "party", "parties", "debtor"))
                    val prodTable = resolveOptionalTable(discovered, listOf("production", "milling", "grinding"))
                    val chokarSaleTable = resolveOptionalTable(discovered, listOf("chokar_sale", "bran_sale"))

                    val rawBills = safeTableGet(billTable)
                    val inventory = parseInventoryArray(safeTableGet(invTable))
                    val shipments = parseShipmentArray(safeTableGet(shpTable))
                    val mills = parseMillArray(safeTableGet(millTable))
                    val invoices = parseInvoiceArray(rawBills)
                    val salesRegister = parseSalesRegisterArray(rawBills)
                    val ledger = parseLedgerArray(safeTableGet(ledTable))
                    val customers = custTable?.let { parseCustomerArray(safeTableGet(it)) }.orEmpty()
                    val productions = prodTable?.let { parseProductionArray(safeTableGet(it)) }.orEmpty()
                    val chokarSales = chokarSaleTable?.let { parseChokarSalesArray(safeTableGet(it)) }.orEmpty()

                    val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    Result.success(
                        SupabaseSnapshot(
                            inventory = inventory,
                            shipments = shipments,
                            mills = mills,
                            invoices = invoices,
                            ledger = ledger,
                            salesRegister = salesRegister,
                            officeCustomers = customers,
                            productionReports = productions,
                            chokarSales = chokarSales,
                            discoveredTables = discovered,
                            latencyMs = latency
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed pulling live Windows ERP snapshot from Supabase", e)
                    Result.failure(e)
                }
            }
        }

    suspend fun pushInventoryUpdate(item: InventoryItem, table: String = "inventory_items"): Result<Long> =
        postUpsert(
            table = table,
            conflictCol = "sku",
            payload = JSONObject().apply {
                put("sku", item.sku)
                put("name", item.name)
                put("category", item.category)
                put("warehouse", item.warehouse)
                put("quantity_mt", item.quantityMt)
                put("capacity_mt", item.capacityMt)
                put("low_threshold_mt", item.lowThresholdMt)
                put("unit_price_inr", item.unitPriceInr)
                put("batch_code", item.batchCode)
            }
        )

    suspend fun pushShipmentStatus(shipment: Shipment, table: String = "shipments"): Result<Long> =
        postUpsert(
            table = table,
            conflictCol = "tracking_code",
            payload = JSONObject().apply {
                put("tracking_code", shipment.trackingCode)
                put("client_name", shipment.clientName)
                put("destination", shipment.destination)
                put("product_name", shipment.productName)
                put("weight_mt", shipment.weightMt)
                put("truck_number", shipment.truckNumber)
                put("status", shipment.status)
                put("eta_text", shipment.etaText)
                put("warehouse_origin", shipment.warehouseOrigin)
            }
        )

    suspend fun pushGstInvoice(invoice: GstInvoice, table: String = "gst_invoices"): Result<Long> =
        postUpsert(
            table = table,
            conflictCol = "invoice_number",
            payload = JSONObject().apply {
                put("invoice_number", invoice.invoiceNumber)
                put("party_name", invoice.partyName)
                put("party_gstin", invoice.partyGstin)
                put("hsn_code", invoice.hsnCode)
                put("item_description", invoice.itemDescription)
                put("quantity_mt", invoice.quantityMt)
                put("taxable_amount_inr", invoice.taxableAmountInr)
                put("gst_rate_pct", invoice.gstRatePct)
                put("cgst_inr", invoice.cgstInr)
                put("sgst_inr", invoice.sgstInr)
                put("total_amount_inr", invoice.totalAmountInr)
                put("payment_status", invoice.paymentStatus)
                put("invoice_date", invoice.invoiceDate)
            }
        )

    suspend fun pushLedgerEntry(entry: PartyLedgerEntry, table: String = "party_ledger"): Result<Long> =
        postUpsert(
            table = table,
            conflictCol = "reference_no",
            payload = JSONObject().apply {
                put("party_name", entry.partyName)
                put("party_phone", entry.partyPhone)
                put("party_city", entry.partyCity)
                put("entry_type", entry.entryType)
                put("amount_inr", entry.amountInr)
                put("reference_no", entry.referenceNo)
                put("notes", entry.notes)
                put("entry_date", entry.entryDate)
            }
        )

    private suspend fun postUpsert(table: String, conflictCol: String, payload: JSONObject): Result<Long> =
        withContext(Dispatchers.IO) {
            if (!hasValidCredentials()) return@withContext Result.success(0L)
            val safeTable = table.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "")
            if (safeTable.isBlank()) return@withContext Result.success(0L)
            concurrencyGate.withPermit {
                val startMs = System.currentTimeMillis()
                try {
                    val request = Request.Builder()
                        .url("$baseUrl/rest/v1/$safeTable?on_conflict=$conflictCol")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                        .post(payload.toString().toRequestBody(JSON_MEDIA))
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Supabase HTTP ${response.code}: ${response.message}")
                        }
                    }
                    Result.success((System.currentTimeMillis() - startMs).coerceAtLeast(1L))
                } catch (e: Exception) {
                    Log.w(TAG, "Supabase push to $safeTable skipped: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    private suspend fun safeTableGet(table: String): String {
        val clean = table.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "")
        if (clean.isBlank()) return "[]"
        return try {
            executeGetWithRetry("$baseUrl/rest/v1/$clean?select=*&limit=250")
        } catch (e: Exception) {
            Log.w(TAG, "Optional table $clean unavailable: ${e.message}")
            "[]"
        }
    }

    private fun resolveTableName(
        configured: String,
        discovered: List<String>,
        keywords: List<String>
    ): String {
        if (discovered.isEmpty()) return configured
        discovered.firstOrNull { it.equals(configured, ignoreCase = true) }?.let { return it }
        return discovered.firstOrNull { tbl ->
            keywords.any { kw -> tbl.contains(kw, ignoreCase = true) }
        } ?: configured
    }

    private fun resolveOptionalTable(discovered: List<String>, keywords: List<String>): String? {
        return discovered.firstOrNull { tbl ->
            keywords.any { kw -> tbl.contains(kw, ignoreCase = true) }
        }
    }

    private suspend fun executeGetWithRetry(url: String, maxAttempts: Int = 3): String {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < maxAttempts) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return response.body?.string() ?: "[]"
                    }
                    if (response.code == 404) return "[]"
                    throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                }
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt < maxAttempts) delay(350L * attempt)
            }
        }
        throw lastError ?: IllegalStateException("Supabase request failed")
    }

    private fun JSONObject.strAny(vararg keys: String, fallback: String = ""): String {
        for (k in keys) {
            val v = optString(k, "").trim()
            if (v.isNotEmpty() && !v.equals("null", ignoreCase = true)) return v
        }
        return fallback
    }

    private fun JSONObject.dblAny(vararg keys: String, fallback: Double = 0.0): Double {
        for (k in keys) {
            if (has(k) && !isNull(k)) {
                val v = optDouble(k, Double.NaN)
                if (!v.isNaN()) return v
                optString(k, "").toDoubleOrNull()?.let { return it }
            }
        }
        return fallback
    }

    private fun parseInventoryArray(raw: String): List<InventoryItem> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val sku = obj.strAny("sku", "item_code", "product_code", "code", "id", fallback = "SUP-${i + 1}")
                val name = obj.strAny("name", "product_name", "item_name", "title", fallback = "Flour Stock").take(100)
                val catRaw = obj.strAny("category", "type", fallback = if (name.contains("chokar", true) || name.contains("bran", true)) "BYPRODUCT" else "FINISHED_ATTA")
                add(
                    InventoryItem(
                        id = sanitizeDocId("sup_inv_$sku"),
                        sku = sku.take(40),
                        name = name,
                        category = catRaw,
                        warehouse = obj.strAny("warehouse", "godown", "location", "branch", fallback = "Main Mill Plant").take(80),
                        quantityMt = obj.dblAny("quantity_mt", "quantity", "qty", "stock", "closing_stock", fallback = 0.0).coerceIn(0.0, 100000.0),
                        capacityMt = obj.dblAny("capacity_mt", "capacity", "max_stock", fallback = 100.0).coerceIn(1.0, 100000.0),
                        lowThresholdMt = obj.dblAny("low_threshold_mt", "min_stock", "reorder_level", fallback = 20.0).coerceIn(0.0, 100000.0),
                        unitPriceInr = obj.dblAny("unit_price_inr", "rate", "price", "unit_price", fallback = 33000.0).coerceIn(0.0, 1000000.0),
                        batchCode = obj.strAny("batch_code", "batch_no", "lot_no", fallback = "ERP-LIVE").take(50),
                        syncSource = "SUPABASE_ERP"
                    )
                )
            }
        }
    }

    private fun parseShipmentArray(raw: String): List<Shipment> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val code = obj.strAny("tracking_code", "dispatch_no", "challan_no", "id", fallback = "SHP-${i + 1}")
                add(
                    Shipment(
                        id = sanitizeDocId("sup_shp_$code"),
                        trackingCode = code.take(40),
                        clientName = obj.strAny("client_name", "party_name", "customer_name", fallback = "Distributor").take(100),
                        destination = obj.strAny("destination", "city", "delivery_address", fallback = "Kasganj Hub").take(100),
                        productName = obj.strAny("product_name", "item_name", "material", fallback = "Whole Wheat Atta").take(100),
                        weightMt = obj.dblAny("weight_mt", "weight", "qty_mt", "quantity", fallback = 10.0).coerceIn(0.1, 10000.0),
                        truckNumber = obj.strAny("truck_number", "vehicle_no", "lorry_no", fallback = "UP87AT5635").take(30),
                        status = obj.strAny("status", "dispatch_status", fallback = "LOADING"),
                        etaText = obj.strAny("eta_text", "eta", "expected_date", fallback = "Scheduled").take(60),
                        warehouseOrigin = obj.strAny("warehouse_origin", "warehouse", "godown", fallback = "Main Mill Plant").take(80)
                    )
                )
            }
        }
    }

    private fun parseMillArray(raw: String): List<MillEquipment> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.strAny("machine_name", "mill_name", "name", fallback = "Mill-${i + 1}")
                add(
                    MillEquipment(
                        id = sanitizeDocId("sup_mill_$name"),
                        machineName = name.take(100),
                        machineType = obj.strAny("machine_type", "type", fallback = "ROLLER_MILL"),
                        zone = obj.strAny("zone", "floor", "section", fallback = "Grinding Floor").take(60),
                        healthScore = obj.dblAny("health_score", "health", fallback = 85.0).toInt().coerceIn(0, 100),
                        temperatureC = obj.dblAny("temperature_c", "temp", "temperature", fallback = 52.0).coerceIn(0.0, 250.0),
                        vibrationMmS = obj.dblAny("vibration_mm_s", "vibration", fallback = 2.0).coerceIn(0.0, 100.0),
                        runningHours = obj.dblAny("running_hours", "hours", fallback = 1000.0).coerceIn(0.0, 500000.0),
                        outputRateMtHr = obj.dblAny("output_rate_mt_hr", "output_rate", fallback = 5.0).coerceIn(0.0, 500.0),
                        status = obj.strAny("status", fallback = "OPTIMAL"),
                        nextServiceDate = obj.strAny("next_service_date", "service_date", fallback = "2026-12-10").take(40)
                    )
                )
            }
        }
    }

    private fun parseInvoiceArray(raw: String): List<GstInvoice> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val invNo = obj.strAny("invoice_number", "invoice_no", "sale_no", "bill_no", "id", fallback = "S-${100 + i}")
                val taxable = obj.dblAny("taxable_amount_inr", "taxable_value", "taxable_amount", "subtotal", "amount", fallback = 0.0).coerceIn(0.0, 100000000.0)
                val gstPct = obj.dblAny("gst_rate_pct", "gst_rate", "tax_pct", fallback = 5.0).coerceIn(0.0, 28.0)
                val totalRaw = obj.dblAny("total_amount_inr", "invoice_total", "grand_total", "total_amount", "total", fallback = 0.0)
                val effectiveTaxable = if (taxable > 0.0) taxable else if (gstPct > 0.0) totalRaw / (1.0 + gstPct / 100.0) else totalRaw
                val halfTax = (effectiveTaxable * gstPct / 200.0)
                val cgst = obj.dblAny("cgst_inr", "cgst", fallback = halfTax).coerceIn(0.0, 50000000.0)
                val sgst = obj.dblAny("sgst_inr", "sgst", fallback = halfTax).coerceIn(0.0, 50000000.0)
                val total = if (totalRaw > 0.0) totalRaw else effectiveTaxable + cgst + sgst
                val weightKg = obj.dblAny("total_weight_kg", "weight_kg", "total_qty_kg", fallback = 0.0)
                val qtyMt = if (weightKg > 0.0) (weightKg / 1000.0) else obj.dblAny("quantity_mt", "quantity", "qty", fallback = 1.0)

                add(
                    GstInvoice(
                        id = sanitizeDocId("sup_gst_$invNo"),
                        invoiceNumber = invNo.take(40),
                        partyName = obj.strAny("party_name", "customer_name", "account_name", "buyer_name", fallback = "Wholesale Party").take(100),
                        partyGstin = obj.strAny("party_gstin", "gstin", "gst_no", fallback = "").take(20),
                        hsnCode = obj.strAny("hsn_code", "hsn", fallback = "1101").take(16),
                        itemDescription = obj.strAny("item_description", "product_name", "description", fallback = "Atta").take(120),
                        quantityMt = qtyMt.coerceIn(0.01, 10000.0),
                        taxableAmountInr = effectiveTaxable.coerceIn(0.0, 100000000.0),
                        gstRatePct = gstPct,
                        cgstInr = cgst,
                        sgstInr = sgst,
                        totalAmountInr = total.coerceIn(0.0, 150000000.0),
                        paymentStatus = obj.strAny("payment_status", "status", fallback = "UNPAID"),
                        invoiceDate = obj.strAny("invoice_date", "sale_date", "bill_date", "date", fallback = "20-11-2026").take(30),
                        syncSource = "SUPABASE_ERP"
                    )
                )
            }
        }
    }

    private fun parseSalesRegisterArray(raw: String): List<SalesRegisterEntry> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val saleNo = obj.strAny("sale_no", "invoice_number", "invoice_no", "bill_no", "id", fallback = "S-${100 + i}")
                val gstPct = obj.dblAny("gst_rate_pct", "gst_rate", "tax_pct", fallback = 5.0).coerceIn(0.0, 28.0)
                val total = obj.dblAny("total_amount_inr", "invoice_total", "grand_total", "total_amount", "total", fallback = 0.0)
                val parsedItems = mutableListOf<InvoiceLineItem>()
                val itemsArr = obj.optJSONArray("items") ?: obj.optJSONArray("line_items")
                if (itemsArr != null) {
                    for (j in 0 until itemsArr.length()) {
                        val itObj = itemsArr.optJSONObject(j) ?: continue
                        parsedItems.add(
                            InvoiceLineItem(
                                productName = itObj.strAny("product", "product_name", "name", fallback = "Atta"),
                                hsnCode = itObj.strAny("hsn", "hsn_code", fallback = "1101"),
                                packKg = itObj.dblAny("pack_kg", "pack_size", "weight_per_bag", fallback = 47.0),
                                bags = itObj.dblAny("bags", "qty", "quantity", fallback = 10.0).toInt().coerceAtLeast(1),
                                ratePerKgInclGst = itObj.dblAny("rate_per_kg", "rate", "price_per_kg", fallback = 33.0),
                                gstRatePct = gstPct
                            )
                        )
                    }
                }
                if (parsedItems.isEmpty()) {
                    val weightKg = obj.dblAny("total_weight_kg", "weight_kg", fallback = obj.dblAny("quantity_mt", fallback = 1.0) * 1000.0)
                    val bags = obj.dblAny("bags", "total_bags", fallback = (weightKg / 47.0).coerceAtLeast(1.0)).toInt().coerceAtLeast(1)
                    val packKg = if (bags > 0) weightKg / bags else 47.0
                    val rateKg = if (weightKg > 0.0 && total > 0.0) total / weightKg else obj.dblAny("rate_per_kg", "rate", fallback = 33.0)
                    parsedItems.add(
                        InvoiceLineItem(
                            productName = obj.strAny("product_name", "item_description", "product", fallback = "Atta"),
                            hsnCode = obj.strAny("hsn_code", "hsn", fallback = "1101"),
                            packKg = packKg,
                            bags = bags,
                            ratePerKgInclGst = rateKg,
                            gstRatePct = gstPct
                        )
                    )
                }
                val pdfPointer = obj.strAny("office_pdf_url", "pdf_url", "pdf_path", "office_pdf_path", "storage_path", fallback = "")
                val computedTotal = if (total > 0.0) total else parsedItems.sumOf { it.grossAmountInr }
                add(
                    SalesRegisterEntry(
                        id = sanitizeDocId("sup_sale_$saleNo"),
                        saleNo = saleNo,
                        invoiceNo = obj.strAny("invoice_no", "invoice_number", fallback = saleNo),
                        date = obj.strAny("sale_date", "invoice_date", "date", fallback = "20-11-2026"),
                        partyName = obj.strAny("party_name", "customer_name", "account_name", fallback = "Customer"),
                        partyAddress = obj.strAny("party_address", "address", fallback = "Mohanpura, Kasganj, Uttar Pradesh - 09"),
                        partyMobile = obj.strAny("party_mobile", "mobile", "phone", fallback = ""),
                        partyGstin = obj.strAny("party_gstin", "gstin", fallback = "Unregistered / Not Provided"),
                        partyState = obj.strAny("place_of_supply", "state", fallback = "Uttar Pradesh (09)"),
                        vehicleNo = obj.strAny("vehicle_no", "truck_number", fallback = ""),
                        items = parsedItems,
                        gstRatePct = gstPct,
                        totalAmountInr = computedTotal,
                        balanceDueInr = obj.dblAny("balance_due", "balance", fallback = computedTotal),
                        paymentStatus = obj.strAny("payment_status", "status", fallback = "UNPAID"),
                        officePdfUrl = resolveOfficePdfUrl(pdfPointer),
                        syncSource = "SUPABASE_ERP"
                    )
                )
            }
        }
    }

    private fun parseLedgerArray(raw: String): List<PartyLedgerEntry> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val ref = obj.strAny("reference_no", "voucher_no", "receipt_no", "ref_no", "id", fallback = "V-${i + 1}")
                val credit = obj.dblAny("credit", "cr_amount", "received", fallback = 0.0)
                val debit = obj.dblAny("debit", "dr_amount", "billed", fallback = 0.0)
                val rawType = obj.strAny("entry_type", "voucher_type", "type", "txn_type", fallback = if (credit > 0.0) "CREDIT_PAYMENT" else "DEBIT_SALE")
                val entryType = if (credit > 0.0 || rawType.contains("CREDIT", true) || rawType.contains("PAY", true) || rawType.contains("REC", true)) {
                    "CREDIT_PAYMENT"
                } else {
                    "DEBIT_SALE"
                }
                val amt = when {
                    entryType == "CREDIT_PAYMENT" && credit > 0.0 -> credit
                    entryType == "DEBIT_SALE" && debit > 0.0 -> debit
                    else -> obj.dblAny("amount_inr", "amount", "total", "balance", fallback = 1000.0)
                }
                add(
                    PartyLedgerEntry(
                        id = sanitizeDocId("sup_led_$ref"),
                        partyName = obj.strAny("party_name", "customer_name", "account_name", "acc_name", fallback = "Party Account").take(100),
                        partyPhone = obj.strAny("party_phone", "phone", "mobile", fallback = "").take(25),
                        partyCity = obj.strAny("party_city", "city", "location", fallback = "Kasganj, UP").take(80),
                        entryType = entryType,
                        amountInr = amt.coerceIn(0.01, 100000000.0),
                        referenceNo = ref.take(50),
                        notes = obj.strAny("notes", "description", "narration", "remarks", fallback = "Windows ERP Entry").take(300),
                        entryDate = obj.strAny("entry_date", "voucher_date", "date", "txn_date", fallback = "20-11-2026").take(30),
                        syncSource = "SUPABASE_ERP"
                    )
                )
            }
        }
    }

    private fun parseCustomerArray(raw: String): List<OfficeCustomer> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.strAny("name", "customer_name", "party_name", "account_name", fallback = "Customer ${i + 1}")
                val closing = obj.dblAny("closing_balance", "balance", "outstanding", "due_amount", fallback = 0.0)
                add(
                    OfficeCustomer(
                        id = sanitizeDocId("sup_cust_$name"),
                        name = name,
                        category = obj.strAny("category", "group_name", "type", fallback = "Customer"),
                        mobile = obj.strAny("mobile", "phone", "party_phone", fallback = ""),
                        city = obj.strAny("city", "location", fallback = "Kasganj, UP"),
                        address = obj.strAny("address", fallback = ""),
                        gstin = obj.strAny("gstin", "gst_no", fallback = ""),
                        openingBalanceDr = obj.dblAny("opening_balance", "op_bal", fallback = 0.0),
                        closingBalanceDr = closing,
                        totalEntries = obj.dblAny("entry_count", "vouchers_count", fallback = 0.0).toInt(),
                        syncSource = "SUPABASE_ERP"
                    )
                )
            }
        }
    }

    private fun parseProductionArray(raw: String): List<ProductionReportEntry> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val batch = obj.strAny("batch_code", "batch_no", "id", fallback = "PRD-${i + 1}")
                add(
                    ProductionReportEntry(
                        id = sanitizeDocId("sup_prd_$batch"),
                        date = obj.strAny("date", "production_date", fallback = "20-11-2026"),
                        batchCode = batch,
                        millUnit = obj.strAny("mill_unit", "machine_name", "line", fallback = "Buhler Roller Mill RM-01"),
                        wheatProcessedKg = obj.dblAny("wheat_kg", "wheat_processed_kg", "input_kg", fallback = 12000.0),
                        attaProducedKg = obj.dblAny("atta_kg", "atta_produced_kg", "output_kg", fallback = 9360.0),
                        chokarProducedKg = obj.dblAny("chokar_kg", "bran_kg", "chokar_produced_kg", fallback = 2400.0),
                        shiftName = obj.strAny("shift", "shift_name", fallback = "Day Shift"),
                        syncSource = "SUPABASE_ERP"
                    )
                )
            }
        }
    }

    private fun parseChokarSalesArray(raw: String): List<ChokarSaleEntry> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val billNo = obj.strAny("bill_no", "sale_no", "invoice_no", "id", fallback = "CHK-${i + 1}")
                val pack = obj.dblAny("pack_kg", "bag_weight", fallback = 40.0)
                val bags = obj.dblAny("bags", "qty", fallback = 25.0).toInt().coerceAtLeast(1)
                val rate = obj.dblAny("rate_per_kg", "rate", fallback = 21.5)
                val total = obj.dblAny("total_amount", "total", "amount", fallback = pack * bags * rate)
                add(
                    ChokarSaleEntry(
                        id = sanitizeDocId("sup_chk_$billNo"),
                        billNo = billNo,
                        date = obj.strAny("date", "sale_date", fallback = "20-11-2026"),
                        partyName = obj.strAny("party_name", "customer_name", fallback = "Dairy Farm Buyer"),
                        partyMobile = obj.strAny("mobile", "phone", fallback = ""),
                        packKg = pack,
                        bags = bags,
                        ratePerKg = rate,
                        totalAmountInr = total,
                        syncSource = "SUPABASE_ERP"
                    )
                )
            }
        }
    }

    private fun sanitizeDocId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(100).ifBlank { "doc_sync" }
}
