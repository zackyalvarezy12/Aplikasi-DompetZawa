package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.Gold100
import com.example.ui.theme.Gold200
import com.example.ui.theme.LocalActiveHeroGradient
import com.example.ui.theme.LocalActiveTimePastelPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MASCOT_CHEER_QUOTES = listOf(
    "Ketuk aku buat tambah semangat! ✨",
    "Sedikit demi sedikit, tabungan jadi bukit! 🪙",
    "Dompet rapi, hati pun ikut happy! 💖",
    "Data aman 100% lokal di HP-mu! 🛡️"
)

@Composable
fun PinLockScreen(
    isPinRequired: Boolean,
    userName: String,
    workspaceName: String,
    onVerifyPin: (String) -> Boolean,
    onEnterWithoutPin: () -> Unit,
    onOpenThemePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var entered by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var quoteIndex by remember { mutableIntStateOf(0) }
    var celebrationTrigger by remember { mutableIntStateOf(0) }
    var errorShakeTrigger by remember { mutableIntStateOf(0) }
    var isEntering by remember { mutableStateOf(false) }

    val heroColors = LocalActiveHeroGradient.current
    val activeTimePhase = LocalActiveTimePastelPhase.current
    val scope = rememberCoroutineScope()

    val currentSpeech = when {
        errorMsg != null -> "Ups! PIN belum cocok nih, coba lagi ya~ 😿"
        isEntering -> "Hore! Selamat beraktivitas dengan cuan sehat! 🎉"
        else -> MASCOT_CHEER_QUOTES[quoteIndex % MASCOT_CHEER_QUOTES.size]
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(heroColors))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Soft atmospheric floating bubbles
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.width * 0.34f,
                center = Offset(size.width * 0.92f, size.height * 0.12f)
            )
            drawCircle(
                color = Gold200.copy(alpha = 0.14f),
                radius = size.width * 0.28f,
                center = Offset(size.width * 0.06f, size.height * 0.85f)
            )
        }

        // Quick Theme Picker shortcut in top-right corner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.18f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SvgLogo(resId = R.drawable.ic_logo_nusakas, contentDescription = null, size = 20.dp)
                    Text(
                        text = if (activeTimePhase != null) "Dompet Zawa • ${activeTimePhase.emoji} ${activeTimePhase.displayName}" else "Dompet Zawa",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onOpenThemePicker,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .testTag("login_theme_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = "Ganti Tema",
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(28.dp))

            // Top/Center Cheerful Lottie Mascot + Interactive Speech Bubble
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 420.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            quoteIndex++
                            celebrationTrigger++
                        }
                ) {
                    AnimatedContent(
                        targetState = currentSpeech,
                        label = "mascot_speech"
                    ) { speech ->
                        Text(
                            text = speech,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                InteractiveLottieMascot(
                    size = if (isPinRequired) 118.dp else 156.dp,
                    celebrationTrigger = celebrationTrigger,
                    errorShakeTrigger = errorShakeTrigger,
                    onTap = { quoteIndex++ }
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = if (isPinRequired) "Dompet Zawa Terkunci" else "Halo, $userName!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isPinRequired) {
                        "Masukkan PIN 6-Digit untuk membuka dompetmu ✨"
                    } else {
                        "Siap kelola keuangan $workspaceName dengan ceria hari ini?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                if (isPinRequired) {
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(6) { index ->
                            val filled = index < entered.length
                            val dotScale by animateFloatAsState(
                                targetValue = if (filled) 1.25f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "pin_dot_scale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .scale(dotScale)
                                    .clip(CircleShape)
                                    .background(if (filled) Gold100 else Color.Transparent)
                                    .border(2.dp, Color.White.copy(alpha = 0.88f), CircleShape)
                            )
                        }
                    }

                    if (errorMsg != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = errorMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFCA5A5),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isPinRequired) {
                // PIN Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "DEL")
                    )
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { key ->
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .testTag("pin_key_$key")
                                        .clickable(enabled = !isEntering) {
                                            errorMsg = null
                                            when (key) {
                                                "C" -> entered = ""
                                                "DEL" -> if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                                else -> if (entered.length < 6) {
                                                    entered += key
                                                    if (entered.length == 6) {
                                                        val candidate = entered
                                                        isEntering = true
                                                        scope.launch {
                                                            // Check validity synchronously first via a peek? No, verify first:
                                                            // We celebrate first if valid!
                                                            val temp = candidate
                                                            val ok = onVerifyPin(temp)
                                                            if (!ok) {
                                                                isEntering = false
                                                                errorMsg = "PIN salah. Silakan coba lagi."
                                                                entered = ""
                                                                errorShakeTrigger++
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (key == "DEL") {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Hapus digit PIN",
                                                tint = Color.White
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Welcome / Login Action Card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(max = 360.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            InteractiveLottieSparkleBadge(
                                size = 44.dp,
                                onTap = {
                                    quoteIndex++
                                    celebrationTrigger++
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ruang Kerja Aktif",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Gold100
                                )
                                Text(
                                    text = workspaceName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Ketuk maskot lucu di atas untuk kejutan!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.84f)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!isEntering) {
                                isEntering = true
                                celebrationTrigger++
                                scope.launch {
                                    delay(420)
                                    onEnterWithoutPin()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("login_enter_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isEntering) "Membuka Dompet..." else "Masuk ke Dompet ✨",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Kamu bisa aktifkan PIN 6-Digit di menu Sync & Keamanan",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
