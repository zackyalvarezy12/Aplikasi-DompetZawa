package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.data.local.SplitBillEntity
import com.example.data.local.WorkspaceMemberEntity
import kotlin.math.abs

@Immutable
data class SimplifiedSettlement(
    val fromMember: WorkspaceMemberEntity,
    val toMember: WorkspaceMemberEntity,
    val amount: Long
)

object SplitBillSimplifier {

    /**
     * Debt Simplification algorithm across all unsettled SplitBills in a workspace.
     * Computes net balance per member and outputs minimum transfers to settle everyone.
     */
    fun calculateSettlements(
        bills: List<SplitBillEntity>,
        members: List<WorkspaceMemberEntity>
    ): List<SimplifiedSettlement> {
        val memberMap = members.associateBy { it.id }
        val netBalances = mutableMapOf<String, Long>()
        members.forEach { netBalances[it.id] = 0L }

        for (bill in bills) {
            if (bill.isSettled) continue
            val entries = bill.participantsData.split("|").mapNotNull { entry ->
                val p = entry.split(":")
                if (p.size >= 3) {
                    Triple(p[0], p[1].toLongOrNull() ?: 0L, p[2] == "true")
                } else null
            }
            for ((memberId, shareAmount, settled) in entries) {
                if (!settled && memberId != bill.paidByMemberId && shareAmount > 0L) {
                    // Payer is owed (+shareAmount), participant owes (-shareAmount)
                    netBalances[bill.paidByMemberId] = (netBalances[bill.paidByMemberId] ?: 0L) + shareAmount
                    netBalances[memberId] = (netBalances[memberId] ?: 0L) - shareAmount
                }
            }
        }

        val debtors = netBalances.filter { it.value < 0L }
            .map { it.key to abs(it.value) }
            .sortedByDescending { it.second }
            .toMutableList()
        val creditors = netBalances.filter { it.value > 0L }
            .map { it.key to it.value }
            .sortedByDescending { it.second }
            .toMutableList()

        val result = mutableListOf<SimplifiedSettlement>()
        var i = 0
        var j = 0
        while (i < debtors.size && j < creditors.size) {
            val (debtorId, debtAmt) = debtors[i]
            val (creditorId, credAmt) = creditors[j]
            val transfer = minOf(debtAmt, credAmt)
            val fromMember = memberMap[debtorId]
            val toMember = memberMap[creditorId]
            if (fromMember != null && toMember != null && transfer > 0L) {
                result.add(SimplifiedSettlement(fromMember, toMember, transfer))
            }
            val remDebt = debtAmt - transfer
            val remCred = credAmt - transfer
            if (remDebt == 0L) i++ else debtors[i] = debtorId to remDebt
            if (remCred == 0L) j++ else creditors[j] = creditorId to remCred
        }
        return result
    }
}
