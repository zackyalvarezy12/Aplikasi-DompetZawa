package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.local.NusaKasDao
import com.example.data.local.SyncStates
import com.example.data.prefs.AppPreferences
import com.example.data.prefs.UserSettings
import com.example.data.remote.FirestoreCloudSyncRepository
import com.example.data.repository.NusaKasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

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
    private val prefs: AppPreferences,
    val firestoreRepo: FirestoreCloudSyncRepository = FirestoreCloudSyncRepository(context)
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
     * Local-First + Automatic Firebase Firestore Sync:
     * - Writes always land in local Room immediately so the UI is 100% responsive offline.
     * - When online and signed in with Google (Firebase Auth), pushes queued mutations to Firestore.
     * - If offline or not yet signed in with Google, retains items safely in sync_queue.
     */
    suspend fun syncPendingQueue(
        settings: UserSettings,
        isNetworkAvailable: Boolean,
        @Suppress("UNUSED_PARAMETER") activeWorkspaceType: String
    ): SyncReport = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable || settings.simulatedOffline) {
            return@withContext SyncReport(
                0,
                0,
                "Tanpa Internet: perubahan tetap tersimpan aman di HP ini.",
                false
            )
        }

        val uid = firestoreRepo.currentAuthenticatedUid()
        if (uid == null) {
            return@withContext SyncReport(
                0,
                0,
                "Tersimpan aman di HP ini. Sambungkan akun Google untuk cadangan online otomatis.",
                true
            )
        }

        val pending = dao.getPendingSyncItems()
        if (pending.isEmpty()) {
            val now = System.currentTimeMillis()
            prefs.updateLastSync(now)
            return@withContext SyncReport(0, 0, "Semua data sudah dicadangkan di akun Google.", true)
        }

        _isSyncing.value = true
        try {
            val succeededIds = mutableListOf<String>()
            val syncedWorkspaces = mutableSetOf<String>()
            var lastError: String? = null

            pending.forEach { item ->
                val result = firestoreRepo.pushQueueItem(item, dao, settings)
                if (result.isSuccess) {
                    succeededIds += item.id
                    syncedWorkspaces += item.workspaceId
                } else {
                    lastError = result.exceptionOrNull()?.localizedMessage
                }
            }

            if (succeededIds.isNotEmpty()) {
                dao.markSyncQueueSynced(succeededIds)
                syncedWorkspaces.forEach { wsId ->
                    dao.markAllTransactionsSynced(wsId, SyncStates.SYNCED)
                }
                val now = System.currentTimeMillis()
                prefs.updateLastSync(now)
            }

            return@withContext if (succeededIds.size == pending.size) {
                val msg = "Cadangan Google berhasil: ${succeededIds.size} perubahan tersimpan online."
                _lastSyncResult.value = msg
                SyncReport(succeededIds.size, 0, msg, true)
            } else {
                val msg = "Sebagian tersimpan online (${succeededIds.size}/${pending.size}). ${lastError ?: ""}".trim()
                _lastSyncResult.value = msg
                SyncReport(succeededIds.size, 0, msg, false)
            }
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun syncFullAndMergeOnSignIn(
        settings: UserSettings,
        repo: NusaKasRepository,
        uid: String,
        email: String,
        displayName: String
    ): SyncReport = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            val pullRes = firestoreRepo.pullUserSnapshot()
            if (pullRes.isSuccess) {
                val snap = pullRes.getOrThrow()
                if (snap.workspaces.isNotEmpty()) {
                    val wsPair = repo.mergeCloudSnapshot(snap, uid, email, displayName)
                    if (wsPair != null) {
                        prefs.setActiveWorkspace(wsPair.first, wsPair.second)
                    }
                    snap.profile?.let { fsProfile ->
                        prefs.setThemePreset(fsProfile.themePreset)
                        if (fsProfile.biometricEnabled) prefs.setBiometricEnabled(true)
                        if (fsProfile.autoSalaryEnabled) {
                            prefs.saveAutoSalaryConfig(
                                enabled = fsProfile.autoSalaryEnabled,
                                baseSalary = fsProfile.monthlyBaseSalary,
                                paydayDay = fsProfile.salaryPaydayDay,
                                workingDays = fsProfile.salaryWorkingDays,
                                targetAccountId = snap.accounts.firstOrNull()?.id ?: ""
                            )
                        }
                    }
                    prefs.setOnboardingState(completedOnboarding = true, completedTutorial = true)
                    val now = System.currentTimeMillis()
                    prefs.updateLastSync(now)
                    val msg = "Data cadangan dari akun Google dipulihkan (${snap.transactions.size} catatan, ${snap.accounts.size} rekening)."
                    _lastSyncResult.value = msg
                    return@withContext SyncReport(snap.transactions.size, 0, msg, true)
                }
            }

            // Cloud is empty for this user: push local data up cleanly
            val pushRes = firestoreRepo.pushFullSnapshot(dao, settings)
            if (pushRes.isSuccess) {
                val count = pushRes.getOrThrow()
                val pendingIds = dao.getPendingSyncItems().map { it.id }
                if (pendingIds.isNotEmpty()) dao.markSyncQueueSynced(pendingIds)
                dao.getAllWorkspacesSnapshot().forEach { ws ->
                    dao.markAllTransactionsSynced(ws.id, SyncStates.SYNCED)
                }
                prefs.updateLastSync(System.currentTimeMillis())
                val msg = "Akun Google tersambung & $count data dari HP dicadangkan online."
                _lastSyncResult.value = msg
                SyncReport(count, 0, msg, true)
            } else {
                val msg = pushRes.exceptionOrNull()?.localizedMessage ?: "Gagal mencadangkan ke akun Google."
                _lastSyncResult.value = msg
                SyncReport(0, 0, msg, false)
            }
        } finally {
            _isSyncing.value = false
        }
    }
}
