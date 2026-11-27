package com.example.data.local

import com.example.domain.CurrencyFormatter
import java.util.Calendar
import java.util.UUID

object DatabaseSeeder {

    suspend fun seedIfEmpty(dao: NusaKasDao) {
        if (dao.getWorkspaceCount() > 0) return

        val now = System.currentTimeMillis()
        val monthKey = CurrencyFormatter.currentMonthKey(now)

        fun daysAgo(days: Int, hour: Int = 10, min: Int = 0): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, -days)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0)
            return cal.timeInMillis
        }

        fun daysAhead(days: Int): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, days)
            return cal.timeInMillis
        }

        // 1. User
        val zacky = UserEntity(
            id = "usr_zacky",
            name = "Zacky Pratama",
            email = "zacky@nusakas.id",
            avatarInitials = "ZP",
            avatarColorHex = 0xFF065F46
        )
        dao.insertUsers(listOf(zacky))

        // 2. Workspaces (Personal 100% Local & Shared Family Sync)
        val wsPersonal = WorkspaceEntity(
            id = "ws_personal",
            name = "Keuangan Pribadi",
            type = WorkspaceTypes.PERSONAL,
            inviteCode = "PRV001",
            inviteLink = "https://nusakas.app/join/PRV001",
            createdByUserId = zacky.id
        )
        val wsFamily = WorkspaceEntity(
            id = "ws_family",
            name = "Keuangan Keluarga",
            type = WorkspaceTypes.SHARED,
            inviteCode = "KLR742",
            inviteLink = "https://nusakas.app/join/KLR742",
            createdByUserId = zacky.id
        )
        dao.insertWorkspaces(listOf(wsPersonal, wsFamily))

        // 3. Members
        val members = listOf(
            WorkspaceMemberEntity(
                id = "mem_p_zacky",
                workspaceId = wsPersonal.id,
                userId = zacky.id,
                name = "Zacky Pratama",
                email = "zacky@nusakas.id",
                avatarInitials = "ZP",
                colorHex = 0xFF065F46,
                role = MemberRoles.OWNER
            ),
            WorkspaceMemberEntity(
                id = "mem_f_zacky",
                workspaceId = wsFamily.id,
                userId = zacky.id,
                name = "Zacky",
                email = "zacky@nusakas.id",
                avatarInitials = "ZK",
                colorHex = 0xFF065F46,
                role = MemberRoles.OWNER
            ),
            WorkspaceMemberEntity(
                id = "mem_f_nadia",
                workspaceId = wsFamily.id,
                userId = "usr_nadia",
                name = "Nadia",
                email = "nadia@nusakas.id",
                avatarInitials = "ND",
                colorHex = 0xFF0284C7,
                role = MemberRoles.EDITOR
            ),
            WorkspaceMemberEntity(
                id = "mem_f_budi",
                workspaceId = wsFamily.id,
                userId = "usr_budi",
                name = "Budi",
                email = "budi@nusakas.id",
                avatarInitials = "BD",
                colorHex = 0xFFD97706,
                role = MemberRoles.VIEWER
            )
        )
        dao.insertMembers(members)

        // 4. Categories (both workspaces)
        val personalCats = defaultCategories(wsPersonal.id, "p")
        val familyCats = defaultCategories(wsFamily.id, "f")
        dao.insertCategories(personalCats + familyCats)

        // 5. Accounts (Personal)
        val personalAccounts = listOf(
            AccountEntity("acc_p_cash", wsPersonal.id, "Dompet Tunai", AccountTypes.CASH, 1_450_000L, institutionName = "Tunai", colorHex = 0xFF059669),
            AccountEntity("acc_p_bca", wsPersonal.id, "Bank BCA", AccountTypes.BANK, 24_800_000L, institutionName = "BCA", colorHex = 0xFF0369A1),
            AccountEntity("acc_p_mandiri", wsPersonal.id, "Bank Mandiri", AccountTypes.BANK, 11_200_000L, institutionName = "Mandiri", colorHex = 0xFF1D4ED8),
            AccountEntity("acc_p_gopay", wsPersonal.id, "GoPay", AccountTypes.EWALLET, 1_850_000L, institutionName = "GoPay", colorHex = 0xFF0EA5E9),
            AccountEntity("acc_p_ovo", wsPersonal.id, "OVO", AccountTypes.EWALLET, 740_000L, institutionName = "OVO", colorHex = 0xFF7C3AED),
            AccountEntity("acc_p_cc", wsPersonal.id, "Kartu Kredit BCA", AccountTypes.CREDIT_CARD, 5_600_000L, creditLimit = 15_000_000L, billingDay = 5, dueDay = 20, institutionName = "BCA Card", colorHex = 0xFFDC2626),
            AccountEntity("acc_p_dep", wsPersonal.id, "Deposito Jenius", AccountTypes.DEPOSIT, 35_000_000L, institutionName = "Bank SMBC", colorHex = 0xFF0F766E)
        )
        val familyAccounts = listOf(
            AccountEntity("acc_f_bca", wsFamily.id, "Rekening Bersama BCA", AccountTypes.BANK, 42_500_000L, institutionName = "BCA", colorHex = 0xFF0369A1),
            AccountEntity("acc_f_gopay", wsFamily.id, "GoPay Rumah Tangga", AccountTypes.EWALLET, 2_350_000L, institutionName = "GoPay", colorHex = 0xFF0EA5E9),
            AccountEntity("acc_f_cash", wsFamily.id, "Kas Tunai Rumah", AccountTypes.CASH, 1_900_000L, institutionName = "Tunai", colorHex = 0xFF059669),
            AccountEntity("acc_f_cc", wsFamily.id, "Kartu Kredit Keluarga", AccountTypes.CREDIT_CARD, 4_200_000L, creditLimit = 20_000_000L, billingDay = 10, dueDay = 25, institutionName = "Mandiri Card", colorHex = 0xFFDC2626)
        )
        dao.insertAccounts(personalAccounts + familyAccounts)

        // 6. Transactions & Split Items (Personal)
        val pTxs = listOf(
            TransactionEntity("tx_p_1", wsPersonal.id, TxTypes.INCOME, 16_500_000L, "acc_p_bca", null, 0L, "cat_p_gaji", "Gaji Bulanan", "Gaji pokok & tunjangan", "#gaji,#rutin", daysAgo(18, 9, 0), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_2", wsPersonal.id, TxTypes.INCOME, 3_250_000L, "acc_p_mandiri", null, 0L, "cat_p_freelance", "Proyek Konsultasi UI", "Termin akhir klien", "#freelance", daysAgo(9, 14, 15), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_3", wsPersonal.id, TxTypes.EXPENSE, 2_800_000L, "acc_p_bca", null, 0L, "cat_p_rumah", "Sewa Apartemen & IPL", "Tagihan bulanan", "#hunian,#wajib", daysAgo(16, 11, 20), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_4", wsPersonal.id, TxTypes.SPLIT, 845_000L, "acc_p_cc", null, 0L, "cat_p_belanja", "Belanja Bulanan GrandLucky", "Split kebutuhan dapur & rumah", "#groceries,#split", daysAgo(7, 17, 45), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_5", wsPersonal.id, TxTypes.EXPENSE, 620_000L, "acc_p_mandiri", null, 0L, "cat_p_tagihan", "Listrik PLN & Biznet", "Token listrik + WiFi", "#utilitas,#rutin", daysAgo(12, 8, 30), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_6", wsPersonal.id, TxTypes.EXPENSE, 48_000L, "acc_p_gopay", null, 0L, "cat_p_makan", "Kopi Susu Gula Aren", "Kopi pagi sebelum meeting", "#nongkrong,#kopi", daysAgo(4, 8, 30), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_7", wsPersonal.id, TxTypes.EXPENSE, 35_000L, "acc_p_gopay", null, 0L, "cat_p_makan", "Es Kopi & Croissant", "Snack sore", "#kopi,#latte", daysAgo(3, 15, 20), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_8", wsPersonal.id, TxTypes.EXPENSE, 42_000L, "acc_p_ovo", null, 0L, "cat_p_makan", "Matcha Latte", "Kafe kerja", "#nongkrong,#kopi", daysAgo(2, 13, 10), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_9", wsPersonal.id, TxTypes.EXPENSE, 39_000L, "acc_p_gopay", null, 0L, "cat_p_makan", "Kopi Americano & Roti", "Sarapan cepat", "#kopi", daysAgo(1, 8, 15), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_10", wsPersonal.id, TxTypes.EXPENSE, 360_000L, "acc_p_gopay", null, 0L, "cat_p_transport", "BBM & Tol Jabodetabek", "Isi bensin Pertamax", "#transport", daysAgo(5, 18, 0), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_11", wsPersonal.id, TxTypes.EXPENSE, 750_000L, "acc_p_bca", null, 0L, "cat_p_makan", "Makan Malam Reuni", "Traktir teman kantor", "#sosial,#makan", daysAgo(1, 20, 10), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_12", wsPersonal.id, TxTypes.TRANSFER, 1_500_000L, "acc_p_bca", "acc_p_gopay", 2_500L, "cat_p_transfer", "Top Up GoPay", "Isi saldo harian", "#topup", daysAgo(6, 10, 0), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY),
            TransactionEntity("tx_p_13", wsPersonal.id, TxTypes.EXPENSE, 189_000L, "acc_p_cc", null, 0L, "cat_p_hiburan", "Netflix & Spotify Family", "Langganan digital", "#langganan", daysAgo(10, 9, 0), null, "mem_p_zacky", "Zacky Pratama", now, SyncStates.OFFLINE_ONLY)
        )
        val pSplitItems = listOf(
            TransactionItemEntity("txi_1", "tx_p_4", wsPersonal.id, "cat_p_makan", "Bahan Makanan & Buah", 545_000L),
            TransactionItemEntity("txi_2", "tx_p_4", wsPersonal.id, "cat_p_belanja", "Perlengkapan Kebersihan", 300_000L)
        )

        // Family transactions (with member attribution!)
        val fTxs = listOf(
            TransactionEntity("tx_f_1", wsFamily.id, TxTypes.INCOME, 22_000_000L, "acc_f_bca", null, 0L, "cat_f_gaji", "Setoran Kas Bulanan", "Kontribusi Zacky & Nadia", "#kas", daysAgo(15, 9, 0), null, "mem_f_zacky", "Zacky", now, SyncStates.SYNCED),
            TransactionEntity("tx_f_2", wsFamily.id, TxTypes.EXPENSE, 1_450_000L, "acc_f_bca", null, 0L, "cat_f_makan", "Belanja Mingguan Keluarga", "Sayur, lauk & susu anak", "#dapur", daysAgo(4, 10, 30), null, "mem_f_nadia", "Nadia", now, SyncStates.SYNCED),
            TransactionEntity("tx_f_3", wsFamily.id, TxTypes.EXPENSE, 950_000L, "acc_f_bca", null, 0L, "cat_f_tagihan", "Listrik & PDAM Rumah", "Tagihan bulan berjalan", "#utilitas", daysAgo(8, 14, 0), null, "mem_f_zacky", "Zacky", now, SyncStates.SYNCED),
            TransactionEntity("tx_f_4", wsFamily.id, TxTypes.EXPENSE, 420_000L, "acc_f_gopay", null, 0L, "cat_f_hiburan", "Makan Siang Akhir Pekan", "Resto keluarga", "#keluarga", daysAgo(2, 13, 0), null, "mem_f_nadia", "Nadia", now, SyncStates.SYNCED)
        )
        dao.insertTransactions(pTxs + fTxs)
        dao.insertTransactionItems(pSplitItems)

        // 7. Budgets
        val pBudgets = listOf(
            BudgetEntity("bdg_p_1", wsPersonal.id, "cat_p_makan", monthKey, 2_000_000L, 180_000L, true),
            BudgetEntity("bdg_p_2", wsPersonal.id, "cat_p_transport", monthKey, 800_000L, 50_000L, true),
            BudgetEntity("bdg_p_3", wsPersonal.id, "cat_p_tagihan", monthKey, 900_000L, 0L, false),
            BudgetEntity("bdg_p_4", wsPersonal.id, "cat_p_belanja", monthKey, 700_000L, 0L, true),
            BudgetEntity("bdg_p_5", wsPersonal.id, "cat_p_hiburan", monthKey, 500_000L, 0L, false)
        )
        val fBudgets = listOf(
            BudgetEntity("bdg_f_1", wsFamily.id, "cat_f_makan", monthKey, 4_500_000L, 350_000L, true),
            BudgetEntity("bdg_f_2", wsFamily.id, "cat_f_tagihan", monthKey, 1_500_000L, 0L, false),
            BudgetEntity("bdg_f_3", wsFamily.id, "cat_f_hiburan", monthKey, 1_200_000L, 100_000L, true)
        )
        dao.insertBudgets(pBudgets + fBudgets)

        // 8. Recurring Rules (with some due soon / overdue so 1-click pay shines immediately)
        val recurring = listOf(
            RecurringRuleEntity("rec_p_1", wsPersonal.id, "Internet Biznet Home", 375_000L, TxTypes.EXPENSE, "acc_p_bca", "cat_p_tagihan", "MONTHLY", daysAhead(0)),
            RecurringRuleEntity("rec_p_2", wsPersonal.id, "Token Listrik PLN", 300_000L, TxTypes.EXPENSE, "acc_p_gopay", "cat_p_tagihan", "MONTHLY", daysAhead(3)),
            RecurringRuleEntity("rec_p_3", wsPersonal.id, "Spotify & Netflix", 189_000L, TxTypes.EXPENSE, "acc_p_cc", "cat_p_hiburan", "MONTHLY", daysAhead(12)),
            RecurringRuleEntity("rec_f_1", wsFamily.id, "Iuran Keamanan & Sampah", 250_000L, TxTypes.EXPENSE, "acc_f_cash", "cat_f_tagihan", "MONTHLY", daysAhead(1))
        )
        dao.insertRecurringRules(recurring)

        // 9. Debts & Loans (for Snowball vs Avalanche Simulator!)
        val debts = listOf(
            DebtLoanEntity("dbt_p_1", wsPersonal.id, "DEBT", "Cicilan PayLater Gadget", "SPayLater", 6_000_000L, 2_400_000L, 28.0, 450_000L, daysAhead(8)),
            DebtLoanEntity("dbt_p_2", wsPersonal.id, "DEBT", "Cicilan Motor Harian", "Adira Finance", 18_000_000L, 7_800_000L, 14.5, 950_000L, daysAhead(14)),
            DebtLoanEntity("dbt_p_3", wsPersonal.id, "DEBT", "Kredit Pemilikan Apartemen", "Bank BCA KPR", 320_000_000L, 185_000_000L, 7.25, 3_200_000L, daysAhead(20)),
            DebtLoanEntity("dbt_p_4", wsPersonal.id, "LOAN", "Piutang Teman (Raka)", "Raka Wijaya", 2_000_000L, 1_500_000L, 0.0, 500_000L, daysAhead(15)),
            DebtLoanEntity("dbt_f_1", wsFamily.id, "DEBT", "Cicilan Mobil Keluarga", "BCA Finance", 160_000_000L, 74_000_000L, 8.9, 3_600_000L, daysAhead(11))
        )
        dao.insertDebtsLoans(debts)

        // 10. Goals
        val goals = listOf(
            GoalEntity("gol_p_1", wsPersonal.id, "Dana Darurat 6 Bulan", 50_000_000L, 35_000_000L, daysAhead(180), 2_500_000L, "shield", "Zacky Pratama:35000000"),
            GoalEntity("gol_p_2", wsPersonal.id, "Upgrade Laptop Kerja", 22_000_000L, 11_500_000L, daysAhead(120), 2_000_000L, "laptop", "Zacky Pratama:11500000"),
            GoalEntity("gol_f_1", wsFamily.id, "Liburan Keluarga ke Bali", 25_000_000L, 14_500_000L, daysAhead(90), 3_500_000L, "flight", "Zacky:9500000|Nadia:5000000"),
            GoalEntity("gol_f_2", wsFamily.id, "Dana Pendidikan Anak", 100_000_000L, 42_000_000L, daysAhead(720), 3_000_000L, "school", "Zacky:24000000|Nadia:18000000")
        )
        dao.insertGoals(goals)

        // 11. Assets & Investments
        val assets = listOf(
            AssetInvestmentEntity("ast_p_1", wsPersonal.id, "Emas Antam LM", "GOLD", 25.0, "gram", 1_520_000L, 38_000_000L, 29_500_000L),
            AssetInvestmentEntity("ast_p_2", wsPersonal.id, "Reksadana Pasar Uang Bibit", "MUTUAL_FUND", 15_400.0, "unit", 2_000L, 30_800_000L, 28_000_000L),
            AssetInvestmentEntity("ast_p_3", wsPersonal.id, "Saham Bluechip BBCA & BMRI", "STOCKS", 30.0, "lot", 960_000L, 28_800_000L, 24_500_000L),
            AssetInvestmentEntity("ast_p_4", wsPersonal.id, "Unit Apartemen Studio", "PROPERTY", 1.0, "unit", 390_000_000L, 390_000_000L, 340_000_000L),
            AssetInvestmentEntity("ast_f_1", wsFamily.id, "Emas Tabungan Keluarga", "GOLD", 40.0, "gram", 1_520_000L, 60_800_000L, 48_000_000L),
            AssetInvestmentEntity("ast_f_2", wsFamily.id, "Mobil MPV Keluarga", "VEHICLE", 1.0, "unit", 215_000_000L, 215_000_000L, 265_000_000L)
        )
        dao.insertAssets(assets)

        // 12. Split Bills (Family workspace)
        val splitBills = listOf(
            SplitBillEntity(
                id = "spl_f_1",
                workspaceId = wsFamily.id,
                title = "Makan Malam & Kue Ultah Ibu",
                totalAmount = 1_200_000L,
                paidByMemberId = "mem_f_zacky",
                splitMode = "EQUAL",
                participantsData = "mem_f_zacky:400000:true|mem_f_nadia:400000:false|mem_f_budi:400000:false",
                dateMillis = daysAgo(3, 19, 30),
                isSettled = false
            ),
            SplitBillEntity(
                id = "spl_f_2",
                workspaceId = wsFamily.id,
                title = "Belanja Perlengkapan Villa",
                totalAmount = 600_000L,
                paidByMemberId = "mem_f_nadia",
                splitMode = "EQUAL",
                participantsData = "mem_f_zacky:200000:false|mem_f_nadia:200000:true|mem_f_budi:200000:false",
                dateMillis = daysAgo(1, 15, 0),
                isSettled = false
            )
        )
        dao.insertSplitBills(splitBills)

        // 13. Audit Logs
        val logs = listOf(
            AuditLogEntity(UUID.randomUUID().toString(), wsFamily.id, "mem_f_nadia", "Nadia", "Catat Transaksi", "Makan Siang Akhir Pekan • Rp 420.000", daysAgo(2, 13, 5)),
            AuditLogEntity(UUID.randomUUID().toString(), wsFamily.id, "mem_f_zacky", "Zacky", "Buat Split Bill", "Makan Malam & Kue Ultah Ibu • Rp 1.200.000", daysAgo(3, 19, 35)),
            AuditLogEntity(UUID.randomUUID().toString(), wsFamily.id, "mem_f_zacky", "Zacky", "Setor Kas Bulanan", "Rekening Bersama BCA • +Rp 22.000.000", daysAgo(15, 9, 2))
        )
        dao.insertAuditLogs(logs)
    }

    fun defaultCategories(workspaceId: String, prefix: String): List<CategoryEntity> = listOf(
        CategoryEntity("cat_${prefix}_gaji", workspaceId, "Gaji & Tunjangan", null, TxTypes.INCOME, "payments", 0xFF059669),
        CategoryEntity("cat_${prefix}_freelance", workspaceId, "Proyek & Bisnis", null, TxTypes.INCOME, "work", 0xFF0D9488),
        CategoryEntity("cat_${prefix}_invest_in", workspaceId, "Dividen & Bunga", null, TxTypes.INCOME, "trending_up", 0xFF0284C7),
        CategoryEntity("cat_${prefix}_makan", workspaceId, "Makanan & Minuman", null, TxTypes.EXPENSE, "restaurant", 0xFFEA580C),
        CategoryEntity("cat_${prefix}_transport", workspaceId, "Transportasi & BBM", null, TxTypes.EXPENSE, "directions_car", 0xFF2563EB),
        CategoryEntity("cat_${prefix}_tagihan", workspaceId, "Tagihan & Utilitas", null, TxTypes.EXPENSE, "receipt_long", 0xFFD97706),
        CategoryEntity("cat_${prefix}_rumah", workspaceId, "Hunian & Sewa", null, TxTypes.EXPENSE, "home", 0xFF7C3AED),
        CategoryEntity("cat_${prefix}_belanja", workspaceId, "Belanja & Kebutuhan", null, TxTypes.EXPENSE, "shopping_bag", 0xFFDB2777),
        CategoryEntity("cat_${prefix}_hiburan", workspaceId, "Hiburan & Langganan", null, TxTypes.EXPENSE, "movie", 0xFF4F46E5),
        CategoryEntity("cat_${prefix}_kesehatan", workspaceId, "Kesehatan", null, TxTypes.EXPENSE, "medical_services", 0xFFDC2626),
        CategoryEntity("cat_${prefix}_transfer", workspaceId, "Transfer Antar Akun", null, TxTypes.TRANSFER, "swap_horiz", 0xFF0284C7)
    )
}
