package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        WorkspaceEntity::class,
        WorkspaceMemberEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        DebtLoanEntity::class,
        GoalEntity::class,
        AssetInvestmentEntity::class,
        SplitBillEntity::class,
        SyncQueueEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NusaKasDatabase : RoomDatabase() {
    abstract fun dao(): NusaKasDao

    companion object {
        @Volatile
        private var INSTANCE: NusaKasDatabase? = null

        fun getInstance(context: Context): NusaKasDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NusaKasDatabase::class.java,
                    "nusakas_local_first.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
