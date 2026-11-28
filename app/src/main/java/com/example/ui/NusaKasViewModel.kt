package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.prefs.AppPreferences
import com.example.data.prefs.UserSettings
import com.example.data.remote.GoogleAuthHelper
import com.example.data.repository.NusaKasRepository
import com.example.data.sync.SyncEngine
import com.example.data.sync.SyncVisualStatus
import com.example.domain.*
import com.example.worker.BillReminderWorker
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

@Immutable
data class CategorySpendSlice(
    val categoryId: String,
    val name: String,
    val amount: Long,
    val colorHex: Long
)

@Immutable
data class MonthlyBarPoint(
    val label: String,
    val income: Long,
    val expense: Long
)

@Immutable
data class MainUiState(
    val isReady: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val authUserUid: String? = null,
    val authUserEmail: String = "",
    val authUserDisplayName: String = "",
    val isAuthBusy: Boolean = false,
    val currentUser: UserEntity? = null,
    val workspaces: List<WorkspaceEntity> = emptyList(),
    val activeWorkspace: WorkspaceEntity? = null,
    val members: List<WorkspaceMemberEntity> = emptyList(),
    val activeMember: WorkspaceMemberEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val pagedTransactions: List<TransactionEntity> = emptyList(),
    val totalTransactionCount: Int = 0,
    val hasMoreTransactions: Boolean = false,
    val transactionItems: List<TransactionItemEntity> = emptyList(),
    val categoryMap: Map<String, CategoryEntity> = emptyMap(),
    val accountMap: Map<String, AccountEntity> = emptyMap(),
    val splitItemsByTxId: Map<String, List<TransactionItemEntity>> = emptyMap(),
    val budgets: List<BudgetEntity> = emptyList(),
    val recurringRules: List<RecurringRuleEntity> = emptyList(),
    val debtsLoans: List<DebtLoanEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val assets: List<AssetInvestmentEntity> = emptyList(),
    val splitBills: List<SplitBillEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val pendingSyncCount: Int = 0,
    val isNetworkConnected: Boolean = true,
    val syncVisualStatus: SyncVisualStatus = SyncVisualStatus.ONLINE_SYNCED,
    val lastSyncMessage: String? = null,

    // Computed Executive KPIs
    val netWorth: Long = 0L,
    val totalLiquidCash: Long = 0L,
    val totalInvestments: Long = 0L,
    val totalDebts: Long = 0L,
    val monthIncome: Long = 0L,
    val monthExpense: Long = 0L,
    val monthNetCashflow: Long = 0L,
    val savingRatePercent: Double = 0.0,
    val emergencyFundMonths: Double = 0.0,
    val totalBudgetLimit: Long = 0L,
    val totalBudgetSpent: Long = 0L,
    val remainingBudget: Long = 0L,
    val budgetRemainingFraction: Float = 1f,

    // Charts Data
    val netWorthTrend12M: List<Pair<String, Long>> = emptyList(),
    val monthlyBars: List<MonthlyBarPoint> = emptyList(),
    val categorySlices: List<CategorySpendSlice> = emptyList(),
    val dailyHeatmapSpending: Map<Int, Long> = emptyMap(),

    // Engines
    val advisorReport: FinancialHealthReport? = null,
    val simplifiedSettlements: List<SimplifiedSettlement> = emptyList()
) {
    val canEdit: Boolean
        get() = (activeMember?.role ?: MemberRoles.OWNER) != MemberRoles.VIEWER

    val isOwner: Boolean
        get() = (activeMember?.role ?: MemberRoles.OWNER) == MemberRoles.OWNER

    val isSharedWorkspace: Boolean
        get() = activeWorkspace?.type == WorkspaceTypes.SHARED

    val isGoogleLinked: Boolean
        get() = !authUserUid.isNullOrBlank()
}

@OptIn(ExperimentalCoroutinesApi::class)
class NusaKasViewModel(application: Application) : AndroidViewModel(application) {

    private val db = NusaKasDatabase.getInstance(application)
    private val dao = db.dao()
    val prefs = AppPreferences(application)
    val repo = NusaKasRepository(dao)
    val syncEngine = SyncEngine(application, dao, prefs)
    private val googleAuthHelper = GoogleAuthHelper(application)

