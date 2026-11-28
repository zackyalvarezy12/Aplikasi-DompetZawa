package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object WorkspaceTypes {
    const val PERSONAL = "PERSONAL"
    const val SHARED = "SHARED"
}

object MemberRoles {
    const val OWNER = "OWNER"
    const val EDITOR = "EDITOR"
    const val VIEWER = "VIEWER"
}

object AccountTypes {
    const val CASH = "CASH"
    const val BANK = "BANK"
    const val EWALLET = "EWALLET"
    const val CREDIT_CARD = "CREDIT_CARD"
    const val DEPOSIT = "DEPOSIT"
    const val INVESTMENT = "INVESTMENT"
}

object TxTypes {
    const val INCOME = "INCOME"
    const val EXPENSE = "EXPENSE"
    const val TRANSFER = "TRANSFER"
    const val SPLIT = "SPLIT"
    const val REFUND = "REFUND"
}

object SyncStates {
    const val SYNCED = "SYNCED"
    const val PENDING = "PENDING"
    const val OFFLINE_ONLY = "OFFLINE_ONLY"
}

// 1. users
@Immutable
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val avatarInitials: String,
    val avatarColorHex: Long = 0xFF065F46,
    val createdAt: Long = System.currentTimeMillis()
)

// 2. workspaces
@Immutable
@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // PERSONAL | SHARED
    val inviteCode: String, // 6-8 char unique code
    val inviteLink: String,
    val currency: String = "IDR",
    val createdByUserId: String,
    val updatedAt: Long = System.currentTimeMillis()
)

// 3. workspace_members
@Immutable
@Entity(
    tableName = "workspace_members",
    indices = [Index("workspaceId")]
)
data class WorkspaceMemberEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val userId: String,
    val name: String,
    val email: String,
    val avatarInitials: String,
    val colorHex: Long,
    val role: String, // OWNER | EDITOR | VIEWER
    val status: String = "ACTIVE", // ACTIVE | INVITED
    val joinedAt: Long = System.currentTimeMillis()
)

// 4. accounts
@Immutable
@Entity(
    tableName = "accounts",
    indices = [Index("workspaceId")]
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val name: String,
    val type: String, // CASH | BANK | EWALLET | CREDIT_CARD | DEPOSIT | INVESTMENT
    val balance: Long,
    val creditLimit: Long = 0L,
    val billingDay: Int = 1,
    val dueDay: Int = 15,
    val institutionName: String = "",
    val colorHex: Long = 0xFF047857,
    val updatedAt: Long = System.currentTimeMillis()
)

// 5. categories
@Immutable
@Entity(
    tableName = "categories",
    indices = [Index("workspaceId")]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val name: String,
    val parentId: String? = null,
    val type: String, // INCOME | EXPENSE
    val iconKey: String = "category",
    val colorHex: Long = 0xFF059669
)

// 6. transactions
@Immutable
@Entity(
    tableName = "transactions",
    indices = [Index("workspaceId"), Index("dateMillis"), Index("accountId")]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val type: String, // INCOME | EXPENSE | TRANSFER | SPLIT | REFUND
    val amount: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val adminFee: Long = 0L,
    val categoryId: String,
    val title: String,
    val note: String = "",
    val tags: String = "", // comma separated e.g. "#nongkrong,#kopi"
    val dateMillis: Long,
    val receiptImagePath: String? = null,
    val createdByMemberId: String,
    val createdByName: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStates.PENDING
)

// 7. transaction_items (for Split Transactions / receipt breakdown)
@Immutable
@Entity(
    tableName = "transaction_items",
    indices = [Index("transactionId")]
)
data class TransactionItemEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val workspaceId: String,
    val categoryId: String,
    val description: String,
    val amount: Long
)

// 8. budgets (Zero-Based & Envelope Rollover)
@Immutable
@Entity(
    tableName = "budgets",
    indices = [Index("workspaceId"), Index("monthYear")]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val categoryId: String,
    val monthYear: String, // "yyyy-MM"
    val limitAmount: Long,
    val rolloverAmount: Long = 0L,
    val rolloverEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

// 9. recurring_rules
@Immutable
@Entity(
    tableName = "recurring_rules",
    indices = [Index("workspaceId")]
)
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String,
    val amount: Long,
    val type: String = TxTypes.EXPENSE,
    val accountId: String,
    val categoryId: String,
    val frequency: String, // DAILY | WEEKLY | MONTHLY | YEARLY
    val nextDueDateMillis: Long,
    val isActive: Boolean = true,
    val lastPaidDateMillis: Long? = null,
    val reminderEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

// 10. debts_loans
@Immutable
@Entity(
    tableName = "debts_loans",
    indices = [Index("workspaceId")]
)
data class DebtLoanEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val kind: String, // DEBT (Utang) | LOAN (Piutang)
    val title: String,
    val counterparty: String,
    val principalAmount: Long,
    val remainingAmount: Long,
    val interestRateAnnual: Double,
    val minimumMonthlyPayment: Long,
    val dueDateMillis: Long,
    val isPaidOff: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

// 11. goals (Sinking Funds & Collective Savings)
@Immutable
@Entity(
    tableName = "goals",
    indices = [Index("workspaceId")]
)
data class GoalEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val targetDateMillis: Long,
    val monthlyContribution: Long,
    val iconKey: String = "savings",
    val contributorSummary: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

// 12. assets_investments
@Immutable
@Entity(
    tableName = "assets_investments",
    indices = [Index("workspaceId")]
)
data class AssetInvestmentEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val name: String,
    val assetClass: String,
    val quantity: Double,
    val unitLabel: String,
    val unitPrice: Long,
    val currentValue: Long,
    val purchaseValue: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

// 13. split_bills
@Immutable
@Entity(
    tableName = "split_bills",
    indices = [Index("workspaceId")]
)
data class SplitBillEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String,
    val totalAmount: Long,
    val paidByMemberId: String,
    val splitMode: String,
    val participantsData: String,
    val dateMillis: Long,
    val isSettled: Boolean = false
)

// 14a. sync_queue
@Immutable
@Entity(
    tableName = "sync_queue",
    indices = [Index("workspaceId"), Index("status")]
)
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val tableName: String,
    val recordId: String,
    val operation: String,
    val payloadJson: String,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis()
)

// 14b. audit_logs
@Immutable
@Entity(
    tableName = "audit_logs",
    indices = [Index("workspaceId"), Index("createdAt")]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val actorMemberId: String,
    val actorName: String,
    val action: String,
    val detail: String,
    val createdAt: Long = System.currentTimeMillis()
)
