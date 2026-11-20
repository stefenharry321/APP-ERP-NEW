package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AiAnalyticsReport
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryCategory
import com.example.data.model.InventoryItem
import com.example.data.model.InvoicePaymentStatus
import com.example.data.model.LedgerEntryType
import com.example.data.model.MillEquipment
import com.example.data.model.MillMachineType
import com.example.data.model.MillStatus
import com.example.data.model.PartyLedgerEntry
import com.example.data.model.Shipment
import com.example.data.model.ShipmentStatus
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ErpRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun userRoot(uid: String) = db.collection("users").document(uid)

    fun observeInventory(userId: String): Flow<List<InventoryItem>> = flow {
        val path = "users/$userId/inventory"
        emitAll(
            userRoot(userId).collection("inventory")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        InventoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, userId, path)
                    throw error
                }
        )
    }

    fun observeShipments(userId: String): Flow<List<Shipment>> = flow {
        val path = "users/$userId/shipments"
        emitAll(
            userRoot(userId).collection("shipments")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Shipment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.statusEnum.stepIndex }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, userId, path)
                    throw error
                }
        )
    }

    fun observeMills(userId: String): Flow<List<MillEquipment>> = flow {
        val path = "users/$userId/mills"
        emitAll(
            userRoot(userId).collection("mills")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MillEquipment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.healthScore }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, userId, path)
                    throw error
                }
        )
    }

    fun observeAiReports(userId: String): Flow<List<AiAnalyticsReport>> = flow {
        val path = "users/$userId/ai_reports"
        emitAll(
            userRoot(userId).collection("ai_reports")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiAnalyticsReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, userId, path)
                    throw error
                }
        )
    }

    fun observeGstInvoices(userId: String): Flow<List<GstInvoice>> = flow {
        val path = "users/$userId/gst_invoices"
        emitAll(
            userRoot(userId).collection("gst_invoices")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GstInvoice::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, userId, path)
                    throw error
                }
        )
    }

    fun observePartyLedger(userId: String): Flow<List<PartyLedgerEntry>> = flow {
        val path = "users/$userId/party_ledger"
        emitAll(
            userRoot(userId).collection("party_ledger")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PartyLedgerEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, userId, path)
                    throw error
                }
        )
    }

    suspend fun upsertInventoryItem(item: InventoryItem): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/inventory"
        return try {
            val uid = requireUserId()
            val skuClean = item.sku.trim().take(40).ifBlank { "KF-${System.currentTimeMillis() % 10000}" }
            val nameClean = item.name.trim().take(100)
            require(nameClean.isNotBlank()) { "Product name is required" }

            val col = userRoot(uid).collection("inventory")
            val docRef = if (item.id.isNotBlank()) col.document(item.id) else col.document()
            docPath = docRef.path

            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "sku" to skuClean,
                        "name" to nameClean,
                        "category" to InventoryCategory.fromRaw(item.category).name,
                        "warehouse" to item.warehouse.trim().take(80).ifBlank { "Main Mill Plant" },
                        "quantityMt" to item.quantityMt.coerceIn(0.0, 100000.0),
                        "capacityMt" to item.capacityMt.coerceIn(1.0, 100000.0),
                        "lowThresholdMt" to item.lowThresholdMt.coerceIn(0.0, 100000.0),
                        "unitPriceInr" to item.unitPriceInr.coerceIn(0.0, 1000000.0),
                        "batchCode" to item.batchCode.trim().take(50).ifBlank { "KF-BATCH" },
                        "syncSource" to if (item.syncSource == "SUPABASE_ERP") "SUPABASE_ERP" else "FIREBASE",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "sku" to skuClean,
                        "name" to nameClean,
                        "category" to InventoryCategory.fromRaw(item.category).name,
                        "warehouse" to item.warehouse.trim().take(80).ifBlank { "Main Mill Plant" },
                        "quantityMt" to item.quantityMt.coerceIn(0.0, 100000.0),
                        "capacityMt" to item.capacityMt.coerceIn(1.0, 100000.0),
                        "lowThresholdMt" to item.lowThresholdMt.coerceIn(0.0, 100000.0),
                        "unitPriceInr" to item.unitPriceInr.coerceIn(0.0, 1000000.0),
                        "batchCode" to item.batchCode.trim().take(50).ifBlank { "KF-BATCH" },
                        "syncSource" to if (item.syncSource == "SUPABASE_ERP") "SUPABASE_ERP" else "FIREBASE",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adjustInventoryStock(
        itemId: String,
        newQuantityMt: Double,
        newThresholdMt: Double,
        syncSource: String = "FIREBASE"
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/inventory/$itemId"
        return try {
            val uid = requireUserId()
            require(itemId.isNotBlank()) { "Inventory item ID required" }
            val docRef = userRoot(uid).collection("inventory").document(itemId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "quantityMt" to newQuantityMt.coerceIn(0.0, 100000.0),
                    "lowThresholdMt" to newThresholdMt.coerceIn(0.0, 100000.0),
                    "syncSource" to if (syncSource == "SUPABASE_ERP") "SUPABASE_ERP" else "FIREBASE",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryItem(itemId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/inventory/$itemId"
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("inventory").document(itemId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createShipment(shipment: Shipment): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/shipments"
        return try {
            val uid = requireUserId()
            val col = userRoot(uid).collection("shipments")
            val docRef = if (shipment.id.isNotBlank()) col.document(shipment.id) else col.document()
            docPath = docRef.path

            val payload = mapOf(
                "trackingCode" to shipment.trackingCode.trim().take(40).ifBlank { "KF-SHP-${System.currentTimeMillis() % 9999}" },
                "clientName" to shipment.clientName.trim().take(100).ifBlank { "Distributor Client" },
                "destination" to shipment.destination.trim().take(100).ifBlank { "Regional Depot" },
                "productName" to shipment.productName.trim().take(100).ifBlank { "Chakki Fresh Atta" },
                "weightMt" to shipment.weightMt.coerceIn(0.1, 10000.0),
                "truckNumber" to shipment.truckNumber.trim().take(30).ifBlank { "UP-14-KT-1024" },
                "status" to ShipmentStatus.fromRaw(shipment.status).name,
                "etaText" to shipment.etaText.trim().take(60).ifBlank { "Today, 6:00 PM" },
                "warehouseOrigin" to shipment.warehouseOrigin.trim().take(80).ifBlank { "Main Mill Plant" },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun advanceShipmentStatus(
        shipmentId: String,
        nextStatus: ShipmentStatus,
        etaText: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/shipments/$shipmentId"
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("shipments").document(shipmentId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to nextStatus.name,
                    "etaText" to etaText.trim().take(60).ifBlank { nextStatus.label },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertMillEquipment(mill: MillEquipment): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/mills"
        return try {
            val uid = requireUserId()
            val col = userRoot(uid).collection("mills")
            val docRef = if (mill.id.isNotBlank()) col.document(mill.id) else col.document()
            docPath = docRef.path

            val existing = docRef.get().await()
            val advice = mill.lastAiAdvice?.trim()?.take(600)?.takeIf { it.isNotEmpty() }
            if (existing.exists()) {
                val updateMap = mutableMapOf<String, Any>(
                    "healthScore" to mill.healthScore.coerceIn(0, 100),
                    "temperatureC" to mill.temperatureC.coerceIn(0.0, 250.0),
                    "vibrationMmS" to mill.vibrationMmS.coerceIn(0.0, 100.0),
                    "runningHours" to mill.runningHours.coerceIn(0.0, 500000.0),
                    "outputRateMtHr" to mill.outputRateMtHr.coerceIn(0.0, 500.0),
                    "status" to MillStatus.fromRaw(mill.status).name,
                    "nextServiceDate" to mill.nextServiceDate.trim().take(40).ifBlank { "2026-12-01" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (advice != null) updateMap["lastAiAdvice"] = advice
                docRef.update(updateMap).await()
            } else {
                val createMap = mutableMapOf<String, Any>(
                    "machineName" to mill.machineName.trim().take(100).ifBlank { "Milling Unit" },
                    "machineType" to MillMachineType.fromRaw(mill.machineType).name,
                    "zone" to mill.zone.trim().take(60).ifBlank { "Grinding Floor A" },
                    "healthScore" to mill.healthScore.coerceIn(0, 100),
                    "temperatureC" to mill.temperatureC.coerceIn(0.0, 250.0),
                    "vibrationMmS" to mill.vibrationMmS.coerceIn(0.0, 100.0),
                    "runningHours" to mill.runningHours.coerceIn(0.0, 500000.0),
                    "outputRateMtHr" to mill.outputRateMtHr.coerceIn(0.0, 500.0),
                    "status" to MillStatus.fromRaw(mill.status).name,
                    "nextServiceDate" to mill.nextServiceDate.trim().take(40).ifBlank { "2026-12-01" },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (advice != null) createMap["lastAiAdvice"] = advice
                docRef.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMillMaintenanceSchedule(
        millId: String,
        newStatus: MillStatus,
        nextServiceDate: String,
        healthScore: Int,
        aiAdvice: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/mills/$millId"
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("mills").document(millId)
            docPath = docRef.path
            val payload = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "nextServiceDate" to nextServiceDate.trim().take(40).ifBlank { "2026-12-01" },
                "healthScore" to healthScore.coerceIn(0, 100),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            aiAdvice?.trim()?.take(600)?.takeIf { it.isNotEmpty() }?.let {
                payload["lastAiAdvice"] = it
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveAiReport(report: AiAnalyticsReport): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/ai_reports"
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("ai_reports").document()
            docPath = docRef.path
            val validType = when (report.reportType) {
                "SALES_FORECAST", "SUPPLY_CHAIN_REPORT", "MAINTENANCE_AUDIT" -> report.reportType
                else -> "SALES_FORECAST"
            }
            docRef.set(
                mapOf(
                    "reportType" to validType,
                    "title" to report.title.trim().take(120).ifBlank { "Kumar Foods AI Report" },
                    "summary" to report.summary.trim().take(4000).ifBlank { "Analysis complete." },
                    "predictedGrowthPct" to report.predictedGrowthPct.coerceIn(-100.0, 500.0),
                    "recommendedRestockMt" to report.recommendedRestockMt.coerceIn(0.0, 100000.0),
                    "forecastLabels" to report.forecastLabels.take(14),
                    "forecastValues" to report.forecastValues.take(14),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Explicit user-triggered starter mill data initializer so plant managers can
     * immediately experience Kumar Foods Processing Company's live dashboard & AI analytics.
     */
    suspend fun initializeKumarFoodsStarterData(): Result<Unit> {
        return try {
            val starterInventory = listOf(
                InventoryItem(
                    id = "inv_chakki_fresh_10kg",
                    sku = "KF-CHK-10KG",
                    name = "Chakki Fresh Whole Wheat Atta",
                    category = InventoryCategory.FINISHED_ATTA.name,
                    warehouse = "Main Mill Plant",
                    quantityMt = 68.4,
                    capacityMt = 120.0,
                    lowThresholdMt = 25.0,
                    unitPriceInr = 35200.0,
                    batchCode = "KF-2611-A1",
                    syncSource = "FIREBASE"
                ),
                InventoryItem(
                    id = "inv_sharbati_gold_5kg",
                    sku = "KF-SHR-05KG",
                    name = "Sharbati Gold MP Atta",
                    category = InventoryCategory.FINISHED_ATTA.name,
                    warehouse = "North Dispatch Hub",
                    quantityMt = 14.2,
                    capacityMt = 80.0,
                    lowThresholdMt = 20.0,
                    unitPriceInr = 41800.0,
                    batchCode = "KF-2611-S4",
                    syncSource = "FIREBASE"
                ),
                InventoryItem(
                    id = "inv_raw_mp_wheat",
                    sku = "KF-WHT-MP01",
                    name = "Raw MP Lokwan Wheat Grain",
                    category = InventoryCategory.RAW_WHEAT.name,
                    warehouse = "Central Silo Yard",
                    quantityMt = 342.0,
                    capacityMt = 500.0,
                    lowThresholdMt = 120.0,
                    unitPriceInr = 26400.0,
                    batchCode = "SILO-01-MP",
                    syncSource = "FIREBASE"
                ),
                InventoryItem(
                    id = "inv_superfine_maida",
                    sku = "KF-MDA-50KG",
                    name = "Superfine Bakery Maida",
                    category = InventoryCategory.FINISHED_ATTA.name,
                    warehouse = "East Dry Depot",
                    quantityMt = 11.5,
                    capacityMt = 60.0,
                    lowThresholdMt = 15.0,
                    unitPriceInr = 33900.0,
                    batchCode = "KF-2611-M2",
                    syncSource = "FIREBASE"
                ),
                InventoryItem(
                    id = "inv_wheat_bran",
                    sku = "KF-BRN-40KG",
                    name = "Refined Wheat Bran (Chokar)",
                    category = InventoryCategory.BYPRODUCT.name,
                    warehouse = "Main Mill Plant",
                    quantityMt = 44.0,
                    capacityMt = 75.0,
                    lowThresholdMt = 12.0,
                    unitPriceInr = 18500.0,
                    batchCode = "KF-2611-B9",
                    syncSource = "FIREBASE"
                )
            )

            val starterShipments = listOf(
                Shipment(
                    id = "shp_8842",
                    trackingCode = "KF-SHP-8842",
                    clientName = "Metro Wholesale Mart",
                    destination = "Lucknow Regional Depot",
                    productName = "Chakki Fresh Atta 10kg",
                    weightMt = 22.0,
                    truckNumber = "UP-32-KT-4421",
                    status = ShipmentStatus.IN_TRANSIT.name,
                    etaText = "Today, 6:45 PM",
                    warehouseOrigin = "Main Mill Plant"
                ),
                Shipment(
                    id = "shp_8845",
                    trackingCode = "KF-SHP-8845",
                    clientName = "Varanasi Grain Syndicate",
                    destination = "Varanasi Mandi Sector 4",
                    productName = "Sharbati Gold MP Atta",
                    weightMt = 16.5,
                    truckNumber = "UP-65-BT-9910",
                    status = ShipmentStatus.OUT_FOR_DELIVERY.name,
                    etaText = "Arriving in 40m",
                    warehouseOrigin = "North Dispatch Hub"
                ),
                Shipment(
                    id = "shp_8851",
                    trackingCode = "KF-SHP-8851",
                    clientName = "Kanpur Bakers Co-op",
                    destination = "Kanpur Industrial Area",
                    productName = "Superfine Bakery Maida",
                    weightMt = 14.0,
                    truckNumber = "UP-78-DN-3108",
                    status = ShipmentStatus.LOADING.name,
                    etaText = "Dispatch at 8:00 PM",
                    warehouseOrigin = "East Dry Depot"
                )
            )

            val starterMills = listOf(
                MillEquipment(
                    id = "mill_buhler_rm01",
                    machineName = "Buhler Roller Mill RM-01",
                    machineType = MillMachineType.ROLLER_MILL.name,
                    zone = "Grinding Floor A",
                    healthScore = 92,
                    temperatureC = 51.4,
                    vibrationMmS = 1.6,
                    runningHours = 3120.0,
                    outputRateMtHr = 7.2,
                    status = MillStatus.OPTIMAL.name,
                    nextServiceDate = "2026-12-14"
                ),
                MillEquipment(
                    id = "mill_chakki_c04",
                    machineName = "Emery Chakki Line C-04",
                    machineType = MillMachineType.CHAKKI_STONE.name,
                    zone = "Stone Milling Bay B",
                    healthScore = 58,
                    temperatureC = 76.8,
                    vibrationMmS = 5.4,
                    runningHours = 6840.0,
                    outputRateMtHr = 3.8,
                    status = MillStatus.CRITICAL.name,
                    nextServiceDate = "2026-11-22"
                ),
                MillEquipment(
                    id = "mill_plansifter_p02",
                    machineName = "Quadra Plansifter PS-02",
                    machineType = MillMachineType.PLANSIFTER.name,
                    zone = "Sifting Tower 2",
                    healthScore = 76,
                    temperatureC = 59.2,
                    vibrationMmS = 3.7,
                    runningHours = 4910.0,
                    outputRateMtHr = 8.5,
                    status = MillStatus.WARNING.name,
                    nextServiceDate = "2026-11-28"
                ),
                MillEquipment(
                    id = "mill_packer_pk01",
                    machineName = "Auto-Weigh Packer PK-01",
                    machineType = MillMachineType.PACKAGING_LINE.name,
                    zone = "Dispatch Packaging Hall",
                    healthScore = 95,
                    temperatureC = 39.5,
                    vibrationMmS = 0.9,
                    runningHours = 2240.0,
                    outputRateMtHr = 9.1,
                    status = MillStatus.OPTIMAL.name,
                    nextServiceDate = "2026-12-20"
                )
            )

            starterInventory.forEach { upsertInventoryItem(it).getOrThrow() }
            starterShipments.forEach { createShipment(it).getOrThrow() }
            starterMills.forEach { upsertMillEquipment(it).getOrThrow() }

            val starterInvoices = listOf(
                GstInvoice(
                    id = "gst_kf_0104",
                    invoiceNumber = "KF/26-27/0104",
                    partyName = "Metro Wholesale Mart",
                    partyGstin = "09AAACM4821F1Z2",
                    hsnCode = "1101",
                    itemDescription = "Chakki Fresh Whole Wheat Atta 10kg (22 MT)",
                    quantityMt = 22.0,
                    taxableAmountInr = 774400.0,
                    gstRatePct = 5.0,
                    cgstInr = 19360.0,
                    sgstInr = 19360.0,
                    totalAmountInr = 813120.0,
                    paymentStatus = InvoicePaymentStatus.PARTIAL.name,
                    invoiceDate = "2026-11-19",
                    syncSource = "FIREBASE"
                ),
                GstInvoice(
                    id = "gst_kf_0105",
                    invoiceNumber = "KF/26-27/0105",
                    partyName = "Varanasi Grain Syndicate",
                    partyGstin = "09AABCV9031K1Z8",
                    hsnCode = "1101",
                    itemDescription = "Sharbati Gold MP Atta 5kg (16.5 MT)",
                    quantityMt = 16.5,
                    taxableAmountInr = 689700.0,
                    gstRatePct = 5.0,
                    cgstInr = 17242.5,
                    sgstInr = 17242.5,
                    totalAmountInr = 724185.0,
                    paymentStatus = InvoicePaymentStatus.UNPAID.name,
                    invoiceDate = "2026-11-20",
                    syncSource = "FIREBASE"
                )
            )
            val starterLedger = listOf(
                PartyLedgerEntry(
                    id = "led_metro_01",
                    partyName = "Metro Wholesale Mart",
                    partyPhone = "+91-9839011220",
                    partyCity = "Lucknow",
                    entryType = LedgerEntryType.DEBIT_SALE.name,
                    amountInr = 813120.0,
                    referenceNo = "KF/26-27/0104",
                    notes = "22 MT Chakki Fresh Atta Dispatch",
                    entryDate = "2026-11-19",
                    syncSource = "FIREBASE"
                ),
                PartyLedgerEntry(
                    id = "led_metro_02",
                    partyName = "Metro Wholesale Mart",
                    partyPhone = "+91-9839011220",
                    partyCity = "Lucknow",
                    entryType = LedgerEntryType.CREDIT_PAYMENT.name,
                    amountInr = 500000.0,
                    referenceNo = "RTGS-882419",
                    notes = "NEFT/RTGS Settlement Received",
                    entryDate = "2026-11-20",
                    syncSource = "FIREBASE"
                ),
                PartyLedgerEntry(
                    id = "led_varanasi_01",
                    partyName = "Varanasi Grain Syndicate",
                    partyPhone = "+91-9415208844",
                    partyCity = "Varanasi",
                    entryType = LedgerEntryType.DEBIT_SALE.name,
                    amountInr = 724185.0,
                    referenceNo = "KF/26-27/0105",
                    notes = "16.5 MT Sharbati Gold MP Atta",
                    entryDate = "2026-11-20",
                    syncSource = "FIREBASE"
                )
            )
            starterInvoices.forEach { upsertGstInvoice(it).getOrThrow() }
            starterLedger.forEach { addPartyLedgerEntry(it).getOrThrow() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertGstInvoice(invoice: GstInvoice): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/gst_invoices"
        return try {
            val uid = requireUserId()
            val col = userRoot(uid).collection("gst_invoices")
            val docRef = if (invoice.id.isNotBlank()) col.document(invoice.id) else col.document()
            docPath = docRef.path

            val taxable = invoice.taxableAmountInr.coerceIn(0.0, 100000000.0)
            val rate = invoice.gstRatePct.coerceIn(0.0, 28.0)
            val cgst = (taxable * rate / 200.0).coerceIn(0.0, 50000000.0)
            val sgst = (taxable * rate / 200.0).coerceIn(0.0, 50000000.0)
            val total = (taxable + cgst + sgst).coerceIn(0.0, 150000000.0)
            val syncTag = if (invoice.syncSource == "SUPABASE_ERP") "SUPABASE_ERP" else "FIREBASE"

            val existing = docRef.get().await()
            if (existing.exists()) {
                if (existing.getString("paymentStatus") == InvoicePaymentStatus.PAID.name) {
                    return Result.success(Unit)
                }
                docRef.update(
                    mapOf(
                        "partyName" to invoice.partyName.trim().take(100).ifBlank { "Wholesale Party" },
                        "partyGstin" to invoice.partyGstin.trim().take(20),
                        "hsnCode" to invoice.hsnCode.trim().take(16).ifBlank { "1101" },
                        "itemDescription" to invoice.itemDescription.trim().take(120).ifBlank { "Whole Wheat Atta" },
                        "quantityMt" to invoice.quantityMt.coerceIn(0.01, 10000.0),
                        "taxableAmountInr" to taxable,
                        "gstRatePct" to rate,
                        "cgstInr" to cgst,
                        "sgstInr" to sgst,
                        "totalAmountInr" to total,
                        "paymentStatus" to InvoicePaymentStatus.fromRaw(invoice.paymentStatus).name,
                        "invoiceDate" to invoice.invoiceDate.trim().take(30).ifBlank { "2026-11-20" },
                        "syncSource" to syncTag,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "invoiceNumber" to invoice.invoiceNumber.trim().take(40).ifBlank { "KF/26-27/${(1000..9999).random()}" },
                        "partyName" to invoice.partyName.trim().take(100).ifBlank { "Wholesale Party" },
                        "partyGstin" to invoice.partyGstin.trim().take(20),
                        "hsnCode" to invoice.hsnCode.trim().take(16).ifBlank { "1101" },
                        "itemDescription" to invoice.itemDescription.trim().take(120).ifBlank { "Whole Wheat Atta" },
                        "quantityMt" to invoice.quantityMt.coerceIn(0.01, 10000.0),
                        "taxableAmountInr" to taxable,
                        "gstRatePct" to rate,
                        "cgstInr" to cgst,
                        "sgstInr" to sgst,
                        "totalAmountInr" to total,
                        "paymentStatus" to InvoicePaymentStatus.fromRaw(invoice.paymentStatus).name,
                        "invoiceDate" to invoice.invoiceDate.trim().take(30).ifBlank { "2026-11-20" },
                        "syncSource" to syncTag,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markInvoicePaid(invoiceId: String, syncSource: String = "FIREBASE"): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/gst_invoices/$invoiceId"
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("gst_invoices").document(invoiceId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "paymentStatus" to InvoicePaymentStatus.PAID.name,
                    "syncSource" to if (syncSource == "SUPABASE_ERP") "SUPABASE_ERP" else "FIREBASE",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addPartyLedgerEntry(entry: PartyLedgerEntry): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/party_ledger"
        return try {
            val uid = requireUserId()
            val col = userRoot(uid).collection("party_ledger")
            val docRef = if (entry.id.isNotBlank()) col.document(entry.id) else col.document()
            docPath = docRef.path

            val syncTag = if (entry.syncSource == "SUPABASE_ERP") "SUPABASE_ERP" else "FIREBASE"
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "partyName" to entry.partyName.trim().take(100).ifBlank { "Party Account" },
                        "partyPhone" to entry.partyPhone.trim().take(25),
                        "partyCity" to entry.partyCity.trim().take(80).ifBlank { "Lucknow" },
                        "entryType" to LedgerEntryType.fromRaw(entry.entryType).name,
                        "amountInr" to entry.amountInr.coerceIn(0.01, 100000000.0),
                        "referenceNo" to entry.referenceNo.trim().take(50).ifBlank { "REF-${System.currentTimeMillis() % 10000}" },
                        "notes" to entry.notes.trim().take(300),
                        "entryDate" to entry.entryDate.trim().take(30).ifBlank { "2026-11-20" },
                        "syncSource" to syncTag,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "partyName" to entry.partyName.trim().take(100).ifBlank { "Party Account" },
                        "partyPhone" to entry.partyPhone.trim().take(25),
                        "partyCity" to entry.partyCity.trim().take(80).ifBlank { "Lucknow" },
                        "entryType" to LedgerEntryType.fromRaw(entry.entryType).name,
                        "amountInr" to entry.amountInr.coerceIn(0.01, 100000000.0),
                        "referenceNo" to entry.referenceNo.trim().take(50).ifBlank { "REF-${System.currentTimeMillis() % 10000}" },
                        "notes" to entry.notes.trim().take(300),
                        "entryDate" to entry.entryDate.trim().take(30).ifBlank { "2026-11-20" },
                        "syncSource" to syncTag,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePartyLedgerEntry(entryId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/party_ledger/$entryId"
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("party_ledger").document(entryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfActive(error: Throwable, uid: String, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
    }
}
