package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SupabaseConnectionConfig
import com.example.data.model.SupabaseTableMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.erpPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kumar_foods_erp_prefs"
)

class SupabasePreferencesStore(private val context: Context) {

    private object Keys {
        val SUPABASE_URL = stringPreferencesKey("supabase_url")
        val SUPABASE_ANON_KEY = stringPreferencesKey("supabase_anon_key")
        val MOBILE_PAIR_CODE = stringPreferencesKey("mobile_pair_code_v2")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val STORAGE_BUCKET = stringPreferencesKey("storage_bucket")
        val TBL_INVENTORY = stringPreferencesKey("tbl_inventory")
        val TBL_SHIPMENTS = stringPreferencesKey("tbl_shipments")
        val TBL_MILLS = stringPreferencesKey("tbl_mills")
        val TBL_INVOICES = stringPreferencesKey("tbl_invoices")
        val TBL_LEDGER = stringPreferencesKey("tbl_ledger")
    }

    val connectionConfigFlow: Flow<SupabaseConnectionConfig> = context.erpPrefsDataStore.data.map { prefs ->
        SupabaseConnectionConfig(
            supabaseUrl = prefs[Keys.SUPABASE_URL].orEmpty(),
            supabaseAnonKey = prefs[Keys.SUPABASE_ANON_KEY].orEmpty(),
            mobilePairCode = prefs[Keys.MOBILE_PAIR_CODE].orEmpty(),
            autoSyncEnabled = prefs[Keys.AUTO_SYNC_ENABLED] ?: true,
            pdfStorageBucket = prefs[Keys.STORAGE_BUCKET].orEmpty().ifBlank { "invoices" }
        )
    }

    val tableMappingFlow: Flow<SupabaseTableMapping> = context.erpPrefsDataStore.data.map { prefs ->
        SupabaseTableMapping(
            inventoryTable = prefs[Keys.TBL_INVENTORY].orEmpty().ifBlank { "inventory_items" },
            shipmentsTable = prefs[Keys.TBL_SHIPMENTS].orEmpty().ifBlank { "shipments" },
            millsTable = prefs[Keys.TBL_MILLS].orEmpty().ifBlank { "mill_equipment" },
            invoicesTable = prefs[Keys.TBL_INVOICES].orEmpty().ifBlank { "gst_invoices" },
            ledgerTable = prefs[Keys.TBL_LEDGER].orEmpty().ifBlank { "party_ledger" }
        )
    }

    suspend fun saveConnectionConfig(
        url: String,
        anonKey: String,
        mobilePairCode: String,
        autoSyncEnabled: Boolean,
        pdfBucket: String = "invoices"
    ) {
        context.erpPrefsDataStore.edit { prefs ->
            prefs[Keys.SUPABASE_URL] = url.trim().trimEnd('/')
            prefs[Keys.SUPABASE_ANON_KEY] = anonKey.trim()
            prefs[Keys.MOBILE_PAIR_CODE] = mobilePairCode.trim()
            prefs[Keys.AUTO_SYNC_ENABLED] = autoSyncEnabled
            prefs[Keys.STORAGE_BUCKET] = pdfBucket.trim().ifBlank { "invoices" }
        }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.erpPrefsDataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_ENABLED] = enabled
        }
    }

    suspend fun saveTableMapping(mapping: SupabaseTableMapping) {
        context.erpPrefsDataStore.edit { prefs ->
            prefs[Keys.TBL_INVENTORY] = mapping.inventoryTable.trim()
            prefs[Keys.TBL_SHIPMENTS] = mapping.shipmentsTable.trim()
            prefs[Keys.TBL_MILLS] = mapping.millsTable.trim()
            prefs[Keys.TBL_INVOICES] = mapping.invoicesTable.trim()
            prefs[Keys.TBL_LEDGER] = mapping.ledgerTable.trim()
        }
    }

    suspend fun clearConnectionConfig() {
        context.erpPrefsDataStore.edit { prefs ->
            prefs.remove(Keys.SUPABASE_URL)
            prefs.remove(Keys.SUPABASE_ANON_KEY)
            prefs.remove(Keys.MOBILE_PAIR_CODE)
        }
    }
}
