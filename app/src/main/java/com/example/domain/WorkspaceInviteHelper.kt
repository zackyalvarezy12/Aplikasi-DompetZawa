package com.example.domain

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.local.WorkspaceEntity

object WorkspaceInviteHelper {

    fun buildInviteMessage(
        workspace: WorkspaceEntity,
        targetName: String? = null
    ): String {
        val greeting = if (!targetName.isNullOrBlank()) "Halo ${targetName.trim()}!" else "Halo!"
        return "$greeting Yuk gabung ke catatan keuangan bersama '${workspace.name}' di Dompet Zawa.\n\n" +
            "Kode Undangan: ${workspace.inviteCode}\n" +
            "Tautan: ${workspace.inviteLink}\n\n" +
            "Buka aplikasi Dompet Zawa > pilih Ruang Keuangan > tab Buat / Gabung > masukkan kode ${workspace.inviteCode}."
    }

    fun copyInvite(
        context: Context,
        workspace: WorkspaceEntity,
        onFeedback: (String) -> Unit
    ) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(
            ClipData.newPlainText(
                "Undangan Ruang Keuangan",
                "${workspace.inviteCode} (${workspace.inviteLink})"
            )
        )
        onFeedback("Kode undangan ${workspace.inviteCode} disalin!")
    }

    fun shareInvite(
        context: Context,
        workspace: WorkspaceEntity,
        targetName: String? = null,
        onFeedback: (String) -> Unit
    ) {
        val message = buildInviteMessage(workspace, targetName)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Undangan Ruang Keuangan ${workspace.name} - Dompet Zawa")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(sendIntent, "Kirim Undangan Ruang Keuangan")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(chooser)
        }.onFailure {
            copyInvite(context, workspace, onFeedback)
        }
    }

    fun contactViaWhatsApp(
        context: Context,
        workspace: WorkspaceEntity,
        phoneNumber: String = "",
        targetName: String? = null,
        onFeedback: (String) -> Unit
    ) {
        val message = buildInviteMessage(workspace, targetName)
        val normalizedPhone = normalizeIndonesianPhone(phoneNumber)
        val url = if (normalizedPhone.isNotBlank()) {
            "https://wa.me/$normalizedPhone?text=${Uri.encode(message)}"
        } else {
            "https://wa.me/?text=${Uri.encode(message)}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            onFeedback("WhatsApp tidak ditemukan, membuka menu bagikan...")
            shareInvite(context, workspace, targetName, onFeedback)
        }
    }

    fun contactViaEmail(
        context: Context,
        workspace: WorkspaceEntity,
        recipientEmail: String = "",
        targetName: String? = null,
        onFeedback: (String) -> Unit
    ) {
        val subject = "Undangan Ruang Keuangan '${workspace.name}' - Dompet Zawa"
        val body = buildInviteMessage(workspace, targetName)
        val cleanEmail = recipientEmail.trim().takeIf { it.contains("@") }.orEmpty()
        val mailUri = Uri.parse(
            "mailto:${Uri.encode(cleanEmail)}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
        )
        val intent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            onFeedback("Aplikasi email tidak ditemukan, membuka menu bagikan...")
            shareInvite(context, workspace, targetName, onFeedback)
        }
    }

    fun normalizeIndonesianPhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        return when {
            digits.startsWith("62") -> digits
            digits.startsWith("0") -> "62" + digits.drop(1)
            digits.startsWith("8") -> "62$digits"
            else -> digits
        }
    }
}
