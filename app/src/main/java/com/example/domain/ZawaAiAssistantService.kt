package com.example.domain

import android.util.Log
import com.example.data.local.AccountTypes
import com.example.data.local.TxTypes
import com.example.ui.MainUiState
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Calendar

private const val TAG = "ZawaAiAssistant"
private val MODEL_CANDIDATES = listOf("gemini-1.5-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite")
private const val AI_TIMEOUT_MS = 60_000L

enum class ZawaAiActionType {
    NONE,
    RECORD_TRANSACTION,
    ADD_BILL_REMINDER,
    SET_BUDGET,
    CREATE_GOAL,
    ADD_DEBT_LOAN,
    ADD_ACCOUNT,
    SETUP_AUTO_SALARY,
    CHECK_BILL_REMINDERS_NOW,
    TOGGLE_STEALTH_MODE,
    SWITCH_THEME,
    NAVIGATE_TAB;

    companion object {
        fun fromString(raw: String?): ZawaAiActionType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NONE
    }
}

data class ZawaAiStructuredResponse(
    val replyMessage: String,
    val actionType: ZawaAiActionType,
    val title: String = "",
    val amount: Long = 0L,
    val secondaryAmount: Long = 0L,
    val txType: String = TxTypes.EXPENSE,
    val accountName: String = "",
    val categoryName: String = "",
    val dayOrOffset: Int = 0,
    val frequency: String = "MONTHLY",
    val counterparty: String = "",
    val targetParam: String = "",
    val followUps: List<String> = emptyList()
)

sealed interface AiAssistantUiState {
    data object Idle : AiAssistantUiState
    data object Loading : AiAssistantUiState
    data class Streaming(val text: String) : AiAssistantUiState
    data class Success(val text: String = "") : AiAssistantUiState
    data class Error(val message: String) : AiAssistantUiState
}

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val executedActionSummary: String? = null,
    val followUps: List<String> = emptyList(),
    val timestampMillis: Long = System.currentTimeMillis()
)

class ZawaAiAssistantService {

