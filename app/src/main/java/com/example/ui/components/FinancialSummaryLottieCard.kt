package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CurrencyFormatter
import com.example.ui.theme.*
import kotlin.math.roundToInt

private val SUMMARY_CHEERS = listOf(
    "Ketuk Zawa untuk semangat cuan! ✨",
    "Hebat! Setiap catatan bikin dompet makin sehat 🪙",
    "Anggaran terjaga, hati pun ikut happy 💖",
    "Pelan-pelan nabung, impian pasti tercapai 🌟"
)

/**
 * Performance-optimized Financial Summary Card with cheerful interactive Lottie mascot,
 * displaying Net Worth (Kekayaan Bersih) and Sisa Anggaran (Remaining Monthly Budget).
 */
@Composable
fun FinancialSummaryLottieCard(
    netWorth: Long,
    totalLiquidAndInvestments: Long,
    totalDebts: Long,
    savingRatePercent: Double,
    totalBudgetLimit: Long,
    totalBudgetSpent: Long,
    remainingBudget: Long,
    budgetRemainingFraction: Float,
    healthScore: Int,
    stealthMode: Boolean,
    celebrationTrigger: Int,
    onCelebrate: () -> Unit,
    onOpenAdvisor: () -> Unit,
    onOpenBudgetDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heroColors = LocalActiveHeroGradient.current
    val heroBrush = remember(heroColors) { Brush.linearGradient(heroColors) }
    var moodIndex by remember { mutableIntStateOf(0) }
    val animatedBudgetProgress by animateFloatAsState(
        targetValue = budgetRemainingFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "remaining_budget_progress"
    )

    val hasBudget = totalBudgetLimit > 0L
    val isOverBudget = hasBudget && remainingBudget < 0L
    val budgetStatusColor = when {
        !hasBudget -> Color.White
        isOverBudget -> Color(0xFFFCA5A5)
        budgetRemainingFraction <= 0.25f -> Gold100
        else -> Color(0xFFA7F3D0)
    }

    Card(
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag("financial_summary_lottie_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroBrush)
                .drawWithCache {
                    val c1 = Offset(size.width * 0.90f, size.height * 0.14f)
                    val r1 = size.height * 0.38f
                    val c2 = Offset(size.width * 0.08f, size.height * 0.88f)
                    val r2 = size.height * 0.24f
                    onDrawBehind {
                        drawCircle(color = Color.White.copy(alpha = 0.13f), radius = r1, center = c1)
                        drawCircle(color = Gold200.copy(alpha = 0.16f), radius = r2, center = c2)
                    }
                }
        ) {
            LottieCelebrationOverlay(
                triggerCount = celebrationTrigger,
                size = 164.dp,
                modifier = Modifier.align(Alignment.Center)
            )

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Row: Cheerful speech bubble + Health Score badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                moodIndex++
                                onCelebrate()
                            }
                    ) {
                        AnimatedContent(
                            targetState = SUMMARY_CHEERS[moodIndex % SUMMARY_CHEERS.size],
                            label = "summary_cheer_bubble"
                        ) { cheer ->
                            Text(
                                text = cheer,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onCelebrate()
                                onOpenAdvisor()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$healthScore",
                                style = MaterialTheme.typography.titleMedium,
                                color = Gold100,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Skor Sehat",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }

                // Middle Row: Cheerful Lottie Mascot + Net Worth Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InteractiveLottieMascot(
                        size = 82.dp,
                        celebrationTrigger = celebrationTrigger,
                        onTap = {
                            moodIndex++
                            onCelebrate()
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TOTAL KEKAYAAN BERSIH (NET WORTH)",
                            style = MaterialTheme.typography.labelMedium,
                            color = Gold100,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = CurrencyFormatter.formatIdr(netWorth, stealthMode),
                            style = MoneyDisplayStyle,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("hero_net_worth")
                        )
                        Text(
                            text = "Kas & aset dikurangi total utang",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f)
                        )
                    }
                }

                // Remaining Monthly Budget (Sisa Anggaran) Highlight Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenBudgetDetail)
                        .testTag("hero_remaining_budget_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                InteractiveLottieSparkleBadge(
                                    size = 28.dp,
                                    onTap = {
                                        moodIndex++
                                        onCelebrate()
                                    }
                                )
                                Text(
                                    text = "Sisa Anggaran Bulan Ini",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (hasBudget) {
                                        "${(budgetRemainingFraction * 100f).roundToInt()}% sisa"
                                    } else {
                                        "Atur batas"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = budgetStatusColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Lihat rincian anggaran",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = if (hasBudget) {
                                    CurrencyFormatter.formatIdr(remainingBudget, stealthMode)
                                } else {
                                    "Belum ada anggaran"
                                },
                                style = MoneyMediumStyle,
                                color = budgetStatusColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("hero_remaining_budget")
                            )
                            if (hasBudget) {
                                Text(
                                    text = "Terpakai ${CurrencyFormatter.formatCompactIdr(totalBudgetSpent, stealthMode)} / ${CurrencyFormatter.formatCompactIdr(totalBudgetLimit, stealthMode)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        if (hasBudget) {
                            LinearProgressIndicator(
                                progress = { animatedBudgetProgress },
                                color = budgetStatusColor,
                                trackColor = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }

                // Bottom Metrics Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryMiniMetric(
                        label = "Uang + Aset",
                        value = CurrencyFormatter.formatCompactIdr(totalLiquidAndInvestments, stealthMode),
                        valueColor = Color.White
                    )
                    SummaryMiniMetric(
                        label = "Total Utang",
                        value = CurrencyFormatter.formatCompactIdr(totalDebts, stealthMode),
                        valueColor = Gold100
                    )
                    SummaryMiniMetric(
                        label = "Nabung",
                        value = "${savingRatePercent.roundToInt()}%",
                        valueColor = Color(0xFFD8F8EE)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMiniMetric(
    label: String,
    value: String,
    valueColor: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.80f)
        )
        Text(
            text = value,
            style = MoneyMediumStyle,
            color = valueColor
        )
    }
}
