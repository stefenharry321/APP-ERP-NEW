package com.example.data.repository

import android.util.Log
import com.example.data.model.AiAnalyticsReport
import com.example.data.model.InventoryItem
import com.example.data.model.MillEquipment
import com.example.data.model.Shipment
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiAnalyticsRepo"

data class MillMaintenanceDiagnosis(
    val riskLevel: String,
    val predictedFailureWindow: String,
    val recommendedServiceDate: String,
    val recommendedAction: String
)

class AiAnalyticsRepository {

    private val forecastSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Executive forecast title under 80 chars"),
            "summary" to Schema.string(description = "Actionable supply chain and demand insight for Kumar Foods Processing Company"),
            "predictedGrowthPct" to Schema.double(description = "Predicted 7-day sales growth percentage"),
            "recommendedRestockMt" to Schema.double(description = "Recommended raw wheat & atta restock volume in metric tonnes"),
            "forecastLabels" to Schema.array(
                items = Schema.string(),
                description = "Exactly 7 short day labels like D1..D7 or Mon..Sun"
            ),
            "forecastValues" to Schema.array(
                items = Schema.double(),
                description = "Exactly 7 predicted daily atta demand values in metric tonnes"
            )
        )
    )

    private val maintenanceSchema = Schema.obj(
        properties = mapOf(
            "riskLevel" to Schema.enumeration(
                values = listOf("OPTIMAL", "WARNING", "CRITICAL", "MAINTENANCE"),
                description = "Recommended operational status"
            ),
            "predictedFailureWindow" to Schema.string(description = "Estimated time before bearing/stone wear risk"),
            "recommendedServiceDate" to Schema.string(description = "Recommended service date in YYYY-MM-DD format"),
            "recommendedAction" to Schema.string(description = "Concise engineering maintenance prescription under 240 characters")
        )
    )

    private val forecastModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = forecastSchema
            }
        )
    }

    private val maintenanceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = maintenanceSchema
            }
        )
    }

    private val reportStreamModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun generateSalesForecast(
        inventory: List<InventoryItem>,
        shipments: List<Shipment>,
        mills: List<MillEquipment>
    ): Result<AiAnalyticsReport> {
        return try {
            val contextPrompt = buildOperationsContext(inventory, shipments, mills)
            val prompt = """
                You are the Chief Supply Chain & Demand Forecasting AI for Kumar Foods Processing Company (a commercial wheat flour / atta manufacturing mill in India).
                Analyze the live ERP telemetry below and predict the next 7 days of flour demand (in Metric Tonnes), projected sales growth percentage, recommended wheat/atta restock tonnage, and a concise strategic summary covering warehouse stock balancing.
                
                $contextPrompt
            """.trimIndent()

            val response = withTimeout(60_000L) {
                forecastModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("AI returned an empty forecast payload.")
            val json = JSONObject(rawJson)

            val labelsArray = json.getJSONArray("forecastLabels")
            val labels = (0 until labelsArray.length()).map { labelsArray.getString(it) }.take(7)
            val valuesArray = json.getJSONArray("forecastValues")
            val values = (0 until valuesArray.length()).map { valuesArray.getDouble(it) }.take(7)

            Result.success(
                AiAnalyticsReport(
                    reportType = "SALES_FORECAST",
                    title = json.optString("title", "7-Day Flour Demand Forecast").take(120),
                    summary = json.optString("summary", "Forecast ready.").take(3500),
                    predictedGrowthPct = json.optDouble("predictedGrowthPct", 8.5).coerceIn(-100.0, 500.0),
                    recommendedRestockMt = json.optDouble("recommendedRestockMt", 40.0).coerceIn(0.0, 100000.0),
                    forecastLabels = labels.ifEmpty { listOf("D1", "D2", "D3", "D4", "D5", "D6", "D7") },
                    forecastValues = values.ifEmpty { listOf(42.0, 44.0, 46.5, 48.0, 51.0, 53.5, 56.0) }
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Sales forecast timed out", e)
            Result.failure(Exception("AI forecast timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sales forecast generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun diagnoseMillMaintenance(mill: MillEquipment): Result<MillMaintenanceDiagnosis> {
        return try {
            val prompt = """
                You are a Predictive Maintenance Industrial Engineer for Kumar Foods Processing Company flour mills.
                Analyze this milling machine telemetry and predict wear risk, recommended service date (from 2026-11-20), and concrete maintenance action (roller re-fluting, chakki stone dressing, bearing lubrication, belt tension, sifter mesh replacement).
                
                Machine: ${mill.machineName} (${mill.machineTypeEnum.label})
                Zone: ${mill.zone}
                Health Score: ${mill.healthScore}%
                Temperature: ${mill.temperatureC} °C
                Vibration: ${mill.vibrationMmS} mm/s
                Running Hours: ${mill.runningHours} hrs
                Throughput: ${mill.outputRateMtHr} MT/hr
                Current Status: ${mill.status}
            """.trimIndent()

            val response = withTimeout(60_000L) {
                maintenanceModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("AI returned empty maintenance diagnostics.")
            val json = JSONObject(rawJson)

            Result.success(
                MillMaintenanceDiagnosis(
                    riskLevel = json.optString("riskLevel", mill.status),
                    predictedFailureWindow = json.optString("predictedFailureWindow", "14 days"),
                    recommendedServiceDate = json.optString("recommendedServiceDate", mill.nextServiceDate).take(40),
                    recommendedAction = json.optString("recommendedAction", "Inspect bearings and roller gap calibration.").take(500)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Maintenance AI diagnosis timed out", e)
            Result.failure(Exception("Predictive maintenance analysis timed out. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Maintenance AI diagnosis failed", e)
            Result.failure(e)
        }
    }

    fun streamExecutiveSupplyChainReport(
        inventory: List<InventoryItem>,
        shipments: List<Shipment>,
        mills: List<MillEquipment>
    ): Flow<String> = flow {
        val contextPrompt = buildOperationsContext(inventory, shipments, mills)
        val prompt = """
            Generate a crisp, executive Automated Operations & Supply Chain Report for Kumar Foods Processing Company.
            Structure with short bullet sections:
            1. Production & Warehouse Health
            2. Immediate Low-Stock Reorder Priorities
            3. Shipment Dispatch Velocity
            4. Milling Equipment Risk & Efficiency Recommendations
            Keep it practical, numerical, and easy to scan on a mobile ERP screen.
            
            $contextPrompt
        """.trimIndent()

        val buffer = StringBuilder()
        withTimeout(60_000L) {
            reportStreamModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    buffer.append(text)
                    emit(buffer.toString())
                }
            }
        }
    }

    private fun buildOperationsContext(
        inventory: List<InventoryItem>,
        shipments: List<Shipment>,
        mills: List<MillEquipment>
    ): String {
        val invLines = if (inventory.isEmpty()) "No inventory items recorded yet."
        else inventory.joinToString("\n") {
            "- ${it.name} [${it.sku}] @ ${it.warehouse}: ${it.quantityMt}/${it.capacityMt} MT (Low threshold: ${it.lowThresholdMt} MT, ₹${it.unitPriceInr}/MT)"
        }

        val shpLines = if (shipments.isEmpty()) "No active shipments."
        else shipments.joinToString("\n") {
            "- ${it.trackingCode}: ${it.productName} (${it.weightMt} MT) to ${it.clientName}, ${it.destination} | Status: ${it.status} | ETA: ${it.etaText}"
        }

        val millLines = if (mills.isEmpty()) "No mills registered."
        else mills.joinToString("\n") {
            "- ${it.machineName}: Health ${it.healthScore}%, Temp ${it.temperatureC}°C, Vib ${it.vibrationMmS}mm/s, Hours ${it.runningHours}, Output ${it.outputRateMtHr} MT/hr, Status: ${it.status}"
        }

        return """
            INVENTORY ACROSS WAREHOUSES:
            $invLines

            LIVE SHIPMENTS:
            $shpLines

            MILLING EQUIPMENT TELEMETRY:
            $millLines
        """.trimIndent()
    }
}
