package com.example.domain

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object CurrencyFormatter {
    private val indonesianLocale = Locale("id", "ID")
    private val currencyFormat: NumberFormat = NumberFormat.getNumberInstance(indonesianLocale).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    fun formatIdr(amount: Long, stealth: Boolean = false, showSign: Boolean = false): String {
        if (stealth) return "Rp •••••••"
        val prefix = when {
            showSign && amount > 0 -> "+Rp "
            amount < 0 -> "-Rp "
            else -> "Rp "
        }
        return "$prefix${currencyFormat.format(abs(amount))}"
    }

    fun formatIdr(amount: Double, stealth: Boolean = false, showSign: Boolean = false): String =
        formatIdr(amount.toLong(), stealth, showSign)

    fun formatCompactIdr(amount: Long, stealth: Boolean = false): String {
        if (stealth) return "Rp •••"
        val sign = if (amount < 0) "-" else ""
        val absVal = abs(amount).toDouble()
        return when {
            absVal >= 1_000_000_000.0 -> String.format(indonesianLocale, "%sRp %.1f M", sign, absVal / 1_000_000_000.0)
            absVal >= 1_000_000.0 -> String.format(indonesianLocale, "%sRp %.1f Jt", sign, absVal / 1_000_000.0)
            absVal >= 1_000.0 -> String.format(indonesianLocale, "%sRp %.0f Rb", sign, absVal / 1_000.0)
            else -> "${sign}Rp ${currencyFormat.format(abs(amount))}"
        }
    }

    fun formatRelativeDateTime(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        val timeStr = SimpleDateFormat("HH:mm", indonesianLocale).format(Date(timestamp))
        return when {
            now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) -> "Hari ini, $timeStr"
            now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR) == 1 -> "Kemarin, $timeStr"
            else -> SimpleDateFormat("dd MMM yyyy, HH:mm", indonesianLocale).format(Date(timestamp))
        }
    }

    fun formatDateShort(timestamp: Long): String =
        SimpleDateFormat("dd MMM yyyy", indonesianLocale).format(Date(timestamp))

    fun currentMonthKey(timestamp: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date(timestamp))

    fun monthLabel(monthKey: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM", Locale.US).parse(monthKey) ?: return monthKey
            SimpleDateFormat("MMMM yyyy", indonesianLocale).format(date)
        } catch (_: Exception) {
            monthKey
        }
    }
}
