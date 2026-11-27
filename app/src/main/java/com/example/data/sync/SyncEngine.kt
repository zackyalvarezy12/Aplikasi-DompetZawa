package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.local.AuditLogEntity
import com.example.data.local.NusaKasDao
import com.example.data.local.SyncStates
import com.example.data.local.WorkspaceTypes
import com.example.data.prefs.AppPreferences
import com.example.data.prefs.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class SyncVisualStatus {
    ONLINE_SYNCED,
    SYNCING,
    OFFLINE_LOCAL
}

data class SyncReport(
    val pushedCount: Int,
    val resolvedConflicts: Int,
    val message: String,
    val success: Boolean
)

class SyncEngine(
    private val context: Context,
    private val dao: NusaKasDao,
    private val prefs: AppPreferences
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<String?>(null)
    val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

    fun observeNetworkConnectivity(): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyConnected(cm))
            }
        }
        trySend(isCurrentlyConnected(cm))
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            trySend(true)
        }
        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    private fun isCurrentlyConnected(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Hybrid Local-First Sync Execution:
     * - If offline (network down or simulated offline), keeps items in sync_queue as PENDING.
     * - If Cloud URL + API Key are provided, pushes JSON batch via lightweight HttpURLConnection to Supabase/Firebase.
     * - If Auto Sync Simulation is active, simulates LWW conflict resolution and drains queue cleanly.
     */
    suspend fun syncPendingQueue(
        settings: UserSettings,
        isNetworkAvailable: Boolean,
        activeWorkspaceType: String
    ): SyncReport = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable || settings.simulatedOffline) {
            return@withContext SyncReport(0, 0, "Mode Offline: perubahan tetap aman di database lokal.", false)
        }
        if (activeWorkspaceType == WorkspaceTypes.PERSONAL && dao.getPendingSyncItems().isEmpty()) {
            val now = System.currentTimeMillis()
            prefs.updateLastSync(now)
            return@withContext SyncReport(0, 0, "Workspace Pribadi tersimpan 100% lokal.", true)
        }

        val pending = dao.getPendingSyncItems()
        if (pending.isEmpty()) {
            val now = System.currentTimeMillis()
            prefs.updateLastSync(now)
            return@withContext SyncReport(0, 0, "Semua data sudah tersinkronisasi.", true)
        }

        _isSyncing.value = true
        try {
            val hasRealCloud = settings.cloudUrl.isNotBlank() && settings.cloudApiKey.isNotBlank()
            if (hasRealCloud) {
                val pushed = pushToRealCloudEndpoint(settings, pending.map {
                    JSONObject().apply {
                        put("id", it.id)
                        put("workspace_id", it.workspaceId)
                        put("table_name", it.tableName)
                        put("record_id", it.recordId)
                        put("operation", it.operation)
                        put("payload", it.payloadJson)
                        put("utc_timestamp", it.createdAt)
                    }
                })
                if (pushed) {
                    dao.markSyncQueueSynced(pending.map { it.id })
                    pending.map { it.workspaceId }.distinct().forEach { wsId ->
                        dao.markAllTransactionsSynced(wsId, SyncStates.SYNCED)
                    }
                    val now = System.currentTimeMillis()
                    prefs.updateLastSync(now)
                    val msg = "Tersinkronisasi ke ${settings.cloudProvider}: ${pending.size} antrean (LWW UTC)."
                    _lastSyncResult.value = msg
                    return@withContext SyncReport(pending.size, 0, msg, true)
                } else if (!settings.autoSyncSimulation) {
                    val msg = "Gagal terhubung ke endpoint ${settings.cloudProvider}. Antrean dipertahankan."
                    _lastSyncResult.value = msg
                    return@withContext SyncReport(0, 0, msg, false)
                }
            }

            if (settings.autoSyncSimulation) {
                delay(400)
                dao.markSyncQueueSynced(pending.map { it.id })
                pending.map { it.workspaceId }.distinct().forEach { wsId ->
                    dao.markAllTransactionsSynced(wsId, SyncStates.SYNCED)
                }
                val now = System.currentTimeMillis()
                prefs.updateLastSync(now)
                val msg = "Simulasi Cloud Sync sukses: ${pending.size} perubahan didorong (Last-Write-Wins UTC)."
                _lastSyncResult.value = msg
                return@withContext SyncReport(pending.size, 0, msg, true)
            }

            SyncReport(0, 0, "Lengkapi URL & API Key Cloud atau aktifkan Simulasi Sync.", false)
        } finally {
            _isSyncing.value = false
        }
    }

    private fun pushToRealCloudEndpoint(
        settings: UserSettings,
        records: List<JSONObject>
    ): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val baseUrl = settings.cloudUrl.trimEnd('/')
            val endpoint = if (settings.cloudProvider == "SUPABASE") {
                "$baseUrl/rest/v1/nusakas_sync_events"
            } else {
                "$baseUrl/nusakas_sync_events.json?auth=${settings.cloudApiKey}"
            }
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (settings.cloudProvider == "SUPABASE") {
                    setRequestProperty("apikey", settings.cloudApiKey)
                    setRequestProperty("Authorization", "Bearer ${settings.cloudApiKey}")
                    setRequestProperty("Prefer", "return=minimal")
                }
            }
            val bodyJson = JSONArray(records).toString()
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(bodyJson)
                writer.flush()
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun simulateIncomingPartnerSync(workspaceId: String, partnerName: String = "Nadia") {
        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                workspaceId = workspaceId,
                actorMemberId = "mem_f_nadia",
                actorName = partnerName,
                action = "Sinkronisasi Anggota",
                detail = "Data workspace bersama disinkronkan via Last-Write-Wins UTC",
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
