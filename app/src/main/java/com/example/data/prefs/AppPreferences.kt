package com.example.data.prefs

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "nusakas_prefs")

@Immutable
data class UserSettings(
    val activeWorkspaceId: String = "ws_personal",
    val activeMemberId: String = "mem_owner",
    val hasCompletedOnboarding: Boolean = false,
    val hasCompletedTutorial: Boolean = false,
    val isPinEnabled: Boolean = false,
    val pinHash: String = "",
    val isBiometricEnabled: Boolean = false,
    val isBillReminderEnabled: Boolean = true,
    val notifiedBillKeys: Set<String> = emptySet(),
    val googleUid: String = "",
    val googleEmail: String = "",
    val googleDisplayName: String = "",
    val isStealthMode: Boolean = false,
    val autoSyncSimulation: Boolean = true,
    val simulatedOffline: Boolean = false,
    val cloudProvider: String = "FIREBASE",
    val cloudUrl: String = "",
    val cloudApiKey: String = "",
    val lastSyncTimestamp: Long = 0L,
    val themePreset: String = "CUTE_PASTEL",
    val hideBeginnerGuide: Boolean = false,
    val isAutoSalaryEnabled: Boolean = false,
    val monthlyBaseSalary: Long = 0L,
    val salaryPaydayDay: Int = 17,
    val salaryWorkingDays: Int = 22,
    val salaryTargetAccountId: String = "",
    val lastSalaryCycleHandled: String = "",
    val lastSalaryGross: Long = 0L,
    val lastSalaryDeduction: Long = 0L,
    val lastSalaryNet: Long = 0L,
    val profilePhotoPath: String = "",
    val workspacePurposes: Map<String, String> = emptyMap()
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val ACTIVE_WORKSPACE_ID = stringPreferencesKey("active_workspace_id")
        val ACTIVE_MEMBER_ID = stringPreferencesKey("active_member_id")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val HAS_COMPLETED_TUTORIAL = booleanPreferencesKey("has_completed_tutorial")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val BILL_REMINDER_ENABLED = booleanPreferencesKey("bill_reminder_enabled")
        val NOTIFIED_BILL_KEYS = stringSetPreferencesKey("notified_bill_keys")
        val GOOGLE_UID = stringPreferencesKey("google_uid")
        val GOOGLE_EMAIL = stringPreferencesKey("google_email")
        val GOOGLE_DISPLAY_NAME = stringPreferencesKey("google_display_name")
        val STEALTH_MODE = booleanPreferencesKey("stealth_mode")
        val AUTO_SYNC_SIMULATION = booleanPreferencesKey("auto_sync_simulation")
        val SIMULATED_OFFLINE = booleanPreferencesKey("simulated_offline")
        val CLOUD_PROVIDER = stringPreferencesKey("cloud_provider")
        val CLOUD_URL = stringPreferencesKey("cloud_url")
        val CLOUD_API_KEY = stringPreferencesKey("cloud_api_key")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val HIDE_BEGINNER_GUIDE = booleanPreferencesKey("hide_beginner_guide")
        val AUTO_SALARY_ENABLED = booleanPreferencesKey("auto_salary_enabled")
        val MONTHLY_BASE_SALARY = longPreferencesKey("monthly_base_salary")
        val SALARY_PAYDAY_DAY = intPreferencesKey("salary_payday_day")
        val SALARY_WORKING_DAYS = intPreferencesKey("salary_working_days")
        val SALARY_TARGET_ACCOUNT_ID = stringPreferencesKey("salary_target_account_id")
        val LAST_SALARY_CYCLE_HANDLED = stringPreferencesKey("last_salary_cycle_handled")
        val LAST_SALARY_GROSS = longPreferencesKey("last_salary_gross")
        val LAST_SALARY_DEDUCTION = longPreferencesKey("last_salary_deduction")
        val LAST_SALARY_NET = longPreferencesKey("last_salary_net")
        val PROFILE_PHOTO_PATH = stringPreferencesKey("profile_photo_path")
        val WORKSPACE_PURPOSES_JSON = stringPreferencesKey("workspace_purposes_json")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            activeWorkspaceId = prefs[Keys.ACTIVE_WORKSPACE_ID] ?: "ws_personal",
            activeMemberId = prefs[Keys.ACTIVE_MEMBER_ID] ?: "mem_owner",
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            hasCompletedTutorial = prefs[Keys.HAS_COMPLETED_TUTORIAL] ?: false,
            isPinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            isBiometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            isBillReminderEnabled = prefs[Keys.BILL_REMINDER_ENABLED] ?: true,
            notifiedBillKeys = prefs[Keys.NOTIFIED_BILL_KEYS] ?: emptySet(),
            googleUid = prefs[Keys.GOOGLE_UID] ?: "",
            googleEmail = prefs[Keys.GOOGLE_EMAIL] ?: "",
            googleDisplayName = prefs[Keys.GOOGLE_DISPLAY_NAME] ?: "",
            isStealthMode = prefs[Keys.STEALTH_MODE] ?: false,
            autoSyncSimulation = prefs[Keys.AUTO_SYNC_SIMULATION] ?: true,
            simulatedOffline = prefs[Keys.SIMULATED_OFFLINE] ?: false,
            cloudProvider = prefs[Keys.CLOUD_PROVIDER] ?: "FIREBASE",
            cloudUrl = prefs[Keys.CLOUD_URL] ?: "",
            cloudApiKey = prefs[Keys.CLOUD_API_KEY] ?: "",
            lastSyncTimestamp = prefs[Keys.LAST_SYNC_TIMESTAMP] ?: 0L,
            themePreset = prefs[Keys.THEME_PRESET] ?: "CUTE_PASTEL",
            hideBeginnerGuide = prefs[Keys.HIDE_BEGINNER_GUIDE] ?: false,
            isAutoSalaryEnabled = prefs[Keys.AUTO_SALARY_ENABLED] ?: false,
            monthlyBaseSalary = prefs[Keys.MONTHLY_BASE_SALARY] ?: 0L,
            salaryPaydayDay = prefs[Keys.SALARY_PAYDAY_DAY] ?: 17,
            salaryWorkingDays = prefs[Keys.SALARY_WORKING_DAYS] ?: 22,
            salaryTargetAccountId = prefs[Keys.SALARY_TARGET_ACCOUNT_ID] ?: "",
            lastSalaryCycleHandled = prefs[Keys.LAST_SALARY_CYCLE_HANDLED] ?: "",
            lastSalaryGross = prefs[Keys.LAST_SALARY_GROSS] ?: 0L,
            lastSalaryDeduction = prefs[Keys.LAST_SALARY_DEDUCTION] ?: 0L,
            lastSalaryNet = prefs[Keys.LAST_SALARY_NET] ?: 0L,
            profilePhotoPath = prefs[Keys.PROFILE_PHOTO_PATH] ?: "",
            workspacePurposes = decodePurposeMap(prefs[Keys.WORKSPACE_PURPOSES_JSON])
        )
    }

    suspend fun setOnboardingState(completedOnboarding: Boolean, completedTutorial: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_ONBOARDING] = completedOnboarding
            prefs[Keys.HAS_COMPLETED_TUTORIAL] = completedTutorial
        }
    }

    suspend fun setTutorialCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_TUTORIAL] = completed
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setProfilePhotoPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROFILE_PHOTO_PATH] = path
        }
    }

    suspend fun setWorkspacePurpose(workspaceId: String, purpose: String) {
        val cleanId = workspaceId.trim()
        val cleanPurpose = purpose.trim()
        if (cleanId.isBlank() || cleanPurpose.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = decodePurposeMap(prefs[Keys.WORKSPACE_PURPOSES_JSON]).toMutableMap()
            current[cleanId] = cleanPurpose
            val json = JSONObject()
            current.forEach { (k, v) -> json.put(k, v) }
            prefs[Keys.WORKSPACE_PURPOSES_JSON] = json.toString()
        }
    }

    suspend fun setBillReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BILL_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun addNotifiedBillKeys(newKeys: Set<String>) {
        if (newKeys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.NOTIFIED_BILL_KEYS] ?: emptySet()
            val merged = (current + newKeys).toList().takeLast(120).toSet()
            prefs[Keys.NOTIFIED_BILL_KEYS] = merged
        }
    }

    suspend fun setGoogleAccount(uid: String, email: String, displayName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GOOGLE_UID] = uid
            prefs[Keys.GOOGLE_EMAIL] = email
            prefs[Keys.GOOGLE_DISPLAY_NAME] = displayName
            prefs[Keys.CLOUD_PROVIDER] = "FIREBASE"
        }
    }

    suspend fun clearGoogleAccount() {
        context.dataStore.edit { prefs ->
            prefs[Keys.GOOGLE_UID] = ""
            prefs[Keys.GOOGLE_EMAIL] = ""
            prefs[Keys.GOOGLE_DISPLAY_NAME] = ""
        }
    }

    suspend fun setThemePreset(preset: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_PRESET] = preset
        }
    }

    suspend fun setHideBeginnerGuide(hide: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIDE_BEGINNER_GUIDE] = hide
        }
    }

    suspend fun setActiveWorkspace(workspaceId: String, memberId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_WORKSPACE_ID] = workspaceId
            prefs[Keys.ACTIVE_MEMBER_ID] = memberId
        }
    }

    suspend fun setActiveMemberId(memberId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_MEMBER_ID] = memberId
        }
    }

    suspend fun setStealthMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STEALTH_MODE] = enabled
        }
    }

    suspend fun setPin(pin: String) {
        val hash = sha256(pin)
        context.dataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.PIN_ENABLED] = true
        }
    }

    suspend fun disablePin() {
        context.dataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = false
            prefs[Keys.PIN_HASH] = ""
            prefs[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    fun verifyPin(inputPin: String, storedHash: String): Boolean = sha256(inputPin) == storedHash

    suspend fun updateCloudConfig(
        provider: String,
        url: String,
        apiKey: String,
        autoSimulation: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CLOUD_PROVIDER] = provider
            prefs[Keys.CLOUD_URL] = url.trim()
            prefs[Keys.CLOUD_API_KEY] = apiKey.trim()
            prefs[Keys.AUTO_SYNC_SIMULATION] = autoSimulation
        }
    }

    suspend fun setSimulatedOffline(offline: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SIMULATED_OFFLINE] = offline
        }
    }

    suspend fun updateLastSync(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun saveAutoSalaryConfig(
        enabled: Boolean,
        baseSalary: Long,
        paydayDay: Int,
        workingDays: Int,
        targetAccountId: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_SALARY_ENABLED] = enabled
            prefs[Keys.MONTHLY_BASE_SALARY] = baseSalary.coerceAtLeast(0L)
            prefs[Keys.SALARY_PAYDAY_DAY] = paydayDay.coerceIn(1, 31)
            prefs[Keys.SALARY_WORKING_DAYS] = workingDays.coerceIn(1, 31)
            prefs[Keys.SALARY_TARGET_ACCOUNT_ID] = targetAccountId
        }
    }

    suspend fun markSalaryCycleHandled(
        cycleKey: String,
        grossAmount: Long,
        deductionAmount: Long,
        netAmount: Long
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SALARY_CYCLE_HANDLED] = cycleKey
            prefs[Keys.LAST_SALARY_GROSS] = grossAmount.coerceAtLeast(0L)
            prefs[Keys.LAST_SALARY_DEDUCTION] = deductionAmount.coerceAtLeast(0L)
            prefs[Keys.LAST_SALARY_NET] = netAmount.coerceAtLeast(0L)
        }
    }

    suspend fun resetForCleanAccount() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_WORKSPACE_ID] = "ws_personal"
            prefs[Keys.ACTIVE_MEMBER_ID] = "mem_owner"
            prefs[Keys.NOTIFIED_BILL_KEYS] = emptySet()
            prefs[Keys.AUTO_SALARY_ENABLED] = false
            prefs[Keys.MONTHLY_BASE_SALARY] = 0L
            prefs[Keys.SALARY_TARGET_ACCOUNT_ID] = ""
            prefs[Keys.LAST_SALARY_CYCLE_HANDLED] = ""
            prefs[Keys.LAST_SALARY_GROSS] = 0L
            prefs[Keys.LAST_SALARY_DEDUCTION] = 0L
            prefs[Keys.LAST_SALARY_NET] = 0L
            prefs[Keys.LAST_SYNC_TIMESTAMP] = 0L
            prefs[Keys.PROFILE_PHOTO_PATH] = ""
            prefs[Keys.WORKSPACE_PURPOSES_JSON] = ""
        }
    }

    suspend fun logoutToFreshOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_ONBOARDING] = false
            prefs[Keys.HAS_COMPLETED_TUTORIAL] = false
            prefs[Keys.ACTIVE_WORKSPACE_ID] = "ws_personal"
            prefs[Keys.ACTIVE_MEMBER_ID] = "mem_owner"
            prefs[Keys.PIN_ENABLED] = false
            prefs[Keys.PIN_HASH] = ""
            prefs[Keys.BIOMETRIC_ENABLED] = false
            prefs[Keys.GOOGLE_UID] = ""
            prefs[Keys.GOOGLE_EMAIL] = ""
            prefs[Keys.GOOGLE_DISPLAY_NAME] = ""
            prefs[Keys.HIDE_BEGINNER_GUIDE] = false
            prefs[Keys.NOTIFIED_BILL_KEYS] = emptySet()
            prefs[Keys.AUTO_SALARY_ENABLED] = false
            prefs[Keys.MONTHLY_BASE_SALARY] = 0L
            prefs[Keys.SALARY_TARGET_ACCOUNT_ID] = ""
            prefs[Keys.LAST_SALARY_CYCLE_HANDLED] = ""
            prefs[Keys.LAST_SALARY_GROSS] = 0L
            prefs[Keys.LAST_SALARY_DEDUCTION] = 0L
            prefs[Keys.LAST_SALARY_NET] = 0L
            prefs[Keys.LAST_SYNC_TIMESTAMP] = 0L
            prefs[Keys.PROFILE_PHOTO_PATH] = ""
            prefs[Keys.WORKSPACE_PURPOSES_JSON] = ""
        }
    }

    private fun decodePurposeMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val valStr = obj.optString(key).trim()
                    if (valStr.isNotBlank()) put(key, valStr)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
