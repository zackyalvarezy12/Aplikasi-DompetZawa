package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.AiAssistantUiState
import com.example.domain.AiChatMessage

private val QUICK_ZAWA_PROMPTS = listOf(
    "Analisa keuanganku",
    "Saran hemat bulan ini",
    "Catat kopi 25rb",
    "Ingatkan tagihan WiFi 350rb tgl 20",
    "Atur batas makan 1,5jt",
    "Cek pengingat tagihan sekarang",
    "Buat target tabungan 5jt"
)

@Composable
fun ZawaAiAssistantDialog(
    uiState: AiAssistantUiState,
    messages: List<AiChatMessage>,
    onSendMessage: (String) -> Unit,
    onRetryLastMessage: () -> Unit = {},
    onClearChat: () -> Unit,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    var lastSentText by rememberSaveable { mutableStateOf("") }
    var lastDispatchTimeMs by remember { mutableLongStateOf(0L) }
    var lastDispatchedText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isLoading = uiState is AiAssistantUiState.Loading

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val sendNow: (String, Boolean) -> Unit = { raw, fromInputField ->
        val clean = raw.trim()
        val now = System.currentTimeMillis()
        val isRapidDuplicate = clean == lastDispatchedText && (now - lastDispatchTimeMs) < 800L
        if (clean.isNotBlank() && !isLoading && !isRapidDuplicate) {
            lastDispatchTimeMs = now
            lastDispatchedText = clean
            lastSentText = clean
            if (fromInputField) {
                inputText = ""
            }
            onSendMessage(clean)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
                topBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SvgLogo(
                                resId = R.drawable.ic_logo_advisor_ai,
                                contentDescription = "Ikon Asisten Zawa",
                                size = 40.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Asisten Keuangan Zawa",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Bantu catat, cek uang, saran hemat & ingatkan tagihan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (messages.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearChat,
                                    modifier = Modifier.testTag("zawa_ai_clear_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteSweep,
                                        contentDescription = "Bersihkan Percakapan"
                                    )
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("zawa_ai_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Tutup Asisten Zawa"
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    placeholder = {
                                        Text("Tulis perintah (mis. catat beli beras 65rb)...")
                                    },
                                    maxLines = 3,
                                    enabled = !isLoading,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { sendNow(inputText, true) }),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("zawa_ai_input")
                                )
                                FilledIconButton(
                                    onClick = { sendNow(inputText, true) },
                                    enabled = inputText.isNotBlank() && !isLoading,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .testTag("zawa_ai_send_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Kirim ke Asisten Zawa"
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(QUICK_ZAWA_PROMPTS) { prompt ->
                            AssistChip(
                                onClick = { sendNow(prompt, false) },
                                enabled = !isLoading,
                                label = { Text(prompt, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    if (uiState is AiAssistantUiState.Error) {
                        val hasPendingUserMsg = messages.lastOrNull()?.isUser == true
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .testTag("zawa_ai_error_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null)
                                Text(
                                    text = uiState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                if (hasPendingUserMsg || lastSentText.isNotBlank()) {
                                    TextButton(onClick = {
                                        onDismissError()
                                        if (hasPendingUserMsg) {
                                            onRetryLastMessage()
                                        } else {
                                            sendNow(lastSentText, false)
                                        }
                                    }) {
                                        Text("Coba Lagi")
                                    }
                                }
                            }
                        }
                    }

                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.widthIn(max = 520.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        SvgLogo(
                                            resId = R.drawable.ic_logo_advisor_ai,
                                            contentDescription = null,
                                            size = 44.dp
                                        )
                                        Text(
                                            text = "Halo! Aku Asisten Zawa",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "Aku siap membantumu langsung di aplikasi Dompet Zawa:",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "• Mencatat & mengatur: catat uang masuk/keluar, pasang pengingat tagihan, atur batas belanja, buat target tabungan, catat utang/piutang, atur gaji, & ganti tema.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "• Memeriksa & memberi saran: cek sisa uang bulan ini, pengeluaran yang terlalu boros, serta cara melunasi cicilan lebih cepat.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Pilih contoh di atas atau tulis pertanyaanmu di bawah!",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items = messages, key = { it.id }) { msg ->
                                AiMessageBubble(
                                    message = msg,
                                    isLatest = msg.id == messages.lastOrNull()?.id,
                                    isLoading = isLoading,
                                    onFollowUpClick = { suggestion -> sendNow(suggestion, false) }
                                )
                            }
                            if (isLoading) {
                                item(key = "ai_loading_indicator") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                        Text(
                                            text = "Asisten Zawa sedang memeriksa & menyiapkan...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun AiMessageBubble(
    message: AiChatMessage,
    isLatest: Boolean,
    isLoading: Boolean,
    onFollowUpClick: (String) -> Unit
) {
    val isUser = message.isUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!message.executedActionSummary.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Aksi berhasil dijalankan",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Sudah Dikerjakan: ${message.executedActionSummary}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (!isUser && isLatest && message.followUps.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(message.followUps) { suggestion ->
                    AssistChip(
                        onClick = { onFollowUpClick(suggestion) },
                        enabled = !isLoading,
                        label = {
                            Text(suggestion, style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
            }
        }
    }
}
