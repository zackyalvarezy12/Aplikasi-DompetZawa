package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = false
)
abstract class NusaKasDatabase : RoomDatabase() {
    abstract fun dao(): NusaKasDao

    companion object {
        @Volatile
        private var INSTANCE: NusaKasDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recurring_rules ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE recurring_rules ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): NusaKasDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NusaKasDatabase::class.java,
                    "nusakas_local_first.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

