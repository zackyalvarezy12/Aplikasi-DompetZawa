package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.*
import com.example.data.sync.SyncVisualStatus
import com.example.domain.CurrencyFormatter
import com.example.domain.WorkspaceInviteHelper
import com.example.ui.MainUiState
import com.example.ui.theme.*

@Composable
fun NusaKasTopBar(
    state: MainUiState,
    onOpenWorkspaceModal: () -> Unit,
    onOpenThemeModal: () -> Unit,
    onOpenSyncSecurityModal: () -> Unit,
    onToggleStealth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Workspace Switcher Pill
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .testTag("workspace_switcher_button")
                    .clickable(onClick = onOpenWorkspaceModal)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SvgLogo(
                        resId = if (state.isSharedWorkspace) R.drawable.ic_logo_workspace_shared else R.drawable.ic_logo_nusakas,
                        contentDescription = "Ruang Keuangan",
                        size = 28.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    val activePurpose = state.activeWorkspace?.let { state.settings.workspacePurposes[it.id] }
                    Column {
                        Text(
                            text = state.activeWorkspace?.name ?: "Keuangan Pribadi",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                state.isSharedWorkspace -> "${activePurpose ?: "Bersama"} • ${CurrencyFormatter.memberRoleLabel(state.activeMember?.role ?: "OWNER")}"
                                !activePurpose.isNullOrBlank() -> "$activePurpose • Di HP"
                                else -> "Tersimpan di HP & Pribadi"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Ganti Ruang Keuangan",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Real-time Sync Status Badge
                val (badgeColor, dotColor, badgeLabel, badgeTextColor) = when (state.syncVisualStatus) {
                    SyncVisualStatus.ONLINE_SYNCED -> listOf(
                        IncomeGreenSoft,
                        IncomeGreen,
                        "Terhubung",
                        PastelPlumText
                    )
                    SyncVisualStatus.SYNCING -> listOf(
                        WarningAmberSoft,
                        WarningAmber,
                        "Menyimpan (${state.pendingSyncCount})",
                        PastelPlumText
                    )
                    SyncVisualStatus.OFFLINE_LOCAL -> listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        "Tanpa Internet",
                        MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeColor as Color,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(20.dp))
                        .testTag("sync_status_badge")
                        .clickable(onClick = onOpenSyncSecurityModal)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor as Color)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = badgeLabel as String,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeTextColor as Color
                        )
                    }
                }

                // Theme Palette Picker
                IconButton(
                    onClick = onOpenThemeModal,
                    modifier = Modifier.testTag("open_theme_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = "Pilih Tema Warna",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Stealth Mode Eye Toggle
                IconButton(
                    onClick = onToggleStealth,
                    modifier = Modifier.testTag("stealth_mode_toggle")
                ) {
                    Icon(
                        imageVector = if (state.settings.isStealthMode) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (state.settings.isStealthMode) "Tampilkan Angka" else "Sembunyikan Angka",
                        tint = if (state.settings.isStealthMode) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // User Profile, Cloud Sync & Security Pill
                val userName = state.currentUser?.name ?: state.activeMember?.name ?: "Profil"
                val avatarColor = Color(state.currentUser?.avatarColorHex ?: 0xFF065F46L)
                val initials = userName.trim().split(" ").filter { it.isNotBlank() }.let { parts ->
                    when {
                        parts.isEmpty() -> "DZ"
                        parts.size == 1 -> parts[0].take(2).uppercase()
                        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
                    }
                }
                IconButton(
                    onClick = onOpenSyncSecurityModal,
                    modifier = Modifier.testTag("open_settings_button")
                ) {
                    UserAvatar(
                        initials = initials,
                        avatarColorHex = state.currentUser?.avatarColorHex ?: 0xFF065F46L,
                        photoPath = state.settings.profilePhotoPath,
                        size = 34.dp,
                        textStyle = MaterialTheme.typography.labelMedium,
                        contentDescription = "Buka Profil & Keamanan"
                    )
                }
            }
        }
    }
}

private data class WorkspacePurposeOption(
    val label: String,
    val suggestedName: String,
    val defaultType: String
)

