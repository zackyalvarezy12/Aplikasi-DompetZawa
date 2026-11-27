package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CurrencyFormatter
import com.example.ui.CategorySpendSlice
import com.example.ui.MonthlyBarPoint
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald700
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.MoneySmallStyle
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun NetWorthTrendChart(
    points: List<Pair<String, Long>>,
    stealthMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    var selectedIdx by remember(points) { mutableStateOf(points.lastIndex) }
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier) {
        val selected = points.getOrNull(selectedIdx) ?: points.last()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bulan ${selected.first}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = CurrencyFormatter.formatIdr(selected.second, stealthMode),
                style = MoneySmallStyle,
                fontWeight = FontWeight.Bold,
                color = primary
            )
        }
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            if (points.size > 1) {
                                val stepX = size.width.toFloat() / (points.size - 1)
                                selectedIdx = (offset.x / stepX).roundToInt().coerceIn(0, points.lastIndex)
                            }
                        }
                    }
                    .drawWithCache {
                        val maxVal = (points.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L).toFloat()
                        val minVal = (points.minOfOrNull { it.second } ?: 0L).toFloat()
                        val range = (maxVal - minVal).coerceAtLeast(1f)
                        val padTop = 16f
                        val padBottom = 16f
                        val drawH = (size.height - padTop - padBottom).coerceAtLeast(1f)
                        val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width

                        val coords = points.mapIndexed { idx, pair ->
                            val x = idx * stepX
                            val ratio = (pair.second.toFloat() - minVal) / range
                            val y = padTop + drawH * (1f - ratio)
                            Offset(x, y)
                        }

                        val linePath = Path().apply {
                            coords.forEachIndexed { idx, pt ->
                                if (idx == 0) moveTo(pt.x, pt.y)
                                else {
                                    val prev = coords[idx - 1]
                                    val cx = (prev.x + pt.x) / 2f
                                    cubicTo(cx, prev.y, cx, pt.y, pt.x, pt.y)
                                }
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(coords.last().x, size.height)
                            lineTo(coords.first().x, size.height)
                            close()
                        }

                        val fillBrush = Brush.verticalGradient(
                            colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent)
                        )
                        val strokeStyle = Stroke(width = 5f, cap = StrokeCap.Round)

                        onDrawBehind {
                            repeat(3) { i ->
                                val y = padTop + (drawH / 2f) * i
                                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                            }
                            drawPath(path = fillPath, brush = fillBrush)
                            drawPath(path = linePath, color = primary, style = strokeStyle)

                            val selPt = coords[selectedIdx.coerceIn(coords.indices)]
                            drawLine(
                                color = accent.copy(alpha = 0.5f),
                                start = Offset(selPt.x, 0f),
                                end = Offset(selPt.x, size.height),
                                strokeWidth = 2f
                            )
                            drawCircle(color = Color.White, radius = 10f, center = selPt)
                            drawCircle(color = primary, radius = 7f, center = selPt)
                        }
                    }
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.filterIndexed { index, _ -> index % 2 == 0 || index == points.lastIndex }.forEach {
                Text(
                    text = it.first,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun IncomeExpenseBarChart(
    bars: List<MonthlyBarPoint>,
    stealthMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return
    var selectedIdx by remember(bars) { mutableStateOf(bars.lastIndex) }
    val selected = bars.getOrNull(selectedIdx) ?: bars.last()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot("Masuk", IncomeGreen)
                LegendDot("Keluar", ExpenseRed)
            }
            Text(
                text = "${selected.label}: +${CurrencyFormatter.formatCompactIdr(selected.income, stealthMode)} / -${CurrencyFormatter.formatCompactIdr(selected.expense, stealthMode)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))

        val maxVal = remember(bars) {
            bars.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1L) ?: 1L
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEachIndexed { idx, pt ->
                val isSel = idx == selectedIdx
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSel) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else Color.Transparent
                        )
                        .clickable { selectedIdx = idx }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val incFrac = (pt.income.toFloat() / maxVal).coerceIn(0.04f, 1f)
                        val expFrac = (pt.expense.toFloat() / maxVal).coerceIn(0.04f, 1f)
                        Box(
                            modifier = Modifier
                                .width(11.dp)
                                .fillMaxHeight(if (pt.income == 0L) 0.03f else incFrac)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(IncomeGreen)
                        )
                        Box(
                            modifier = Modifier
                                .width(11.dp)
                                .fillMaxHeight(if (pt.expense == 0L) 0.03f else expFrac)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(ExpenseRed.copy(alpha = 0.85f))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pt.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    slices: List<CategorySpendSlice>,
    stealthMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Belum ada pengeluaran bulan ini",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val total = remember(slices) { slices.sumOf { it.amount }.coerceAtLeast(1L) }
    var selectedIdx by remember(slices) { mutableStateOf(0) }
    val selected = slices.getOrNull(selectedIdx) ?: slices.first()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(slices) {
                        detectTapGestures { offset ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = offset.x - cx
                            val dy = offset.y - cy
                            if (hypot(dx, dy) > size.width * 0.2f) {
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                if (angle < 0f) angle += 360f
                                var acc = 0f
                                slices.forEachIndexed { idx, s ->
                                    val sweep = (s.amount.toFloat() / total) * 360f
                                    if (angle in acc..(acc + sweep)) {
                                        selectedIdx = idx
                                        return@detectTapGestures
                                    }
                                    acc += sweep
                                }
                            }
                        }
                    }
            ) {
                var startAngle = -90f
                val strokeWidth = 30f
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                val arcSize = Size(diameter, diameter)

                slices.forEachIndexed { idx, slice ->
                    val sweep = (slice.amount.toFloat() / total) * 360f
                    val isSel = idx == selectedIdx
                    drawArc(
                        color = Color(slice.colorHex),
                        startAngle = startAngle,
                        sweepAngle = (sweep - 2f).coerceAtLeast(2f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = if (isSel) strokeWidth + 8f else strokeWidth)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val pct = ((selected.amount.toDouble() / total) * 100).roundToInt()
                Text("$pct%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = selected.name.split(" ").firstOrNull() ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.take(5).forEachIndexed { idx, slice ->
                val isSel = idx == selectedIdx
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSel) MaterialTheme.colorScheme.surfaceVariant
                            else Color.Transparent
                        )
                        .clickable { selectedIdx = idx }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(slice.colorHex))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = slice.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatCompactIdr(slice.amount, stealthMode),
                        style = MoneySmallStyle,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun DailySpendingHeatmap(
    dailySpend: Map<Int, Long>,
    stealthMode: Boolean,
    modifier: Modifier = Modifier
) {
    val maxDaySpend = remember(dailySpend) { (dailySpend.values.maxOrNull() ?: 1L).coerceAtLeast(1L) }
    var selectedDay by remember(dailySpend) {
        mutableStateOf(dailySpend.keys.maxOrNull() ?: 1)
    }
    val dayWeeks = remember { (1..31).chunked(7) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Intensitas Belanja Harian (Tgl 1–31)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Tgl $selectedDay: ${CurrencyFormatter.formatIdr(dailySpend[selectedDay] ?: 0L, stealthMode)}",
                    style = MoneySmallStyle,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // 7 columns x 5 rows grid for days 1..31
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            dayWeeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    week.forEach { day ->
                        val amt = dailySpend[day] ?: 0L
                        val ratio = (amt.toFloat() / maxDaySpend).coerceIn(0f, 1f)
                        val bg = when {
                            amt == 0L -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ratio < 0.25f -> Emerald500.copy(alpha = 0.28f)
                            ratio < 0.55f -> Emerald500.copy(alpha = 0.58f)
                            ratio < 0.80f -> Color(0xFFD97706).copy(alpha = 0.72f)
                            else -> ExpenseRed.copy(alpha = 0.85f)
                        }
                        val isSel = day == selectedDay
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(bg)
                                .then(
                                    if (isSel) Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(7.dp)
                                    ) else Modifier
                                )
                                .clickable { selectedDay = day },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$day",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (amt > 0L && ratio >= 0.55f) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    repeat(7 - week.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
