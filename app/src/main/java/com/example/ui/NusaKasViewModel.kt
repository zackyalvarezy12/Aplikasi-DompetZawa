package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.prefs.AppPreferences
import com.example.data.prefs.UserSettings
import com.example.data.repository.NusaKasRepository
import com.example.data.sync.SyncEngine
import com.example.data.sync.SyncVisualStatus
import androidx.compose.runtime.Immutable
import com.example.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}

@OptIn(ExperimentalCoroutinesApi::class)
class NusaKasViewModel(application: Application) : AndroidViewModel(application) {

    private val db = NusaKasDatabase.getInstance(application)
    private val dao = db.dao()
    val prefs = AppPreferences(application)
    val repo = NusaKasRepository(dao)
    val syncEngine = SyncEngine(application, dao, prefs)

    private val _pinUnlocked = MutableStateFlow(false)
    val isPinUnlocked: StateFlow<Boolean> = _pinUnlocked.asStateFlow()

    private val _hasEnteredSession = MutableStateFlow(false)
    val hasEnteredSession: StateFlow<Boolean> = _hasEnteredSession.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbar.asStateFlow()

    private val _lastSyncMsg = MutableStateFlow<String?>(null)
    private val _ledgerLimit = MutableStateFlow(30)

    private val activeWorkspaceIdFlow = prefs.settingsFlow
        .map { it.activeWorkspaceId }
        .distinctUntilChanged()

