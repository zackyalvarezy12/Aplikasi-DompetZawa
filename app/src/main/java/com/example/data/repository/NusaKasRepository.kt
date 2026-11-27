package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

class NusaKasRepository(private val dao: NusaKasDao) {

    fun observeCurrentUser(): Flow<UserEntity?> = dao.observeCurrentUser()
    fun observeWorkspaces(): Flow<List<WorkspaceEntity>> = dao.observeAllWorkspaces()
    fun observeMembers(workspaceId: String): Flow<List<WorkspaceMemberEntity>> = dao.observeMembers(workspaceId)
    fun observeAccounts(workspaceId: String): Flow<List<AccountEntity>> = dao.observeAccounts(workspaceId)
    fun observeCategories(workspaceId: String): Flow<List<CategoryEntity>> = dao.observeCategories(workspaceId)
    fun observeTransactions(workspaceId: String): Flow<List<TransactionEntity>> = dao.observeTransactions(workspaceId)
    fun observeTransactionsPaged(workspaceId: String, limit: Int): Flow<List<TransactionEntity>> = dao.observeTransactionsPaged(workspaceId, limit)
    fun observeRecentTransactions(workspaceId: String): Flow<List<TransactionEntity>> = dao.observeRecentTransactions(workspaceId)
    fun observeTransactionCount(workspaceId: String): Flow<Int> = dao.observeTransactionCount(workspaceId)
    fun observeTransactionItems(workspaceId: String): Flow<List<TransactionItemEntity>> = dao.observeTransactionItems(workspaceId)
    fun observeBudgets(workspaceId: String): Flow<List<BudgetEntity>> = dao.observeBudgets(workspaceId)
    fun observeRecurringRules(workspaceId: String): Flow<List<RecurringRuleEntity>> = dao.observeRecurringRules(workspaceId)
    fun observeDebtsLoans(workspaceId: String): Flow<List<DebtLoanEntity>> = dao.observeDebtsLoans(workspaceId)
    fun observeGoals(workspaceId: String): Flow<List<GoalEntity>> = dao.observeGoals(workspaceId)
    fun observeAssets(workspaceId: String): Flow<List<AssetInvestmentEntity>> = dao.observeAssets(workspaceId)
    fun observeSplitBills(workspaceId: String): Flow<List<SplitBillEntity>> = dao.observeSplitBills(workspaceId)
    fun observeAuditLogs(workspaceId: String): Flow<List<AuditLogEntity>> = dao.observeAuditLogs(workspaceId)
    fun observePendingSyncCount(): Flow<Int> = dao.observePendingSyncCount()

    suspend fun ensureSeeded() = DatabaseSeeder.seedIfEmpty(dao)

