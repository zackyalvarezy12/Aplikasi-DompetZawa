package com.example.data.repository

import com.example.data.local.*
import com.example.data.remote.CloudPullSnapshot
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

    private suspend fun enqueueForSync(
        workspaceId: String,
        tableName: String,
        recordId: String,
        operation: String,
        payload: JSONObject = JSONObject()
    ) {
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

    suspend fun addAuditLog(
        workspaceId: String,
        actorMemberId: String,
        actorName: String,
        action: String,
        detail: String
    ) {
        val log = AuditLogEntity(
            id = "log_${UUID.randomUUID().toString().take(12)}",
            workspaceId = workspaceId,
            actorMemberId = actorMemberId,
            actorName = actorName,
            action = action,
            detail = detail,
            createdAt = System.currentTimeMillis()
        )
        dao.insertAuditLog(log)
        enqueueForSync(
            workspaceId = workspaceId,
            tableName = "audit_logs",
            recordId = log.id,
            operation = "UPSERT",
            payload = JSONObject().put("action", action)
        )
    }

    // --- ONBOARDING & PROFILE ---
    suspend fun initializeCleanOnboarding(
        displayName: String,
        email: String = "",
        avatarColorHex: Long = 0xFF065F46,
        userId: String = "usr_owner"
    ): Triple<UserEntity, WorkspaceEntity, WorkspaceMemberEntity> {
        val (user, ws, member) = DatabaseSeeder.initializeCleanPersonalWorkspace(
            dao = dao,
            displayName = displayName,
            email = email,
            avatarColorHex = avatarColorHex,
            userId = userId
        )
        enqueueForSync(ws.id, "users", user.id, "UPSERT")
        enqueueForSync(ws.id, "workspaces", ws.id, "UPSERT")
        dao.getAllAccountsSnapshot().filter { it.workspaceId == ws.id }.forEach { acc ->
            enqueueForSync(ws.id, "accounts", acc.id, "UPSERT")
        }
        dao.getAllCategoriesSnapshot().filter { it.workspaceId == ws.id }.forEach { cat ->
            enqueueForSync(ws.id, "categories", cat.id, "UPSERT")
        }
        return Triple(user, ws, member)
    }

    suspend fun updateUserProfile(
        displayName: String,
        email: String,
        avatarColorHex: Long,
        activeWorkspaceId: String,
        actorMemberId: String
    ): UserEntity? {
        val existing = dao.getCurrentUserSnapshot() ?: return null
        val cleanName = displayName.trim().ifBlank { existing.name }
        val cleanEmail = email.trim().ifBlank { existing.email }
        val initials = DatabaseSeeder.computeInitials(cleanName)
        val updated = existing.copy(
            name = cleanName,
            email = cleanEmail,
            avatarInitials = initials,
            avatarColorHex = avatarColorHex
        )
        dao.upsertUser(updated)
        dao.updateMemberProfileByUserId(
            userId = updated.id,
            name = cleanName,
            email = cleanEmail,
            initials = initials,
            colorHex = avatarColorHex
        )
        enqueueForSync(activeWorkspaceId, "users", updated.id, "UPSERT")
        addAuditLog(
            workspaceId = activeWorkspaceId,
            actorMemberId = actorMemberId,
            actorName = cleanName,
            action = "Ubah Profil",
            detail = "Nama diperbarui menjadi $cleanName"
        )
        return updated
    }

    suspend fun resetAllLocalDataClean(
        displayName: String,
        email: String,
        avatarColorHex: Long
    ): Triple<UserEntity, WorkspaceEntity, WorkspaceMemberEntity> {
        dao.clearAllLocalData()
        return initializeCleanOnboarding(
            displayName = displayName,
            email = email,
            avatarColorHex = avatarColorHex
        )
    }

    suspend fun clearAllLocalData() {
        dao.clearAllLocalData()
    }

    // --- CLOUD MERGE ---
    suspend fun mergeCloudSnapshot(
        snapshot: CloudPullSnapshot,
        firebaseUid: String,
        googleEmail: String,
        googleDisplayName: String
    ): Pair<String, String>? {
        val now = System.currentTimeMillis()
        val existingUser = dao.getCurrentUserSnapshot()
        val resolvedName = snapshot.profile?.displayName?.takeIf { it.isNotBlank() }
            ?: existingUser?.name?.takeIf { it.isNotBlank() }
            ?: googleDisplayName.ifBlank { "Pengguna" }
        val resolvedEmail = snapshot.profile?.email?.takeIf { it.isNotBlank() }
            ?: googleEmail.ifBlank { existingUser?.email ?: "" }
        val resolvedColor = snapshot.profile?.avatarColorHex ?: existingUser?.avatarColorHex ?: 0xFF065F46
        val userId = existingUser?.id ?: "usr_owner"

        val mergedUser = UserEntity(
            id = userId,
            name = resolvedName,
            email = resolvedEmail,
            avatarInitials = DatabaseSeeder.computeInitials(resolvedName),
            avatarColorHex = resolvedColor,
            createdAt = existingUser?.createdAt ?: now
        )
        dao.upsertUser(mergedUser)

        if (snapshot.workspaces.isEmpty()) {
            // Ensure local workspace exists if cloud was empty
            val localWs = dao.getAllWorkspacesSnapshot()
            if (localWs.isEmpty()) {
                val (_, ws, mem) = initializeCleanOnboarding(resolvedName, resolvedEmail, resolvedColor, userId)
                return ws.id to mem.id
            }
            val firstWs = localWs.first()
            val members = dao.getMembersSnapshot(firstWs.id)
            val mem = members.firstOrNull { it.userId == userId } ?: members.firstOrNull()
            return firstWs.id to (mem?.id ?: "mem_owner")
        }

        dao.insertWorkspaces(snapshot.workspaces)
        snapshot.workspaces.forEach { ws ->
            val members = dao.getMembersSnapshot(ws.id)
            if (members.none { it.userId == userId }) {
                dao.upsertMember(
                    WorkspaceMemberEntity(
                        id = if (ws.id == "ws_personal") "mem_owner" else "mem_${ws.id.takeLast(6)}",
                        workspaceId = ws.id,
                        userId = userId,
                        name = resolvedName,
                        email = resolvedEmail,
                        avatarInitials = DatabaseSeeder.computeInitials(resolvedName),
                        colorHex = resolvedColor,
                        role = MemberRoles.OWNER,
                        joinedAt = now
                    )
                )
            }
            if (snapshot.categories.none { it.workspaceId == ws.id }) {
                val prefix = if (ws.id == "ws_personal") "p" else ws.id.takeLast(4)
                dao.insertCategories(DatabaseSeeder.defaultCategories(ws.id, prefix))
            }
        }
        if (snapshot.categories.isNotEmpty()) dao.insertCategories(snapshot.categories)
        if (snapshot.accounts.isNotEmpty()) dao.insertAccounts(snapshot.accounts)
        if (snapshot.transactions.isNotEmpty()) dao.insertTransactions(snapshot.transactions)
        if (snapshot.budgets.isNotEmpty()) dao.insertBudgets(snapshot.budgets)
        if (snapshot.recurringRules.isNotEmpty()) dao.insertRecurringRules(snapshot.recurringRules)
        if (snapshot.debtsLoans.isNotEmpty()) dao.insertDebtsLoans(snapshot.debtsLoans)
        if (snapshot.goals.isNotEmpty()) dao.insertGoals(snapshot.goals)
        if (snapshot.assets.isNotEmpty()) dao.insertAssets(snapshot.assets)
        if (snapshot.auditLogs.isNotEmpty()) dao.insertAuditLogs(snapshot.auditLogs)

        val primaryWs = snapshot.workspaces.first()
        val primaryMem = dao.getMembersSnapshot(primaryWs.id).firstOrNull { it.userId == userId }
            ?: dao.getMembersSnapshot(primaryWs.id).firstOrNull()
        return primaryWs.id to (primaryMem?.id ?: "mem_owner")
    }

    suspend fun mergeRemoteTransactions(remoteTxs: List<TransactionEntity>) {
        if (remoteTxs.isEmpty()) return
        val localById = dao.getAllTransactionsSnapshot().associateBy { it.id }
        val toUpsert = remoteTxs.filter { remote ->
            val local = localById[remote.id]
            local == null || remote.updatedAt >= local.updatedAt
        }
        if (toUpsert.isNotEmpty()) {
            dao.insertTransactions(toUpsert)
        }
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
        val defaultCats = DatabaseSeeder.defaultCategories(wsId, prefix)
        dao.insertCategories(defaultCats)
        val defaultAcc = AccountEntity(
            id = "acc_${prefix}_cash",
            workspaceId = wsId,
            name = "Kas Tunai",
            type = AccountTypes.CASH,
            balance = 0L,
            institutionName = "Tunai"
        )
        dao.upsertAccount(defaultAcc)
        enqueueForSync(wsId, "workspaces", wsId, "UPSERT")
        enqueueForSync(wsId, "accounts", defaultAcc.id, "UPSERT")
        defaultCats.forEach { cat -> enqueueForSync(wsId, "categories", cat.id, "UPSERT") }
        addAuditLog(wsId, ownerMember.id, ownerMember.name, "Buat Ruang Keuangan", name)
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
            addAuditLog(existing.id, member.id, member.name, "Gabung Ruang Keuangan", "Kode $code")
            return existing
        }
        if (code.length in 6..8 && code.all { it.isLetterOrDigit() }) {
            val wsId = "ws_join_${code.lowercase()}"
            val joinedWs = WorkspaceEntity(
                id = wsId,
                name = "Grup Bersama ($code)",
                type = WorkspaceTypes.SHARED,
                inviteCode = code,
                inviteLink = "https://nusakas.app/join/$code",
                createdByUserId = user.id
            )
            dao.upsertWorkspace(joinedWs)
            val prefix = code.lowercase()
            val cats = DatabaseSeeder.defaultCategories(wsId, prefix)
            dao.insertCategories(cats)
            val acc = AccountEntity("acc_${prefix}_kas", wsId, "Kas Grup Bersama", AccountTypes.BANK, 0L, institutionName = "Bank")
            dao.upsertAccount(acc)
            val me = WorkspaceMemberEntity("mem_${prefix}_me", wsId, user.id, user.name, user.email, user.avatarInitials, user.avatarColorHex, MemberRoles.OWNER)
            dao.upsertMember(me)
            enqueueForSync(wsId, "workspaces", wsId, "UPSERT")
            enqueueForSync(wsId, "accounts", acc.id, "UPSERT")
            cats.forEach { enqueueForSync(wsId, "categories", it.id, "UPSERT") }
            addAuditLog(wsId, me.id, me.name, "Gabung via Undangan", "Kode $code")
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
        val initials = DatabaseSeeder.computeInitials(name)
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
        addAuditLog(workspaceId, actorMemberId, actorName, "Tambah Anggota", "${member.name} (${com.example.domain.CurrencyFormatter.memberRoleLabel(role)})")
    }

    suspend fun updateMemberRole(workspaceId: String, memberId: String, role: String, actorMemberId: String, actorName: String) {
        dao.updateMemberRole(memberId, role)
        addAuditLog(workspaceId, actorMemberId, actorName, "Ubah Peran Anggota", "Peran → ${com.example.domain.CurrencyFormatter.memberRoleLabel(role)}")
    }

    suspend fun deleteWorkspace(workspaceId: String) {
        dao.deleteWorkspaceById(workspaceId)
        enqueueForSync(workspaceId, "workspaces", workspaceId, "DELETE")
    }

    // --- CATEGORIES ---
    suspend fun upsertCategory(category: CategoryEntity, actorMemberId: String, actorName: String) {
        dao.upsertCategory(category)
        addAuditLog(category.workspaceId, actorMemberId, actorName, "Kelola Kategori", "${category.name} (${category.type})")
        enqueueForSync(category.workspaceId, "categories", category.id, "UPSERT", JSONObject().put("name", category.name))
    }

    suspend fun deleteCategory(category: CategoryEntity, actorMemberId: String, actorName: String) {
        dao.deleteCategoryById(category.id)
        addAuditLog(category.workspaceId, actorMemberId, actorName, "Hapus Kategori", category.name)
        enqueueForSync(category.workspaceId, "categories", category.id, "DELETE")
    }

    // --- TRANSACTIONS & BALANCES ---
    suspend fun recordTransaction(
        tx: TransactionEntity,
        splitItems: List<TransactionItemEntity> = emptyList(),
        isSharedWorkspace: Boolean
    ) {
        val status = if (isSharedWorkspace) SyncStates.PENDING else SyncStates.PENDING
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
            "Catat ${com.example.domain.CurrencyFormatter.txTypeLabel(finalTx.type)}",
            "${finalTx.title} • Rp ${finalTx.amount}"
        )
        enqueueForSync(
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

    suspend fun updateTransaction(
        oldTx: TransactionEntity,
        newTx: TransactionEntity,
        newSplitItems: List<TransactionItemEntity> = emptyList(),
        actorMemberId: String,
        actorName: String
    ) {
        // 1. Reverse previous balance effect
        applyBalanceEffect(oldTx, multiplier = -1)
        // 2. Replace transaction & split items
        val updatedTx = newTx.copy(
            id = oldTx.id,
            workspaceId = oldTx.workspaceId,
            createdByMemberId = oldTx.createdByMemberId,
            createdByName = oldTx.createdByName,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStates.PENDING
        )
        dao.upsertTransaction(updatedTx)
        dao.deleteTransactionItems(oldTx.id)
        if (newSplitItems.isNotEmpty()) {
            dao.insertTransactionItems(newSplitItems)
        }
        // 3. Apply new balance effect
        applyBalanceEffect(updatedTx, multiplier = 1)
        addAuditLog(
            updatedTx.workspaceId,
            actorMemberId,
            actorName,
            "Ubah ${com.example.domain.CurrencyFormatter.txTypeLabel(updatedTx.type)}",
            "${updatedTx.title} • Rp ${updatedTx.amount}"
        )
        enqueueForSync(
            updatedTx.workspaceId,
            "transactions",
            updatedTx.id,
            "UPSERT",
            JSONObject()
                .put("title", updatedTx.title)
                .put("amount", updatedTx.amount)
                .put("updatedAt", updatedTx.updatedAt)
        )
    }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        applyBalanceEffect(tx, multiplier = -1)
        dao.deleteTransactionItems(tx.id)
        dao.deleteTransactionById(tx.id)
        addAuditLog(tx.workspaceId, tx.createdByMemberId, tx.createdByName, "Hapus Transaksi", tx.title)
        enqueueForSync(tx.workspaceId, "transactions", tx.id, "DELETE", JSONObject().put("id", tx.id))
    }

    private suspend fun applyBalanceEffect(tx: TransactionEntity, multiplier: Int) {
        val account = dao.getAccountById(tx.accountId) ?: return
        val isCreditCard = account.type == AccountTypes.CREDIT_CARD
        when (tx.type) {
            TxTypes.INCOME, TxTypes.REFUND -> {
                val delta = if (isCreditCard) -tx.amount * multiplier else tx.amount * multiplier
                dao.adjustAccountBalance(account.id, delta)
            }
            TxTypes.EXPENSE, TxTypes.SPLIT -> {
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
        enqueueForSync(account.workspaceId, "accounts", account.id, "UPSERT")
    }

    // --- ACCOUNTS & RECONCILIATION ---
    suspend fun upsertAccount(account: AccountEntity, actorMemberId: String, actorName: String) {
        val updated = account.copy(updatedAt = System.currentTimeMillis())
        dao.upsertAccount(updated)
        addAuditLog(updated.workspaceId, actorMemberId, actorName, "Kelola Akun", "${updated.name} • Rp ${updated.balance}")
        enqueueForSync(updated.workspaceId, "accounts", updated.id, "UPSERT", JSONObject().put("name", updated.name).put("balance", updated.balance))
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
        val fallbackCat = dao.getAllCategoriesSnapshot()
            .firstOrNull { it.workspaceId == account.workspaceId && it.type == if (txType == TxTypes.INCOME || txType == TxTypes.REFUND) TxTypes.INCOME else TxTypes.EXPENSE }
            ?.id ?: "cat_${catPrefix}_${if (txType == TxTypes.INCOME) "gaji" else "belanja"}"

        val tx = TransactionEntity(
            id = "tx_rec_${UUID.randomUUID().toString().take(8)}",
            workspaceId = account.workspaceId,
            type = txType,
            amount = kotlin.math.abs(diff),
            accountId = account.id,
            categoryId = fallbackCat,
            title = "Penyesuaian Saldo: ${account.name}",
            note = "Penyesuaian selisih saldo otomatis",
            tags = "#penyesuaiansaldo",
            dateMillis = System.currentTimeMillis(),
            createdByMemberId = actorMemberId,
            createdByName = actorName,
            syncStatus = if (isShared) SyncStates.PENDING else SyncStates.PENDING
        )
        dao.upsertTransaction(tx)
        addAuditLog(account.workspaceId, actorMemberId, actorName, "Penyesuaian Saldo", "${account.name}: selisih Rp $diff")
        enqueueForSync(account.workspaceId, "accounts", account.id, "UPSERT", JSONObject().put("balance", actualBalance))
        enqueueForSync(account.workspaceId, "transactions", tx.id, "UPSERT")
    }

    suspend fun deleteAccount(account: AccountEntity, actorMemberId: String, actorName: String) {
        dao.deleteAccountById(account.id)
        addAuditLog(account.workspaceId, actorMemberId, actorName, "Hapus Akun", account.name)
        enqueueForSync(account.workspaceId, "accounts", account.id, "DELETE")
    }

    // --- ASSETS ---
    suspend fun upsertAsset(asset: AssetInvestmentEntity, actorMemberId: String, actorName: String) {
        val updated = asset.copy(updatedAt = System.currentTimeMillis())
        dao.upsertAsset(updated)
        addAuditLog(updated.workspaceId, actorMemberId, actorName, "Update Aset", "${updated.name} • Rp ${updated.currentValue}")
        enqueueForSync(updated.workspaceId, "assets_investments", updated.id, "UPSERT", JSONObject().put("name", updated.name).put("value", updated.currentValue))
    }

    suspend fun deleteAsset(asset: AssetInvestmentEntity, actorMemberId: String, actorName: String) {
        dao.deleteAssetById(asset.id)
        addAuditLog(asset.workspaceId, actorMemberId, actorName, "Hapus Aset", asset.name)
        enqueueForSync(asset.workspaceId, "assets_investments", asset.id, "DELETE")
    }

    // --- BUDGETS ---
    suspend fun upsertBudget(budget: BudgetEntity, actorMemberId: String, actorName: String) {
        val updated = budget.copy(updatedAt = System.currentTimeMillis())
        dao.upsertBudget(updated)
        addAuditLog(updated.workspaceId, actorMemberId, actorName, "Atur Anggaran", "Rp ${updated.limitAmount}")
        enqueueForSync(updated.workspaceId, "budgets", updated.id, "UPSERT", JSONObject().put("limit", updated.limitAmount))
    }

    suspend fun deleteBudget(budget: BudgetEntity, actorMemberId: String, actorName: String) {
        dao.deleteBudgetById(budget.id)
        addAuditLog(budget.workspaceId, actorMemberId, actorName, "Hapus Anggaran", budget.monthYear)
        enqueueForSync(budget.workspaceId, "budgets", budget.id, "DELETE")
    }

    // --- RECURRING RULES ---
    suspend fun upsertRecurringRule(rule: RecurringRuleEntity, actorMemberId: String = "mem_owner", actorName: String = "Pengguna") {
        val updated = rule.copy(updatedAt = System.currentTimeMillis())
        dao.upsertRecurringRule(updated)
        addAuditLog(updated.workspaceId, actorMemberId, actorName, "Kelola Tagihan", "${updated.title} • Rp ${updated.amount}")
        enqueueForSync(updated.workspaceId, "recurring_rules", updated.id, "UPSERT", JSONObject().put("title", updated.title))
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
            syncStatus = SyncStates.PENDING
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
        val updatedRule = rule.copy(
            nextDueDateMillis = cal.timeInMillis,
            lastPaidDateMillis = now,
            updatedAt = now
        )
        dao.upsertRecurringRule(updatedRule)
        enqueueForSync(updatedRule.workspaceId, "recurring_rules", updatedRule.id, "UPSERT")
    }

    suspend fun deleteRecurringRule(rule: RecurringRuleEntity, actorMemberId: String, actorName: String) {
        dao.deleteRecurringRuleById(rule.id)
        addAuditLog(rule.workspaceId, actorMemberId, actorName, "Hapus Tagihan", rule.title)
        enqueueForSync(rule.workspaceId, "recurring_rules", rule.id, "DELETE")
    }

    // --- DEBTS & LOANS ---
    suspend fun upsertDebtLoan(item: DebtLoanEntity, actorMemberId: String, actorName: String) {
        val updated = item.copy(updatedAt = System.currentTimeMillis())
        dao.upsertDebtLoan(updated)
        addAuditLog(updated.workspaceId, actorMemberId, actorName, "Catat ${com.example.domain.CurrencyFormatter.debtKindLabel(updated.kind)}", "${updated.title} • Rp ${updated.remainingAmount}")
        enqueueForSync(updated.workspaceId, "debts_loans", updated.id, "UPSERT", JSONObject().put("remaining", updated.remainingAmount))
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
        enqueueForSync(updated.workspaceId, "debts_loans", updated.id, "UPSERT")
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
            syncStatus = SyncStates.PENDING
        )
        recordTransaction(tx, emptyList(), isShared)
    }

    suspend fun deleteDebtLoan(item: DebtLoanEntity, actorMemberId: String, actorName: String) {
        dao.deleteDebtLoanById(item.id)
        addAuditLog(item.workspaceId, actorMemberId, actorName, "Hapus ${com.example.domain.CurrencyFormatter.debtKindLabel(item.kind)}", item.title)
        enqueueForSync(item.workspaceId, "debts_loans", item.id, "DELETE")
    }

    // --- GOALS ---
    suspend fun upsertGoal(goal: GoalEntity, actorMemberId: String, actorName: String) {
        val updated = goal.copy(updatedAt = System.currentTimeMillis())
        dao.upsertGoal(updated)
        addAuditLog(updated.workspaceId, actorMemberId, actorName, "Target Tabungan", updated.title)
        enqueueForSync(updated.workspaceId, "goals", updated.id, "UPSERT", JSONObject().put("current", updated.currentAmount))
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
        enqueueForSync(updatedGoal.workspaceId, "goals", updatedGoal.id, "UPSERT")
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
            syncStatus = SyncStates.PENDING
        )
        recordTransaction(tx, emptyList(), isShared)
    }

    suspend fun deleteGoal(goal: GoalEntity, actorMemberId: String, actorName: String) {
        dao.deleteGoalById(goal.id)
        addAuditLog(goal.workspaceId, actorMemberId, actorName, "Hapus Target", goal.title)
        enqueueForSync(goal.workspaceId, "goals", goal.id, "DELETE")
    }

    // --- SPLIT BILLS ---
    suspend fun upsertSplitBill(bill: SplitBillEntity, actorMemberId: String, actorName: String) {
        dao.upsertSplitBill(bill)
        addAuditLog(bill.workspaceId, actorMemberId, actorName, "Buat Patungan", "${bill.title} • Rp ${bill.totalAmount}")
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
            title = "Pelunasan Patungan: ${fromMember.name} → ${toMember.name}",
            note = "Pelunasan otomatis patungan bersama",
            tags = "#patungan,#lunas",
            dateMillis = System.currentTimeMillis(),
            createdByMemberId = fromMember.id,
            createdByName = fromMember.name,
            syncStatus = SyncStates.PENDING
        )
        dao.upsertTransaction(tx)
        addAuditLog(workspaceId, fromMember.id, fromMember.name, "Lunasi Patungan", "${fromMember.name} lunasi Rp $amount ke ${toMember.name}")
        enqueueForSync(workspaceId, "transactions", tx.id, "UPSERT")
    }

    suspend fun deleteSplitBill(bill: SplitBillEntity, actorMemberId: String, actorName: String) {
        dao.deleteSplitBillById(bill.id)
        addAuditLog(bill.workspaceId, actorMemberId, actorName, "Hapus Patungan", bill.title)
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
