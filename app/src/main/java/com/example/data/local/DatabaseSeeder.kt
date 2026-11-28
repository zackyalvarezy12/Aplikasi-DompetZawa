package com.example.data.local

import java.util.UUID

object DatabaseSeeder {

    /**
     * Kept for backward compatibility during app startup; intentionally does NOT seed any dummy data.
     * New users go through Onboarding and receive a clean Rp 0 workspace via initializeCleanPersonalWorkspace().
     */
    suspend fun seedIfEmpty(@Suppress("UNUSED_PARAMETER") dao: NusaKasDao) {
        // Intentionally empty: zero dummy data policy.
    }

    /**
     * Initializes a clean personal workspace (Rp 0 starting balance, no fake transactions/debts/assets)
     * with standard Indonesian categories and one clean default wallet so the user can begin immediately.
     */
    suspend fun initializeCleanPersonalWorkspace(
        dao: NusaKasDao,
        displayName: String,
        email: String = "",
        avatarColorHex: Long = 0xFF065F46,
        userId: String = "usr_owner"
    ): Triple<UserEntity, WorkspaceEntity, WorkspaceMemberEntity> {
        val cleanName = displayName.trim().ifBlank { "Pengguna" }
        val initials = computeInitials(cleanName)
        val now = System.currentTimeMillis()

        val user = UserEntity(
            id = userId,
            name = cleanName,
            email = email.trim(),
            avatarInitials = initials,
            avatarColorHex = avatarColorHex,
            createdAt = now
        )
        dao.upsertUser(user)

        val existingWorkspaces = dao.getAllWorkspacesSnapshot()
        if (existingWorkspaces.isNotEmpty()) {
            val ws = existingWorkspaces.first()
            val members = dao.getMembersSnapshot(ws.id)
            val ownerMember = members.firstOrNull { it.userId == userId } ?: members.firstOrNull()
            ?: WorkspaceMemberEntity(
                id = "mem_owner",
                workspaceId = ws.id,
                userId = userId,
                name = cleanName,
                email = email.trim(),
                avatarInitials = initials,
                colorHex = avatarColorHex,
                role = MemberRoles.OWNER,
                joinedAt = now
            ).also { dao.upsertMember(it) }
            return Triple(user, ws, ownerMember)
        }

        val wsPersonal = WorkspaceEntity(
            id = "ws_personal",
            name = "Keuangan Pribadi",
            type = WorkspaceTypes.PERSONAL,
            inviteCode = "PRV001",
            inviteLink = "https://nusakas.app/join/PRV001",
            createdByUserId = user.id,
            updatedAt = now
        )
        dao.upsertWorkspace(wsPersonal)

        val ownerMember = WorkspaceMemberEntity(
            id = "mem_owner",
            workspaceId = wsPersonal.id,
            userId = user.id,
            name = cleanName,
            email = email.trim(),
            avatarInitials = initials,
            colorHex = avatarColorHex,
            role = MemberRoles.OWNER,
            joinedAt = now
        )
        dao.upsertMember(ownerMember)

        // Standard clean categories (no fake transactions, budgets, debts, or assets)
        val defaultCats = defaultCategories(wsPersonal.id, "p")
        dao.insertCategories(defaultCats)

        // Single clean Rp 0 wallet so user can record income/expense immediately
        val defaultAccount = AccountEntity(
            id = "acc_p_cash",
            workspaceId = wsPersonal.id,
            name = "Dompet Utama",
            type = AccountTypes.CASH,
            balance = 0L,
            institutionName = "Tunai",
            colorHex = 0xFF059669,
            updatedAt = now
        )
        dao.upsertAccount(defaultAccount)

        dao.insertAuditLog(
            AuditLogEntity(
                id = "log_${UUID.randomUUID().toString().take(8)}",
                workspaceId = wsPersonal.id,
                actorMemberId = ownerMember.id,
                actorName = cleanName,
                action = "Akun Dibuat",
                detail = "Profil bersih tanpa data contoh siap digunakan",
                createdAt = now
            )
        )

        return Triple(user, wsPersonal, ownerMember)
    }

    fun computeInitials(name: String): String =
        name.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "DZ" }

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
