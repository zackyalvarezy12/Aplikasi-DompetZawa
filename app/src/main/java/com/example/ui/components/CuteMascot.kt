package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ultra-lightweight pure Canvas kawaii piggy-cat coin mascot (0 KB bitmap RAM!).
 */
@Composable
fun CuteKawaiiMascot(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val outlineColor = Color(0xFF2B2135)
        val mintBody = Color(0xFFA7F3D0)
        val innerEar = Color(0xFFFF9EBB)
        val cheekColor = Color(0xFFFF7597)
        val coinGold = Color(0xFFFFD166)

        // Left & Right Cat Ears
        val leftEar = Path().apply {
            moveTo(w * 0.22f, h * 0.36f)
            lineTo(w * 0.20f, h * 0.14f)
            lineTo(w * 0.40f, h * 0.26f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(w * 0.78f, h * 0.36f)
            lineTo(w * 0.80f, h * 0.14f)
            lineTo(w * 0.60f, h * 0.26f)
            close()
        }
        drawPath(leftEar, mintBody)
        drawPath(leftEar, outlineColor, style = Stroke(width = w * 0.045f, cap = StrokeCap.Round))
        drawPath(rightEar, mintBody)
        drawPath(rightEar, outlineColor, style = Stroke(width = w * 0.045f, cap = StrokeCap.Round))

        // Inner Pink Ears
        drawCircle(innerEar, radius = w * 0.055f, center = Offset(w * 0.27f, h * 0.25f))
        drawCircle(innerEar, radius = w * 0.055f, center = Offset(w * 0.73f, h * 0.25f))

        // Chubby Face Head
        drawOval(
            color = mintBody,
            topLeft = Offset(w * 0.14f, h * 0.22f),
            size = Size(w * 0.72f, h * 0.62f)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(w * 0.14f, h * 0.22f),
            size = Size(w * 0.72f, h * 0.62f),
            style = Stroke(width = w * 0.045f)
        )

        // Piggy Bank Coin Slot on Forehead
        drawLine(
            color = outlineColor,
            start = Offset(w * 0.41f, h * 0.30f),
            end = Offset(w * 0.59f, h * 0.30f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )

        // Happy Curved Eyes (^ ^)
        drawArc(
            color = outlineColor,
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.29f, h * 0.43f),
            size = Size(w * 0.13f, h * 0.10f),
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
        )
        drawArc(
            color = outlineColor,
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.58f, h * 0.43f),
            size = Size(w * 0.13f, h * 0.10f),
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
        )

        // Rosy Cheeks
        drawCircle(cheekColor.copy(alpha = 0.65f), radius = w * 0.065f, center = Offset(w * 0.27f, h * 0.57f))
        drawCircle(cheekColor.copy(alpha = 0.65f), radius = w * 0.065f, center = Offset(w * 0.73f, h * 0.57f))

        // Cute Cat Smile (w)
        drawArc(
            color = outlineColor,
            startAngle = 10f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(w * 0.42f, h * 0.51f),
            size = Size(w * 0.08f, h * 0.07f),
            style = Stroke(width = w * 0.038f, cap = StrokeCap.Round)
        )
        drawArc(
            color = outlineColor,
            startAngle = 10f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(w * 0.50f, h * 0.51f),
            size = Size(w * 0.08f, h * 0.07f),
            style = Stroke(width = w * 0.038f, cap = StrokeCap.Round)
        )

        // Little Star Coin held at bottom
        drawCircle(coinGold, radius = w * 0.14f, center = Offset(w * 0.50f, h * 0.78f))
        drawCircle(outlineColor, radius = w * 0.14f, center = Offset(w * 0.50f, h * 0.78f), style = Stroke(width = w * 0.04f))
    }
}
