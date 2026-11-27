package com.example.domain

import com.example.data.local.AccountEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.TxTypes

data class ParsedQuickTransaction(
    val type: String,
    val amount: Long,
    val title: String,
    val accountId: String?,
    val toAccountId: String?,
    val categoryId: String?,
    val tags: String,
    val isValid: Boolean
)

object SmartQuickInputParser {

    fun parse(
        rawInput: String,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): ParsedQuickTransaction {
        val text = rawInput.trim()
        if (text.isBlank()) {
            return ParsedQuickTransaction(TxTypes.EXPENSE, 0L, "", null, null, null, "", false)
        }

        // 1. Extract #tags
        val tagRegex = Regex("#[\\p{L}0-9_]+")
        val tagsList = tagRegex.findAll(text).map { it.value.lowercase() }.toList()
        val tagsString = tagsList.joinToString(",")
        var cleaned = tagRegex.replace(text, "").trim()

        // 2. Extract amount (supports: 28rb, 28k, 1.5jt, 1,5 juta, 150000, Rp 50.000)
        val amountRegex = Regex("(?i)(?:rp\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(jt|juta|rb|ribu|k|m)?\\b")
        var parsedAmount = 0L
        val matches = amountRegex.findAll(cleaned).toList()
        val bestAmountMatch = matches.firstOrNull { m ->
            val unit = m.groupValues[2]
            val num = m.groupValues[1].replace(".", "").replace(",", "").toLongOrNull() ?: 0L
            unit.isNotEmpty() || num >= 100L
        }
        if (bestAmountMatch != null) {
            val rawNum = bestAmountMatch.groupValues[1]
            val unit = bestAmountMatch.groupValues[2].lowercase()
            parsedAmount = parseAmountNumber(rawNum, unit)
            cleaned = cleaned.removeRange(bestAmountMatch.range).trim()
        }

        // 3. Detect transaction type
        val lower = text.lowercase()
        val txType = when {
            lower.contains("transfer") || lower.contains("tf ") || lower.contains("top up") || lower.contains("topup") -> TxTypes.TRANSFER
            lower.contains("gaji") || lower.contains("terima") || lower.contains("masuk") ||
                lower.contains("bonus") || lower.contains("dividen") || lower.contains("freelance") ||
                lower.contains("pemasukan") || lower.contains("jual") -> TxTypes.INCOME
            lower.contains("refund") || lower.contains("cashback") -> TxTypes.REFUND
            else -> TxTypes.EXPENSE
        }

        // 4. Match accounts
        var matchedAccount: AccountEntity? = null
        var matchedToAccount: AccountEntity? = null

        if (txType == TxTypes.TRANSFER) {
            val sortedAccs = accounts.sortedByDescending { it.name.length }
            val foundInOrder = sortedAccs
                .mapNotNull { acc ->
                    val idx = findAccountIndex(lower, acc)
                    if (idx >= 0) idx to acc else null
                }
                .sortedBy { it.first }
            matchedAccount = foundInOrder.firstOrNull()?.second
            matchedToAccount = foundInOrder.getOrNull(1)?.second
        } else {
            matchedAccount = accounts.firstOrNull { acc -> findAccountIndex(lower, acc) >= 0 }
        }

        // Remove filler prepositions & account names from title
        var titleCandidate = cleaned
        accounts.forEach { acc ->
            titleCandidate = titleCandidate.replace(Regex("(?i)\\b${Regex.escape(acc.name)}\\b"), "")
            if (acc.institutionName.isNotBlank()) {
                titleCandidate = titleCandidate.replace(Regex("(?i)\\b${Regex.escape(acc.institutionName)}\\b"), "")
            }
        }
        titleCandidate = titleCandidate
            .replace(Regex("(?i)\\b(pakai|pake|via|dari|ke|untuk|buat|sebesar|senilai|di)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        if (titleCandidate.isBlank()) {
            titleCandidate = when (txType) {
                TxTypes.TRANSFER -> "Transfer Saldo"
                TxTypes.INCOME -> "Pemasukan Cepat"
                else -> "Transaksi Cepat"
            }
        }

        // 5. Match category
        val matchedCategory = matchCategory(lower, txType, categories)

        return ParsedQuickTransaction(
            type = txType,
            amount = parsedAmount,
            title = titleCandidate,
            accountId = matchedAccount?.id ?: accounts.firstOrNull()?.id,
            toAccountId = matchedToAccount?.id,
            categoryId = matchedCategory?.id ?: categories.firstOrNull { it.type == txType }?.id ?: categories.firstOrNull()?.id,
            tags = tagsString,
            isValid = parsedAmount > 0L
        )
    }

    private fun parseAmountNumber(rawNum: String, unit: String): Long {
        return when (unit) {
            "jt", "juta" -> {
                val normalized = rawNum.replace(",", ".").toDoubleOrNull() ?: 0.0
                (normalized * 1_000_000.0).toLong()
            }
            "rb", "ribu", "k" -> {
                val normalized = rawNum.replace(",", ".").toDoubleOrNull() ?: 0.0
                (normalized * 1_000.0).toLong()
            }
            "m" -> {
                val normalized = rawNum.replace(",", ".").toDoubleOrNull() ?: 0.0
                (normalized * 1_000_000_000.0).toLong()
            }
            else -> {
                rawNum.replace(".", "").replace(",", "").toLongOrNull() ?: 0L
            }
        }
    }

    private fun findAccountIndex(lowerText: String, account: AccountEntity): Int {
        val candidates = listOf(account.name.lowercase(), account.institutionName.lowercase())
            .filter { it.isNotBlank() }
        for (c in candidates) {
            val idx = lowerText.indexOf(c)
            if (idx >= 0) return idx
        }
        return -1
    }

    private fun matchCategory(lower: String, txType: String, categories: List<CategoryEntity>): CategoryEntity? {
        if (txType == TxTypes.TRANSFER) {
            return categories.firstOrNull { it.type == TxTypes.TRANSFER }
        }
        val keywordMap = mapOf(
            listOf("kopi", "makan", "minum", "nasi", "bakso", "mie", "cafe", "resto", "sarapan", "snack", "latte", "matcha", "jajan") to "makan",
            listOf("bensin", "bbm", "pertamax", "gojek", "grab", "tol", "parkir", "kereta", "ojek", "tiket", "transport") to "transport",
            listOf("listrik", "pln", "pdam", "air", "wifi", "internet", "biznet", "pulsa", "kuota", "tagihan") to "tagihan",
            listOf("sewa", "kos", "kontrakan", "apartemen", "ipl") to "rumah",
            listOf("belanja", "indomaret", "alfamart", "supermarket", "baju", "sabun", "tokopedia", "shopee") to "belanja",
            listOf("netflix", "spotify", "bioskop", "game", "hiburan", "nonton") to "hiburan",
            listOf("dokter", "obat", "apotek", "rs", "klinik", "sehat") to "kesehatan",
            listOf("gaji", "bonus", "thr") to "gaji",
            listOf("freelance", "proyek", "klien", "usaha", "jual") to "freelance"
        )
        for ((keywords, catHint) in keywordMap) {
            if (keywords.any { lower.contains(it) }) {
                categories.firstOrNull { it.name.lowercase().contains(catHint) || it.id.contains(catHint) }?.let {
                    return it
                }
            }
        }
        return categories.firstOrNull { it.type == txType }
    }
}