private val WORKSPACE_PURPOSE_OPTIONS = listOf(
    WorkspacePurposeOption("Keuangan Pribadi", "Keuangan Pribadi", WorkspaceTypes.PERSONAL),
    WorkspacePurposeOption("Keuangan Keluarga", "Keuangan Keluarga", WorkspaceTypes.SHARED),
    WorkspacePurposeOption("Usaha / Bisnis", "Kas Usaha", WorkspaceTypes.PERSONAL),
    WorkspacePurposeOption("Tabungan Nikah", "Tabungan Nikah", WorkspaceTypes.SHARED),
    WorkspacePurposeOption("Liburan & Travel", "Dana Liburan", WorkspaceTypes.SHARED),
    WorkspacePurposeOption("Kos / Kontrakan", "Uang Kos", WorkspaceTypes.SHARED),
    WorkspacePurposeOption("Proyek / Event", "Kas Proyek", WorkspaceTypes.SHARED)
)

@Composable
fun WorkspaceCollaborationDialog(
    state: MainUiState,
    onDismiss: () -> Unit,
    onSwitchWorkspace: (WorkspaceEntity) -> Unit,
    onCreateWorkspace: (name: String, type: String, purpose: String) -> Unit,
    onJoinWorkspace: (code: String) -> Unit,
    onInviteMember: (name: String, email: String, role: String) -> Unit,
    onChangeRole: (memberId: String, role: String) -> Unit,
    onSwitchActiveMemberDemo: (WorkspaceMemberEntity) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) } // 0: Daftar, 1: Anggota & Undangan, 2: Buat / Gabung

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.86f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        SvgLogo(resId = R.drawable.ic_logo_workspace_shared, contentDescription = null, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Ruang Keuangan & Bersama", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Pisahkan uang pribadi, keluarga, usaha, atau keperluan lain",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Daftar Ruang", "Anggota & Undang", "Buat / Gabung").forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = tab == idx,
                            onClick = { tab = idx },
                            shape = SegmentedButtonDefaults.itemShape(idx, 3)
                        ) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                when (tab) {
                    0 -> {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(state.workspaces) { ws ->
                                    val isSelected = ws.id == state.activeWorkspace?.id
                                    val purpose = state.settings.workspacePurposes[ws.id]
                                        ?: if (ws.type == WorkspaceTypes.SHARED) "Keuangan Bersama" else "Keuangan Pribadi"
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("workspace_item_${ws.id}")
                                            .clickable {
                                                onSwitchWorkspace(ws)
                                                onDismiss()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SvgLogo(
                                                resId = if (ws.type == WorkspaceTypes.SHARED) R.drawable.ic_logo_workspace_shared else R.drawable.ic_logo_workspace_personal,
                                                contentDescription = ws.type,
                                                size = 38.dp
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ws.name, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    text = if (ws.type == WorkspaceTypes.SHARED) {
                                                        "$purpose • Bersama • Kode: ${ws.inviteCode}"
                                                    } else "$purpose • Pribadi (Di HP)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = "Aktif", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { tab = 2 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("add_workspace_from_list_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tambah Ruang Keuangan Baru")
                            }
                        }
                    }

                    1 -> {
                        val activeWs = state.activeWorkspace
                        var newName by remember { mutableStateOf("") }
                        var newEmail by remember { mutableStateOf("") }
                        var newPhone by remember { mutableStateOf("") }
                        var newRole by remember { mutableStateOf(MemberRoles.EDITOR) }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (activeWs != null && activeWs.type == WorkspaceTypes.PERSONAL) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "Hubungi & Undang Orang Lain",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Catatan ini masih mode Pribadi. Buat Ruang Keuangan Bersama agar bisa mengajak keluarga atau teman lewat WhatsApp, Email, atau Tautan.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.88f)
                                            )
                                            Button(
                                                onClick = { tab = 2 },
                                                modifier = Modifier.fillMaxWidth().testTag("switch_to_create_shared_workspace")
                                            ) {
                                                Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Buat Ruang Bersama & Undang")
                                            }
                                        }
                                    }
                                }
                            }

                            if (activeWs != null && activeWs.type == WorkspaceTypes.SHARED) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                "Kode & Hubungi via Undangan",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        activeWs.inviteCode,
                                                        style = MaterialTheme.typography.headlineSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        activeWs.inviteLink,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        WorkspaceInviteHelper.copyInvite(context, activeWs, onNotify)
                                                    },
                                                    modifier = Modifier.testTag("copy_workspace_invite_button")
                                                ) {
                                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Salin")
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        WorkspaceInviteHelper.shareInvite(context, activeWs, onFeedback = onNotify)
                                                    },
                                                    modifier = Modifier.weight(1f).testTag("share_workspace_invite_button")
                                                ) {
                                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Bagikan", maxLines = 1)
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        WorkspaceInviteHelper.contactViaWhatsApp(context, activeWs, onFeedback = onNotify)
                                                    },
                                                    modifier = Modifier.weight(1f).testTag("whatsapp_workspace_invite_button")
                                                ) {
                                                    Icon(Icons.Outlined.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("WhatsApp", maxLines = 1)
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        WorkspaceInviteHelper.contactViaEmail(context, activeWs, onFeedback = onNotify)
                                                    },
                                                    modifier = Modifier.weight(1f).testTag("email_workspace_invite_button")
                                                ) {
                                                    Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Email", maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Text(
                                    "Daftar Anggota & Hak Akses (Ketuk 'Pakai' untuk coba peran)",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            items(state.members) { m ->
                                val isMe = m.id == state.activeMember?.id
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        contentColor = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(m.colorHex)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(m.avatarInitials, color = Color.White, style = MaterialTheme.typography.labelLarge)
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = m.name + if (isMe) " (Aktif)" else "",
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    Text(
                                                        m.email,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            TextButton(onClick = { onSwitchActiveMemberDemo(m) }) {
                                                Text(if (isMe) "Aktif" else "Pakai")
                                            }
                                        }
                                        if (state.isOwner && !isMe) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                listOf(MemberRoles.OWNER, MemberRoles.EDITOR, MemberRoles.VIEWER).forEach { r ->
                                                    FilterChip(
                                                        selected = m.role == r,
                                                        onClick = { onChangeRole(m.id, r) },
                                                        label = { Text(CurrencyFormatter.memberRoleLabel(r), style = MaterialTheme.typography.labelMedium) }
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                "Peran: ${CurrencyFormatter.memberRoleLabel(m.role)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (!isMe && activeWs != null && activeWs.type == WorkspaceTypes.SHARED) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        WorkspaceInviteHelper.contactViaWhatsApp(
                                                            context = context,
                                                            workspace = activeWs,
                                                            targetName = m.name,
                                                            onFeedback = onNotify
                                                        )
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("WhatsApp", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        WorkspaceInviteHelper.contactViaEmail(
                                                            context = context,
                                                            workspace = activeWs,
                                                            recipientEmail = m.email,
                                                            targetName = m.name,
                                                            onFeedback = onNotify
                                                        )
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Email", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        WorkspaceInviteHelper.shareInvite(
                                                            context = context,
                                                            workspace = activeWs,
                                                            targetName = m.name,
                                                            onFeedback = onNotify
                                                        )
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Share, contentDescription = "Bagikan Undangan")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.isOwner && activeWs?.type == WorkspaceTypes.SHARED) {
                                item {
                                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                    Text("Tambah & Hubungi Anggota Baru", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        label = { Text("Nama Anggota") },
                                        placeholder = { Text("mis. Najwa Nahdatul Huda") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("invite_member_name_input")
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = newEmail,
                                        onValueChange = { newEmail = it },
                                        label = { Text("Email (opsional)") },
                                        placeholder = { Text("mis. najwa@email.com") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("invite_member_email_input")
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = newPhone,
                                        onValueChange = { newPhone = it },
                                        label = { Text("No. WhatsApp / HP (opsional)") },
                                        placeholder = { Text("mis. 081234567890") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("invite_member_phone_input")
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(MemberRoles.EDITOR, MemberRoles.VIEWER).forEach { r ->
                                            FilterChip(
                                                selected = newRole == r,
                                                onClick = { newRole = r },
                                                label = { Text(CurrencyFormatter.memberRoleLabel(r)) }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            if (newName.isNotBlank()) {
                                                onInviteMember(
                                                    newName.trim(),
                                                    newEmail.trim().ifBlank { "${newName.trim().lowercase().replace(" ", ".")}@nusakas.id" },
                                                    newRole
                                                )
                                                newName = ""
                                                newEmail = ""
                                                newPhone = ""
                                            }
                                        },
                                        enabled = newName.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().testTag("invite_member_submit_button")
                                    ) {
                                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Undang Anggota")
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val targetName = newName.trim().takeIf { it.isNotBlank() }
                                                if (targetName != null) {
                                                    onInviteMember(
                                                        targetName,
                                                        newEmail.trim().ifBlank { "${targetName.lowercase().replace(" ", ".")}@nusakas.id" },
                                                        newRole
                                                    )
                                                }
                                                WorkspaceInviteHelper.contactViaWhatsApp(
                                                    context = context,
                                                    workspace = activeWs,
                                                    phoneNumber = newPhone,
                                                    targetName = targetName,
                                                    onFeedback = onNotify
                                                )
                                                newName = ""
                                                newEmail = ""
                                                newPhone = ""
                                            },
                                            modifier = Modifier.weight(1f).testTag("invite_and_whatsapp_button")
                                        ) {
                                            Icon(Icons.Outlined.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Undang & WA", maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val targetName = newName.trim().takeIf { it.isNotBlank() }
                                                if (targetName != null) {
                                                    onInviteMember(
                                                        targetName,
                                                        newEmail.trim().ifBlank { "${targetName.lowercase().replace(" ", ".")}@nusakas.id" },
                                                        newRole
                                                    )
                                                }
                                                WorkspaceInviteHelper.contactViaEmail(
                                                    context = context,
                                                    workspace = activeWs,
                                                    recipientEmail = newEmail,
                                                    targetName = targetName,
                                                    onFeedback = onNotify
                                                )
                                                newName = ""
                                                newEmail = ""
                                                newPhone = ""
                                            },
                                            modifier = Modifier.weight(1f).testTag("invite_and_email_button")
                                        ) {
                                            Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Undang & Email", maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        var wsName by remember { mutableStateOf("") }
                        var wsPurpose by remember { mutableStateOf("Usaha / Bisnis") }
                        var wsType by remember { mutableStateOf(WorkspaceTypes.PERSONAL) }
                        var joinCode by remember { mutableStateOf("") }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text("Buat Ruang Keuangan Baru", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Pilih keperluan di bawah atau tulis sendiri sesuai kebutuhan:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(WORKSPACE_PURPOSE_OPTIONS) { opt ->
                                    FilterChip(
                                        selected = wsPurpose.equals(opt.label, ignoreCase = true),
                                        onClick = {
                                            wsPurpose = opt.label
                                            wsType = opt.defaultType
                                            if (wsName.isBlank() || WORKSPACE_PURPOSE_OPTIONS.any { it.suggestedName == wsName }) {
                                                wsName = opt.suggestedName
                                            }
                                        },
                                        label = { Text(opt.label) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = wsPurpose,
                                onValueChange = { wsPurpose = it },
                                label = { Text("Keperluan Ruang Keuangan (Bisa Tulis Sendiri)") },
                                placeholder = { Text("mis. Kas Toko, Tabungan Sekolah, Patungan RT") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("create_workspace_purpose_input")
                            )
                            OutlinedTextField(
                                value = wsName,
                                onValueChange = { wsName = it },
                                label = { Text("Nama Ruang Keuangan") },
                                placeholder = { Text("mis. Kas Usaha Kopi, Dana Nikah") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("create_workspace_name_input")
                            )
                            Text("Pilihan Pemakaian", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = wsType == WorkspaceTypes.PERSONAL,
                                    onClick = { wsType = WorkspaceTypes.PERSONAL },
                                    label = { Text("Sendiri (Di HP)") }
                                )
                                FilterChip(
                                    selected = wsType == WorkspaceTypes.SHARED,
                                    onClick = { wsType = WorkspaceTypes.SHARED },
                                    label = { Text("Bersama (Bisa Undang)") }
                                )
                            }
                            Button(
                                onClick = {
                                    if (wsName.isNotBlank()) {
                                        onCreateWorkspace(
                                            wsName.trim(),
                                            wsType,
                                            wsPurpose.trim().ifBlank { wsName.trim() }
                                        )
                                        onDismiss()
                                    }
                                },
                                enabled = wsName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("create_workspace_submit")
                            ) {
                                Text("Tambah Ruang Keuangan")
                            }

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text("Gabung Ruang Keuangan dengan Kode", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = { joinCode = it.uppercase() },
                                label = { Text("Kode Undangan 6-8 Karakter (mis. KLR742)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("join_workspace_code_input")
                            )
                            OutlinedButton(
                                onClick = {
                                    if (joinCode.isNotBlank()) {
                                        onJoinWorkspace(joinCode)
                                        onDismiss()
                                    }
                                },
                                enabled = joinCode.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("join_workspace_submit")
                            ) {
                                Text("Gabung Sekarang")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemePickerDialog(
    currentPresetId: String,
    autoTimePreviewPhase: TimeOfDayPastelPhase? = null,
    onSelectAutoTimePhase: (TimeOfDayPastelPhase?) -> Unit = {},
    onSelectPreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val activeTimePhase = LocalActiveTimePastelPhase.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            SvgLogo(resId = R.drawable.ic_logo_nusakas, contentDescription = null, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Pilih Tema Tampilan", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Sesuaikan suasana aplikasi sesukamu",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Tutup")
                        }
                    }
                }

                items(AppThemePreset.entries, key = { it.id }) { preset ->
                    val isSelected = preset.id == currentPresetId
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("theme_option_${preset.id}")
                            .clickable {
                                onSelectPreset(preset.id)
                                if (preset != AppThemePreset.AUTO_TIME_PASTEL) {
                                    onDismiss()
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(preset.previewPrimary)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .align(Alignment.BottomEnd)
                                                .clip(CircleShape)
                                                .background(preset.previewSecondary)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .align(Alignment.TopEnd)
                                                .clip(CircleShape)
                                                .background(preset.previewTertiary)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(preset.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            preset.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Terpilih", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            if (preset == AppThemePreset.AUTO_TIME_PASTEL && isSelected) {
                                val activeLabel = activeTimePhase?.let { "${it.emoji} ${it.displayName} (${it.timeRangeLabel})" } ?: ""
                                Text(
                                    text = "Aktif sekarang: $activeLabel",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = autoTimePreviewPhase == null,
                                        onClick = { onSelectAutoTimePhase(null) },
                                        label = { Text("Jam Asli", maxLines = 1) },
                                        modifier = Modifier.testTag("auto_theme_realtime_chip")
                                    )
                                    FilterChip(
                                        selected = autoTimePreviewPhase == TimeOfDayPastelPhase.SUNRISE_PINK,
                                        onClick = { onSelectAutoTimePhase(TimeOfDayPastelPhase.SUNRISE_PINK) },
                                        label = { Text("🌅 Pagi", maxLines = 1) },
                                        modifier = Modifier.testTag("auto_theme_sunrise_chip")
                                    )
                                    FilterChip(
                                        selected = autoTimePreviewPhase == TimeOfDayPastelPhase.MOONLIGHT_LAVENDER,
                                        onClick = { onSelectAutoTimePhase(TimeOfDayPastelPhase.MOONLIGHT_LAVENDER) },
                                        label = { Text("🌙 Malam", maxLines = 1) },
                                        modifier = Modifier.testTag("auto_theme_moonlight_chip")
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = autoTimePreviewPhase == TimeOfDayPastelPhase.FRESH_MINT,
                                        onClick = { onSelectAutoTimePhase(TimeOfDayPastelPhase.FRESH_MINT) },
                                        label = { Text("🌿 Siang", maxLines = 1) },
                                        modifier = Modifier.testTag("auto_theme_mint_chip")
                                    )
                                    FilterChip(
                                        selected = autoTimePreviewPhase == TimeOfDayPastelPhase.TWILIGHT_ROSE,
                                        onClick = { onSelectAutoTimePhase(TimeOfDayPastelPhase.TWILIGHT_ROSE) },
                                        label = { Text("🌇 Sore", maxLines = 1) },
                                        modifier = Modifier.testTag("auto_theme_twilight_chip")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