    private val _authUser = MutableStateFlow<FirebaseUser?>(runCatching { Firebase.auth.currentUser }.getOrNull())
    private val _isAuthBusy = MutableStateFlow(false)

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _authUser.value = firebaseAuth.currentUser
    }

    private val _pinUnlocked = MutableStateFlow(false)
    val isPinUnlocked: StateFlow<Boolean> = _pinUnlocked.asStateFlow()

    private val _hasEnteredSession = MutableStateFlow(false)
    val hasEnteredSession: StateFlow<Boolean> = _hasEnteredSession.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbar.asStateFlow()

    private val _lastSyncMsg = MutableStateFlow<String?>(null)
    private val _ledgerLimit = MutableStateFlow(30)

    private val aiService = ZawaAiAssistantService()
    private var aiChatJob: Job? = null
    private var aiStreamJob: Job? = null

    private val _aiUiState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
    val aiUiState: StateFlow<AiAssistantUiState> = _aiUiState.asStateFlow()

    private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()

    private val _aiAnalysisState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
    val aiAnalysisState: StateFlow<AiAssistantUiState> = _aiAnalysisState.asStateFlow()

    private val _aiStreamingAnalysis = MutableStateFlow("")
    val aiStreamingAnalysis: StateFlow<String> = _aiStreamingAnalysis.asStateFlow()

    private val _aiNavigationRequest = MutableStateFlow<Pair<String, Int>?>(null)
    val aiNavigationRequest: StateFlow<Pair<String, Int>?> = _aiNavigationRequest.asStateFlow()
    private var aiNavNonce = 0

    private val activeWorkspaceIdFlow = prefs.settingsFlow
        .map { it.activeWorkspaceId }
        .distinctUntilChanged()

    private val networkFlow = syncEngine.observeNetworkConnectivity()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        runCatching { Firebase.auth.addAuthStateListener(authStateListener) }

        // Schedule periodic H-3 / H-1 bill reminders
        runCatching { BillReminderWorker.schedulePeriodic(application) }

        // Automatically sync pending local mutations when authenticated with Google and online
        viewModelScope.launch {
            combine(
                networkFlow,
                prefs.settingsFlow,
                repo.observePendingSyncCount(),
                activeWorkspaceIdFlow,
                _authUser
            ) { online, settings, pending, wsId, user ->
                SyncTrigger(online, settings, pending, wsId, user?.uid)
            }.collect { trigger ->
                if (trigger.online &&
                    !trigger.settings.simulatedOffline &&
                    trigger.pending > 0 &&
                    !trigger.uid.isNullOrBlank()
                ) {
                    val ws = dao.getWorkspaceById(trigger.wsId)
                    val rep = syncEngine.syncPendingQueue(
                        trigger.settings,
                        trigger.online,
                        ws?.type ?: WorkspaceTypes.PERSONAL
                    )
                    if (rep.pushedCount > 0) _lastSyncMsg.value = rep.message
                }
            }
        }

        // Observe remote Firestore updates when authenticated
        viewModelScope.launch {
            _authUser
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid.isNullOrBlank()) emptyFlow()
                    else syncEngine.firestoreRepo.observeRemoteTransactionChanges(uid)
                }
                .collect { remoteTxs ->
                    repo.mergeRemoteTransactions(remoteTxs)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { Firebase.auth.removeAuthStateListener(authStateListener) }
    }

    private data class SyncTrigger(
        val online: Boolean,
        val settings: UserSettings,
        val pending: Int,
        val wsId: String,
        val uid: String?
    )

    private val workspaceCoreFlow = combine(activeWorkspaceIdFlow, _ledgerLimit) { wsId, limit ->
        wsId to limit
    }.flatMapLatest { (wsId, limit) ->
        combine(
            repo.observeMembers(wsId),
            repo.observeAccounts(wsId),
            repo.observeCategories(wsId),
            repo.observeRecentTransactions(wsId),
            repo.observeTransactionsPaged(wsId, limit),
            repo.observeTransactionCount(wsId),
            repo.observeTransactionItems(wsId)
        ) { vals ->
            @Suppress("UNCHECKED_CAST")
            WorkspaceCoreBundle(
                members = vals[0] as List<WorkspaceMemberEntity>,
                accounts = vals[1] as List<AccountEntity>,
                categories = vals[2] as List<CategoryEntity>,
                transactions = vals[3] as List<TransactionEntity>,
                pagedTransactions = vals[4] as List<TransactionEntity>,
                totalTxCount = vals[5] as Int,
                items = vals[6] as List<TransactionItemEntity>
            )
        }
    }

    private val workspacePlanningFlow = activeWorkspaceIdFlow.flatMapLatest { wsId ->
        combine(
            repo.observeBudgets(wsId),
            repo.observeRecurringRules(wsId),
            repo.observeDebtsLoans(wsId),
            repo.observeGoals(wsId),
            repo.observeAssets(wsId)
        ) { budgets, recurring, debts, goals, assets ->
            WorkspacePlanningBundle(budgets, recurring, debts, goals, assets)
        }
    }

    private val workspaceGroupFlow = activeWorkspaceIdFlow.flatMapLatest { wsId ->
        combine(
            repo.observeSplitBills(wsId),
            repo.observeAuditLogs(wsId)
        ) { splitBills, logs ->
            splitBills to logs
        }
    }

    private val syncAndAuthBundleFlow = combine(
        repo.observePendingSyncCount(),
        networkFlow,
        syncEngine.isSyncing,
        _lastSyncMsg,
        _authUser,
        _isAuthBusy
    ) { vals ->
        SyncAndAuthBundle(
            pendingCount = vals[0] as Int,
            online = vals[1] as Boolean,
            isSyncing = vals[2] as Boolean,
            syncMsg = vals[3] as String?,
            authUser = vals[4] as FirebaseUser?,
            isAuthBusy = vals[5] as Boolean
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        prefs.settingsFlow,
        repo.observeCurrentUser(),
        repo.observeWorkspaces(),
        workspaceCoreFlow,
        workspacePlanningFlow,
        workspaceGroupFlow,
        syncAndAuthBundleFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val settings = values[0] as UserSettings
        val user = values[1] as UserEntity?
        val workspaces = values[2] as List<WorkspaceEntity>
        val core = values[3] as WorkspaceCoreBundle
        val plan = values[4] as WorkspacePlanningBundle
        val group = values[5] as Pair<List<SplitBillEntity>, List<AuditLogEntity>>
        val syncAuth = values[6] as SyncAndAuthBundle

        val activeWs = workspaces.firstOrNull { it.id == settings.activeWorkspaceId } ?: workspaces.firstOrNull()
        val activeMember = core.members.firstOrNull { it.id == settings.activeMemberId }
            ?: core.members.firstOrNull { it.userId == user?.id }
            ?: core.members.firstOrNull()

        val liquidCash = core.accounts.filter {
            it.type in listOf(AccountTypes.CASH, AccountTypes.BANK, AccountTypes.EWALLET, AccountTypes.DEPOSIT)
        }.sumOf { it.balance }
        val investments = plan.assets.sumOf { it.currentValue }
        val receivables = plan.debtsLoans.filter { it.kind == "LOAN" && !it.isPaidOff }.sumOf { it.remainingAmount }
        val ccDebt = core.accounts.filter { it.type == AccountTypes.CREDIT_CARD }.sumOf { it.balance }
        val loanDebt = plan.debtsLoans.filter { it.kind == "DEBT" && !it.isPaidOff }.sumOf { it.remainingAmount }
        val totalDebts = ccDebt + loanDebt
        val netWorth = (liquidCash + investments + receivables) - totalDebts

        val currentMonth = CurrencyFormatter.currentMonthKey()
        val monthTxs = core.transactions.filter { CurrencyFormatter.currentMonthKey(it.dateMillis) == currentMonth }
        val monthIncome = monthTxs.filter { it.type == TxTypes.INCOME || it.type == TxTypes.REFUND }.sumOf { it.amount }
        val monthExpense = monthTxs.filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }.sumOf { it.amount }
        val monthNet = monthIncome - monthExpense
        val savingRate = if (monthIncome > 0L) (monthNet.toDouble() / monthIncome) * 100.0 else 0.0
        val emergencyMonths = if (monthExpense > 0L) liquidCash.toDouble() / monthExpense else (liquidCash.toDouble() / 3_500_000.0)

        val monthBudgets = plan.budgets.filter { it.monthYear == currentMonth }
        val totalBudgetLimit = monthBudgets.sumOf { it.limitAmount + if (it.rolloverEnabled) it.rolloverAmount else 0L }
        val spentByCategory = HashMap<String, Long>()
        monthTxs.forEach { tx ->
            if (tx.type == TxTypes.EXPENSE || tx.type == TxTypes.SPLIT) {
                spentByCategory[tx.categoryId] = (spentByCategory[tx.categoryId] ?: 0L) + tx.amount
            }
        }
        val totalBudgetSpent = if (monthBudgets.isNotEmpty()) {
            monthBudgets.map { it.categoryId }.distinct().sumOf { spentByCategory[it] ?: 0L }
        } else monthExpense
        val remainingBudget = if (totalBudgetLimit > 0L) totalBudgetLimit - totalBudgetSpent else 0L
        val budgetRemainingFraction = if (totalBudgetLimit > 0L) {
            (remainingBudget.toFloat() / totalBudgetLimit).coerceIn(0f, 1f)
        } else 0f

        val syncVisual = when {
            !syncAuth.online || settings.simulatedOffline -> SyncVisualStatus.OFFLINE_LOCAL
            syncAuth.isSyncing || (syncAuth.authUser != null && syncAuth.pendingCount > 0) -> SyncVisualStatus.SYNCING
            else -> SyncVisualStatus.ONLINE_SYNCED
        }

        val advisor = FinancialAdvisorEngine.analyze(
            core.accounts,
            plan.assets,
            plan.debtsLoans,
            core.transactions,
            core.categories,
            plan.budgets
        )

        val settlements = SplitBillSimplifier.calculateSettlements(group.first, core.members)
        val catMap = core.categories.associateBy { it.id }
        val accMap = core.accounts.associateBy { it.id }
        val itemsByTx = core.items.groupBy { it.transactionId }

        MainUiState(
            isReady = true,
            settings = settings,
            authUserUid = syncAuth.authUser?.uid ?: settings.googleUid.takeIf { it.isNotBlank() },
            authUserEmail = syncAuth.authUser?.email ?: settings.googleEmail,
            authUserDisplayName = syncAuth.authUser?.displayName ?: settings.googleDisplayName,
            isAuthBusy = syncAuth.isAuthBusy,
            currentUser = user,
            workspaces = workspaces,
            activeWorkspace = activeWs,
            members = core.members,
            activeMember = activeMember,
            accounts = core.accounts,
            categories = core.categories,
            transactions = core.transactions,
            pagedTransactions = core.pagedTransactions,
            totalTransactionCount = core.totalTxCount,
            hasMoreTransactions = core.pagedTransactions.size < core.totalTxCount,
            transactionItems = core.items,
            categoryMap = catMap,
            accountMap = accMap,
            splitItemsByTxId = itemsByTx,
            budgets = plan.budgets,
            recurringRules = plan.recurring,
            debtsLoans = plan.debtsLoans,
            goals = plan.goals,
            assets = plan.assets,
            splitBills = group.first,
            auditLogs = group.second,
            pendingSyncCount = syncAuth.pendingCount,
            isNetworkConnected = syncAuth.online,
            syncVisualStatus = syncVisual,
            lastSyncMessage = syncAuth.syncMsg,
            netWorth = netWorth,
            totalLiquidCash = liquidCash,
            totalInvestments = investments,
            totalDebts = totalDebts,
            monthIncome = monthIncome,
            monthExpense = monthExpense,
            monthNetCashflow = monthNet,
            savingRatePercent = savingRate,
            emergencyFundMonths = emergencyMonths,
            totalBudgetLimit = totalBudgetLimit,
            totalBudgetSpent = totalBudgetSpent,
            remainingBudget = remainingBudget,
            budgetRemainingFraction = budgetRemainingFraction,
            netWorthTrend12M = buildNetWorthTrend12M(netWorth, monthNet),
            monthlyBars = buildMonthlyBars(core.transactions),
            categorySlices = buildCategorySlices(monthTxs, core.categories),
            dailyHeatmapSpending = buildDailyHeatmap(monthTxs),
            advisorReport = advisor,
            simplifiedSettlements = settlements
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    private data class SyncAndAuthBundle(
        val pendingCount: Int,
        val online: Boolean,
        val isSyncing: Boolean,
        val syncMsg: String?,
        val authUser: FirebaseUser?,
        val isAuthBusy: Boolean
    )

    private data class WorkspaceCoreBundle(
        val members: List<WorkspaceMemberEntity>,
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>,
        val pagedTransactions: List<TransactionEntity>,
        val totalTxCount: Int,
        val items: List<TransactionItemEntity>
    )

    private data class WorkspacePlanningBundle(
        val budgets: List<BudgetEntity>,
        val recurring: List<RecurringRuleEntity>,
        val debtsLoans: List<DebtLoanEntity>,
        val goals: List<GoalEntity>,
        val assets: List<AssetInvestmentEntity>
    )

    // --- CHART SERIES BUILDERS ---
    private fun buildNetWorthTrend12M(currentNetWorth: Long, monthlyNet: Long): List<Pair<String, Long>> {
        val cal = Calendar.getInstance()
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Ags", "Sep", "Okt", "Nov", "Des")
        val step = if (currentNetWorth == 0L && monthlyNet == 0L) 0L else (monthlyNet.coerceAtLeast(500_000L) * 0.65).toLong()
        val list = mutableListOf<Pair<String, Long>>()
        for (i in 11 downTo 0) {
            val c = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -i) }
            val label = monthNames[c.get(Calendar.MONTH)]
            val valAtMonth = (currentNetWorth - (i * step)).coerceAtLeast(0L)
            list.add(label to valAtMonth)
        }
        return list
    }

    private fun buildMonthlyBars(transactions: List<TransactionEntity>): List<MonthlyBarPoint> {
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Ags", "Sep", "Okt", "Nov", "Des")
        val cal = Calendar.getInstance()
        val result = mutableListOf<MonthlyBarPoint>()
        for (i in 5 downTo 0) {
            val c = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -i) }
            val key = CurrencyFormatter.currentMonthKey(c.timeInMillis)
            val label = monthNames[c.get(Calendar.MONTH)]
            val monthTxs = transactions.filter { CurrencyFormatter.currentMonthKey(it.dateMillis) == key }
            val inc = monthTxs.filter { it.type == TxTypes.INCOME || it.type == TxTypes.REFUND }.sumOf { it.amount }
            val exp = monthTxs.filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }.sumOf { it.amount }
            result.add(MonthlyBarPoint(label, inc, exp))
        }
        return result
    }

    private fun buildCategorySlices(monthTxs: List<TransactionEntity>, categories: List<CategoryEntity>): List<CategorySpendSlice> {
        val catMap = categories.associateBy { it.id }
        return monthTxs
            .filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }
            .groupBy { it.categoryId }
            .map { (catId, list) ->
                val cat = catMap[catId]
                CategorySpendSlice(
                    categoryId = catId,
                    name = cat?.name ?: "Lainnya",
                    amount = list.sumOf { it.amount },
                    colorHex = cat?.colorHex ?: 0xFF059669
                )
            }
            .sortedByDescending { it.amount }
    }

    private fun buildDailyHeatmap(monthTxs: List<TransactionEntity>): Map<Int, Long> {
        val map = mutableMapOf<Int, Long>()
        val cal = Calendar.getInstance()
        monthTxs
            .filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }
            .forEach { tx ->
                cal.timeInMillis = tx.dateMillis
                val day = cal.get(Calendar.DAY_OF_MONTH)
                map[day] = (map[day] ?: 0L) + tx.amount
            }
        return map
    }

    // --- ONBOARDING, PROFILE & GOOGLE AUTH ---
    fun completeOnboarding(
        displayName: String,
        avatarColorHex: Long,
        pin6Digit: String?,
        enableBiometric: Boolean,
        profilePhotoUri: Uri? = null,
        markTutorialDone: Boolean = false
    ) {
        viewModelScope.launch {
            val authUser = _authUser.value
            val email = authUser?.email ?: uiState.value.settings.googleEmail
            val curWs = uiState.value.activeWorkspace
            val curMem = uiState.value.activeMember
            if (curWs == null || curMem == null) {
                val (_, ws, mem) = repo.initializeCleanOnboarding(
                    displayName = displayName,
                    email = email,
                    avatarColorHex = avatarColorHex
                )
                prefs.setActiveWorkspace(ws.id, mem.id)
                prefs.setWorkspacePurpose(ws.id, "Keuangan Pribadi")
            } else {
                repo.updateUserProfile(
                    displayName = displayName.ifBlank { curMem.name },
                    email = email.ifBlank { curMem.email },
                    avatarColorHex = avatarColorHex,
                    activeWorkspaceId = curWs.id,
                    actorMemberId = curMem.id
                )
            }
            if (profilePhotoUri != null) {
                val savedPath = ProfileImageHelper.compressAndSaveAvatar(
                    context = getApplication(),
                    uri = profilePhotoUri,
                    previousPath = uiState.value.settings.profilePhotoPath
                )
                if (!savedPath.isNullOrBlank()) {
                    prefs.setProfilePhotoPath(savedPath)
                }
            } else {
                prefs.setProfilePhotoPath("")
            }
            if (!pin6Digit.isNullOrBlank() && pin6Digit.length == 6) {
                prefs.setPin(pin6Digit)
                prefs.setBiometricEnabled(enableBiometric)
            }
            prefs.setOnboardingState(completedOnboarding = true, completedTutorial = markTutorialDone)
            _pinUnlocked.value = true
            _hasEnteredSession.value = true
            _snackbar.value = "Selamat datang, ${displayName.trim()}! Yuk lihat panduan singkat aplikasi."
        }
    }

    fun markTutorialCompleted() {
        viewModelScope.launch {
            prefs.setTutorialCompleted(true)
        }
    }

    fun replayTutorial() {
        viewModelScope.launch {
            prefs.setTutorialCompleted(false)
        }
    }

    fun updateUserProfile(displayName: String, avatarColorHex: Long) {
        val state = uiState.value
        val wsId = state.activeWorkspace?.id ?: state.settings.activeWorkspaceId
        val memId = state.activeMember?.id ?: state.settings.activeMemberId
        viewModelScope.launch {
            val email = state.authUserEmail.ifBlank { state.currentUser?.email ?: "" }
            repo.updateUserProfile(displayName, email, avatarColorHex, wsId, memId)
            _snackbar.value = "Profil '${displayName.trim()}' berhasil diperbarui."
        }
    }

    fun updateProfilePhoto(photoUri: Uri) {
        val state = uiState.value
        val wsId = state.activeWorkspace?.id ?: state.settings.activeWorkspaceId
        val memId = state.activeMember?.id ?: state.settings.activeMemberId
        val actorName = state.currentUser?.name ?: state.activeMember?.name ?: "Pengguna"
        viewModelScope.launch {
            val savedPath = ProfileImageHelper.compressAndSaveAvatar(
                context = getApplication(),
                uri = photoUri,
                previousPath = state.settings.profilePhotoPath
            )
            if (!savedPath.isNullOrBlank()) {
                prefs.setProfilePhotoPath(savedPath)
                repo.addAuditLog(wsId, memId, actorName, "Foto Profil", "Foto profil diperbarui")
                _snackbar.value = "Foto profil berhasil diperbarui."
            } else {
                _snackbar.value = "Gagal memproses foto profil. Coba pilih foto lain."
            }
        }
    }

    fun removeProfilePhoto() {
        val state = uiState.value
        val wsId = state.activeWorkspace?.id ?: state.settings.activeWorkspaceId
        val memId = state.activeMember?.id ?: state.settings.activeMemberId
        val actorName = state.currentUser?.name ?: state.activeMember?.name ?: "Pengguna"
        viewModelScope.launch {
            ProfileImageHelper.deletePhoto(state.settings.profilePhotoPath)
            prefs.setProfilePhotoPath("")
            repo.addAuditLog(wsId, memId, actorName, "Foto Profil", "Foto profil dihapus (kembali ke inisial)")
            _snackbar.value = "Foto profil dihapus."
        }
    }

    fun registerWithEmailPassword(
        activityContext: Context,
        email: String,
        pass: String,
        name: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (_isAuthBusy.value) return
        viewModelScope.launch {
            _isAuthBusy.value = true
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val result = auth.createUserWithEmailAndPassword(email, pass).await()
                val firebaseUser = result.user
                if (firebaseUser != null) {
                    _authUser.value = firebaseUser
                    val finalName = name.ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } }
                    prefs.setGoogleAccount(firebaseUser.uid, email, finalName)

                    if (uiState.value.currentUser == null) {
                        val (_, ws, mem) = repo.initializeCleanOnboarding(finalName, email)
                        prefs.setActiveWorkspace(ws.id, mem.id)
                    }

                    val report = syncEngine.syncFullAndMergeOnSignIn(
                        settings = prefs.settingsFlow.first(),
                        repo = repo,
                        uid = firebaseUser.uid,
                        email = email,
                        displayName = finalName
                    )
                    val wsId = uiState.value.activeWorkspace?.id ?: prefs.settingsFlow.first().activeWorkspaceId
                    val memId = uiState.value.activeMember?.id ?: prefs.settingsFlow.first().activeMemberId
                    repo.addAuditLog(wsId, memId, finalName, "Daftar Akun Email", email)
                    _snackbar.value = "Akun berhasil didaftarkan dan tersinkronisasi online!"
                    onComplete(true, null)
                } else {
                    onComplete(false, "Gagal mendaftarkan akun.")
                }
            } catch (e: Exception) {
                Log.e("NusaKasVM", "Register email failed", e)
                onComplete(false, e.localizedMessage ?: "Gagal mendaftarkan akun. Email mungkin sudah terdaftar.")
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun signInWithEmailPassword(
        activityContext: Context,
        email: String,
        pass: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (_isAuthBusy.value) return
        viewModelScope.launch {
            _isAuthBusy.value = true
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val result = auth.signInWithEmailAndPassword(email, pass).await()
                val firebaseUser = result.user
                if (firebaseUser != null) {
                    _authUser.value = firebaseUser
                    val name = firebaseUser.displayName.orEmpty().ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } }
                    prefs.setGoogleAccount(firebaseUser.uid, email, name)

                    if (uiState.value.currentUser == null) {
                        val (_, ws, mem) = repo.initializeCleanOnboarding(name, email)
                        prefs.setActiveWorkspace(ws.id, mem.id)
                    }

                    val report = syncEngine.syncFullAndMergeOnSignIn(
                        settings = prefs.settingsFlow.first(),
                        repo = repo,
                        uid = firebaseUser.uid,
                        email = email,
                        displayName = name
                    )
                    val wsId = uiState.value.activeWorkspace?.id ?: prefs.settingsFlow.first().activeWorkspaceId
                    val memId = uiState.value.activeMember?.id ?: prefs.settingsFlow.first().activeMemberId
                    repo.addAuditLog(wsId, memId, name, "Masuk Akun Email", email)
                    _snackbar.value = report.message
                    onComplete(true, null)
                } else {
                    onComplete(false, "Gagal masuk akun.")
                }
            } catch (e: Exception) {
                Log.e("NusaKasVM", "Sign in email failed", e)
                onComplete(false, e.localizedMessage ?: "Gagal masuk. Periksa kembali email dan kata sandi Anda.")
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun signInWithGoogle(activityContext: Context, onComplete: ((Boolean) -> Unit)? = null) {
        if (_isAuthBusy.value) return
        viewModelScope.launch {
            _isAuthBusy.value = true
            try {
                val outcome = googleAuthHelper.signInInteractive(activityContext)
                val firebaseUser = outcome.user
                if (firebaseUser != null) {
                    _authUser.value = firebaseUser
                    val email = firebaseUser.email.orEmpty()
                    val name = firebaseUser.displayName.orEmpty().ifBlank { uiState.value.currentUser?.name ?: "Pengguna" }
                    prefs.setGoogleAccount(firebaseUser.uid, email, name)

                    // Update local profile email if needed
                    if (uiState.value.currentUser != null) {
                        val cur = uiState.value.currentUser!!
                        repo.updateUserProfile(
                            displayName = cur.name.ifBlank { name },
                            email = email,
                            avatarColorHex = cur.avatarColorHex,
                            activeWorkspaceId = uiState.value.activeWorkspace?.id ?: uiState.value.settings.activeWorkspaceId,
                            actorMemberId = uiState.value.activeMember?.id ?: uiState.value.settings.activeMemberId
                        )
                    } else {
                        val (_, ws, mem) = repo.initializeCleanOnboarding(name, email)
                        prefs.setActiveWorkspace(ws.id, mem.id)
                    }

                    val report = syncEngine.syncFullAndMergeOnSignIn(
                        settings = prefs.settingsFlow.first(),
                        repo = repo,
                        uid = firebaseUser.uid,
                        email = email,
                        displayName = name
                    )
                    val wsId = uiState.value.activeWorkspace?.id ?: prefs.settingsFlow.first().activeWorkspaceId
                    val memId = uiState.value.activeMember?.id ?: prefs.settingsFlow.first().activeMemberId
                    repo.addAuditLog(wsId, memId, name, "Sambungkan Akun Google", email.ifBlank { firebaseUser.uid })
                    _lastSyncMsg.value = report.message
                    _snackbar.value = report.message
                    onComplete?.invoke(true)
                } else {
                    if (!outcome.errorMessage.isNullOrBlank()) {
                        _snackbar.value = outcome.errorMessage
                    }
                    onComplete?.invoke(false)
                }
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            _isAuthBusy.value = true
            try {
                val wsId = uiState.value.activeWorkspace?.id ?: uiState.value.settings.activeWorkspaceId
                val memId = uiState.value.activeMember?.id ?: uiState.value.settings.activeMemberId
                val actorName = uiState.value.currentUser?.name ?: "Pengguna"
                repo.addAuditLog(wsId, memId, actorName, "Lepas Akun Google", "Catatan di HP tetap aman")
                googleAuthHelper.signOut()
                _authUser.value = null
                prefs.clearGoogleAccount()
                _snackbar.value = "Akun Google dilepas. Catatan tetap aman tersimpan di HP ini."
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun logoutSessionKeepData() {
        viewModelScope.launch {
            _isAuthBusy.value = true
            try {
                val wsId = uiState.value.activeWorkspace?.id ?: uiState.value.settings.activeWorkspaceId
                val memId = uiState.value.activeMember?.id ?: uiState.value.settings.activeMemberId
                val actorName = uiState.value.currentUser?.name ?: "Pengguna"
                if (uiState.value.activeWorkspace != null) {
                    repo.addAuditLog(wsId, memId, actorName, "Keluar Akun", "Keluar sesi; catatan di HP tetap tersimpan")
                }
                if (uiState.value.isGoogleLinked) {
                    googleAuthHelper.signOut()
                    _authUser.value = null
                    prefs.clearGoogleAccount()
                }
                _pinUnlocked.value = false
                _hasEnteredSession.value = false
                _snackbar.value = "Berhasil keluar dari akun."
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun logoutAndSwitchAccount() {
        viewModelScope.launch {
            _isAuthBusy.value = true
            try {
                ProfileImageHelper.deletePhoto(uiState.value.settings.profilePhotoPath)
                if (uiState.value.isGoogleLinked) {
                    googleAuthHelper.signOut()
                }
                _authUser.value = null
                prefs.logoutToFreshOnboarding()
                repo.clearAllLocalData()
                _pinUnlocked.value = false
                _hasEnteredSession.value = false
                _snackbar.value = "Berhasil keluar. Silakan buat atau masuk ke akun lain."
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    fun resetCleanAccount() {
        val state = uiState.value
        val name = state.currentUser?.name ?: state.authUserDisplayName.ifBlank { "Pengguna" }
        val email = state.authUserEmail.ifBlank { state.currentUser?.email ?: "" }
        val color = state.currentUser?.avatarColorHex ?: 0xFF065F46
        viewModelScope.launch {
            ProfileImageHelper.deletePhoto(state.settings.profilePhotoPath)
            prefs.resetForCleanAccount()
            val (_, ws, mem) = repo.resetAllLocalDataClean(name, email, color)
            prefs.setActiveWorkspace(ws.id, mem.id)
            prefs.setWorkspacePurpose(ws.id, "Keuangan Pribadi")
            _snackbar.value = "Seluruh catatan keuangan dikosongkan kembali menjadi Rp 0."
        }
    }

    // --- GENERAL ACTIONS & SECURITY ---
    fun loadMoreTransactions() {
        _ledgerLimit.update { current -> current + 30 }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    fun showMessage(msg: String) {
        _snackbar.value = msg
    }

    fun toggleStealthMode() {
        viewModelScope.launch {
            prefs.setStealthMode(!uiState.value.settings.isStealthMode)
        }
    }

    fun setThemePreset(presetId: String) {
        viewModelScope.launch {
            prefs.setThemePreset(presetId)
            val label = com.example.ui.theme.AppThemePreset.fromId(presetId).displayName
            _snackbar.value = "Tema diganti ke '$label'"
        }
    }

    fun dismissBeginnerGuide() {
        viewModelScope.launch {
            prefs.setHideBeginnerGuide(true)
        }
    }

    fun enterSession() {
        _hasEnteredSession.value = true
    }

    fun unlockWithPin(pin: String): Boolean {
        val stored = uiState.value.settings.pinHash
        return if (prefs.verifyPin(pin, stored)) {
            _pinUnlocked.value = true
            _hasEnteredSession.value = true
            true
        } else {
            false
        }
    }

    fun unlockWithBiometric() {
        _pinUnlocked.value = true
        _hasEnteredSession.value = true
        viewModelScope.launch {
            val state = uiState.value
            val wsId = state.activeWorkspace?.id ?: return@launch
            val mem = state.activeMember ?: return@launch
            repo.addAuditLog(wsId, mem.id, mem.name, "Buka Kunci Wajah / Sidik Jari", "Akses dibuka dengan wajah atau sidik jari")
        }
    }

    fun lockAppNow() {
        _pinUnlocked.value = false
        _hasEnteredSession.value = false
    }

    fun enablePin(pin: String) {
        viewModelScope.launch {
            prefs.setPin(pin)
            _pinUnlocked.value = true
            _hasEnteredSession.value = true
            val state = uiState.value
            state.activeWorkspace?.let { ws ->
                state.activeMember?.let { mem ->
                    repo.addAuditLog(ws.id, mem.id, mem.name, "Aktifkan Kunci PIN", "PIN 6 angka diperbarui")
                }
            }
            _snackbar.value = "Kunci PIN 6 angka berhasil diaktifkan."
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            prefs.disablePin()
            val state = uiState.value
            state.activeWorkspace?.let { ws ->
                state.activeMember?.let { mem ->
                    repo.addAuditLog(ws.id, mem.id, mem.name, "Nonaktifkan Kunci PIN", "PIN dan kunci wajah/sidik jari dimatikan")
                }
            }
            _snackbar.value = "Kunci PIN & Wajah/Sidik Jari dimatikan."
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !uiState.value.settings.isPinEnabled) {
                _snackbar.value = "Aktifkan PIN 6 angka sebagai cadangan dulu sebelum mengaktifkan Kunci Wajah."
                return@launch
            }
            prefs.setBiometricEnabled(enabled)
            val state = uiState.value
            state.activeWorkspace?.let { ws ->
                state.activeMember?.let { mem ->
                    repo.addAuditLog(
                        ws.id,
                        mem.id,
                        mem.name,
                        if (enabled) "Aktifkan Kunci Wajah" else "Matikan Kunci Wajah",
                        if (enabled) "Kunci wajah/sidik jari diaktifkan" else "Kunci wajah/sidik jari dimatikan"
                    )
                }
            }
            _snackbar.value = if (enabled) {
                "Kunci Wajah / Sidik Jari aktif!"
            } else {
                "Kunci Wajah / Sidik Jari dimatikan."
            }
        }
    }

    fun setBillReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBillReminderEnabled(enabled)
            if (enabled) {
                BillReminderWorker.schedulePeriodic(getApplication())
                BillReminderWorker.triggerImmediateCheck(getApplication())
            }
            _snackbar.value = if (enabled) {
                "Pengingat tagihan 3 hari & 1 hari sebelum jatuh tempo diaktifkan."
            } else {
                "Pengingat tagihan otomatis dimatikan."
            }
        }
    }

    fun testBillReminderNotificationNow() {
        viewModelScope.launch {
            BillReminderWorker.triggerImmediateCheck(getApplication())
            val sent = BillReminderWorker.sendSampleTestNotification(getApplication())
            _snackbar.value = if (sent) {
                "Contoh pengingat dikirim! Pengecekan tagihan dijalankan."
            } else {
                "Izinkan notifikasi agar pengingat tagihan bulanan dapat muncul."
            }
        }
    }

    // --- WORKSPACE & COLLABORATION ---
    fun switchWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            _ledgerLimit.value = 30
            val user = uiState.value.currentUser
            val members = dao.observeMembers(workspace.id).first()
            val member = members.firstOrNull { it.userId == user?.id } ?: members.firstOrNull()
            prefs.setActiveWorkspace(workspace.id, member?.id ?: "mem_owner")
            _snackbar.value = "Beralih ke ${workspace.name}"
        }
    }

    fun switchActiveMemberRoleDemo(member: WorkspaceMemberEntity) {
        viewModelScope.launch {
            prefs.setActiveMemberId(member.id)
            _snackbar.value = "Akun aktif: ${member.name} (${CurrencyFormatter.memberRoleLabel(member.role)})"
        }
    }

    fun createWorkspace(name: String, type: String, purpose: String = "") {
        viewModelScope.launch {
            val user = uiState.value.currentUser ?: return@launch
            val (ws, member) = repo.createWorkspace(name, type, user)
            val resolvedPurpose = purpose.trim().ifBlank {
                if (type == WorkspaceTypes.SHARED) "Keuangan Bersama" else "Keuangan Pribadi"
            }
            prefs.setWorkspacePurpose(ws.id, resolvedPurpose)
            prefs.setActiveWorkspace(ws.id, member.id)
            _snackbar.value = "Ruang keuangan '${ws.name}' ($resolvedPurpose) dibuat!"
        }
    }

    fun joinWorkspace(inviteCode: String) {
        viewModelScope.launch {
            val user = uiState.value.currentUser ?: return@launch
            val ws = repo.joinWorkspaceByCode(inviteCode, user)
            if (ws != null) {
                val members = dao.observeMembers(ws.id).first()
                val me = members.lastOrNull { it.userId == user.id } ?: members.first()
                prefs.setActiveWorkspace(ws.id, me.id)
                _snackbar.value = "Berhasil bergabung ke ${ws.name}"
            } else {
                _snackbar.value = "Kode undangan tidak valid (gunakan 6-8 karakter)."
            }
        }
    }

    fun inviteMember(name: String, email: String, role: String) {
        val state = uiState.value
        if (!state.isOwner) {
            _snackbar.value = "Hanya Pemilik yang dapat mengundang atau mengubah anggota."
            return
        }
        viewModelScope.launch {
            val ws = state.activeWorkspace ?: return@launch
            val actor = state.activeMember ?: return@launch
            repo.inviteMemberToWorkspace(ws.id, name, email, role, actor.id, actor.name)
            _snackbar.value = "$name ditambahkan sebagai ${CurrencyFormatter.memberRoleLabel(role)}"
        }
    }

    fun changeMemberRole(memberId: String, newRole: String) {
        val state = uiState.value
        if (!state.isOwner) {
            _snackbar.value = "Hanya Pemilik yang dapat mengubah peran."
            return
        }
        viewModelScope.launch {
            val ws = state.activeWorkspace ?: return@launch
            val actor = state.activeMember ?: return@launch
            repo.updateMemberRole(ws.id, memberId, newRole, actor.id, actor.name)
            _snackbar.value = "Peran anggota diubah ke ${CurrencyFormatter.memberRoleLabel(newRole)}"
        }
    }

    // --- SYNC & CLOUD CONFIG ---
    fun toggleSimulatedOffline() {
        viewModelScope.launch {
            val next = !uiState.value.settings.simulatedOffline
            prefs.setSimulatedOffline(next)
            _snackbar.value = if (next) "Mode Tanpa Internet diaktifkan. Catatan disimpan di HP." else "Koneksi internet tersambung kembali."
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            val state = uiState
                .value
            val ws = state.activeWorkspace ?: return@launch
            val report = syncEngine.syncPendingQueue(state.settings, state.isNetworkConnected, ws.type)
            _lastSyncMsg.value = report.message
            _snackbar.value = report.message
        }
    }

    // --- CATEGORIES ---
    fun saveCategory(existingId: String?, name: String, type: String, iconKey: String, colorHex: Long) {
        val state = uiState.value
        if (!state.canEdit) return
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            val cat = CategoryEntity(
                id = existingId ?: "cat_${UUID.randomUUID().toString().take(8)}",
                workspaceId = ws.id,
                name = name.trim(),
                type = type,
                iconKey = iconKey,
                colorHex = colorHex
            )
            repo.upsertCategory(cat, member.id, member.name)
            _snackbar.value = "Kategori '${cat.name}' disimpan."
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.deleteCategory(category, member.id, member.name)
            _snackbar.value = "Kategori '${category.name}' dihapus."
        }
    }

    // --- TRANSACTIONS ---
    fun submitQuickInput(rawInput: String): Boolean {
        val state = uiState.value
        if (!state.canEdit) {
            _snackbar.value = "Peran Hanya Lihat tidak bisa mengubah catatan."
            return false
        }
        val parsed = SmartQuickInputParser.parse(rawInput, state.accounts, state.categories)
        if (!parsed.isValid || parsed.accountId == null || parsed.categoryId == null) {
            _snackbar.value = "Format belum dikenali. Contoh: 'Beli beras 65rb Tunai'"
            return false
        }
        val ws = state.activeWorkspace ?: return false
        val member = state.activeMember ?: return false
        viewModelScope.launch {
            val tx = TransactionEntity(
                id = "tx_${UUID.randomUUID().toString().take(8)}",
                workspaceId = ws.id,
                type = parsed.type,
                amount = parsed.amount,
                accountId = parsed.accountId,
                toAccountId = parsed.toAccountId,
                categoryId = parsed.categoryId,
                title = parsed.title,
                note = "Catat Cepat: \"$rawInput\"",
                tags = parsed.tags,
                dateMillis = System.currentTimeMillis(),
                createdByMemberId = member.id,
                createdByName = member.name
            )
            repo.recordTransaction(tx, emptyList(), ws.type == WorkspaceTypes.SHARED)
            _snackbar.value = "Tercatat: ${tx.title} (${CurrencyFormatter.formatIdr(tx.amount)})"
        }
        return true
    }

    fun saveTransaction(
        existingTx: TransactionEntity? = null,
        type: String,
        amount: Long,
        accountId: String,
        toAccountId: String?,
        adminFee: Long,
        categoryId: String,
        title: String,
        note: String,
        tags: String,
        dateMillis: Long,
        receiptUri: Uri?,
        splitItemsDraft: List<Triple<String, String, Long>>
    ) {
        val state = uiState.value
        if (!state.canEdit) {
            _snackbar.value = "Peran Hanya Lihat tidak bisa mencatat perubahan."
            return
        }
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            val newReceiptPath = receiptUri?.let {
                ReceiptImageHelper.compressAndSaveReceipt(getApplication(), it)
            } ?: existingTx?.receiptImagePath

            val txId = existingTx?.id ?: "tx_${UUID.randomUUID().toString().take(8)}"
            val items = if (type == TxTypes.SPLIT && splitItemsDraft.isNotEmpty()) {
                splitItemsDraft.map { (cId, desc, amt) ->
                    TransactionItemEntity(
                        id = "txi_${UUID.randomUUID().toString().take(8)}",
                        transactionId = txId,
                        workspaceId = ws.id,
                        categoryId = cId,
                        description = desc,
                        amount = amt
                    )
                }
            } else emptyList()

            val totalAmt = if (items.isNotEmpty()) items.sumOf { it.amount } else amount
            val newTx = TransactionEntity(
                id = txId,
                workspaceId = ws.id,
                type = type,
                amount = totalAmt,
                accountId = accountId,
                toAccountId = if (type == TxTypes.TRANSFER) toAccountId else null,
                adminFee = if (type == TxTypes.TRANSFER) adminFee else 0L,
                categoryId = categoryId,
                title = title.ifBlank { if (type == TxTypes.INCOME) "Pemasukan" else "Transaksi" },
                note = note,
                tags = tags,
                dateMillis = dateMillis,
                receiptImagePath = newReceiptPath,
                createdByMemberId = existingTx?.createdByMemberId ?: member.id,
                createdByName = existingTx?.createdByName ?: member.name
            )
            if (existingTx != null) {
                repo.updateTransaction(existingTx, newTx, items, member.id, member.name)
                _snackbar.value = "Transaksi '${newTx.title}' diperbarui & saldo disesuaikan."
            } else {
                repo.recordTransaction(newTx, items, ws.type == WorkspaceTypes.SHARED)
                _snackbar.value = "Transaksi '${newTx.title}' tersimpan."
            }
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        val state = uiState.value
        if (!state.canEdit) {
            _snackbar.value = "Peran Hanya Lihat tidak dapat menghapus catatan."
            return
        }
        viewModelScope.launch {
            repo.deleteTransaction(tx)
            _snackbar.value = "Catatan '${tx.title}' dihapus & saldo disesuaikan."
        }
    }

    // --- ACCOUNTS & ASSETS ---
    fun saveAccount(
        existingId: String?,
        name: String,
        type: String,
        balance: Long,
        creditLimit: Long,
        billingDay: Int,
        dueDay: Int,
        institution: String
    ) {
        val state = uiState.value
        if (!state.canEdit) {
            _snackbar.value = "Akun ini hanya bisa melihat catatan."
            return
        }
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            val acc = AccountEntity(
                id = existingId ?: "acc_${UUID.randomUUID().toString().take(8)}",
                workspaceId = ws.id,
                name = name.trim(),
                type = type,
                balance = balance,
                creditLimit = creditLimit,
                billingDay = billingDay.coerceIn(1, 31),
                dueDay = dueDay.coerceIn(1, 31),
                institutionName = institution.trim().ifBlank { name.trim() }
            )
            repo.upsertAccount(acc, member.id, member.name)
            _snackbar.value = "Rekening '${acc.name}' disimpan."
        }
    }

    fun deleteAccount(account: AccountEntity) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.deleteAccount(account, member.id, member.name)
            _snackbar.value = "Rekening '${account.name}' dihapus."
        }
    }

    fun reconcileAccount(account: AccountEntity, actualBalance: Long) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.reconcileAccountBalance(account, actualBalance, member.id, member.name, state.isSharedWorkspace)
            _snackbar.value = "Saldo '${account.name}' disesuaikan."
        }
    }

    fun saveAsset(
        existingId: String? = null,
        name: String,
        assetClass: String,
        quantity: Double,
        unitLabel: String,
        unitPrice: Long,
        purchaseValue: Long
    ) {
        val state = uiState.value
        if (!state.canEdit) return
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            val currentValue = (quantity * unitPrice).toLong()
            val asset = AssetInvestmentEntity(
                id = existingId ?: "ast_${UUID.randomUUID().toString().take(8)}",
                workspaceId = ws.id,
                name = name.trim(),
                assetClass = assetClass,
                quantity = quantity,
                unitLabel = unitLabel,
                unitPrice = unitPrice,
                currentValue = currentValue,
                purchaseValue = purchaseValue
            )
            repo.upsertAsset(asset, member.id, member.name)
            _snackbar.value = if (existingId == null) "Aset '${asset.name}' ditambahkan." else "Aset '${asset.name}' diperbarui."
        }
    }

    fun deleteAsset(asset: AssetInvestmentEntity) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.deleteAsset(asset, member.id, member.name)
            _snackbar.value = "Aset '${asset.name}' dihapus."
        }
    }

    // --- BUDGETS & RECURRING ---
    fun saveBudget(
        existingId: String? = null,
        categoryId: String,
        limitAmount: Long,
        rolloverAmount: Long,
        rolloverEnabled: Boolean,
        monthKey: String = CurrencyFormatter.currentMonthKey()
    ) {
        val state = uiState.value
        if (!state.isOwner && state.isSharedWorkspace) {
            _snackbar.value = "Hanya Pemilik yang dapat mengatur batas belanja bersama."
            return
        }
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        val existing = existingId?.let { id -> state.budgets.firstOrNull { it.id == id } }
            ?: state.budgets.firstOrNull { it.categoryId == categoryId && it.monthYear == monthKey }
        viewModelScope.launch {
            val budget = BudgetEntity(
                id = existing?.id ?: "bdg_${UUID.randomUUID().toString().take(8)}",
                workspaceId = ws.id,
                categoryId = categoryId,
                monthYear = monthKey,
                limitAmount = limitAmount,
                rolloverAmount = rolloverAmount,
                rolloverEnabled = rolloverEnabled
            )
            repo.upsertBudget(budget, member.id, member.name)
            _snackbar.value = "Anggaran kategori disimpan."
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.deleteBudget(budget, member.id, member.name)
            _snackbar.value = "Anggaran kategori dihapus."
        }
    }

    fun saveRecurringRule(
        existingId: String? = null,
        title: String,
        amount: Long,
        accountId: String,
        categoryId: String,
        frequency: String,
        nextDueDateMillis: Long,
        reminderEnabled: Boolean = true,
        isActive: Boolean = true
    ) {
        val state = uiState.value
        if (!state.canEdit) return
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            val existing = existingId?.let { id -> state.recurringRules.firstOrNull { it.id == id } }
            repo.upsertRecurringRule(
                RecurringRuleEntity(
                    id = existingId ?: "rec_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = ws.id,
                    title = title.trim(),
                    amount = amount,
                    type = existing?.type ?: TxTypes.EXPENSE,
                    accountId = accountId,
                    categoryId = categoryId,
                    frequency = frequency,
                    nextDueDateMillis = nextDueDateMillis,
                    isActive = isActive,
                    lastPaidDateMillis = existing?.lastPaidDateMillis,
                    reminderEnabled = reminderEnabled
                ),
                actorMemberId = member.id,
                actorName = member.name
            )
            BillReminderWorker.triggerImmediateCheck(getApplication())
            _snackbar.value = if (existingId == null) "Tagihan '${title.trim()}' dibuat." else "Tagihan '${title.trim()}' diperbarui."
        }
    }

    fun deleteRecurringRule(rule: RecurringRuleEntity) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.deleteRecurringRule(rule, member.id, member.name)
            _snackbar.value = "Jadwal tagihan '${rule.title}' dihapus."
        }
    }

    fun payRecurringNow(rule: RecurringRuleEntity) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.payRecurringRule(rule, member.id, member.name, state.isSharedWorkspace)
            BillReminderWorker.triggerImmediateCheck(getApplication())
            _snackbar.value = "Tagihan '${rule.title}' dibayar & jadwal berikutnya diperbarui."
        }
    }

    // --- DEBTS & LOANS ---
    fun saveDebtLoan(
        existingId: String? = null,
        kind: String,
        title: String,
        counterparty: String,
        principal: Long,
        remainingAmount: Long? = null,
        interestRateAnnual: Double,
        minMonthlyPayment: Long,
        dueDateMillis: Long = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000L
    ) {
        val state = uiState.value
        if (!state.canEdit) return
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            val rem = remainingAmount ?: principal
            val item = DebtLoanEntity(
                id = existingId ?: "dbt_${UUID.randomUUID().toString().take(8)}",
                workspaceId = ws.id,
                kind = kind,
                title = title.trim(),
                counterparty = counterparty.trim(),
                principalAmount = principal,
                remainingAmount = rem,
                interestRateAnnual = interestRateAnnual,
                minimumMonthlyPayment = minMonthlyPayment,
                dueDateMillis = dueDateMillis,
                isPaidOff = rem == 0L
            )
            repo.upsertDebtLoan(item, member.id, member.name)
            _snackbar.value = "Catatan ${if (kind == "DEBT") "Utang" else "Piutang"} disimpan."
        }
    }

    fun deleteDebtLoan(item: DebtLoanEntity) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.deleteDebtLoan(item, member.id, member.name)
            _snackbar.value = "Catatan '${item.title}' dihapus."
        }
    }

    fun payDebtInstallment(item: DebtLoanEntity, amount: Long, accountId: String) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        val cat = state.categories.firstOrNull {
            it.type == if (item.kind == "DEBT") TxTypes.EXPENSE else TxTypes.INCOME
        } ?: state.categories.firstOrNull() ?: return
        viewModelScope.launch {
            repo.payDebtInstallment(item, amount, accountId, cat.id, member.id, member.name, state.isSharedWorkspace)
            _snackbar.value = "Cicilan '${item.title}' tercatat."
        }
    }

    // --- GOALS ---
    fun saveGoal(
        existingId: String? = null,
        title: String,
        targetAmount: Long,
        currentAmount: Long = 0L,
        monthlyContribution: Long,
        monthsAhead: Int
    ) {
        val state = uiState.value
        if (!state.canEdit) return
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        val existing = existingId?.let { id -> state.goals.firstOrNull { it.id == id } }
        val targetDate = existing?.targetDateMillis ?: Calendar.getInstance().apply { add(Calendar.MONTH, monthsAhead.coerceAtLeast(1)) }.timeInMillis
        viewModelScope.launch {
            repo.upsertGoal(
                GoalEntity(
                    id = existingId ?: "gol_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = ws.id,
                    title = title.trim(),
                    targetAmount = targetAmount,
                    currentAmount = currentAmount,
                    targetDateMillis = targetDate,
                    monthlyContribution = monthlyContribution,
                    contributorSummary = existing?.contributorSummary ?: ""
                ),
                member.id,
                member.name
            )
            _snackbar.value = if (existingId == null) "Target '$title' dibuat." else "Target '$title' diperbarui."
        }
    }

    fun deleteGoal(goal: GoalEntity) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.deleteGoal(goal, member.id, member.name)
            _snackbar.value = "Target '${goal.title}' dihapus."
        }
    }

    fun contributeToGoal(goal: GoalEntity, amount: Long, accountId: String) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        val cat = state.categories.firstOrNull { it.type == TxTypes.EXPENSE } ?: state.categories.firstOrNull() ?: return
        viewModelScope.launch {
            repo.contributeToGoal(goal, amount, accountId, cat.id, member.id, member.name, state.isSharedWorkspace)
            _snackbar.value = "Setoran ${CurrencyFormatter.formatIdr(amount)} ke '${goal.title}' berhasil!"
        }
    }

    // --- SPLIT BILLS ---
    fun createEqualSplitBill(title: String, totalAmount: Long, paidByMemberId: String, participantIds: List<String>) {
        val state = uiState.value
        if (!state.canEdit || participantIds.isEmpty()) return
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        val share = totalAmount / participantIds.size
        val data = participantIds.joinToString("|") { mId ->
            val settled = (mId == paidByMemberId)
            "$mId:$share:$settled"
        }
        viewModelScope.launch {
            repo.upsertSplitBill(
                SplitBillEntity(
                    id = "spl_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = ws.id,
                    title = title.trim(),
                    totalAmount = totalAmount,
                    paidByMemberId = paidByMemberId,
                    splitMode = "EQUAL",
                    participantsData = data,
                    dateMillis = System.currentTimeMillis(),
                    isSettled = false
                ),
                member.id,
                member.name
            )
            _snackbar.value = "Patungan '$title' dibuat."
        }
    }

    fun deleteSplitBill(bill: SplitBillEntity) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.deleteSplitBill(bill, member.id, member.name)
            _snackbar.value = "Patungan '${bill.title}' dihapus."
        }
    }

    fun settleGroupTransfer(settlement: SimplifiedSettlement) {
        val state = uiState.value
        if (!state.canEdit) return
        val ws = state.activeWorkspace ?: return
        val acc = state.accounts.firstOrNull { it.type != AccountTypes.CREDIT_CARD } ?: state.accounts.firstOrNull() ?: return
        val cat = state.categories.firstOrNull() ?: return
        viewModelScope.launch {
            repo.settleDebtTransfer(
                workspaceId = ws.id,
                fromMember = settlement.fromMember,
                toMember = settlement.toMember,
                amount = settlement.amount,
                accountId = acc.id,
                categoryId = cat.id,
                billsToMark = state.splitBills,
                isShared = state.isSharedWorkspace
            )
            _snackbar.value = "${settlement.fromMember.name} melunasi ${CurrencyFormatter.formatIdr(settlement.amount)} ke ${settlement.toMember.name}"
        }
    }

    // --- AUTO PAYROLL / GAJI OTOMATIS ---
    fun saveAutoSalaryConfig(
        enabled: Boolean,
        baseSalary: Long,
        paydayDay: Int,
        workingDays: Int,
        targetAccountId: String
    ) {
        viewModelScope.launch {
            prefs.saveAutoSalaryConfig(
                enabled = enabled,
                baseSalary = baseSalary,
                paydayDay = paydayDay,
                workingDays = workingDays,
                targetAccountId = targetAccountId
            )
            val state = uiState.value
            state.currentUser?.let { u ->
                state.activeWorkspace?.let { ws ->
                    repo.addAuditLog(
                        ws.id,
                        state.activeMember?.id ?: "mem_owner",
                        u.name,
                        "Atur Gaji Otomatis",
                        if (enabled) "Tgl $paydayDay • Rp $baseSalary" else "Dimatikan"
                    )
                }
            }
            _snackbar.value = if (enabled) {
                "Gaji otomatis aktif: ${CurrencyFormatter.formatIdr(baseSalary)} tiap tgl ${paydayDay.coerceIn(1, 31)} ($workingDays hari kerja)."
            } else {
                "Pengingat hitungan gaji otomatis dimatikan."
            }
        }
    }

    fun submitMonthlyPayroll(
        input: PayrollCalculationInput,
        targetAccountId: String,
        cycleKey: String = CurrencyFormatter.currentMonthKey()
    ) {
        val state = uiState.value
        if (!state.canEdit) {
            _snackbar.value = "Peran Hanya Lihat tidak dapat mencatat gaji."
            return
        }
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        val account = state.accounts.firstOrNull { it.id == targetAccountId }
            ?: state.accounts.firstOrNull { it.type != AccountTypes.CREDIT_CARD }
            ?: state.accounts.firstOrNull()
            ?: return
        val salaryCategory = state.categories.firstOrNull { it.type == TxTypes.INCOME && it.iconKey == "payments" }
            ?: state.categories.firstOrNull { it.type == TxTypes.INCOME }
            ?: state.categories.firstOrNull()
            ?: return

        val result = PayrollCalculator.calculate(input)
        val noteParts = buildList {
            add("Gaji Pokok: ${CurrencyFormatter.formatIdr(result.baseSalary)}")
            add("Masuk: ${result.effectiveWorkingDays}/${result.workingDaysInMonth} hari (${CurrencyFormatter.formatIdr(result.dailyRate)}/hr)")
            if (result.offDays > 0) add("Libur: ${result.offDays} hr (-${CurrencyFormatter.formatIdr(result.offDeduction)})")
            if (result.sickDays > 0) add("Sakit: ${result.sickDays} hr (-${CurrencyFormatter.formatIdr(result.sickDeduction)})")
            if (result.otherLeaveDays > 0) add("Izin/Lain: ${result.otherLeaveDays} hr (-${CurrencyFormatter.formatIdr(result.otherLeaveDeduction)})")
            if (result.extraDeduction > 0L) add("Potongan Lain: -${CurrencyFormatter.formatIdr(result.extraDeduction)}")
            if (result.bonusAmount > 0L) add("Bonus/Lembur: +${CurrencyFormatter.formatIdr(result.bonusAmount)}")
            add("Total Potongan: ${CurrencyFormatter.formatIdr(result.totalDeduction)}")
        }.joinToString(" | ")

        viewModelScope.launch {
            if (result.netSalary > 0L) {
                val tx = TransactionEntity(
                    id = "tx_pay_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = ws.id,
                    type = TxTypes.INCOME,
                    amount = result.netSalary,
                    accountId = account.id,
                    categoryId = salaryCategory.id,
                    title = "Gaji Bersih ${CurrencyFormatter.monthLabel(cycleKey)}",
                    note = noteParts,
                    tags = "#gaji,#payroll",
                    dateMillis = System.currentTimeMillis(),
                    createdByMemberId = member.id,
                    createdByName = member.name
                )
                repo.recordTransaction(tx, emptyList(), ws.type == WorkspaceTypes.SHARED)
            }
            prefs.markSalaryCycleHandled(
                cycleKey = cycleKey,
                grossAmount = result.baseSalary,
                deductionAmount = result.totalDeduction,
                netAmount = result.netSalary
            )
            _snackbar.value = "Gaji bersih ${CurrencyFormatter.formatIdr(result.netSalary)} masuk ke ${account.name} (potongan ${CurrencyFormatter.formatIdr(result.totalDeduction)})."
        }
    }

    fun skipSalaryCycle(cycleKey: String = CurrencyFormatter.currentMonthKey()) {
        viewModelScope.launch {
            val current = uiState.value.settings
            prefs.markSalaryCycleHandled(
                cycleKey = cycleKey,
                grossAmount = current.lastSalaryGross,
                deductionAmount = current.lastSalaryDeduction,
                netAmount = current.lastSalaryNet
            )
            _snackbar.value = "Slip gaji bulan ${CurrencyFormatter.monthLabel(cycleKey)} dilewati."
        }
    }

    // --- ZAWA AI ASSISTANT (FIREBASE AI LOGIC + APP CHECK) ---
    fun sendMessageToZawaAi(rawMessage: String) {
        val message = rawMessage.trim()
        if (message.isBlank()) return
        if (aiChatJob?.isActive == true || _aiUiState.value is AiAssistantUiState.Loading) return

        val currentMessages = _aiMessages.value
        val lastMsg = currentMessages.lastOrNull()
        val isRetryingTrailingUserMsg = lastMsg != null && lastMsg.isUser && lastMsg.text == message

        val historyBefore = if (isRetryingTrailingUserMsg) {
            currentMessages.dropLast(1)
        } else {
            currentMessages
        }
        if (!isRetryingTrailingUserMsg) {
            val userEntry = AiChatMessage(
                id = "ai_u_${UUID.randomUUID().toString().take(8)}",
                isUser = true,
                text = message
            )
            _aiMessages.value = historyBefore + userEntry
        }
        _aiUiState.value = AiAssistantUiState.Loading

        aiChatJob = viewModelScope.launch {
            try {
                val result = aiService.askAndPlanAction(
                    userMessage = message,
                    history = historyBefore,
                    state = uiState.value
                )
                result.onSuccess { resp ->
                    val actionSummary = if (resp.actionType != ZawaAiActionType.NONE) {
                        executeAiAction(resp)
                    } else {
                        null
                    }
                    val aiEntry = AiChatMessage(
                        id = "ai_m_${UUID.randomUUID().toString().take(8)}",
                        isUser = false,
                        text = resp.replyMessage,
                        executedActionSummary = actionSummary,
                        followUps = resp.followUps
                    )
                    _aiMessages.value = _aiMessages.value + aiEntry
                    _aiUiState.value = AiAssistantUiState.Success(resp.replyMessage)
                }.onFailure { err ->
                    _aiUiState.value = AiAssistantUiState.Error(
                        err.localizedMessage ?: "Gagal menghubungi Asisten Zawa. Coba lagi sebentar ya."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun retryLastZawaAiMessage() {
        val lastUserMsg = _aiMessages.value.lastOrNull()?.takeIf { it.isUser }?.text ?: return
        sendMessageToZawaAi(lastUserMsg)
    }

    fun runDeepAiAnalysisStream(
        focusPrompt: String = "Periksa kondisi keuanganku saat ini, temukan pengingat tagihan penting, dan berikan saran hemat yang mudah dipahami."
    ) {
        aiStreamJob?.cancel()
        _aiAnalysisState.value = AiAssistantUiState.Loading
        _aiStreamingAnalysis.value = ""

        aiStreamJob = viewModelScope.launch {
            try {
                aiService.streamDeepAnalysis(uiState.value, focusPrompt).collect { partial ->
                    _aiStreamingAnalysis.value = partial
                    _aiAnalysisState.value = AiAssistantUiState.Streaming(partial)
                }
                val finalText = _aiStreamingAnalysis.value
                _aiAnalysisState.value = AiAssistantUiState.Success(finalText)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiAnalysisState.value = AiAssistantUiState.Error(
                    e.localizedMessage ?: "Gagal memeriksa keuangan bersama Asisten Zawa."
                )
            }
        }
    }

    fun clearAiChat() {
        aiChatJob?.cancel()
        _aiMessages.value = emptyList()
        _aiUiState.value = AiAssistantUiState.Idle
    }

    fun dismissAiError() {
        if (_aiUiState.value is AiAssistantUiState.Error) {
            _aiUiState.value = AiAssistantUiState.Idle
        }
        if (_aiAnalysisState.value is AiAssistantUiState.Error) {
            _aiAnalysisState.value = AiAssistantUiState.Idle
        }
    }

    fun consumeAiNavigationRequest() {
        _aiNavigationRequest.value = null
    }

    private fun executeAiAction(resp: ZawaAiStructuredResponse): String? {
        val state = uiState.value
        return when (resp.actionType) {
            ZawaAiActionType.NONE -> null

            ZawaAiActionType.RECORD_TRANSACTION -> {
                if (!state.canEdit) return "Akun Hanya Lihat tidak bisa menambah catatan."
                if (resp.amount <= 0L) return "Nominal uang belum disebutkan (Rp 0)."
                val normType = when (resp.txType.uppercase()) {
                    TxTypes.INCOME, "MASUK", "PEMASUKAN" -> TxTypes.INCOME
                    TxTypes.TRANSFER, "PINDAH" -> TxTypes.TRANSFER
                    else -> TxTypes.EXPENSE
                }
                val account = matchAccount(state.accounts, resp.accountName)
                    ?: return "Belum ada rekening atau dompet. Tambahkan rekening dulu ya."
                val toAcc = if (normType == TxTypes.TRANSFER) {
                    state.accounts.firstOrNull {
                        it.id != account.id &&
                            (it.name.contains(resp.counterparty, ignoreCase = true) ||
                                it.name.contains(resp.targetParam, ignoreCase = true))
                    } ?: state.accounts.firstOrNull { it.id != account.id }
                } else null
                val category = matchCategory(state.categories, resp.categoryName, normType)
                    ?: return "Kategori catatan tidak ditemukan."
                val txTitle = resp.title.ifBlank { category.name }
                saveTransaction(
                    existingTx = null,
                    type = normType,
                    amount = resp.amount,
                    accountId = account.id,
                    toAccountId = toAcc?.id,
                    adminFee = 0L,
                    categoryId = category.id,
                    title = txTitle,
                    note = "Dicatat oleh Asisten Zawa",
                    tags = "#asistenzawa",
                    dateMillis = System.currentTimeMillis(),
                    receiptUri = null,
                    splitItemsDraft = emptyList()
                )
                "Catatan '$txTitle' (${CurrencyFormatter.formatIdr(resp.amount)}) disimpan di ${account.name}"
            }

            ZawaAiActionType.ADD_BILL_REMINDER -> {
                if (!state.canEdit) return "Akun Hanya Lihat tidak bisa membuat pengingat tagihan."
                if (resp.amount <= 0L) return "Nominal tagihan belum disebutkan (Rp 0)."
                val account = matchAccount(state.accounts, resp.accountName)
                    ?: return "Belum ada rekening dompet untuk tagihan."
                val category = matchCategory(state.categories, resp.categoryName.ifBlank { "Tagihan" }, TxTypes.EXPENSE)
                    ?: return "Kategori tidak ditemukan."
                val cal = Calendar.getInstance()
                val todayDay = cal.get(Calendar.DAY_OF_MONTH)
                val targetDay = if (resp.dayOrOffset in 1..31) resp.dayOrOffset else todayDay
                if (targetDay < todayDay) {
                    cal.add(Calendar.MONTH, 1)
                }
                val maxDayInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(maxDayInMonth))
                val dueMillis = cal.timeInMillis
                val freq = when (resp.frequency.uppercase()) {
                    "WEEKLY", "MINGGUAN" -> "WEEKLY"
                    "YEARLY", "TAHUNAN" -> "YEARLY"
                    "DAILY", "HARIAN" -> "DAILY"
                    else -> "MONTHLY"
                }
                val billTitle = resp.title.ifBlank { "Tagihan Bulanan" }
                saveRecurringRule(
                    existingId = null,
                    title = billTitle,
                    amount = resp.amount,
                    accountId = account.id,
                    categoryId = category.id,
                    frequency = freq,
                    nextDueDateMillis = dueMillis,
                    reminderEnabled = true,
                    isActive = true
                )
                "Pengingat '$billTitle' (${CurrencyFormatter.formatIdr(resp.amount)}, tgl ${CurrencyFormatter.formatDateShort(dueMillis)}) dibuat"
            }

            ZawaAiActionType.SET_BUDGET -> {
                if (!state.isOwner && state.isSharedWorkspace) {
                    return "Hanya Pemilik yang dapat mengatur batas belanja bersama."
                }
                if (resp.amount <= 0L) return "Nominal batas belanja belum disebutkan (Rp 0)."
                val category = matchCategory(
                    state.categories,
                    resp.categoryName.ifBlank { resp.title },
                    TxTypes.EXPENSE
                ) ?: return "Kategori belanja tidak ditemukan."
                saveBudget(
                    existingId = null,
                    categoryId = category.id,
                    limitAmount = resp.amount,
                    rolloverAmount = 0L,
                    rolloverEnabled = false
                )
                "Batas belanja '${category.name}' diatur ke ${CurrencyFormatter.formatIdr(resp.amount)}/bulan"
            }

            ZawaAiActionType.CREATE_GOAL -> {
                if (!state.canEdit) return "Akun Hanya Lihat tidak bisa membuat target tabungan."
                if (resp.amount <= 0L) return "Target tabungan belum menyebutkan nominal."
                val goalTitle = resp.title.ifBlank { "Target Tabungan" }
                val monthly = if (resp.secondaryAmount > 0L) {
                    resp.secondaryAmount
                } else {
                    (resp.amount / 12L).coerceAtLeast(100_000L)
                }
                val monthsAhead = ((resp.amount + monthly - 1L) / monthly).toInt().coerceIn(1, 120)
                saveGoal(
                    existingId = null,
                    title = goalTitle,
                    targetAmount = resp.amount,
                    currentAmount = 0L,
                    monthlyContribution = monthly,
                    monthsAhead = monthsAhead
                )
                "Target tabungan '$goalTitle' (${CurrencyFormatter.formatIdr(resp.amount)}) dibuat"
            }

            ZawaAiActionType.ADD_DEBT_LOAN -> {
                if (!state.canEdit) return "Akun Hanya Lihat tidak bisa mencatat utang/piutang."
                if (resp.amount <= 0L) return "Nominal utang/piutang belum disebutkan."
                val kind = if (resp.targetParam.equals("LOAN", ignoreCase = true)) "LOAN" else "DEBT"
                val title = resp.title.ifBlank { if (kind == "LOAN") "Piutang" else "Utang" }
                val party = resp.counterparty.ifBlank { "Pihak Lain" }
                saveDebtLoan(
                    existingId = null,
                    kind = kind,
                    title = title,
                    counterparty = party,
                    principal = resp.amount,
                    remainingAmount = resp.amount,
                    interestRateAnnual = 0.0,
                    minMonthlyPayment = resp.secondaryAmount
                )
                "${if (kind == "LOAN") "Piutang" else "Utang"} '$title' (${CurrencyFormatter.formatIdr(resp.amount)}) dicatat"
            }

            ZawaAiActionType.ADD_ACCOUNT -> {
                if (!state.canEdit) return "Akun Hanya Lihat tidak bisa menambah rekening dompet."
                val accName = resp.title.ifBlank { resp.accountName.ifBlank { "Dompet Baru" } }
                val accType = when (resp.targetParam.uppercase()) {
                    AccountTypes.CASH, "TUNAI" -> AccountTypes.CASH
                    AccountTypes.EWALLET, "E-WALLET", "DOMPET" -> AccountTypes.EWALLET
                    AccountTypes.CREDIT_CARD, "KARTU KREDIT", "CC" -> AccountTypes.CREDIT_CARD
                    else -> AccountTypes.BANK
                }
                saveAccount(
                    existingId = null,
                    name = accName,
                    type = accType,
                    balance = resp.amount,
                    creditLimit = resp.secondaryAmount,
                    billingDay = 1,
                    dueDay = 15,
                    institution = accName
                )
                "Rekening '$accName' (${CurrencyFormatter.accountTypeLabel(accType)}) dengan saldo ${CurrencyFormatter.formatIdr(resp.amount)} ditambahkan"
            }

            ZawaAiActionType.SETUP_AUTO_SALARY -> {
                if (!state.canEdit) return "Akun Hanya Lihat tidak bisa mengatur gaji otomatis."
                val baseSalary = if (resp.amount > 0L) resp.amount else state.settings.monthlyBaseSalary
                if (baseSalary <= 0L) return "Nominal gaji pokok belum disebutkan."
                val payday = (if (resp.dayOrOffset in 1..31) resp.dayOrOffset else state.settings.salaryPaydayDay).coerceIn(1, 31)
                val workDays = (if (resp.secondaryAmount in 1..31) resp.secondaryAmount.toInt() else state.settings.salaryWorkingDays).coerceIn(1, 31)
                val account = matchAccount(state.accounts, resp.accountName)
                val targetAccId = account?.id ?: state.settings.salaryTargetAccountId
                saveAutoSalaryConfig(
                    enabled = true,
                    baseSalary = baseSalary,
                    paydayDay = payday,
                    workingDays = workDays,
                    targetAccountId = targetAccId
                )
                "Gaji otomatis aktif: ${CurrencyFormatter.formatIdr(baseSalary)} tiap tgl $payday ($workDays hari kerja)"
            }

            ZawaAiActionType.CHECK_BILL_REMINDERS_NOW -> {
                testBillReminderNotificationNow()
                "Pengecekan pengingat tagihan 3 hari & 1 hari dijalankan"
            }

            ZawaAiActionType.TOGGLE_STEALTH_MODE -> {
                val targetStealth = when (resp.targetParam.uppercase()) {
                    "ON", "TRUE", "AKTIF" -> true
                    "OFF", "FALSE", "MATI" -> false
                    else -> !state.settings.isStealthMode
                }
                viewModelScope.launch {
                    prefs.setStealthMode(targetStealth)
                }
                if (targetStealth) "Mode Sembunyikan Angka diaktifkan" else "Mode Sembunyikan Angka dimatikan"
            }

            ZawaAiActionType.SWITCH_THEME -> {
                val raw = resp.targetParam.uppercase()
                val preset = when {
                    raw.contains("DARK") || raw.contains("GELAP") || raw.contains("ELEGANT") ->
                        com.example.ui.theme.AppThemePreset.DARK_ELEGANT
                    raw.contains("AUTO") || raw.contains("WAKTU") ->
                        com.example.ui.theme.AppThemePreset.AUTO_TIME_PASTEL
                    raw.contains("BRIGHT") || raw.contains("LIGHT") || raw.contains("CERAH") || raw.contains("SUNNY") ->
                        com.example.ui.theme.AppThemePreset.BRIGHT_SUNNY
                    raw.contains("COLOR") || raw.contains("VIBRANT") || raw.contains("POP") || raw.contains("WARNA") ->
                        com.example.ui.theme.AppThemePreset.COLORFUL_POP
                    raw.contains("EMERALD") || raw.contains("HIJAU") || raw.contains("KLASIK") ->
                        com.example.ui.theme.AppThemePreset.EMERALD_PRO
                    raw.contains("OCEAN") || raw.contains("LAUT") || raw.contains("BIRU") ->
                        com.example.ui.theme.AppThemePreset.OCEAN_BREEZE
                    else -> com.example.ui.theme.AppThemePreset.CUTE_PASTEL
                }
                setThemePreset(preset.id)
                "Tema diganti ke '${preset.displayName}'"
            }

            ZawaAiActionType.NAVIGATE_TAB -> {
                val tabKey = when (resp.targetParam.uppercase()) {
                    "LEDGER", "CATATAN" -> "LEDGER"
                    "WEALTH", "DOMPET", "ANGGARAN" -> "WEALTH"
                    "GROUP_DEBT", "PATUNGAN", "UTANG", "TARGET" -> "GROUP_DEBT"
                    "ADVISOR", "LAPORAN" -> "ADVISOR"
                    else -> "DASHBOARD"
                }
                val tabLabel = when (tabKey) {
                    "LEDGER" -> "Catatan"
                    "WEALTH" -> "Dompet"
                    "GROUP_DEBT" -> "Patungan"
                    "ADVISOR" -> "Laporan"
                    else -> "Beranda"
                }
                _aiNavigationRequest.value = tabKey to (++aiNavNonce)
                "Membuka halaman $tabLabel"
            }
        }
    }

    private fun matchAccount(accounts: List<AccountEntity>, hint: String): AccountEntity? {
        if (accounts.isEmpty()) return null
        if (hint.isNotBlank()) {
            val matched = accounts.firstOrNull { it.name.contains(hint.trim(), ignoreCase = true) }
            if (matched != null) return matched
        }
        return accounts.firstOrNull { it.type != AccountTypes.CREDIT_CARD } ?: accounts.first()
    }

    private fun matchCategory(categories: List<CategoryEntity>, hint: String, txType: String): CategoryEntity? {
        if (categories.isEmpty()) return null
        val preferredType = if (txType == TxTypes.INCOME) TxTypes.INCOME else TxTypes.EXPENSE
        if (hint.isNotBlank()) {
            val clean = hint.trim()
            val exact = categories.firstOrNull {
                it.type == preferredType && it.name.contains(clean, ignoreCase = true)
            }
            if (exact != null) return exact
            val anyTypeMatch = categories.firstOrNull { it.name.contains(clean, ignoreCase = true) }
            if (anyTypeMatch != null) return anyTypeMatch
        }
        return categories.firstOrNull { it.type == preferredType } ?: categories.first()
    }
}
