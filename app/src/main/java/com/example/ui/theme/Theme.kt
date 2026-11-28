package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar

enum class TimeOfDayPastelPhase(
    val id: String,
    val displayName: String,
    val emoji: String,
    val timeRangeLabel: String,
    val heroGradient: List<Color>,
    val screenGradient: List<Color>,
    val isDark: Boolean
) {
    SUNRISE_PINK(
        id = "SUNRISE_PINK",
        displayName = "Merah Muda Pagi",
        emoji = "🌅",
        timeRangeLabel = "Pagi (05:00 - 10:59)",
        heroGradient = listOf(Color(0xFFEC4899), Color(0xFFFB7185), Color(0xFFF59E0B)),
        screenGradient = listOf(SunriseBgTop, PastelPinkBg, SunriseBgBottom),
        isDark = false
    ),
    FRESH_MINT(
        id = "FRESH_MINT",
        displayName = "Hijau Segar Siang",
        emoji = "🌿",
        timeRangeLabel = "Siang (11:00 - 15:59)",
        heroGradient = listOf(Color(0xFF0D9488), Color(0xFF14B8A6), Color(0xFF38BDF8)),
        screenGradient = listOf(FreshMintBgTop, PastelMintBg, FreshMintBgBottom),
        isDark = false
    ),
    TWILIGHT_ROSE(
        id = "TWILIGHT_ROSE",
        displayName = "Senja Lembut Sore",
        emoji = "🌇",
        timeRangeLabel = "Sore (16:00 - 18:59)",
        heroGradient = listOf(Color(0xFFDB2777), Color(0xFFA855F7), Color(0xFFF59E0B)),
        screenGradient = listOf(TwilightBgTop, PastelLavenderBg, TwilightBgBottom),
        isDark = false
    ),
    MOONLIGHT_LAVENDER(
        id = "MOONLIGHT_LAVENDER",
        displayName = "Ungu Malam Tenang",
        emoji = "🌙",
        timeRangeLabel = "Malam (19:00 - 04:59)",
        heroGradient = listOf(Color(0xFF6D28D9), Color(0xFF8B5CF6), Color(0xFFDB2777)),
        screenGradient = listOf(MoonlightBgTop, MoonlightBgBottom, Color(0xFF281E42)),
        isDark = true
    );

    companion object {
        fun fromHour(hourOfDay: Int): TimeOfDayPastelPhase = when (hourOfDay) {
            in 5..10 -> SUNRISE_PINK
            in 11..15 -> FRESH_MINT
            in 16..18 -> TWILIGHT_ROSE
            else -> MOONLIGHT_LAVENDER
        }
    }
}

