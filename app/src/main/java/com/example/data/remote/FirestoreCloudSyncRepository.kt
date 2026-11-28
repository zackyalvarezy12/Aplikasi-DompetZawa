package com.example.data.remote

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.data.local.*
import com.example.data.prefs.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Keep
@IgnoreExtraProperties
data class FsUserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarInitials: String = "DZ",
    val avatarColorHex: Long = 0xFF065F46,
    val themePreset: String = "CUTE_PASTEL",
    val biometricEnabled: Boolean = false,
    val autoSalaryEnabled: Boolean = false,
    val monthlyBaseSalary: Long = 0L,
    val salaryPaydayDay: Int = 17,
    val salaryWorkingDays: Int = 22,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsWorkspace(
    val userId: String = "",
    val name: String = "",
    val type: String = WorkspaceTypes.PERSONAL,
    val inviteCode: String = "",
    val currency: String = "IDR",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsAccount(
    val userId: String = "",
    val workspaceId: String = "",
    val name: String = "",
    val type: String = AccountTypes.CASH,
    val balance: Long = 0L,
    val creditLimit: Long = 0L,
    val billingDay: Int = 1,
    val dueDay: Int = 15,
    val institutionName: String = "",
    val colorHex: Long = 0xFF059669,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsCategory(
    val userId: String = "",
    val workspaceId: String = "",
    val name: String = "",
    val type: String = TxTypes.EXPENSE,
    val iconKey: String = "category",
    val colorHex: Long = 0xFF059669,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsTransaction(
    val userId: String = "",
    val workspaceId: String = "",
    val type: String = TxTypes.EXPENSE,
    val amount: Long = 0L,
    val accountId: String = "",
    val toAccountId: String? = null,
    val adminFee: Long = 0L,
    val categoryId: String = "",
    val title: String = "",
    val note: String = "",
    val tags: String = "",
    val dateMillis: Long = 0L,
    val createdByMemberId: String = "",
    val createdByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsBudget(
    val userId: String = "",
    val workspaceId: String = "",
    val categoryId: String = "",
    val monthYear: String = "",
    val limitAmount: Long = 0L,
    val rolloverAmount: Long = 0L,
    val rolloverEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsRecurringRule(
    val userId: String = "",
    val workspaceId: String = "",
    val title: String = "",
    val amount: Long = 0L,
    val type: String = TxTypes.EXPENSE,
    val accountId: String = "",
    val categoryId: String = "",
    val frequency: String = "MONTHLY",
    val nextDueDateMillis: Long = 0L,
    val active: Boolean = true,
    val reminderEnabled: Boolean = true,
    val lastPaidDateMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsDebtLoan(
    val userId: String = "",
    val workspaceId: String = "",
    val kind: String = "DEBT",
    val title: String = "",
    val counterparty: String = "",
    val principalAmount: Long = 0L,
    val remainingAmount: Long = 0L,
    val interestRateAnnual: Double = 0.0,
    val minimumMonthlyPayment: Long = 0L,
    val dueDateMillis: Long = 0L,
    val paidOff: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsGoal(
    val userId: String = "",
    val workspaceId: String = "",
    val title: String = "",
    val targetAmount: Long = 0L,
    val currentAmount: Long = 0L,
    val targetDateMillis: Long = 0L,
    val monthlyContribution: Long = 0L,
    val iconKey: String = "savings",
    val contributorSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsAsset(
    val userId: String = "",
    val workspaceId: String = "",
    val name: String = "",
    val assetClass: String = "GOLD",
    val quantity: Double = 1.0,
    val unitLabel: String = "unit",
    val unitPrice: Long = 0L,
    val currentValue: Long = 0L,
    val purchaseValue: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsAuditLog(
    val userId: String = "",
    val workspaceId: String = "",
    val actorMemberId: String = "",
    val actorName: String = "",
    val action: String = "",
    val detail: String = "",
    val eventTimeMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class CloudPullSnapshot(
    val profile: FsUserProfile?,
    val workspaces: List<WorkspaceEntity>,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val recurringRules: List<RecurringRuleEntity>,
    val debtsLoans: List<DebtLoanEntity>,
    val goals: List<GoalEntity>,
    val assets: List<AssetInvestmentEntity>,
    val auditLogs: List<AuditLogEntity>
)

class FirestoreCloudSyncRepository(private val context: Context) {

    private val firestore: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }
    }

    private fun requireUserId(): String =
        Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna belum masuk ke akun Google.")

    fun currentAuthenticatedUid(): String? = Firebase.auth.currentUser?.uid

    private suspend fun upsertPreservingCreatedAt(
        docRef: DocumentReference,
        createFields: Map<String, Any?>,
        updateFields: Map<String, Any?>
    ) {
        val snap = docRef.get().await()
        if (snap.exists()) {
            val cleanedUpdate = updateFields.filterValues { it != null }.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            docRef.update(cleanedUpdate).await()
        } else {
            val cleanedCreate = createFields.filterValues { it != null }.toMutableMap().apply {
                this["createdAt"] = FieldValue.serverTimestamp()
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(cleanedCreate, SetOptions.merge()).await()
        }
    }

    suspend fun syncUserProfile(
        user: UserEntity,
        settings: UserSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid"
        try {
            val mutable = mapOf<String, Any?>(
                "displayName" to user.name.trim().ifBlank { "Pengguna" }.take(80),
                "email" to (settings.googleEmail.ifBlank { user.email }).take(160),
                "avatarInitials" to user.avatarInitials.ifBlank { "DZ" }.take(4),
                "avatarColorHex" to user.avatarColorHex,
                "themePreset" to settings.themePreset.take(40),
                "biometricEnabled" to settings.isBiometricEnabled,
                "autoSalaryEnabled" to settings.isAutoSalaryEnabled,
                "monthlyBaseSalary" to settings.monthlyBaseSalary.coerceAtLeast(0L),
                "salaryPaydayDay" to settings.salaryPaydayDay.coerceIn(1, 31),
                "salaryWorkingDays" to settings.salaryWorkingDays.coerceIn(1, 31)
            )
            val create = mutable + ("userId" to uid)
            upsertPreservingCreatedAt(firestore.document(path), create, mutable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncWorkspace(ws: WorkspaceEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/workspaces/${ws.id}"
        try {
            val mutable = mapOf<String, Any?>(
                "name" to ws.name.trim().ifBlank { "Keuangan Pribadi" }.take(80),
                "type" to if (ws.type == WorkspaceTypes.SHARED) WorkspaceTypes.SHARED else WorkspaceTypes.PERSONAL,
                "inviteCode" to ws.inviteCode.take(16),
                "currency" to ws.currency.ifBlank { "IDR" }.take(8)
            )
            upsertPreservingCreatedAt(firestore.document(path), mutable + ("userId" to uid), mutable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncAccount(acc: AccountEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/accounts/${acc.id}"
        try {
            val mutable = mapOf<String, Any?>(
                "workspaceId" to acc.workspaceId,
                "name" to acc.name.trim().ifBlank { "Dompet" }.take(80),
                "type" to acc.type,
                "balance" to acc.balance,
                "creditLimit" to acc.creditLimit.coerceAtLeast(0L),
                "billingDay" to acc.billingDay.coerceIn(1, 31),
                "dueDay" to acc.dueDay.coerceIn(1, 31),
                "institutionName" to acc.institutionName.take(80),
                "colorHex" to acc.colorHex
            )
            upsertPreservingCreatedAt(firestore.document(path), mutable + ("userId" to uid), mutable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncCategory(cat: CategoryEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/categories/${cat.id}"
        try {
            val mutable = mapOf<String, Any?>(
                "workspaceId" to cat.workspaceId,
                "name" to cat.name.trim().ifBlank { "Kategori" }.take(80),
                "type" to cat.type,
                "iconKey" to cat.iconKey.ifBlank { "category" }.take(40),
                "colorHex" to cat.colorHex
            )
            upsertPreservingCreatedAt(firestore.document(path), mutable + ("userId" to uid), mutable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncTransaction(tx: TransactionEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/transactions/${tx.id}"
        try {
            val mutable = mapOf<String, Any?>(
                "workspaceId" to tx.workspaceId,
                "type" to tx.type,
                "amount" to tx.amount.coerceAtLeast(0L),
                "accountId" to tx.accountId,
                "toAccountId" to tx.toAccountId?.takeIf { it.isNotBlank() }?.take(128),
                "adminFee" to tx.adminFee.coerceAtLeast(0L),
                "categoryId" to tx.categoryId,
                "title" to tx.title.trim().ifBlank { "Transaksi" }.take(160),
                "note" to tx.note.take(500),
                "tags" to tx.tags.take(200),
                "dateMillis" to tx.dateMillis.coerceAtLeast(0L),
                "createdByMemberId" to tx.createdByMemberId.ifBlank { "mem_owner" }.take(128),
                "createdByName" to tx.createdByName.ifBlank { "Pengguna" }.take(80)
            )
            upsertPreservingCreatedAt(firestore.document(path), mutable + ("userId" to uid), mutable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncBudget(b: BudgetEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/budgets/${b.id}"
        try {
            val mutable = mapOf<String, Any?>(
                "workspaceId" to b.workspaceId,
                "categoryId" to b.categoryId,
                "monthYear" to b.monthYear.take(12),
                "limitAmount" to b.limitAmount.coerceAtLeast(0L),
                "rolloverAmount" to b.rolloverAmount.coerceAtLeast(0L),
                "rolloverEnabled" to b.rolloverEnabled
            )
            upsertPreservingCreatedAt(firestore.document(path), mutable + ("userId" to uid), mutable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncRecurringRule(r: RecurringRuleEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/recurring_rules/${r.id}"
        try {
            val mutable = mapOf<String, Any?>(
                "workspaceId" to r.workspaceId,
                "title" to r.title.trim().ifBlank { "Tagihan" }.take(120),
                "amount" to r.amount.coerceAtLeast(0L),
                "type" to if (r.type == TxTypes.INCOME) TxTypes.INCOME else TxTypes.EXPENSE,
                "accountId" to r.accountId,
                "categoryId" to r.categoryId,
                "frequency" to r.frequency,
                "nextDueDateMillis" to r.nextDueDateMillis.coerceAtLeast(0L),
                "active" to r.isActive,
                "reminderEnabled" to r.reminderEnabled,
                "lastPaidDateMillis" to r.lastPaidDateMillis?.coerceAtLeast(0L)
            )
            upsertPreservingCreatedAt(firestore.document(path), mutable + ("userId" to uid), mutable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncDebtLoan(d: DebtLoanEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/debts_loans/${d.id}"
        try {
            val mutable = mapOf<String, Any?>(
                "workspaceId" to d.workspaceId,
                "kind" to if (d.kind == "LOAN") "LOAN" else "DEBT",
                "title" to d.title.trim().ifBlank { "Catatan Utang" }.take(120),
                "counterparty" to d.counterparty.take(120),
                "principalAmount" to d.principalAmount.coerceAtLeast(0L),
                "remainingAmount" to d.remainingAmount.coerceAtLeast(0L),
                "interestRateAnnual" to d.interestRateAnnual.coerceAtLeast(0.0),
                "minimumMonthlyPayment" to d.minimumMonthlyPayment.coerceAtLeast(0L),
                "dueDateMillis" to d.dueDateMillis.coerceAtLeast(0L),
                "paidOff" to d.isPaidOff
            )
            upsertPreservingCreatedAt(firestore.document(path), mutable + ("userId" to uid), mutable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncGoal(g: GoalEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/goals/${g.id}"
        try {
            val mutable = mapOf<String, Any?>(
                "workspaceId" to g.workspaceId,
                "title" to g.title.trim().ifBlank { "Target Tabungan" }.take(120),
                "targetAmount" to g.targetAmount.coerceAtLeast(0L),
                "currentAmount" to g.currentAmount.coerceAtLeast(0L),
                "targetDateMillis" to g.targetDateMillis.coerceAtLeast(0L),
                "monthlyContribution" to g.monthlyContribution.coerceAtLeast(0L),
                "iconKey" to g.iconKey.ifBlank { "savings" }.take(40),
                "contributorSummary" to g.contributorSummary.take(500)
            )
            upsertPreservingCreatedAt(firestore.document(path), mutable + ("userId" to uid), mutable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncAsset(a: AssetInvestmentEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/assets/${a.id}"
        try {
            val mutable = mapOf<String, Any?>(
                "workspaceId" to a.workspaceId,
                "name" to a.name.trim().ifBlank { "Aset" }.take(120),
                "assetClass" to a.assetClass.ifBlank { "GOLD" }.take(40),
                "quantity" to a.quantity.coerceAtLeast(0.0),
                "unitLabel" to a.unitLabel.take(24),
                "unitPrice" to a.unitPrice.coerceAtLeast(0L),
                "currentValue" to a.currentValue.coerceAtLeast(0L),
                "purchaseValue" to a.purchaseValue.coerceAtLeast(0L)
            )
            upsertPreservingCreatedAt(firestore.document(path), mutable + ("userId" to uid), mutable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncAuditLog(log: AuditLogEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/audit_logs/${log.id}"
        try {
            val docRef = firestore.document(path)
            if (!docRef.get().await().exists()) {
                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "workspaceId" to log.workspaceId,
                    "actorMemberId" to log.actorMemberId.ifBlank { "mem_owner" }.take(128),
                    "actorName" to log.actorName.ifBlank { "Pengguna" }.take(80),
                    "action" to log.action.ifBlank { "Aktivitas" }.take(80),
                    "detail" to log.detail.take(300),
                    "eventTimeMillis" to log.createdAt.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.CREATE, path))
        }
    }

    suspend fun deleteDocument(subcollection: String, recordId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val path = "users/$uid/$subcollection/$recordId"
        try {
            firestore.document(path).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.DELETE, path))
        }
    }

    /**
     * Pushes a pending SyncQueueEntity to the corresponding Firestore subcollection.
     */
    suspend fun pushQueueItem(item: SyncQueueEntity, dao: NusaKasDao, settings: UserSettings): Result<Unit> {
        if (item.operation == "DELETE") {
            val subcollection = mapTableToSubcollection(item.tableName) ?: return Result.success(Unit)
            return deleteDocument(subcollection, item.recordId)
        }
        return when (item.tableName) {
            "users" -> {
                val u = dao.getCurrentUserSnapshot() ?: return Result.success(Unit)
                syncUserProfile(u, settings)
            }
            "workspaces" -> {
                val w = dao.getWorkspaceById(item.recordId) ?: return Result.success(Unit)
                syncWorkspace(w)
            }
            "accounts" -> {
                val a = dao.getAccountById(item.recordId) ?: return Result.success(Unit)
                syncAccount(a)
            }
            "categories" -> {
                val c = dao.getCategoryById(item.recordId) ?: return Result.success(Unit)
                syncCategory(c)
            }
            "transactions" -> {
                val t = dao.getTransactionById(item.recordId) ?: return Result.success(Unit)
                syncTransaction(t)
            }
            "budgets" -> {
                val b = dao.getBudgetById(item.recordId) ?: return Result.success(Unit)
                syncBudget(b)
            }
            "recurring_rules" -> {
                val r = dao.getRecurringRuleById(item.recordId) ?: return Result.success(Unit)
                syncRecurringRule(r)
            }
            "debts_loans" -> {
                val d = dao.getDebtLoanById(item.recordId) ?: return Result.success(Unit)
                syncDebtLoan(d)
            }
            "goals" -> {
                val g = dao.getGoalById(item.recordId) ?: return Result.success(Unit)
                syncGoal(g)
            }
            "assets_investments", "assets" -> {
                val a = dao.getAssetById(item.recordId) ?: return Result.success(Unit)
                syncAsset(a)
            }
            "audit_logs" -> {
                val l = dao.getAuditLogById(item.recordId) ?: return Result.success(Unit)
                syncAuditLog(l)
            }
            else -> Result.success(Unit)
        }
    }

    /**
     * Full push of local Room snapshot to the authenticated user's Firestore tree.
     */
    suspend fun pushFullSnapshot(dao: NusaKasDao, settings: UserSettings): Result<Int> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        try {
            var syncedCount = 0
            dao.getCurrentUserSnapshot()?.let { user ->
                syncUserProfile(user, settings).getOrThrow()
                syncedCount++
            }
            dao.getAllWorkspacesSnapshot().forEach { ws ->
                syncWorkspace(ws).getOrThrow(); syncedCount++
            }
            dao.getAllAccountsSnapshot().forEach { acc ->
                syncAccount(acc).getOrThrow(); syncedCount++
            }
            dao.getAllCategoriesSnapshot().forEach { cat ->
                syncCategory(cat).getOrThrow(); syncedCount++
            }
            dao.getAllTransactionsSnapshot().forEach { tx ->
                syncTransaction(tx).getOrThrow(); syncedCount++
            }
            dao.getAllBudgetsSnapshot().forEach { b ->
                syncBudget(b).getOrThrow(); syncedCount++
            }
            dao.getAllRecurringRulesSnapshot().forEach { r ->
                syncRecurringRule(r).getOrThrow(); syncedCount++
            }
            dao.getAllDebtsLoansSnapshot().forEach { d ->
                syncDebtLoan(d).getOrThrow(); syncedCount++
            }
            dao.getAllGoalsSnapshot().forEach { g ->
                syncGoal(g).getOrThrow(); syncedCount++
            }
            dao.getAllAssetsSnapshot().forEach { a ->
                syncAsset(a).getOrThrow(); syncedCount++
            }
            dao.getAllAuditLogsSnapshot().take(50).forEach { log ->
                syncAuditLog(log).getOrThrow(); syncedCount++
            }
            Result.success(syncedCount)
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, "users/$uid"))
        }
    }

    /**
     * Pulls all user-owned collections from Firestore for cross-device restore/merge.
     */
    suspend fun pullUserSnapshot(): Result<CloudPullSnapshot> = withContext(Dispatchers.IO) {
        val uid = runCatching { requireUserId() }.getOrElse { return@withContext Result.failure(it) }
        val userDocPath = "users/$uid"
        try {
            val profileSnap = firestore.document(userDocPath).get().await()
            val profile = profileSnap.toObject(FsUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            val wsDocs = firestore.collection("$userDocPath/workspaces").get().await()
            val workspaces = wsDocs.documents.mapNotNull { doc ->
                val fs = doc.toObject(FsWorkspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                WorkspaceEntity(
                    id = doc.id,
                    name = fs.name,
                    type = fs.type,
                    inviteCode = fs.inviteCode,
                    inviteLink = "https://nusakas.app/join/${fs.inviteCode}",
                    currency = fs.currency,
                    createdByUserId = uid,
                    updatedAt = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }

            val accDocs = firestore.collection("$userDocPath/accounts").get().await()
            val accounts = accDocs.documents.mapNotNull { doc ->
                val fs = doc.toObject(FsAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                AccountEntity(
                    id = doc.id,
                    workspaceId = fs.workspaceId,
                    name = fs.name,
                    type = fs.type,
                    balance = fs.balance,
                    creditLimit = fs.creditLimit,
                    billingDay = fs.billingDay,
                    dueDay = fs.dueDay,
                    institutionName = fs.institutionName,
                    colorHex = fs.colorHex,
                    updatedAt = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }

            val catDocs = firestore.collection("$userDocPath/categories").get().await()
            val categories = catDocs.documents.mapNotNull { doc ->
                val fs = doc.toObject(FsCategory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                CategoryEntity(
                    id = doc.id,
                    workspaceId = fs.workspaceId,
                    name = fs.name,
                    parentId = null,
                    type = fs.type,
                    iconKey = fs.iconKey,
                    colorHex = fs.colorHex
                )
            }

            val txDocs = firestore.collection("$userDocPath/transactions").get().await()
            val transactions = txDocs.documents.mapNotNull { doc ->
                val fs = doc.toObject(FsTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                TransactionEntity(
                    id = doc.id,
                    workspaceId = fs.workspaceId,
                    type = fs.type,
                    amount = fs.amount,
                    accountId = fs.accountId,
                    toAccountId = fs.toAccountId,
                    adminFee = fs.adminFee,
                    categoryId = fs.categoryId,
                    title = fs.title,
                    note = fs.note,
                    tags = fs.tags,
                    dateMillis = fs.dateMillis,
                    receiptImagePath = null,
                    createdByMemberId = fs.createdByMemberId,
                    createdByName = fs.createdByName,
                    updatedAt = fs.updatedAt?.toDate()?.time ?: fs.dateMillis,
                    syncStatus = SyncStates.SYNCED
                )
            }

            val bdgDocs = firestore.collection("$userDocPath/budgets").get().await()
            val budgets = bdgDocs.documents.mapNotNull { doc ->
                val fs = doc.toObject(FsBudget::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                BudgetEntity(
                    id = doc.id,
                    workspaceId = fs.workspaceId,
                    categoryId = fs.categoryId,
                    monthYear = fs.monthYear,
                    limitAmount = fs.limitAmount,
                    rolloverAmount = fs.rolloverAmount,
                    rolloverEnabled = fs.rolloverEnabled,
                    updatedAt = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }

            val recDocs = firestore.collection("$userDocPath/recurring_rules").get().await()
            val rules = recDocs.documents.mapNotNull { doc ->
                val fs = doc.toObject(FsRecurringRule::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                RecurringRuleEntity(
                    id = doc.id,
                    workspaceId = fs.workspaceId,
                    title = fs.title,
                    amount = fs.amount,
                    type = fs.type,
                    accountId = fs.accountId,
                    categoryId = fs.categoryId,
                    frequency = fs.frequency,
                    nextDueDateMillis = fs.nextDueDateMillis,
                    isActive = fs.active,
                    lastPaidDateMillis = fs.lastPaidDateMillis,
                    reminderEnabled = fs.reminderEnabled,
                    updatedAt = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }

            val debtDocs = firestore.collection("$userDocPath/debts_loans").get().await()
            val debts = debtDocs.documents.mapNotNull { doc ->
                val fs = doc.toObject(FsDebtLoan::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                DebtLoanEntity(
                    id = doc.id,
                    workspaceId = fs.workspaceId,
                    kind = fs.kind,
                    title = fs.title,
                    counterparty = fs.counterparty,
                    principalAmount = fs.principalAmount,
                    remainingAmount = fs.remainingAmount,
                    interestRateAnnual = fs.interestRateAnnual,
                    minimumMonthlyPayment = fs.minimumMonthlyPayment,
                    dueDateMillis = fs.dueDateMillis,
                    isPaidOff = fs.paidOff,
                    updatedAt = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }

            val goalDocs = firestore.collection("$userDocPath/goals").get().await()
            val goals = goalDocs.documents.mapNotNull { doc ->
                val fs = doc.toObject(FsGoal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                GoalEntity(
                    id = doc.id,
                    workspaceId = fs.workspaceId,
                    title = fs.title,
                    targetAmount = fs.targetAmount,
                    currentAmount = fs.currentAmount,
                    targetDateMillis = fs.targetDateMillis,
                    monthlyContribution = fs.monthlyContribution,
                    iconKey = fs.iconKey,
                    contributorSummary = fs.contributorSummary,
                    updatedAt = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }

            val assetDocs = firestore.collection("$userDocPath/assets").get().await()
            val assets = assetDocs.documents.mapNotNull { doc ->
                val fs = doc.toObject(FsAsset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                AssetInvestmentEntity(
                    id = doc.id,
                    workspaceId = fs.workspaceId,
                    name = fs.name,
                    assetClass = fs.assetClass,
                    quantity = fs.quantity,
                    unitLabel = fs.unitLabel,
                    unitPrice = fs.unitPrice,
                    currentValue = fs.currentValue,
                    purchaseValue = fs.purchaseValue,
                    updatedAt = fs.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }

            val auditDocs = firestore.collection("$userDocPath/audit_logs").get().await()
            val auditLogs = auditDocs.documents.mapNotNull { doc ->
                val fs = doc.toObject(FsAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                AuditLogEntity(
                    id = doc.id,
                    workspaceId = fs.workspaceId,
                    actorMemberId = fs.actorMemberId,
                    actorName = fs.actorName,
                    action = fs.action,
                    detail = fs.detail,
                    createdAt = fs.eventTimeMillis
                )
            }

            Result.success(
                CloudPullSnapshot(
                    profile = profile,
                    workspaces = workspaces,
                    accounts = accounts,
                    categories = categories,
                    transactions = transactions,
                    budgets = budgets,
                    recurringRules = rules,
                    debtsLoans = debts,
                    goals = goals,
                    assets = assets,
                    auditLogs = auditLogs
                )
            )
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.LIST, userDocPath))
        }
    }

    /**
     * Real-time Firestore listener for remote updates gated by authenticated UID.
     */
    fun observeRemoteTransactionChanges(uid: String): Flow<List<TransactionEntity>> = callbackFlow {
        val path = "users/$uid/transactions"
        var reg: ListenerRegistration? = null
        try {
            reg = firestore.collection(path).addSnapshotListener { snapshots, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, path)
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val list = snapshots.documents.mapNotNull { doc ->
                        val fs = doc.toObject(FsTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                        TransactionEntity(
                            id = doc.id,
                            workspaceId = fs.workspaceId,
                            type = fs.type,
                            amount = fs.amount,
                            accountId = fs.accountId,
                            toAccountId = fs.toAccountId,
                            adminFee = fs.adminFee,
                            categoryId = fs.categoryId,
                            title = fs.title,
                            note = fs.note,
                            tags = fs.tags,
                            dateMillis = fs.dateMillis,
                            receiptImagePath = null,
                            createdByMemberId = fs.createdByMemberId,
                            createdByName = fs.createdByName,
                            updatedAt = fs.updatedAt?.toDate()?.time ?: fs.dateMillis,
                            syncStatus = SyncStates.SYNCED
                        )
                    }
                    trySend(list)
                }
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
        }
        awaitClose { reg?.remove() }
    }

    private fun mapTableToSubcollection(tableName: String): String? = when (tableName) {
        "workspaces" -> "workspaces"
        "accounts" -> "accounts"
        "categories" -> "categories"
        "transactions" -> "transactions"
        "budgets" -> "budgets"
        "recurring_rules" -> "recurring_rules"
        "debts_loans" -> "debts_loans"
        "goals" -> "goals"
        "assets_investments", "assets" -> "assets"
        "audit_logs" -> "audit_logs"
        else -> null
    }
}