    private val networkFlow = syncEngine.observeNetworkConnectivity()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
        }

        // Auto-trigger sync when back online and shared workspace has pending items
        viewModelScope.launch {
            combine(
                networkFlow,
                prefs.settingsFlow,
                repo.observePendingSyncCount(),
                activeWorkspaceIdFlow
            ) { online, settings, pending, wsId ->
                Quad(online, settings, pending, wsId)
            }.collect { (online, settings, pending, wsId) ->
                if (online && !settings.simulatedOffline && pending > 0 && settings.autoSyncSimulation) {
                    val ws = dao.getWorkspaceById(wsId)
                    if (ws?.type == WorkspaceTypes.SHARED) {
                        val rep = syncEngine.syncPendingQueue(settings, online, ws.type)
                        if (rep.pushedCount > 0) _lastSyncMsg.value = rep.message
                    }
                }
            }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

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

    val uiState: StateFlow<MainUiState> = combine(
        prefs.settingsFlow,
        repo.observeCurrentUser(),
        repo.observeWorkspaces(),
        workspaceCoreFlow,
        workspacePlanningFlow,
        workspaceGroupFlow,
        repo.observePendingSyncCount(),
        networkFlow,
        syncEngine.isSyncing,
        _lastSyncMsg
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val settings = values[0] as UserSettings
        val user = values[1] as UserEntity?
        val workspaces = values[2] as List<WorkspaceEntity>
        val core = values[3] as WorkspaceCoreBundle
        val plan = values[4] as WorkspacePlanningBundle
        val group = values[5] as Pair<List<SplitBillEntity>, List<AuditLogEntity>>
        val pendingCount = values[6] as Int
        val online = values[7] as Boolean
        val isSyncing = values[8] as Boolean
        val syncMsg = values[9] as String?

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
        val monthIncome = monthTxs.filter { it.type == TxTypes.INCOME }.sumOf { it.amount }
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
            !online || settings.simulatedOffline -> SyncVisualStatus.OFFLINE_LOCAL
            isSyncing || pendingCount > 0 -> SyncVisualStatus.SYNCING
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

        // Precompute O(1) lookup maps off the main thread for zero-jank LazyColumn scrolling
        val catMap = core.categories.associateBy { it.id }
        val accMap = core.accounts.associateBy { it.id }
        val itemsByTx = core.items.groupBy { it.transactionId }

        MainUiState(
            isReady = workspaces.isNotEmpty(),
            settings = settings,
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
            pendingSyncCount = pendingCount,
            isNetworkConnected = online,
            syncVisualStatus = syncVisual,
            lastSyncMessage = syncMsg,
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
        val step = (monthlyNet.coerceAtLeast(1_500_000L) * 0.65).toLong()
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
            val inc = monthTxs.filter { it.type == TxTypes.INCOME }.sumOf { it.amount }
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

    // --- ACTIONS ---
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

    fun lockAppNow() {
        _pinUnlocked.value = false
        _hasEnteredSession.value = false
    }

    fun enablePin(pin: String) {
        viewModelScope.launch {
            prefs.setPin(pin)
            _pinUnlocked.value = true
            _hasEnteredSession.value = true
            _snackbar.value = "Kunci PIN 6-Digit berhasil diaktifkan."
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            prefs.disablePin()
            _snackbar.value = "Kunci PIN dinonaktifkan."
        }
    }

    // --- WORKSPACE & COLLABORATION ---
    fun switchWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            _ledgerLimit.value = 30
            val user = uiState.value.currentUser
            val members = dao.observeMembers(workspace.id).first()
            val member = members.firstOrNull { it.userId == user?.id } ?: members.firstOrNull()
            prefs.setActiveWorkspace(workspace.id, member?.id ?: "mem_p_zacky")
            _snackbar.value = "Beralih ke ${workspace.name}"
        }
    }

    fun switchActiveMemberRoleDemo(member: WorkspaceMemberEntity) {
        viewModelScope.launch {
            prefs.setActiveMemberId(member.id)
            _snackbar.value = "Simulasi akun aktif: ${member.name} (${member.role})"
        }
    }

    fun createWorkspace(name: String, type: String) {
        viewModelScope.launch {
            val user = uiState.value.currentUser ?: return@launch
            val (ws, member) = repo.createWorkspace(name, type, user)
            prefs.setActiveWorkspace(ws.id, member.id)
            _snackbar.value = "Workspace '${ws.name}' dibuat (Kode: ${ws.inviteCode})"
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
            _snackbar.value = "Hanya Owner yang dapat mengundang atau mengubah anggota."
            return
        }
        viewModelScope.launch {
            val ws = state.activeWorkspace ?: return@launch
            val actor = state.activeMember ?: return@launch
            repo.inviteMemberToWorkspace(ws.id, name, email, role, actor.id, actor.name)
            _snackbar.value = "$name ditambahkan sebagai $role"
        }
    }

    fun changeMemberRole(memberId: String, newRole: String) {
        val state = uiState.value
        if (!state.isOwner) {
            _snackbar.value = "Hanya Owner yang dapat mengubah peran."
            return
        }
        viewModelScope.launch {
            val ws = state.activeWorkspace ?: return@launch
            val actor = state.activeMember ?: return@launch
            repo.updateMemberRole(ws.id, memberId, newRole, actor.id, actor.name)
            _snackbar.value = "Peran anggota diperbarui ke $newRole"
        }
    }

    // --- SYNC & CLOUD CONFIG ---
    fun toggleSimulatedOffline() {
        viewModelScope.launch {
            val next = !uiState.value.settings.simulatedOffline
            prefs.setSimulatedOffline(next)
            _snackbar.value = if (next) "Mode Offline diaktifkan. Perubahan masuk ke Sync Queue." else "Koneksi Online dipulihkan."
        }
    }

    fun saveCloudSettings(provider: String, url: String, apiKey: String, autoSim: Boolean) {
        viewModelScope.launch {
            prefs.updateCloudConfig(provider, url, apiKey, autoSim)
            _snackbar.value = "Pengaturan Cloud Sync ($provider) disimpan."
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            val state = uiState.value
            val ws = state.activeWorkspace ?: return@launch
            val report = syncEngine.syncPendingQueue(state.settings, state.isNetworkConnected, ws.type)
            _lastSyncMsg.value = report.message
            _snackbar.value = report.message
        }
    }

    // --- TRANSACTIONS ---
    fun submitQuickInput(rawInput: String): Boolean {
        val state = uiState.value
        if (!state.canEdit) {
            _snackbar.value = "Akses Viewer hanya bisa melihat data (read-only)."
            return false
        }
        val parsed = SmartQuickInputParser.parse(rawInput, state.accounts, state.categories)
        if (!parsed.isValid || parsed.accountId == null || parsed.categoryId == null) {
            _snackbar.value = "Format belum dikenali. Contoh: 'Beli kopi 28rb pakai GoPay #nongkrong'"
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
                note = "Quick Input: \"$rawInput\"",
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
        splitItemsDraft: List<Triple<String, String, Long>> // catId, desc, amount
    ) {
        val state = uiState.value
        if (!state.canEdit) {
            _snackbar.value = "Peran Viewer tidak memiliki izin mencatat transaksi."
            return
        }
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            val receiptPath = receiptUri?.let {
                ReceiptImageHelper.compressAndSaveReceipt(getApplication(), it)
            }
            val txId = "tx_${UUID.randomUUID().toString().take(8)}"
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
            val tx = TransactionEntity(
                id = txId,
                workspaceId = ws.id,
                type = type,
                amount = totalAmt,
                accountId = accountId,
                toAccountId = if (type == TxTypes.TRANSFER) toAccountId else null,
                adminFee = if (type == TxTypes.TRANSFER) adminFee else 0L,
                categoryId = categoryId,
                title = title.ifBlank { "Transaksi Baru" },
                note = note,
                tags = tags,
                dateMillis = dateMillis,
                receiptImagePath = receiptPath,
                createdByMemberId = member.id,
                createdByName = member.name
            )
            repo.recordTransaction(tx, items, ws.type == WorkspaceTypes.SHARED)
            _snackbar.value = "Transaksi '${tx.title}' tersimpan."
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        val state = uiState.value
        if (!state.canEdit) {
            _snackbar.value = "Peran Viewer tidak dapat menghapus transaksi."
            return
        }
        viewModelScope.launch {
            repo.deleteTransaction(tx)
            _snackbar.value = "Transaksi '${tx.title}' dihapus & saldo disesuaikan."
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
            _snackbar.value = "Akses read-only (Viewer)."
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
                billingDay = billingDay,
                dueDay = dueDay,
                institutionName = institution.trim().ifBlank { name.trim() }
            )
            repo.upsertAccount(acc, member.id, member.name)
            _snackbar.value = "Akun '${acc.name}' disimpan."
        }
    }

    fun reconcileAccount(account: AccountEntity, actualBalance: Long) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.reconcileAccountBalance(account, actualBalance, member.id, member.name, state.isSharedWorkspace)
            _snackbar.value = "Rekonsiliasi '${account.name}' selesai."
        }
    }

    fun saveAsset(
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
                id = "ast_${UUID.randomUUID().toString().take(8)}",
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
            _snackbar.value = "Aset '${asset.name}' ditambahkan."
        }
    }

    // --- BUDGETS & RECURRING ---
    fun saveBudget(categoryId: String, limitAmount: Long, rolloverAmount: Long, rolloverEnabled: Boolean) {
        val state = uiState.value
        if (!state.isOwner && state.isSharedWorkspace) {
            _snackbar.value = "Hanya Owner yang dapat mengatur batas anggaran bersama."
            return
        }
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        val monthKey = CurrencyFormatter.currentMonthKey()
        val existing = state.budgets.firstOrNull { it.categoryId == categoryId && it.monthYear == monthKey }
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
            _snackbar.value = "Anggaran kategori diperbarui."
        }
    }

    fun saveRecurringRule(
        title: String,
        amount: Long,
        accountId: String,
        categoryId: String,
        frequency: String,
        nextDueDateMillis: Long
    ) {
        val state = uiState.value
        if (!state.canEdit) return
        val ws = state.activeWorkspace ?: return
        viewModelScope.launch {
            repo.upsertRecurringRule(
                RecurringRuleEntity(
                    id = "rec_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = ws.id,
                    title = title.trim(),
                    amount = amount,
                    type = TxTypes.EXPENSE,
                    accountId = accountId,
                    categoryId = categoryId,
                    frequency = frequency,
                    nextDueDateMillis = nextDueDateMillis
                )
            )
            _snackbar.value = "Tagihan berulang '$title' dibuat."
        }
    }

    fun payRecurringNow(rule: RecurringRuleEntity) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            repo.payRecurringRule(rule, member.id, member.name, state.isSharedWorkspace)
            _snackbar.value = "Tagihan '${rule.title}' dibayar & jadwal berikutnya diperbarui."
        }
    }

    // --- DEBTS & LOANS ---
    fun saveDebtLoan(
        kind: String,
        title: String,
        counterparty: String,
        principal: Long,
        interestRateAnnual: Double,
        minMonthlyPayment: Long
    ) {
        val state = uiState.value
        if (!state.canEdit) return
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        viewModelScope.launch {
            val item = DebtLoanEntity(
                id = "dbt_${UUID.randomUUID().toString().take(8)}",
                workspaceId = ws.id,
                kind = kind,
                title = title.trim(),
                counterparty = counterparty.trim(),
                principalAmount = principal,
                remainingAmount = principal,
                interestRateAnnual = interestRateAnnual,
                minimumMonthlyPayment = minMonthlyPayment,
                dueDateMillis = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000L
            )
            repo.upsertDebtLoan(item, member.id, member.name)
            _snackbar.value = "Catatan ${if (kind == "DEBT") "Utang" else "Piutang"} disimpan."
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
    fun saveGoal(title: String, targetAmount: Long, monthlyContribution: Long, monthsAhead: Int) {
        val state = uiState.value
        if (!state.canEdit) return
        val ws = state.activeWorkspace ?: return
        val member = state.activeMember ?: return
        val targetDate = Calendar.getInstance().apply { add(Calendar.MONTH, monthsAhead.coerceAtLeast(1)) }.timeInMillis
        viewModelScope.launch {
            repo.upsertGoal(
                GoalEntity(
                    id = "gol_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = ws.id,
                    title = title.trim(),
                    targetAmount = targetAmount,
                    currentAmount = 0L,
                    targetDateMillis = targetDate,
                    monthlyContribution = monthlyContribution,
                    contributorSummary = ""
                ),
                member.id,
                member.name
            )
            _snackbar.value = "Target '$title' dibuat."
        }
    }

    fun contributeToGoal(goal: GoalEntity, amount: Long, accountId: String) {
        val state = uiState.value
        if (!state.canEdit) return
        val member = state.activeMember ?: return
        val cat = state.categories.firstOrNull { it.type == TxTypes.EXPENSE } ?: return
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
            _snackbar.value = "Split Bill '$title' dibuat."
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
            _snackbar.value = if (enabled) {
                "Gaji otomatis aktif: ${CurrencyFormatter.formatIdr(baseSalary)} tiap tgl ${paydayDay.coerceIn(1, 31)} ($workingDays hari kerja)."
            } else {
                "Pengingat pop-up gaji otomatis dimatikan."
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
            _snackbar.value = "Peran Viewer tidak dapat mencatat gaji."
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
}