enum class AppThemePreset(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val previewPrimary: Color,
    val previewSecondary: Color,
    val previewTertiary: Color,
    val heroGradient: List<Color>,
    val screenGradient: List<Color>,
    val isDark: Boolean
) {
    AUTO_TIME_PASTEL(
        id = "AUTO_TIME_PASTEL",
        displayName = "Tema Otomatis",
        subtitle = "Otomatis berganti warna cerah di pagi hari & lembut di malam hari",
        previewPrimary = SunrisePinkPrimary,
        previewSecondary = PastelMintVibrant,
        previewTertiary = MoonlightLavenderPrimary,
        heroGradient = TimeOfDayPastelPhase.SUNRISE_PINK.heroGradient,
        screenGradient = TimeOfDayPastelPhase.SUNRISE_PINK.screenGradient,
        isDark = false
    ),
    CUTE_PASTEL(
        id = "CUTE_PASTEL",
        displayName = "Pastel Lembut",
        subtitle = "Merah muda, hijau lembut & ungu muda yang nyaman di mata",
        previewPrimary = PastelPinkVibrant,
        previewSecondary = PastelMintVibrant,
        previewTertiary = PastelLavenderPrimary,
        heroGradient = listOf(Color(0xFFEC4899), Color(0xFF9F7AEA), Color(0xFF20B894)),
        screenGradient = listOf(PastelPinkBg, PastelLavenderBg, PastelMintBg),
        isDark = false
    ),
    BRIGHT_SUNNY(
        id = "BRIGHT_SUNNY",
        displayName = "Cerah Segar",
        subtitle = "Biru langit & kuning matahari",
        previewPrimary = Color(0xFF0284C7),
        previewSecondary = Color(0xFFF59E0B),
        previewTertiary = Color(0xFF10B981),
        heroGradient = listOf(Color(0xFF0284C7), Color(0xFF0EA5E9), Color(0xFF38BDF8)),
        screenGradient = listOf(Color(0xFFF8FBFF), Color(0xFFF0F9FF)),
        isDark = false
    ),
    DARK_ELEGANT(
        id = "DARK_ELEGANT",
        displayName = "Gelap Elegan",
        subtitle = "Hemat baterai & nyaman di malam hari",
        previewPrimary = Color(0xFF10B981),
        previewSecondary = Color(0xFFF59E0B),
        previewTertiary = Color(0xFF38BDF8),
        heroGradient = listOf(Color(0xFF064E3B), Color(0xFF065F46), Color(0xFF0F766E)),
        screenGradient = listOf(Color(0xFF0A110E), Color(0xFF0E1713)),
        isDark = true
    ),
    COLORFUL_POP(
        id = "COLORFUL_POP",
        displayName = "Warna-Warni",
        subtitle = "Ungu ceria, merah muda & biru toska",
        previewPrimary = Color(0xFF7C3AED),
        previewSecondary = Color(0xFFEC4899),
        previewTertiary = Color(0xFF06B6D4),
        heroGradient = listOf(Color(0xFF7C3AED), Color(0xFFEC4899), Color(0xFF06B6D4)),
        screenGradient = listOf(Color(0xFFFAF7FF), Color(0xFFFDF2F8)),
        isDark = false
    ),
    EMERALD_PRO(
        id = "EMERALD_PRO",
        displayName = "Hijau Klasik",
        subtitle = "Rapi, tenang & profesional",
        previewPrimary = Color(0xFF047857),
        previewSecondary = Color(0xFFD97706),
        previewTertiary = Color(0xFF0284C7),
        heroGradient = listOf(Color(0xFF022C22), Color(0xFF064E3B), Color(0xFF047857)),
        screenGradient = listOf(Color(0xFFF6F7F4), Color(0xFFECFDF5)),
        isDark = false
    ),
    OCEAN_BREEZE(
        id = "OCEAN_BREEZE",
        displayName = "Biru Laut",
        subtitle = "Segar seperti pantai & langit",
        previewPrimary = Color(0xFF0D9488),
        previewSecondary = Color(0xFFFB7185),
        previewTertiary = Color(0xFF0284C7),
        heroGradient = listOf(Color(0xFF0F766E), Color(0xFF0891B2), Color(0xFF38BDF8)),
        screenGradient = listOf(Color(0xFFF2FBFB), Color(0xFFEFF6FF)),
        isDark = false
    );

    companion object {
        fun fromId(id: String): AppThemePreset = entries.firstOrNull { it.id == id } ?: CUTE_PASTEL
    }
}

val LocalAppThemePreset = staticCompositionLocalOf { AppThemePreset.CUTE_PASTEL }
val LocalActiveTimePastelPhase = staticCompositionLocalOf<TimeOfDayPastelPhase?> { null }
val LocalActiveHeroGradient = staticCompositionLocalOf { AppThemePreset.CUTE_PASTEL.heroGradient }
val LocalActiveScreenGradient = staticCompositionLocalOf { AppThemePreset.CUTE_PASTEL.screenGradient }

private val CutePastelScheme = lightColorScheme(
    primary = PastelPinkPrimary,
    onPrimary = Color.White,
    primaryContainer = PastelPinkSoft,
    onPrimaryContainer = Color(0xFF831843),
    secondary = PastelMintPrimary,
    onSecondary = Color.White,
    secondaryContainer = PastelMintSoft,
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = PastelLavenderPrimary,
    onTertiary = Color.White,
    tertiaryContainer = PastelLavenderSoft,
    onTertiaryContainer = Color(0xFF4C1D95),
    background = PastelPinkBg,
    onBackground = PastelPlumText,
    surface = PastelCardSurface,
    onSurface = PastelPlumText,
    surfaceVariant = PastelSurfaceTint,
    onSurfaceVariant = PastelPlumMuted,
    outline = PastelLavenderOutline,
    outlineVariant = Color(0xFFF3E4EE),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = ExpenseRedSoft,
    onErrorContainer = Color(0xFF881337)
)

private val SunrisePinkScheme = lightColorScheme(
    primary = SunrisePinkPrimary,
    onPrimary = Color.White,
    primaryContainer = SunrisePinkContainer,
    onPrimaryContainer = Color(0xFF881337),
    secondary = SunrisePeachSecondary,
    onSecondary = Color.White,
    secondaryContainer = SunrisePeachContainer,
    onSecondaryContainer = Color(0xFF7C2D12),
    tertiary = SunriseLavenderTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SunriseLavenderContainer,
    onTertiaryContainer = Color(0xFF581C87),
    background = SunriseBgTop,
    onBackground = PastelPlumText,
    surface = Color.White,
    onSurface = PastelPlumText,
    surfaceVariant = Color(0xFFFFF0F5),
    onSurfaceVariant = PastelPlumMuted,
    outline = Color(0xFFFBCFE8),
    outlineVariant = Color(0xFFFCE7F3),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = ExpenseRedSoft,
    onErrorContainer = Color(0xFF881337)
)