    private val actionResponseSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "replyMessage" to Schema.string(
                    description = "Jawaban lengkap dalam Bahasa Indonesia yang ramah, ringkas, dan jelas untuk pengguna."
                ),
                "actionType" to Schema.enumeration(
                    values = ZawaAiActionType.entries.map { it.name },
                    description = "Jenis aksi aplikasi yang harus dijalankan otomatis. Gunakan NONE jika hanya menjawab, menganalisa, atau memberi saran."
                ),
                "title" to Schema.string(
                    description = "Judul transaksi, nama tagihan, nama target tabungan, nama utang/piutang, atau nama akun dompet baru.",
                    nullable = true
                ),
                "amount" to Schema.integer(
                    description = "Nominal utama dalam Rupiah bulat (mis. 25000, 1500000). Gunakan 0 jika tidak relevan.",
                    nullable = true
                ),
                "secondaryAmount" to Schema.integer(
                    description = "Nominal kedua opsional (mis. setoran bulanan target tabungan, atau jumlah hari kerja sebulan).",
                    nullable = true
                ),
                "txType" to Schema.string(
                    description = "EXPENSE, INCOME, atau TRANSFER untuk RECORD_TRANSACTION.",
                    nullable = true
                ),
                "accountName" to Schema.string(
                    description = "Nama akun/dompet yang cocok (mis. Dompet Utama, BCA, GoPay).",
                    nullable = true
                ),
                "categoryName" to Schema.string(
                    description = "Nama kategori yang paling cocok dari daftar kategori pengguna.",
                    nullable = true
                ),
                "dayOrOffset" to Schema.integer(
                    description = "Tanggal jatuh tempo (1-31) untuk tagihan atau tanggal gajian (1-31) untuk gaji otomatis.",
                    nullable = true
                ),
                "frequency" to Schema.string(
                    description = "MONTHLY, WEEKLY, YEARLY, atau DAILY untuk tagihan berulang.",
                    nullable = true
                ),
                "counterparty" to Schema.string(
                    description = "Nama pihak terkait untuk utang/piutang atau tujuan transfer.",
                    nullable = true
                ),
                "targetParam" to Schema.string(
                    description = "Parameter tambahan: DEBT/LOAN untuk utang, CASH/BANK/EWALLET/CREDIT_CARD untuk akun, DASHBOARD/LEDGER/WEALTH/GROUP_DEBT/ADVISOR untuk navigasi, ON/OFF untuk stealth, atau AUTO_TIME_PASTEL/CUTE_PASTEL/BRIGHT_SUNNY/DARK_ELEGANT/COLORFUL_POP/EMERALD_PRO/OCEAN_BREEZE untuk tema.",
                    nullable = true
                ),
                "followUps" to Schema.array(
                    items = Schema.string(),
                    description = "2 sampai 3 saran pertanyaan atau perintah lanjutan singkat."
                )
            ),
            optionalProperties = listOf(
                "title",
                "amount",
                "secondaryAmount",
                "txType",
                "accountName",
                "categoryName",
                "dayOrOffset",
                "frequency",
                "counterparty",
                "targetParam"
            )
        )
    }

    private val structuredSystemInstruction = content {
        text(
            """
            Kamu adalah Asisten Zawa, asisten keuangan keluarga yang ramah, sabar, dan mudah dimengerti di aplikasi "Dompet Zawa".
            Tugasmu membantu pengguna:
            1. Mengelola keuangan secara langsung lewat fitur aplikasi (mencatat uang masuk/keluar/pindah saldo, menambah pengingat tagihan bulanan 3 hari & 1 hari sebelum jatuh tempo, mengatur batas belanja, membuat target tabungan, mencatat utang/piutang, menambah rekening/dompet, mengatur gaji otomatis, mengganti tema warna, menyembunyikan/menampilkan angka saldo, mengecek pengingat tagihan, atau membuka halaman tertentu).
            2. Memeriksa kondisi keuangan nyata pengguna (uang masuk dan keluar bulan ini, batas belanja, cicilan/utang, tabungan darurat, dan jajan kecil yang sering tidak terasa).
            3. Memberikan saran praktis dalam Bahasa Indonesia sehari-hari yang hangat, sopan, dan mudah dipahami orang tua maupun orang awam.

            Aturan Penting:
            - HINDARI istilah asing atau istilah bank/ekonomi yang sulit dipahami orang awam (jangan pakai kata: cashflow, saving rate, latte factor, net worth, balance sheet, snowball, avalanche, rollover, stealth mode, workspace, split bill, likuid). Gunakan kata sederhana seperti: uang masuk/keluar, porsi tabungan, jajan kecil, total harta bersih, tagihan, patungan, uang siap pakai, ruang keuangan, mode sembunyikan angka.
            - Selalu gunakan data catatan keuangan pengguna yang diberikan; jangan mengarang angka fiktif jika datanya Rp 0 atau kosong.
            - Jika pengguna meminta melakukan sesuatu (contoh: "catat makan 35rb", "ingatkan tagihan listrik 350rb tanggal 20", "set batas jajan 1jt", "buat target dana darurat 10jt", "atur gaji 8jt tanggal 17", "buka laporan", "ganti tema gelap"), isi `actionType` dan field terkait secara akurat agar aplikasi langsung menjalankannya!
            - Jika pengguna hanya bertanya atau meminta saran tanpa mengubah data, isi `actionType` dengan "NONE".
            - Semua respon `replyMessage` wajib dalam Bahasa Indonesia sederhana yang ramah untuk orang tua.
            """.trimIndent()
        )
    }

    private val streamingSystemInstruction = content {
        text(
            """
            Kamu adalah Asisten Zawa, pendamping keuangan keluarga di Dompet Zawa.
            Berikan pemeriksaan yang jelas, terstruktur dengan poin bernomor yang rapi di layar HP, dan saran praktis dalam Bahasa Indonesia sehari-hari yang sangat mudah dipahami orang tua dan orang awam.
            HINDARI istilah asing atau istilah keuangan rumit (seperti cashflow, saving rate, latte factor, net worth, likuid, ekuitas, defisit, surplus). Pakai bahasa sederhana seperti uang masuk/keluar, sisa uang, uang siap pakai, porsi tabungan, total harta bersih.
            Fokus pada: (1) Ringkasan Uang Masuk, Keluar & Sisa Saldo, (2) Cek Batas Belanja & Saran Hemat, (3) Pengingat Tagihan & Utang Prioritas, dan (4) Langkah Praktis Minggu Ini.
            Jangan mengarang catatan atau saldo fiktif.
            """.trimIndent()
        )
    }

    suspend fun askAndPlanAction(
        userMessage: String,
        history: List<AiChatMessage>,
        state: MainUiState
    ): Result<ZawaAiStructuredResponse> {
        return try {
            val contextSnapshot = buildFinancialContextSnapshot(state)
            val recentDialogue = history.takeLast(6).joinToString("\n") { msg ->
                val speaker = if (msg.isUser) "Pengguna" else "Asisten Zawa"
                "$speaker: ${msg.text}"
            }

            val fullPrompt = buildString {
                appendLine("=== KONTEKS KEUANGAN PENGGUNA SAAT INI ===")
                appendLine(contextSnapshot)
                if (recentDialogue.isNotBlank()) {
                    appendLine("=== PERCAKAPAN SEBELUMNYA ===")
                    appendLine(recentDialogue)
                }
                appendLine("=== PERMINTAAN PENGGUNA ===")
                appendLine(userMessage.trim())
            }

            var responseText: String? = null
            var lastEx: Exception? = null

            for (modelName in MODEL_CANDIDATES) {
                try {
                    val model = Firebase.ai.generativeModel(
                        modelName = modelName,
                        generationConfig = generationConfig {
                            temperature = 0.35f
                            responseMimeType = "application/json"
                            responseSchema = actionResponseSchema
                        },
                        systemInstruction = structuredSystemInstruction
                    )
                    val response = withTimeout(AI_TIMEOUT_MS) {
                        model.generateContent(fullPrompt)
                    }
                    val text = response.text
                    if (!text.isNullOrBlank()) {
                        responseText = text
                        break
                    }
                } catch (e: Exception) {
                    lastEx = e
                    Log.w(TAG, "Model $modelName generation failed, trying fallback", e)
                }
            }

            val jsonText = responseText
            if (jsonText.isNullOrBlank()) {
                throw lastEx ?: IllegalStateException("Asisten Zawa belum memberikan jawaban.")
            }

            val json = JSONObject(jsonText)
            val followUpsArr = json.optJSONArray("followUps")
            val followUps = buildList {
                if (followUpsArr != null) {
                    for (i in 0 until followUpsArr.length()) {
                        val item = followUpsArr.optString(i).trim()
                        if (item.isNotBlank()) add(item)
                    }
                }
            }

            Result.success(
                ZawaAiStructuredResponse(
                    replyMessage = json.optString("replyMessage").ifBlank {
                        "Permintaan diproses oleh Asisten Zawa."
                    },
                    actionType = ZawaAiActionType.fromString(json.optString("actionType")),
                    title = json.optString("title").takeIf { it != "null" }.orEmpty(),
                    amount = json.optLong("amount", 0L).coerceAtLeast(0L),
                    secondaryAmount = json.optLong("secondaryAmount", 0L).coerceAtLeast(0L),
                    txType = json.optString("txType").takeIf { it != "null" && it.isNotBlank() } ?: TxTypes.EXPENSE,
                    accountName = json.optString("accountName").takeIf { it != "null" }.orEmpty(),
                    categoryName = json.optString("categoryName").takeIf { it != "null" }.orEmpty(),
                    dayOrOffset = json.optInt("dayOrOffset", 0),
                    frequency = json.optString("frequency").takeIf { it != "null" && it.isNotBlank() } ?: "MONTHLY",
                    counterparty = json.optString("counterparty").takeIf { it != "null" }.orEmpty(),
                    targetParam = json.optString("targetParam").takeIf { it != "null" }.orEmpty(),
                    followUps = followUps
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Zawa AI request timed out", e)
            Result.failure(
                IllegalStateException("Asisten Zawa butuh waktu terlalu lama untuk menjawab. Pastikan internet lancar lalu coba lagi.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Zawa AI inference failed across all models", e)
            Result.failure(IllegalStateException(formatFriendlyAiError(e)))
        }
    }

    fun streamDeepAnalysis(
        state: MainUiState,
        customFocusPrompt: String = "Analisa kondisi keuanganku saat ini, temukan pengingat penting, dan berikan saran terbaik."
    ): Flow<String> = flow {
        val contextSnapshot = buildFinancialContextSnapshot(state)
        val prompt = buildString {
            appendLine("=== KONTEKS KEUANGAN PENGGUNA SAAT INI ===")
            appendLine(contextSnapshot)
            appendLine("=== PERMINTAAN ANALISA ===")
            appendLine(customFocusPrompt)
        }
        val accumulated = StringBuilder()
        var streamedSuccessfully = false
        var lastEx: Exception? = null

        for (modelName in MODEL_CANDIDATES) {
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig {
                        temperature = 0.55f
                    },
                    systemInstruction = streamingSystemInstruction
                )
                withTimeout(AI_TIMEOUT_MS) {
                    model.generateContentStream(prompt).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(accumulated.toString())
                        }
                    }
                }
                streamedSuccessfully = true
                break
            } catch (e: Exception) {
                lastEx = e
                Log.w(TAG, "Streaming model $modelName failed, trying fallback", e)
            }
        }

        if (!streamedSuccessfully && accumulated.isBlank()) {
            throw lastEx ?: IllegalStateException("Gagal memeriksa keuangan bersama Asisten Zawa.")
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Asisten Zawa belum memberikan hasil pemeriksaan.")
        }
    }

    private fun formatFriendlyAiError(e: Exception): String {
        val raw = "${e::class.java.simpleName}: ${e.localizedMessage.orEmpty()}"
        Log.e(TAG, "AI Error Detail: $raw", e)
        return when {
            raw.contains("403", ignoreCase = true) ||
                raw.contains("AppCheck", ignoreCase = true) ||
                raw.contains("App Check", ignoreCase = true) ||
                raw.contains("attestation", ignoreCase = true) ||
                raw.contains("PERMISSION_DENIED", ignoreCase = true) ||
                raw.contains("API has not been used", ignoreCase = true) ||
                raw.contains("disabled", ignoreCase = true) -> {
                "Asisten Zawa sedang menghubungkan layanan keamanan server. Coba beberapa saat lagi ya."
            }
            raw.contains("UnknownHost", ignoreCase = true) ||
                raw.contains("Unable to resolve host", ignoreCase = true) ||
                raw.contains("ConnectException", ignoreCase = true) ||
                raw.contains("SocketTimeout", ignoreCase = true) ||
                raw.contains("network", ignoreCase = true) -> {
                "Tidak bisa terhubung ke Asisten Zawa. Periksa koneksi internet Anda lalu coba lagi."
            }
            else -> {
                val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "kendala sementara"
                "Gagal menghubungi Asisten Zawa ($detail). Coba lagi ya."
            }
        }
    }

    fun buildFinancialContextSnapshot(state: MainUiState): String {
        val cal = Calendar.getInstance()
        val today = CurrencyFormatter.formatDateShort(cal.timeInMillis)
        val monthKey = CurrencyFormatter.currentMonthKey()
        val userName = state.currentUser?.name ?: state.activeMember?.name ?: "Pengguna"
        val wsName = state.activeWorkspace?.name ?: "Keuangan Pribadi"

        val accountsSummary = if (state.accounts.isEmpty()) "Belum ada akun" else {
            state.accounts.joinToString("; ") { "${it.name} (${it.type}): Rp ${it.balance}" }
        }
        val expenseCats = state.categories.filter { it.type == TxTypes.EXPENSE }.joinToString(", ") { it.name }
        val incomeCats = state.categories.filter { it.type == TxTypes.INCOME }.joinToString(", ") { it.name }

        val recentTxs = if (state.transactions.isEmpty()) "Belum ada transaksi" else {
            state.transactions.take(10).joinToString("\n") { tx ->
                val cat = state.categoryMap[tx.categoryId]?.name ?: "-"
                val acc = state.accountMap[tx.accountId]?.name ?: "-"
                "- [${CurrencyFormatter.formatDateShort(tx.dateMillis)}] ${tx.type} '${tx.title}' Rp ${tx.amount} | Kategori: $cat | Akun: $acc"
            }
        }

        val monthBudgets = state.budgets.filter { it.monthYear == monthKey }
        val spentByCat = state.transactions
            .filter { (it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT) && CurrencyFormatter.currentMonthKey(it.dateMillis) == monthKey }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val budgetsSummary = if (monthBudgets.isEmpty()) "Belum ada anggaran diatur" else {
            monthBudgets.joinToString("; ") { b ->
                val name = state.categoryMap[b.categoryId]?.name ?: b.categoryId
                val spent = spentByCat[b.categoryId] ?: 0L
                val limit = b.limitAmount + if (b.rolloverEnabled) b.rolloverAmount else 0L
                "$name: terpakai Rp $spent / batas Rp $limit"
            }
        }

        val nowMs = System.currentTimeMillis()
        val billsSummary = if (state.recurringRules.isEmpty()) "Belum ada jadwal tagihan" else {
            state.recurringRules.joinToString("; ") { r ->
                val daysDiff = ((r.nextDueDateMillis - nowMs) / (24L * 60 * 60 * 1000L)).toInt()
                "${r.title} Rp ${r.amount} (jatuh tempo ${CurrencyFormatter.formatDateShort(r.nextDueDateMillis)}, H-${daysDiff}, reminder=${if (r.reminderEnabled) "aktif" else "mati"})"
            }
        }

        val debtsSummary = if (state.debtsLoans.isEmpty()) "Tidak ada utang/piutang" else {
            state.debtsLoans.joinToString("; ") { d ->
                "${d.kind} '${d.title}' sisa Rp ${d.remainingAmount} (bunga ${d.interestRateAnnual}%)"
            }
        }

        val goalsSummary = if (state.goals.isEmpty()) "Belum ada target tabungan" else {
            state.goals.joinToString("; ") { g ->
                "${g.title}: Rp ${g.currentAmount} / Rp ${g.targetAmount}"
            }
        }

        val salaryInfo = if (state.settings.isAutoSalaryEnabled) {
            "Aktif (Gaji pokok Rp ${state.settings.monthlyBaseSalary}, tgl ${state.settings.salaryPaydayDay}, ${state.settings.salaryWorkingDays} hari kerja)"
        } else "Belum diaktifkan"

        val advisorSummary = state.advisorReport?.let { rep ->
            "Nilai Kesehatan: ${rep.score}/100 (${rep.gradeLabel}). Catatan otomatis: " +
                rep.insights.take(4).joinToString(" | ") { "${it.title}: ${it.description}" }
        } ?: "-"

        return """
            Tanggal hari ini: $today (Bulan: $monthKey)
            Pengguna: $userName | Ruang Keuangan: $wsName (${state.activeWorkspace?.type ?: "PERSONAL"})
            Total Harta Bersih: Rp ${state.netWorth} | Uang Siap Pakai: Rp ${state.totalLiquidCash} | Harta/Emas/Tabungan: Rp ${state.totalInvestments} | Total Utang: Rp ${state.totalDebts}
            Bulan Ini -> Pemasukan: Rp ${state.monthIncome} | Pengeluaran: Rp ${state.monthExpense} | Selisih: Rp ${state.monthNetCashflow} | Porsi Tabungan: ${"%.1f".format(state.savingRatePercent)}%
            Akun/Dompet: $accountsSummary
            Kategori Pengeluaran: $expenseCats
            Kategori Pemasukan: $incomeCats
            Anggaran Bulan Ini: $budgetsSummary
            Tagihan Rutin & Pengingat H-3/H-1: $billsSummary
            Utang & Piutang: $debtsSummary
            Target Tabungan: $goalsSummary
            Gaji Otomatis: $salaryInfo
            $advisorSummary
            15 Transaksi Terbaru:
            $recentTxs
        """.trimIndent()
    }
}
