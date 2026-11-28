package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NusaKasDao {

    // Users
    @Query("SELECT * FROM users LIMIT 1")
    fun observeCurrentUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUserSnapshot(): UserEntity?

    @Query("SELECT COUNT(*) FROM workspaces")
    suspend fun getWorkspaceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    // Workspaces
    @Query("SELECT * FROM workspaces ORDER BY type DESC, name ASC")
    fun observeAllWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces ORDER BY type DESC, name ASC")
    suspend fun getAllWorkspacesSnapshot(): List<WorkspaceEntity>

    @Query("SELECT * FROM workspaces WHERE id = :workspaceId")
    suspend fun getWorkspaceById(workspaceId: String): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE inviteCode = :code LIMIT 1")
    suspend fun getWorkspaceByInviteCode(code: String): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspace(workspace: WorkspaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspaces(workspaces: List<WorkspaceEntity>)

    @Query("DELETE FROM workspaces WHERE id = :workspaceId")
    suspend fun deleteWorkspaceById(workspaceId: String)

    // Workspace Members
    @Query("SELECT * FROM workspace_members WHERE workspaceId = :workspaceId ORDER BY joinedAt ASC")
    fun observeMembers(workspaceId: String): Flow<List<WorkspaceMemberEntity>>

    @Query("SELECT * FROM workspace_members WHERE workspaceId = :workspaceId ORDER BY joinedAt ASC")
    suspend fun getMembersSnapshot(workspaceId: String): List<WorkspaceMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: WorkspaceMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<WorkspaceMemberEntity>)

    @Query("UPDATE workspace_members SET role = :role WHERE id = :memberId")
    suspend fun updateMemberRole(memberId: String, role: String)

    @Query("UPDATE workspace_members SET name = :name, email = :email, avatarInitials = :initials, colorHex = :colorHex WHERE userId = :userId")
    suspend fun updateMemberProfileByUserId(
        userId: String,
        name: String,
        email: String,
        initials: String,
        colorHex: Long
    )

    @Query("DELETE FROM workspace_members WHERE id = :memberId")
    suspend fun deleteMemberById(memberId: String)

    // Accounts
    @Query("SELECT * FROM accounts WHERE workspaceId = :workspaceId ORDER BY type ASC, name ASC")
    fun observeAccounts(workspaceId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY type ASC, name ASC")
    suspend fun getAllAccountsSnapshot(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :accountId")
    suspend fun getAccountById(accountId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Query("UPDATE accounts SET balance = balance + :delta, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun adjustAccountBalance(accountId: String, delta: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccountById(accountId: String)

    // Categories
    @Query("SELECT * FROM categories WHERE workspaceId = :workspaceId ORDER BY type ASC, name ASC")
    fun observeCategories(workspaceId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type ASC, name ASC")
    suspend fun getAllCategoriesSnapshot(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: String)

    // Transactions & Items
    @Query("SELECT * FROM transactions WHERE workspaceId = :workspaceId ORDER BY dateMillis DESC")
    fun observeTransactions(workspaceId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE workspaceId = :workspaceId ORDER BY dateMillis DESC LIMIT :limit")
    fun observeTransactionsPaged(workspaceId: String, limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE workspaceId = :workspaceId ORDER BY dateMillis DESC LIMIT 200")
    fun observeRecentTransactions(workspaceId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC")
    suspend fun getAllTransactionsSnapshot(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :txId")
    suspend fun getTransactionById(txId: String): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE workspaceId = :workspaceId")
    fun observeTransactionCount(workspaceId: String): Flow<Int>

    @Query("SELECT * FROM transaction_items WHERE workspaceId = :workspaceId")
    fun observeTransactionItems(workspaceId: String): Flow<List<TransactionItemEntity>>

    @Query("SELECT * FROM transaction_items WHERE transactionId = :txId")
    suspend fun getTransactionItemsByTxId(txId: String): List<TransactionItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(tx: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(txs: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItems(items: List<TransactionItemEntity>)

    @Query("DELETE FROM transaction_items WHERE transactionId = :txId")
    suspend fun deleteTransactionItems(txId: String)

    @Query("DELETE FROM transactions WHERE id = :txId")
    suspend fun deleteTransactionById(txId: String)

    @Query("UPDATE transactions SET syncStatus = :status WHERE workspaceId = :workspaceId")
    suspend fun markAllTransactionsSynced(workspaceId: String, status: String = SyncStates.SYNCED)

    // Budgets
    @Query("SELECT * FROM budgets WHERE workspaceId = :workspaceId ORDER BY monthYear DESC")
    fun observeBudgets(workspaceId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY monthYear DESC")
    suspend fun getAllBudgetsSnapshot(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :budgetId")
    suspend fun getBudgetById(budgetId: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE id = :budgetId")
    suspend fun deleteBudgetById(budgetId: String)

    // Recurring Rules
    @Query("SELECT * FROM recurring_rules WHERE workspaceId = :workspaceId ORDER BY nextDueDateMillis ASC")
    fun observeRecurringRules(workspaceId: String): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY nextDueDateMillis ASC")
    suspend fun getAllRecurringRulesSnapshot(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE isActive = 1 AND reminderEnabled = 1 ORDER BY nextDueDateMillis ASC")
    suspend fun getActiveReminderRulesSnapshot(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :ruleId")
    suspend fun getRecurringRuleById(ruleId: String): RecurringRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurringRule(rule: RecurringRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringRules(rules: List<RecurringRuleEntity>)

    @Query("DELETE FROM recurring_rules WHERE id = :ruleId")
    suspend fun deleteRecurringRuleById(ruleId: String)

    // Debts & Loans
    @Query("SELECT * FROM debts_loans WHERE workspaceId = :workspaceId ORDER BY isPaidOff ASC, dueDateMillis ASC")
    fun observeDebtsLoans(workspaceId: String): Flow<List<DebtLoanEntity>>

    @Query("SELECT * FROM debts_loans ORDER BY isPaidOff ASC, dueDateMillis ASC")
    suspend fun getAllDebtsLoansSnapshot(): List<DebtLoanEntity>

    @Query("SELECT * FROM debts_loans WHERE id = :id")
    suspend fun getDebtLoanById(id: String): DebtLoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDebtLoan(item: DebtLoanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtsLoans(items: List<DebtLoanEntity>)

    @Query("DELETE FROM debts_loans WHERE id = :id")
    suspend fun deleteDebtLoanById(id: String)

    // Goals
    @Query("SELECT * FROM goals WHERE workspaceId = :workspaceId ORDER BY targetDateMillis ASC")
    fun observeGoals(workspaceId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY targetDateMillis ASC")
    suspend fun getAllGoalsSnapshot(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :goalId")
    suspend fun getGoalById(goalId: String): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<GoalEntity>)

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun deleteGoalById(goalId: String)

    // Assets & Investments
    @Query("SELECT * FROM assets_investments WHERE workspaceId = :workspaceId ORDER BY currentValue DESC")
    fun observeAssets(workspaceId: String): Flow<List<AssetInvestmentEntity>>

    @Query("SELECT * FROM assets_investments ORDER BY currentValue DESC")
    suspend fun getAllAssetsSnapshot(): List<AssetInvestmentEntity>

    @Query("SELECT * FROM assets_investments WHERE id = :assetId")
    suspend fun getAssetById(assetId: String): AssetInvestmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAsset(asset: AssetInvestmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<AssetInvestmentEntity>)

    @Query("DELETE FROM assets_investments WHERE id = :assetId")
    suspend fun deleteAssetById(assetId: String)

    // Split Bills
    @Query("SELECT * FROM split_bills WHERE workspaceId = :workspaceId ORDER BY dateMillis DESC")
    fun observeSplitBills(workspaceId: String): Flow<List<SplitBillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSplitBill(bill: SplitBillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplitBills(bills: List<SplitBillEntity>)

    @Query("DELETE FROM split_bills WHERE id = :billId")
    suspend fun deleteSplitBillById(billId: String)

    // Sync Queue
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun observePendingSyncQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSyncItems(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSync(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET status = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSyncQueueSynced(ids: List<String>)

    // Audit Logs
    @Query("SELECT * FROM audit_logs WHERE workspaceId = :workspaceId ORDER BY createdAt DESC LIMIT 100")
    fun observeAuditLogs(workspaceId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 200")
    suspend fun getAllAuditLogsSnapshot(): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE id = :id")
    suspend fun getAuditLogById(id: String): AuditLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(logs: List<AuditLogEntity>)

    // Clean Reset Helpers
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("DELETE FROM workspaces")
    suspend fun deleteAllWorkspaces()

    @Query("DELETE FROM workspace_members")
    suspend fun deleteAllMembers()

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM transaction_items")
    suspend fun deleteAllTransactionItems()

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAllRecurringRules()

    @Query("DELETE FROM debts_loans")
    suspend fun deleteAllDebtsLoans()

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM assets_investments")
    suspend fun deleteAllAssets()

    @Query("DELETE FROM split_bills")
    suspend fun deleteAllSplitBills()

    @Query("DELETE FROM sync_queue")
    suspend fun deleteAllSyncQueue()

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAllAuditLogs()

    @Transaction
    suspend fun clearAllLocalData() {
        deleteAllTransactionItems()
        deleteAllTransactions()
        deleteAllBudgets()
        deleteAllRecurringRules()
        deleteAllDebtsLoans()
        deleteAllGoals()
        deleteAllAssets()
        deleteAllSplitBills()
        deleteAllSyncQueue()
        deleteAllAuditLogs()
        deleteAllCategories()
        deleteAllAccounts()
        deleteAllMembers()
        deleteAllWorkspaces()
        deleteAllUsers()
    }
}