private val FreshMintScheme = lightColorScheme(
    primary = FreshMintPrimary,
    onPrimary = Color.White,
    primaryContainer = FreshMintContainer,
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = FreshMintSkySecondary,
    onSecondary = Color.White,
    secondaryContainer = FreshMintSkyContainer,
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = PastelPinkPrimary,
    onTertiary = Color.White,
    tertiaryContainer = PastelPinkSoft,
    onTertiaryContainer = Color(0xFF831843),
    background = FreshMintBgTop,
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE6FAF4),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFA7F3D0),
    outlineVariant = Color(0xFFD1FAE5),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = ExpenseRedSoft,
    onErrorContainer = Color(0xFF881337)
)

private val TwilightRoseScheme = lightColorScheme(
    primary = TwilightRosePrimary,
    onPrimary = Color.White,
    primaryContainer = TwilightRoseContainer,
    onPrimaryContainer = Color(0xFF831843),
    secondary = TwilightCoralSecondary,
    onSecondary = Color.White,
    secondaryContainer = TwilightCoralContainer,
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = PastelLavenderPrimary,
    onTertiary = Color.White,
    tertiaryContainer = PastelLavenderSoft,
    onTertiaryContainer = Color(0xFF4C1D95),
    background = TwilightBgTop,
    onBackground = PastelPlumText,
    surface = Color.White,
    onSurface = PastelPlumText,
    surfaceVariant = Color(0xFFF9EEFA),
    onSurfaceVariant = PastelPlumMuted,
    outline = Color(0xFFE9D5FF),
    outlineVariant = Color(0xFFF3E8FF),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = ExpenseRedSoft,
    onErrorContainer = Color(0xFF881337)
)

private val MoonlightLavenderScheme = darkColorScheme(
    primary = MoonlightLavenderPrimary,
    onPrimary = Color(0xFF2E1065),
    primaryContainer = MoonlightLavenderContainer,
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = MoonlightPinkSecondary,
    onSecondary = Color(0xFF500724),
    secondaryContainer = MoonlightPinkContainer,
    onSecondaryContainer = Color(0xFFFCE7F3),
    tertiary = MoonlightMintTertiary,
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = MoonlightMintContainer,
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = MoonlightBgTop,
    onBackground = Color(0xFFF5F3FF),
    surface = MoonlightCardSurface,
    onSurface = Color(0xFFF5F3FF),
    surfaceVariant = MoonlightSurfaceTint,
    onSurfaceVariant = Color(0xFFD8D0F0),
    outline = MoonlightOutline,
    outlineVariant = Color(0xFF392D58),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val BrightSunnyScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF10B981),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF8FBFF),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEFF6FF),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkElegantScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF065F46),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF2E1500),
    secondaryContainer = Color(0xFF543300),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF002A40),
    tertiaryContainer = Color(0xFF0C4A6E),
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0A110E),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF111C18),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1A2923),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF2B4037),
    outlineVariant = Color(0xFF1E3129),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val ColorfulPopScheme = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF4C1D95),
    secondary = Color(0xFFEC4899),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE7F3),
    onSecondaryContainer = Color(0xFF831843),
    tertiary = Color(0xFF06B6D4),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFFAFE),
    onTertiaryContainer = Color(0xFF164E63),
    background = Color(0xFFFAF7FF),
    onBackground = Color(0xFF1E1B4B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1B4B),
    surfaceVariant = Color(0xFFF3E8FF),
    onSurfaceVariant = Color(0xFF4C1D95),
    outline = Color(0xFFDDD6FE),
    outlineVariant = Color(0xFFEDE9FE),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337)
)

private val EmeraldProScheme = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF022C22),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0C4A6E),
    background = Color(0xFFF6F7F4),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFECEFE9),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val OceanBreezeScheme = lightColorScheme(
    primary = Color(0xFF0D9488),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFFFB7185),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4E6),
    onSecondaryContainer = Color(0xFF881337),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0C4A6E),
    background = Color(0xFFF2FBFB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE6F7F6),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFBAE6FD),
    outlineVariant = Color(0xFFE0F2FE),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337)
)

val NusaKasShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

/**
 * Jetpack Compose State holder that observes device clock hour automatically every 30s
 * and resolves the current time-of-day pastel theme phase (or an optional interactive preview phase).
 */
