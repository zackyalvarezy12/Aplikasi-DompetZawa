package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.compose.*
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Interactive cute kawaii mascot powered by Airbnb Lottie Compose.
 * - Plays smooth local-first Lottie JSON from res/raw (zero network needed).
 * - Reacts to taps with a spring bounce + one-shot golden coin & confetti burst.
 * - Supports error shake feedback (e.g. wrong PIN on login screen).
 */
@Composable
fun InteractiveLottieMascot(
    size: Dp = 96.dp,
    celebrationTrigger: Int = 0,
    errorShakeTrigger: Int = 0,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val mascotComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_kawaii_mascot)
    )
    val burstComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_coin_celebration)
    )

    var internalTapBurst by remember { mutableIntStateOf(0) }
    var isExcited by remember { mutableStateOf(false) }

    val totalBursts = celebrationTrigger + internalTapBurst
    val burstAnimatable = rememberLottieAnimatable()

    val bounceScale = remember { Animatable(1f) }
    val shakeOffsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val mascotProgress by animateLottieCompositionAsState(
        composition = mascotComposition,
        iterations = LottieConstants.IterateForever,
        speed = if (isExcited) 1.75f else 0.9f,
        isPlaying = true
    )

    LaunchedEffect(totalBursts) {
        if (totalBursts > 0 && burstComposition != null) {
            isExcited = true
            launch {
                bounceScale.snapTo(0.86f)
                bounceScale.animateTo(
                    targetValue = 1.12f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                bounceScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            burstAnimatable.animate(
                composition = burstComposition,
                iterations = 1,
                speed = 1.15f
            )
            delay(350)
            isExcited = false
        }
    }

    LaunchedEffect(errorShakeTrigger) {
        if (errorShakeTrigger > 0) {
            val offsets = listOf(-18f, 18f, -14f, 14f, -8f, 8f, 0f)
            offsets.forEach { target ->
                shakeOffsetX.animateTo(
                    targetValue = target,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh)
                )
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Maskot lucu Zawa, ketuk untuk animasi ceria" }
            .testTag("interactive_lottie_mascot")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button
            ) {
                internalTapBurst++
                onTap?.invoke()
            }
    ) {
        if (mascotComposition != null) {
            LottieAnimation(
                composition = mascotComposition,
                progress = { mascotProgress },
                renderMode = RenderMode.AUTOMATIC,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = bounceScale.value
                        scaleY = bounceScale.value
                        translationX = shakeOffsetX.value
                    }
            )
        } else {
            CuteKawaiiMascot(size = size * 0.78f)
        }

        if (burstComposition != null && burstAnimatable.isPlaying) {
            LottieAnimation(
                composition = burstComposition,
                progress = { burstAnimatable.progress },
                renderMode = RenderMode.AUTOMATIC,
                modifier = Modifier.size(size * 1.35f)
            )
        }
    }
}

/**
 * Standalone one-shot Lottie confetti & coin celebration overlay.
 * Only draws while actively triggered, zero CPU overhead at rest.
 */
@Composable
fun LottieCelebrationOverlay(
    triggerCount: Int,
    size: Dp = 150.dp,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_coin_celebration)
    )
    val animatable = rememberLottieAnimatable()

    LaunchedEffect(triggerCount) {
        if (triggerCount > 0 && composition != null) {
            animatable.animate(
                composition = composition,
                iterations = 1,
                speed = 1.1f
            )
        }
    }

    if (composition != null && animatable.isPlaying) {
        LottieAnimation(
            composition = composition,
            progress = { animatable.progress },
            renderMode = RenderMode.AUTOMATIC,
            modifier = modifier.size(size)
        )
    }
}

/**
 * Interactive cheerful sunshine-sparkle Lottie badge for Dashboard greeting & tips.
 */
@Composable
fun InteractiveLottieSparkleBadge(
    size: Dp = 52.dp,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_cheerful_sparkles)
    )
    var boosted by remember { mutableStateOf(false) }
    val popScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = if (boosted) 2.1f else 0.95f,
        isPlaying = true
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .testTag("lottie_sparkle_badge")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button
            ) {
                scope.launch {
                    boosted = true
                    popScale.snapTo(0.85f)
                    popScale.animateTo(
                        1.18f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                    popScale.animateTo(1f)
                    delay(1100)
                    boosted = false
                }
                onTap?.invoke()
            }
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                renderMode = RenderMode.AUTOMATIC,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = popScale.value
                        scaleY = popScale.value
                    }
            )
        } else {
            SvgLogo(resId = R.drawable.ic_logo_nusakas, contentDescription = null, size = size * 0.72f)
        }
    }
}