    private suspend fun queueIfShared(
        workspaceId: String,
        tableName: String,
        recordId: String,
        operation: String,
        payload: JSONObject
    ) {
        val ws = dao.getWorkspaceById(workspaceId)
        if (ws?.type == WorkspaceTypes.SHARED) {
            dao.enqueueSync(
                SyncQueueEntity(
                    id = UUID.randomUUID().toString(),
                    workspaceId = workspaceId,
                    tableName = tableName,
                    recordId = recordId,
                    operation = operation,
                    payloadJson = payload.toString(),
                    status = "PENDING",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun addAuditLog(
        workspaceId: String,
        actorMemberId: String,
        actorName: String,
        action: String,
        detail: String
    ) {
        dao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                workspaceId = workspaceId,
                actorMemberId = actorMemberId,
                actorName = actorName,
                action = action,
                detail = detail,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    // --- WORKSPACE & MEMBERS ---
    suspend fun createWorkspace(
        name: String,
        type: String,
        ownerUser: UserEntity
    ): Pair<WorkspaceEntity, WorkspaceMemberEntity> {
        val wsId = "ws_${UUID.randomUUID().toString().take(8)}"
        val code = generateInviteCode()
        val workspace = WorkspaceEntity(
            id = wsId,
            name = name.trim(),
            type = type,
            inviteCode = code,
            inviteLink = "https://nusakas.app/join/$code",
            createdByUserId = ownerUser.id
        )
        val ownerMember = WorkspaceMemberEntity(
            id = "mem_${UUID.randomUUID().toString().take(8)}",
            workspaceId = wsId,
            userId = ownerUser.id,
            name = ownerUser.name,
            email = ownerUser.email,
            avatarInitials = ownerUser.avatarInitials,
            colorHex = ownerUser.avatarColorHex,
            role = MemberRoles.OWNER
        )
        dao.upsertWorkspace(workspace)
        dao.upsertMember(ownerMember)
        val prefix = wsId.takeLast(4)
        dao.insertCategories(DatabaseSeeder.defaultCategories(wsId, prefix))
        dao.upsertAccount(
            AccountEntity(
                id = "acc_${prefix}_cash",
                workspaceId = wsId,
                name = "Kas Tunai",
                type = AccountTypes.CASH,
                balance = 0L,
                institutionName = "Tunai"
            )
        )
        addAuditLog(wsId, ownerMember.id, ownerMember.name, "Buat Workspace", name)
        return workspace to ownerMember
    }

    suspend fun joinWorkspaceByCode(rawCode: String, user: UserEntity): WorkspaceEntity? {
        val code = rawCode.trim().uppercase().substringAfterLast("/")
        val existing = dao.getWorkspaceByInviteCode(code)
        if (existing != null) {
            val member = WorkspaceMemberEntity(
                id = "mem_${UUID.randomUUID().toString().take(8)}",
                workspaceId = existing.id,
                userId = user.id,
                name = user.name,
                email = user.email,
                avatarInitials = user.avatarInitials,
                colorHex = user.avatarColorHex,
                role = MemberRoles.EDITOR
            )
            dao.upsertMember(member)
            addAuditLog(existing.id, member.id, member.name, "Gabung Workspace", "Kode $code")
            return existing
        }
        // If user types a remote invite code (e.g. demo code from another device), create shared workspace locally ready for sync
        if (code.length in 6..8 && code.all { it.isLetterOrDigit() }) {
            val wsId = "ws_join_${code.lowercase()}"
            val joinedWs = WorkspaceEntity(
                id = wsId,
                name = "Grup Bersama ($code)",
                type = WorkspaceTypes.SHARED,
                inviteCode = code,
                inviteLink = "https://nusakas.app/join/$code",
                createdByUserId = "usr_remote"
            )
            dao.upsertWorkspace(joinedWs)
            val prefix = code.lowercase()
            dao.insertCategories(DatabaseSeeder.defaultCategories(wsId, prefix))
            dao.upsertAccount(
                AccountEntity("acc_${prefix}_kas", wsId, "Kas Grup Bersama", AccountTypes.BANK, 5_000_000L, institutionName = "BCA")
            )
            val partner = WorkspaceMemberEntity("mem_${prefix}_host", wsId, "usr_host", "Rekan Grup", "partner@nusakas.id", "RG", 0xFF0284C7, MemberRoles.OWNER)
            val me = WorkspaceMemberEntity("mem_${prefix}_me", wsId, user.id, user.name, user.email, user.avatarInitials, user.avatarColorHex, MemberRoles.EDITOR)
            dao.insertMembers(listOf(partner, me))
            addAuditLog(wsId, me.id, me.name, "Gabung via Undangan", "Kode $code")
            queueIfShared(wsId, "workspace_members", me.id, "UPSERT", JSONObject().put("code", code))
            return joinedWs
        }
        return null
    }

    suspend fun inviteMemberToWorkspace(
        workspaceId: String,
        name: String,
        email: String,
        role: String,
        actorMemberId: String,
        actorName: String
    ) {
        val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "AG" }
        val colors = listOf(0xFF0284C7, 0xFF7C3AED, 0xFFD97706, 0xFF059669, 0xFFDB2777)
        val member = WorkspaceMemberEntity(
            id = "mem_${UUID.randomUUID().toString().take(8)}",
            workspaceId = workspaceId,
            userId = "usr_${UUID.randomUUID().toString().take(6)}",
            name = name.trim(),
            email = email.trim(),
            avatarInitials = initials,
            colorHex = colors.random(),
            role = role,
            status = "ACTIVE"
        )
        dao.upsertMember(member)
        addAuditLog(workspaceId, actorMemberId, actorName, "Tambah Anggota", "${member.name} ($role)")
        queueIfShared(workspaceId, "workspace_members", member.id, "UPSERT", JSONObject().put("name", member.name).put("role", role))
    }

    suspend fun updateMemberRole(workspaceId: String, memberId: String, role: String, actorMemberId: String, actorName: String) {
        dao.updateMemberRole(memberId, role)
        addAuditLog(workspaceId, actorMemberId, actorName, "Ubah Peran Anggota", "Role -> $role")
        queueIfShared(workspaceId, "workspace_members", memberId, "UPSERT", JSONObject().put("role", role))
    }

    suspend fun deleteWorkspace(workspaceId: String) {
        dao.deleteWorkspaceById(workspaceId)
    }

    // --- TRANSACTIONS & BALANCES ---
    suspend fun recordTransaction(
        tx: TransactionEntity,
        splitItems: List<TransactionItemEntity> = emptyList(),
        isSharedWorkspace: Boolean
    ) {
        val status = if (isSharedWorkspace) SyncStates.PENDING else SyncStates.OFFLINE_ONLY
        val finalTx = tx.copy(syncStatus = status, updatedAt = System.currentTimeMillis())
        dao.upsertTransaction(finalTx)
        if (splitItems.isNotEmpty()) {
            dao.deleteTransactionItems(finalTx.id)
            dao.insertTransactionItems(splitItems)
        }
        applyBalanceEffect(finalTx, multiplier = 1)
        addAuditLog(
            finalTx.workspaceId,
            finalTx.createdByMemberId,
            finalTx.createdByName,
            "Catat ${finalTx.type}",
            "${finalTx.title} • Rp ${finalTx.amount}"
        )
        queueIfShared(
            finalTx.workspaceId,
            "transactions",
            finalTx.id,
            "UPSERT",
            JSONObject()
                .put("title", finalTx.title)
                .put("amount", finalTx.amount)
                .put("type", finalTx.type)
                .put("updatedAt", finalTx.updatedAt)
        )
    }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        applyBalanceEffect(tx, multiplier = -1)
        dao.deleteTransactionItems(tx.id)
        dao.deleteTransactionById(tx.id)
        addAuditLog(tx.workspaceId, tx.createdByMemberId, tx.createdByName, "Hapus Transaksi", tx.title)
        queueIfShared(tx.workspaceId, "transactions", tx.id, "DELETE", JSONObject().put("id", tx.id))
    }

    private suspend fun applyBalanceEffect(tx: TransactionEntity, multiplier: Int) {
        val account = dao.getAccountById(tx.accountId) ?: return
        val isCreditCard = account.type == AccountTypes.CREDIT_CARD
        when (tx.type) {
            TxTypes.INCOME, TxTypes.REFUND -> {
                // For cash/bank/ewallet: +amount. For CC: refund lowers used balance (-amount)
                val delta = if (isCreditCard) -tx.amount * multiplier else tx.amount * multiplier
                dao.adjustAccountBalance(account.id, delta)
            }
            TxTypes.EXPENSE, TxTypes.SPLIT -> {
                // For cash/bank/ewallet: -amount. For CC: expense increases used balance (+amount)
                val delta = if (isCreditCard) tx.amount * multiplier else -tx.amount * multiplier
                dao.adjustAccountBalance(account.id, delta)
            }
            TxTypes.TRANSFER -> {
                val outDelta = if (isCreditCard) (tx.amount + tx.adminFee) * multiplier else -(tx.amount + tx.adminFee) * multiplier
                dao.adjustAccountBalance(account.id, outDelta)
                tx.toAccountId?.let { toId ->
                    val dest = dao.getAccountById(toId)
                    if (dest != null) {
                        val inDelta = if (dest.type == AccountTypes.CREDIT_CARD) -tx.amount * multiplier else tx.amount * multiplier
                        dao.adjustAccountBalance(toId, inDelta)
                    }
                }
            }
        }
    }

    // --- ACCOUNTS & RECONCILIATION ---
    suspend fun upsertAccount(account: AccountEntity, actorMemberId: String, actorName: String) {
        dao.upsertAccount(account.copy(updatedAt = System.currentTimeMillis()))
        addAuditLog(account.workspaceId, actorMemberId, actorName, "Kelola Akun", "${account.name} • Rp ${account.balance}")
        queueIfShared(account.workspaceId, "accounts", account.id, "UPSERT", JSONObject().put("name", account.name).put("balance", account.balance))
    }

    suspend fun reconcileAccountBalance(
        account: AccountEntity,
        actualBalance: Long,
        actorMemberId: String,
        actorName: String,
        isShared: Boolean
    ) {
        val diff = actualBalance - account.balance
        if (diff == 0L) return
        val updated = account.copy(balance = actualBalance, updatedAt = System.currentTimeMillis())
        dao.upsertAccount(updated)
        val catPrefix = if (account.workspaceId == "ws_personal") "p" else if (account.workspaceId == "ws_family") "f" else account.workspaceId.takeLast(4)
        val txType = if (account.type == AccountTypes.CREDIT_CARD) {
            if (diff > 0) TxTypes.EXPENSE else TxTypes.REFUND
        } else {
            if (diff > 0) TxTypes.INCOME else TxTypes.EXPENSE
        }
        val tx = TransactionEntity(
            id = "tx_rec_${UUID.randomUUID().toString().take(8)}",
            workspaceId = account.workspaceId,
            type = txType,
            amount = kotlin.math.abs(diff),
            accountId = account.id,
            categoryId = "cat_${catPrefix}_${if (txType == TxTypes.INCOME) "gaji" else "belanja"}",
            title = "Rekonsiliasi Saldo: ${account.name}",
            note = "Penyesuaian selisih saldo otomatis",
            tags = "#rekonsiliasi",
            dateMillis = System.currentTimeMillis(),
            createdByMemberId = actorMemberId,
            createdByName = actorName,
            syncStatus = if (isShared) SyncStates.PENDING else SyncStates.OFFLINE_ONLY
        )
        dao.upsertTransaction(tx)
        addAuditLog(account.workspaceId, actorMemberId, actorName, "Rekonsiliasi Akun", "${account.name}: selisih Rp $diff")
        queueIfShared(account.workspaceId, "accounts", account.id, "UPSERT", JSONObject().put("balance", actualBalance))
    }

    suspend fun deleteAccount(accountId: String) = dao.deleteAccountById(accountId)

    // --- ASSETS ---
    suspend fun upsertAsset(asset: AssetInvestmentEntity, actorMemberId: String, actorName: String) {
        dao.upsertAsset(asset.copy(updatedAt = System.currentTimeMillis()))
        addAuditLog(asset.workspaceId, actorMemberId, actorName, "Update Aset", "${asset.name} • Rp ${asset.currentValue}")
        queueIfShared(asset.workspaceId, "assets_investments", asset.id, "UPSERT", JSONObject().put("name", asset.name).put("value", asset.currentValue))
    }

    suspend fun deleteAsset(assetId: String) = dao.deleteAssetById(assetId)

    // --- BUDGETS ---
    suspend fun upsertBudget(budget: BudgetEntity, actorMemberId: String, actorName: String) {
        dao.upsertBudget(budget.copy(updatedAt = System.currentTimeMillis()))
        addAuditLog(budget.workspaceId, actorMemberId, actorName, "Atur Anggaran", "Rp ${budget.limitAmount}")
        queueIfShared(budget.workspaceId, "budgets", budget.id, "UPSERT", JSONObject().put("limit", budget.limitAmount))
    }

    suspend fun deleteBudget(budgetId: String) = dao.deleteBudgetById(budgetId)

    // --- RECURRING RULES ---
    suspend fun upsertRecurringRule(rule: RecurringRuleEntity) {
        dao.upsertRecurringRule(rule)
        queueIfShared(rule.workspaceId, "recurring_rules", rule.id, "UPSERT", JSONObject().put("title", rule.title))
    }

    suspend fun payRecurringRule(
        rule: RecurringRuleEntity,
        actorMemberId: String,
        actorName: String,
        isShared: Boolean
    ) {
        val now = System.currentTimeMillis()
        val tx = TransactionEntity(
            id = "tx_bill_${UUID.randomUUID().toString().take(8)}",
            workspaceId = rule.workspaceId,
            type = rule.type,
            amount = rule.amount,
            accountId = rule.accountId,
            categoryId = rule.categoryId,
            title = "Bayar Tagihan: ${rule.title}",
            note = "Pembayaran otomatis dari jadwal ${rule.frequency}",
            tags = "#tagihan,#rutin",
            dateMillis = now,
            createdByMemberId = actorMemberId,
            createdByName = actorName,
            syncStatus = if (isShared) SyncStates.PENDING else SyncStates.OFFLINE_ONLY
        )
        recordTransaction(tx, emptyList(), isShared)
        val cal = Calendar.getInstance().apply { timeInMillis = rule.nextDueDateMillis }
        when (rule.frequency) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "MONTHLY" -> cal.add(Calendar.MONTH, 1)
            "YEARLY" -> cal.add(Calendar.YEAR, 1)
            else -> cal.add(Calendar.MONTH, 1)
        }
        dao.upsertRecurringRule(rule.copy(nextDueDateMillis = cal.timeInMillis, lastPaidDateMillis = now))
    }

    suspend fun deleteRecurringRule(ruleId: String) = dao.deleteRecurringRuleById(ruleId)

    // --- DEBTS & LOANS ---
    suspend fun upsertDebtLoan(item: DebtLoanEntity, actorMemberId: String, actorName: String) {
        dao.upsertDebtLoan(item.copy(updatedAt = System.currentTimeMillis()))
        addAuditLog(item.workspaceId, actorMemberId, actorName, "Kelola ${item.kind}", "${item.title} • Rp ${item.remainingAmount}")
        queueIfShared(item.workspaceId, "debts_loans", item.id, "UPSERT", JSONObject().put("remaining", item.remainingAmount))
    }

    suspend fun payDebtInstallment(
        item: DebtLoanEntity,
        payAmount: Long,
        accountId: String,
        categoryId: String,
        actorMemberId: String,
        actorName: String,
        isShared: Boolean
    ) {
        val newRemaining = (item.remainingAmount - payAmount).coerceAtLeast(0L)
        val updated = item.copy(
            remainingAmount = newRemaining,
            isPaidOff = newRemaining == 0L,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertDebtLoan(updated)
        val txType = if (item.kind == "DEBT") TxTypes.EXPENSE else TxTypes.INCOME
        val tx = TransactionEntity(
            id = "tx_dbt_${UUID.randomUUID().toString().take(8)}",
            workspaceId = item.workspaceId,
            type = txType,
            amount = payAmount,
            accountId = accountId,
            categoryId = categoryId,
            title = if (item.kind == "DEBT") "Cicilan Utang: ${item.title}" else "Pelunasan Piutang: ${item.title}",
            note = "Sisa pokok: Rp $newRemaining",
            tags = if (item.kind == "DEBT") "#utang" else "#piutang",
            dateMillis = System.currentTimeMillis(),
            createdByMemberId = actorMemberId,
            createdByName = actorName,
            syncStatus = if (isShared) SyncStates.PENDING else SyncStates.OFFLINE_ONLY
        )
        recordTransaction(tx, emptyList(), isShared)
    }

    suspend fun deleteDebtLoan(id: String) = dao.deleteDebtLoanById(id)

    // --- GOALS ---
    suspend fun upsertGoal(goal: GoalEntity, actorMemberId: String, actorName: String) {
        dao.upsertGoal(goal.copy(updatedAt = System.currentTimeMillis()))
        addAuditLog(goal.workspaceId, actorMemberId, actorName, "Target Tabungan", goal.title)
        queueIfShared(goal.workspaceId, "goals", goal.id, "UPSERT", JSONObject().put("current", goal.currentAmount))
    }

    suspend fun contributeToGoal(
        goal: GoalEntity,
        amount: Long,
        sourceAccountId: String,
        categoryId: String,
        actorMemberId: String,
        actorName: String,
        isShared: Boolean
    ) {
        val contributions = goal.contributorSummary
            .split("|")
            .filter { it.contains(":") }
            .associate {
                val p = it.split(":")
                p[0] to (p.getOrNull(1)?.toLongOrNull() ?: 0L)
            }
            .toMutableMap()
        contributions[actorName] = (contributions[actorName] ?: 0L) + amount
        val newSummary = contributions.entries.joinToString("|") { "${it.key}:${it.value}" }
        val updatedGoal = goal.copy(
            currentAmount = goal.currentAmount + amount,
            contributorSummary = newSummary,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertGoal(updatedGoal)
        val tx = TransactionEntity(
            id = "tx_gol_${UUID.randomUUID().toString().take(8)}",
            workspaceId = goal.workspaceId,
            type = TxTypes.EXPENSE,
            amount = amount,
            accountId = sourceAccountId,
            categoryId = categoryId,
            title = "Setor Target: ${goal.title}",
            note = "Tabungan oleh $actorName",
            tags = "#tabungan,#goal",
            dateMillis = System.currentTimeMillis(),
            createdByMemberId = actorMemberId,
            createdByName = actorName,
            syncStatus = if (isShared) SyncStates.PENDING else SyncStates.OFFLINE_ONLY
        )
        recordTransaction(tx, emptyList(), isShared)
    }

    suspend fun deleteGoal(goalId: String) = dao.deleteGoalById(goalId)

    // --- SPLIT BILLS ---
    suspend fun upsertSplitBill(bill: SplitBillEntity, actorMemberId: String, actorName: String) {
        dao.upsertSplitBill(bill)
        addAuditLog(bill.workspaceId, actorMemberId, actorName, "Buat Split Bill", "${bill.title} • Rp ${bill.totalAmount}")
        queueIfShared(bill.workspaceId, "split_bills", bill.id, "UPSERT", JSONObject().put("title", bill.title))
    }

    suspend fun settleDebtTransfer(
        workspaceId: String,
        fromMember: WorkspaceMemberEntity,
        toMember: WorkspaceMemberEntity,
        amount: Long,
        accountId: String,
        categoryId: String,
        billsToMark: List<SplitBillEntity>,
        isShared: Boolean
    ) {
        // Mark participant shares from fromMember to toMember as settled
        billsToMark.forEach { bill ->
            if (bill.paidByMemberId == toMember.id) {
                val parts = bill.participantsData.split("|").map { entry ->
                    val seg = entry.split(":")
                    if (seg.size >= 3 && seg[0] == fromMember.id) {
                        "${seg[0]}:${seg[1]}:true"
                    } else {
                        entry
                    }
                }
                val allSettled = parts.all { it.split(":").getOrNull(2) == "true" }
                dao.upsertSplitBill(bill.copy(participantsData = parts.joinToString("|"), isSettled = allSettled))
            }
        }
        val tx = TransactionEntity(
            id = "tx_stl_${UUID.randomUUID().toString().take(8)}",
            workspaceId = workspaceId,
            type = TxTypes.TRANSFER,
            amount = amount,
            accountId = accountId,
            toAccountId = accountId,
            categoryId = categoryId,
            title = "Pelunasan Split Bill: ${fromMember.name} → ${toMember.name}",
            note = "Settlement otomatis grup",
            tags = "#splitbill,#lunas",
            dateMillis = System.currentTimeMillis(),
            createdByMemberId = fromMember.id,
            createdByName = fromMember.name,
            syncStatus = if (isShared) SyncStates.PENDING else SyncStates.OFFLINE_ONLY
        )
        dao.upsertTransaction(tx)
        addAuditLog(workspaceId, fromMember.id, fromMember.name, "Settle Split Bill", "${fromMember.name} lunasi Rp $amount ke ${toMember.name}")
        queueIfShared(workspaceId, "split_bills", tx.id, "UPSERT", JSONObject().put("settledAmount", amount))
    }

    suspend fun deleteSplitBill(billId: String) = dao.deleteSplitBillById(billId)

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