@Composable
fun rememberTimeOfDayPastelPhase(
    enabled: Boolean,
    overridePhase: TimeOfDayPastelPhase? = null
): TimeOfDayPastelPhase {
    val currentHour by produceState(
        initialValue = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        key1 = enabled
    ) {
        if (!enabled) return@produceState
        while (true) {
            value = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            delay(30_000L)
        }
    }
    return remember(currentHour, overridePhase) {
        overridePhase ?: TimeOfDayPastelPhase.fromHour(currentHour)
    }
}

@Composable
fun DompetZawaTheme(
    preset: AppThemePreset = AppThemePreset.CUTE_PASTEL,
    autoTimeOverridePhase: TimeOfDayPastelPhase? = null,
    content: @Composable () -> Unit
) = NusaKasTheme(
    preset = preset,
    autoTimeOverridePhase = autoTimeOverridePhase,
    content = content
)

@Composable
fun NusaKasTheme(
    preset: AppThemePreset = AppThemePreset.CUTE_PASTEL,
    autoTimeOverridePhase: TimeOfDayPastelPhase? = null,
    content: @Composable () -> Unit
) {
    val isAuto = preset == AppThemePreset.AUTO_TIME_PASTEL
    val resolvedTimePhase = rememberTimeOfDayPastelPhase(
        enabled = isAuto,
        overridePhase = autoTimeOverridePhase
    )

    val targetScheme: ColorScheme = when {
        isAuto -> when (resolvedTimePhase) {
            TimeOfDayPastelPhase.SUNRISE_PINK -> SunrisePinkScheme
            TimeOfDayPastelPhase.FRESH_MINT -> FreshMintScheme
            TimeOfDayPastelPhase.TWILIGHT_ROSE -> TwilightRoseScheme
            TimeOfDayPastelPhase.MOONLIGHT_LAVENDER -> MoonlightLavenderScheme
        }
        preset == AppThemePreset.CUTE_PASTEL -> CutePastelScheme
        preset == AppThemePreset.BRIGHT_SUNNY -> BrightSunnyScheme
        preset == AppThemePreset.DARK_ELEGANT -> DarkElegantScheme
        preset == AppThemePreset.COLORFUL_POP -> ColorfulPopScheme
        preset == AppThemePreset.EMERALD_PRO -> EmeraldProScheme
        else -> OceanBreezeScheme
    }

    val animSpec = tween<Color>(durationMillis = 420)
    val animatedPrimary by animateColorAsState(targetScheme.primary, animSpec, label = "theme_primary")
    val animatedSecondary by animateColorAsState(targetScheme.secondary, animSpec, label = "theme_secondary")
    val animatedTertiary by animateColorAsState(targetScheme.tertiary, animSpec, label = "theme_tertiary")
    val animatedBg by animateColorAsState(targetScheme.background, animSpec, label = "theme_bg")
    val animatedOnBg by animateColorAsState(targetScheme.onBackground, animSpec, label = "theme_on_bg")
    val animatedSurface by animateColorAsState(targetScheme.surface, animSpec, label = "theme_surface")
    val animatedOnSurface by animateColorAsState(targetScheme.onSurface, animSpec, label = "theme_on_surface")
    val animatedSurfaceVariant by animateColorAsState(targetScheme.surfaceVariant, animSpec, label = "theme_surface_var")
    val animatedOnSurfaceVariant by animateColorAsState(targetScheme.onSurfaceVariant, animSpec, label = "theme_on_surface_var")

    val smoothScheme = targetScheme.copy(
        primary = animatedPrimary,
        secondary = animatedSecondary,
        tertiary = animatedTertiary,
        background = animatedBg,
        onBackground = animatedOnBg,
        surface = animatedSurface,
        onSurface = animatedOnSurface,
        surfaceVariant = animatedSurfaceVariant,
        onSurfaceVariant = animatedOnSurfaceVariant
    )

    val activeHeroGradient = if (isAuto) resolvedTimePhase.heroGradient else preset.heroGradient
    val activeScreenGradient = if (isAuto) resolvedTimePhase.screenGradient else preset.screenGradient

    CompositionLocalProvider(
        LocalAppThemePreset provides preset,
        LocalActiveTimePastelPhase provides (if (isAuto) resolvedTimePhase else null),
        LocalActiveHeroGradient provides activeHeroGradient,
        LocalActiveScreenGradient provides activeScreenGradient
    ) {
        MaterialTheme(
            colorScheme = smoothScheme,
            typography = NusaKasTypography,
            shapes = NusaKasShapes,
            content = content
        )
    }
}
