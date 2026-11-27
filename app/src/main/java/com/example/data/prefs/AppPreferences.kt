package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "nusakas_prefs")

@Immutable
data class UserSettings(
    val activeWorkspaceId: String = "ws_personal",
    val activeMemberId: String = "mem_p_zacky",
    val isPinEnabled: Boolean = false,
    val pinHash: String = "",
    val isStealthMode: Boolean = false,
    val autoSyncSimulation: Boolean = true,
    val simulatedOffline: Boolean = false,
    val cloudProvider: String = "SUPABASE", // SUPABASE | FIREBASE
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
    val lastSalaryNet: Long = 0L
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val ACTIVE_WORKSPACE_ID = stringPreferencesKey("active_workspace_id")
        val ACTIVE_MEMBER_ID = stringPreferencesKey("active_member_id")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
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
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            activeWorkspaceId = prefs[Keys.ACTIVE_WORKSPACE_ID] ?: "ws_personal",
            activeMemberId = prefs[Keys.ACTIVE_MEMBER_ID] ?: "mem_p_zacky",
            isPinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            isStealthMode = prefs[Keys.STEALTH_MODE] ?: false,
            autoSyncSimulation = prefs[Keys.AUTO_SYNC_SIMULATION] ?: true,
            simulatedOffline = prefs[Keys.SIMULATED_OFFLINE] ?: false,
            cloudProvider = prefs[Keys.CLOUD_PROVIDER] ?: "SUPABASE",
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
            lastSalaryNet = prefs[Keys.LAST_SALARY_NET] ?: 0L
        )
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

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
