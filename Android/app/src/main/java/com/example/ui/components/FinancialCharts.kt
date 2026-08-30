package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PieSliceData(
    val label: String,
    val value: Double,
    val color: Color = Color.Unspecified
)

data class BarItemData(
    val label: String,
    val value: Double,
    val color: Color = PrimaryBlue,
    val subtitle: String? = null
)

// Harmonious graduated dark-to-light color series for pie charts
private val DeepToLightColors = listOf(
    Color(0xFF0F2B48), // Deep Navy
    Color(0xFF1B4F72), // Dark Blue
    Color(0xFF21618C), // Midnight Blue
    Color(0xFF2874A6), // Slate Blue
    Color(0xFF2E86C1), // Ocean Blue
    Color(0xFF3498DB), // Sky Blue
    Color(0xFF5DADE2), // Soft Blue
    Color(0xFF7FB3D5), // Powder Blue
    Color(0xFFA9CCE3), // Light Blue
    Color(0xFFD4E6F1), // Pale Blue
    Color(0xFFEBF5FB)  // Ice White Blue
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun FinancialPieChart(
    slices: List<PieSliceData>,
    modifier: Modifier = Modifier,
    emptyLabel: String = "No Data",
    currencySymbol: String = "$"
) {
    val total = slices.sumOf { it.value }
    val textMeasurer = rememberTextMeasurer()

    if (total <= 0.0 || slices.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = GrayTextMuted
            )
        }
        return
    }

    // 1. Sort slices strictly descending by value (from largest to smallest clockwise)
    val rawSorted = remember(slices) {
        slices.filter { it.value > 0.0 }.sortedByDescending { it.value }
    }

    // 2. Assign color gradient from dark (largest) to light (smallest)
    val coloredSlices = remember(rawSorted) {
        val count = rawSorted.size
        rawSorted.mapIndexed { idx, slice ->
            val color = if (slice.color != Color.Unspecified) {
                // Modulate user-provided color from dark/deep to light
                val factor = if (count > 1) idx.toFloat() / (count - 1) else 0f
                lerp(slice.color.copy(alpha = 1f), Color.White, factor * 0.55f)
            } else {
                DeepToLightColors.getOrElse(idx % DeepToLightColors.size) { Color.Gray }
            }
            slice.copy(color = color)
        }
    }

    // 3. Legend only shows items >= 5%, max 5 items
    val legendItems = remember(coloredSlices, total) {
        coloredSlices.filter { (it.value / total) >= 0.05 }.take(5)
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selectedSlice = selectedIndex?.let { if (it in coloredSlices.indices) coloredSlices[it] else null }

    Column(modifier = modifier.fillMaxWidth()) {
        // Selected Slice Detail Indicator Banner (Interactive click response)
        if (selectedSlice != null) {
            val pct = (selectedSlice.value / total) * 100.0
            Surface(
                color = selectedSlice.color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, selectedSlice.color.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(10.dp).background(selectedSlice.color, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedSlice.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NavySidebar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$currencySymbol${"%,.2f".format(selectedSlice.value)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NavySidebar
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(%.1f%%)".format(pct),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = GrayTextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { selectedIndex = null },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Selection",
                                tint = GrayTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive Pie Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .pointerInput(coloredSlices, total) {
                            detectTapGestures { offset ->
                                val diameter = minOf(size.width, size.height)
                                val radius = diameter / 2f
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                val dx = offset.x - centerX
                                val dy = offset.y - centerY
                                val dist = sqrt(dx * dx + dy * dy)

                                if (dist <= radius * 1.15f && dist >= 8.dp.toPx()) {
                                    var touchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    // Normalize start angle to 0 at top (-90 deg in canvas coordinates)
                                    touchAngle = (touchAngle + 90f + 360f) % 360f

                                    var curAngle = 0f
                                    var clickedIndex: Int? = null
                                    for (i in coloredSlices.indices) {
                                        val sweep = (coloredSlices[i].value / total * 360.0).toFloat()
                                        if (touchAngle >= curAngle && touchAngle <= (curAngle + sweep)) {
                                            clickedIndex = i
                                            break
                                        }
                                        curAngle += sweep
                                    }
                                    selectedIndex = if (selectedIndex == clickedIndex) null else clickedIndex
                                } else {
                                    selectedIndex = null
                                }
                            }
                        }
                ) {
                    val diameter = minOf(size.width, size.height)
                    val baseRadius = diameter / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    var startAngle = -90f
                    coloredSlices.forEachIndexed { index, slice ->
                        val sweepAngle = (slice.value / total * 360f).toFloat()
                        if (sweepAngle > 0f) {
                            val isSelected = selectedIndex == index
                            val radius = if (isSelected) baseRadius * 1.05f else baseRadius
                            val sliceOffset = if (isSelected) {
                                val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
                                Offset(
                                    (4.dp.toPx() * cos(midAngleRad)).toFloat(),
                                    (4.dp.toPx() * sin(midAngleRad)).toFloat()
                                )
                            } else Offset.Zero

                            val drawCenter = center + sliceOffset

                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true,
                                topLeft = Offset(drawCenter.x - radius, drawCenter.y - radius),
                                size = Size(radius * 2, radius * 2)
                            )

                            // Border between slices
                            drawArc(
                                color = if (isSelected) NavySidebar else Color.White,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true,
                                topLeft = Offset(drawCenter.x - radius, drawCenter.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx())
                            )

                            // Draw percentage text inside slice if >= 8%
                            val pct = (slice.value / total) * 100.0
                            if (pct >= 8.0) {
                                val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
                                val labelRadius = radius * 0.62f
                                val textX = drawCenter.x + (labelRadius * cos(midAngleRad)).toFloat()
                                val textY = drawCenter.y + (labelRadius * sin(midAngleRad)).toFloat()
                                val pctText = "%.1f%%".format(pct)
                                val textLayoutResult = textMeasurer.measure(
                                    text = AnnotatedString(pctText),
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 4f)
                                    )
                                )
                                drawText(
                                    textLayoutResult = textLayoutResult,
                                    topLeft = Offset(
                                        textX - textLayoutResult.size.width / 2f,
                                        textY - textLayoutResult.size.height / 2f
                                    )
                                )
                            }

                            startAngle += sweepAngle
                        }
                    }
                }
            }

            // Legend Column: Top 5 with >= 5%
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                legendItems.forEach { slice ->
                    val isSelected = selectedSlice?.label == slice.label
                    val pct = (slice.value / total) * 100.0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val idx = coloredSlices.indexOfFirst { it.label == slice.label }
                                selectedIndex = if (selectedIndex == idx) null else idx
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 11.dp else 9.dp)
                                .background(slice.color, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) PrimaryBlue else Color(0xFF0F172A),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$currencySymbol${formatCompact(slice.value)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) PrimaryBlue else Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "%.1f%%".format(pct),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun FinancialTrendLineChart(
    labels: List<String>,
    line1Data: List<Double?>,
    line1Color: Color = PrimaryBlue,
    line1Label: String = "Series 1",
    line2Data: List<Double?> = emptyList(),
    line2Color: Color = RedExpense,
    line2Label: String = "Series 2",
    currencySymbol: String = "$",
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val allValues = (line1Data + line2Data).filterNotNull()

    if (allValues.isEmpty() || labels.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No Data", color = GrayTextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val maxVal = allValues.maxOrNull() ?: 100.0
    val minVal = minOf(0.0, allValues.minOrNull() ?: 0.0)
    val range = if (maxVal == minVal) 1.0 else (maxVal - minVal)

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Selected Date Point Indicator Badge
        selectedIndex?.let { idx ->
            if (idx in labels.indices) {
                val v1 = line1Data.getOrNull(idx)
                val v2 = line2Data.getOrNull(idx)
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📅 ${labels[idx]}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = NavySidebar
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (v1 != null) {
                                Text(
                                    text = "$line1Label: $currencySymbol${"%,.2f".format(v1)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = line1Color
                                )
                            }
                            if (v2 != null) {
                                Text(
                                    text = "$line2Label: $currencySymbol${"%,.2f".format(v2)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = line2Color
                                )
                            }
                        }
                    }
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(line1Color, CircleShape))
            Spacer(modifier = Modifier.width(4.dp))
            Text(line1Label, style = MaterialTheme.typography.labelSmall, color = GrayTextSecondary)
            if (line2Data.any { it != null }) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.size(8.dp).background(line2Color, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(line2Label, style = MaterialTheme.typography.labelSmall, color = GrayTextSecondary)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(start = 45.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                .pointerInput(labels.size) {
                    detectTapGestures { offset ->
                        val n = labels.size
                        if (n > 0) {
                            val w = size.width
                            val tappedIdx = ((offset.x / w) * n).toInt().coerceIn(0, n - 1)
                            selectedIndex = if (selectedIndex == tappedIdx) null else tappedIdx
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val n = labels.size

            // Draw Y Grid lines
            val steps = 4
            for (i in 0..steps) {
                val y = h - (h * (i.toFloat() / steps))
                val v = minVal + (range * (i.toDouble() / steps))
                drawLine(
                    color = GrayBorder,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
                val yText = "$currencySymbol${formatCompact(v)}"
                val yLayout = textMeasurer.measure(
                    text = AnnotatedString(yText),
                    style = TextStyle(fontSize = 9.sp, color = GrayTextMuted)
                )
                drawText(
                    textLayoutResult = yLayout,
                    topLeft = Offset(-yLayout.size.width - 6.dp.toPx(), y - yLayout.size.height / 2f)
                )
            }

            // Zero line if min < 0
            if (minVal < 0.0) {
                val zeroY = h - (h * ((0.0 - minVal) / range).toFloat())
                drawLine(
                    color = GrayTextSecondary,
                    start = Offset(0f, zeroY),
                    end = Offset(w, zeroY),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Selected point vertical indicator line
            selectedIndex?.let { idx ->
                val selX = if (n > 1) (w * (idx.toFloat() / (n - 1))) else w / 2f
                drawLine(
                    color = NavySidebar.copy(alpha = 0.5f),
                    start = Offset(selX, 0f),
                    end = Offset(selX, h),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }

            // Plot Line 1
            drawLineSeries(
                data = line1Data,
                color = line1Color,
                minVal = minVal,
                range = range,
                width = w,
                height = h,
                count = n,
                highlightIndex = selectedIndex
            )

            // Plot Line 2
            if (line2Data.any { it != null }) {
                drawLineSeries(
                    data = line2Data,
                    color = line2Color,
                    minVal = minVal,
                    range = range,
                    width = w,
                    height = h,
                    count = n,
                    highlightIndex = selectedIndex
                )
            }

            // Draw X-axis tick labels
            val step = if (n <= 6) 1 else (n + 5) / 6
            for (i in 0 until n step step) {
                val x = if (n > 1) (w * (i.toFloat() / (n - 1))) else w / 2f
                val xText = labels[i]
                val isSelected = selectedIndex == i
                val xLayout = textMeasurer.measure(
                    text = AnnotatedString(xText),
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) NavySidebar else GrayTextMuted
                    )
                )
                drawText(
                    textLayoutResult = xLayout,
                    topLeft = Offset(x - xLayout.size.width / 2f, h + 4.dp.toPx())
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineSeries(
    data: List<Double?>,
    color: Color,
    minVal: Double,
    range: Double,
    width: Float,
    height: Float,
    count: Int,
    highlightIndex: Int? = null
) {
    val path = Path()
    var segmentStarted = false
    val points = mutableListOf<Pair<Int, Offset>>()

    for (i in data.indices) {
        val v = data[i]
        if (v != null) {
            val x = if (count > 1) (width * (i.toFloat() / (count - 1))) else width / 2f
            val y = height - (height * ((v - minVal) / range).toFloat())
            val pt = Offset(x, y)
            points.add(Pair(i, pt))
            if (!segmentStarted) {
                path.moveTo(x, y)
                segmentStarted = true
            } else {
                path.lineTo(x, y)
            }
        } else {
            segmentStarted = false
        }
    }

    if (points.isNotEmpty()) {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw points or highlight
        points.forEach { (idx, pt) ->
            if (highlightIndex == idx) {
                drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = pt)
                drawCircle(color = color, radius = 4.dp.toPx(), center = pt)
            } else if (count <= 15) {
                drawCircle(color = color, radius = 2.dp.toPx(), center = pt)
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun CompositeIncomeExpenseBalanceChart(
    labels: List<String>,
    incomeList: List<Double?>,
    expenseList: List<Double?>,
    balanceList: List<Double?>,
    currencySymbol: String = "$",
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val allValues = (incomeList + expenseList + balanceList).filterNotNull()

    if (allValues.isEmpty() || labels.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No Data", color = GrayTextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val maxVal = allValues.maxOrNull() ?: 100.0
    val minVal = minOf(0.0, (balanceList.filterNotNull().minOrNull() ?: 0.0))
    val range = if (maxVal == minVal) 1.0 else (maxVal - minVal)

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Selected Detail Banner
        selectedIndex?.let { idx ->
            if (idx in labels.indices) {
                val inc = incomeList.getOrNull(idx) ?: 0.0
                val exp = expenseList.getOrNull(idx) ?: 0.0
                val bal = balanceList.getOrNull(idx) ?: 0.0
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📅 ${labels[idx]}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = NavySidebar
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("In: $currencySymbol${"%,.0f".format(inc)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenIncome)
                            Text("Ex: $currencySymbol${"%,.0f".format(exp)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedExpense)
                            Text(
                                text = "Bal: ${if (bal >= 0) "+" else ""}$currencySymbol${"%,.0f".format(bal)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (bal >= 0) Color(0xFF1B4F72) else Color(0xFF922B21)
                            )
                        }
                    }
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(GreenIncome, CircleShape))
            Spacer(modifier = Modifier.width(3.dp))
            Text("Income", style = MaterialTheme.typography.labelSmall, color = GrayTextSecondary)

            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.size(8.dp).background(RedExpense, CircleShape))
            Spacer(modifier = Modifier.width(3.dp))
            Text("Expense", style = MaterialTheme.typography.labelSmall, color = GrayTextSecondary)

            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFA9DFBF), RoundedCornerShape(1.dp)))
            Spacer(modifier = Modifier.width(3.dp))
            Text("Balance (+)", style = MaterialTheme.typography.labelSmall, color = GrayTextSecondary)

            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFF5B7B1), RoundedCornerShape(1.dp)))
            Spacer(modifier = Modifier.width(3.dp))
            Text("Balance (-)", style = MaterialTheme.typography.labelSmall, color = GrayTextSecondary)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(start = 45.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                .pointerInput(labels.size) {
                    detectTapGestures { offset ->
                        val n = labels.size
                        if (n > 0) {
                            val w = size.width
                            val tappedIdx = ((offset.x / w) * n).toInt().coerceIn(0, n - 1)
                            selectedIndex = if (selectedIndex == tappedIdx) null else tappedIdx
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val n = labels.size
            val barWidth = (w / n) * 0.55f

            val zeroY = h - (h * ((0.0 - minVal) / range).toFloat())

            // Grid lines
            for (i in 0..4) {
                val y = h - (h * (i.toFloat() / 4))
                val v = minVal + (range * (i.toDouble() / 4))
                drawLine(color = GrayBorder, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1.dp.toPx())
                val yText = "$currencySymbol${formatCompact(v)}"
                val yLayout = textMeasurer.measure(
                    text = AnnotatedString(yText),
                    style = TextStyle(fontSize = 9.sp, color = GrayTextMuted)
                )
                drawText(textLayoutResult = yLayout, topLeft = Offset(-yLayout.size.width - 4.dp.toPx(), y - yLayout.size.height / 2f))
            }

            // Zero baseline
            drawLine(color = GrayTextMuted, start = Offset(0f, zeroY), end = Offset(w, zeroY), strokeWidth = 1.dp.toPx())

            // Draw Balance Bars (Light Green for positive, Light Red for negative)
            for (i in 0 until n) {
                val bal = balanceList.getOrNull(i)
                if (bal != null) {
                    val centerX = (w / n) * (i + 0.5f)
                    val barY = h - (h * ((bal - minVal) / range).toFloat())
                    val barTop = minOf(zeroY, barY)
                    val barHeight = kotlin.math.abs(zeroY - barY)
                    val isSelected = selectedIndex == i
                    val barColor = if (bal >= 0) {
                        if (isSelected) Color(0xFF58D68D) else Color(0xFFA9DFBF)
                    } else {
                        if (isSelected) Color(0xFFEC7063) else Color(0xFFF5B7B1)
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(centerX - barWidth / 2f, barTop),
                        size = Size(barWidth, maxOf(2f, barHeight)),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }

            // Draw Income Line
            drawLineSeries(
                data = incomeList,
                color = GreenIncome,
                minVal = minVal,
                range = range,
                width = w,
                height = h,
                count = n,
                highlightIndex = selectedIndex
            )

            // Draw Expense Line
            drawLineSeries(
                data = expenseList,
                color = RedExpense,
                minVal = minVal,
                range = range,
                width = w,
                height = h,
                count = n,
                highlightIndex = selectedIndex
            )

            // Draw Month Labels
            for (i in 0 until n) {
                val x = (w / n) * (i + 0.5f)
                val label = labels[i]
                val isSelected = selectedIndex == i
                val layout = textMeasurer.measure(
                    text = AnnotatedString(label),
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) NavySidebar else GrayTextSecondary
                    )
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x - layout.size.width / 2f, h + 4.dp.toPx())
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun ForecastBalanceBarChart(
    labels: List<String>,
    balanceList: List<Double?>,
    currencySymbol: String = "$",
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val values = balanceList.filterNotNull()

    if (values.isEmpty() || labels.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No Forecast Data", color = GrayTextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val rawMax = values.maxOrNull() ?: 100.0
    val rawMin = values.minOrNull() ?: 0.0
    val maxVal = if (rawMax > 0) rawMax * 1.15 else 100.0
    val minVal = if (rawMin < 0) rawMin * 1.15 else 0.0
    val range = if (maxVal == minVal) 1.0 else (maxVal - minVal)

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Selected Bar Interactive Indicator Banner
        selectedIndex?.let { idx ->
            if (idx in labels.indices) {
                val bal = balanceList.getOrNull(idx)
                if (bal != null) {
                    val isPos = bal >= 0
                    Surface(
                        color = if (isPos) Color(0xFFE8F8F5) else Color(0xFFFDEDEC),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isPos) Color(0xFFA9DFBF) else Color(0xFFF5B7B1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📅 ${labels[idx]} Forecast",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = NavySidebar
                            )
                            Text(
                                text = "${if (isPos) "+$currencySymbol" else "-$currencySymbol"}${"%,.2f".format(kotlin.math.abs(bal))} (${if (isPos) "Surplus" else "Deficit"})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isPos) Color(0xFF1E8449) else Color(0xFFC0392B)
                            )
                        }
                    }
                }
            }
        }

        // Legend: Light Green for Surplus (+), Light Red for Deficit (-)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(9.dp).background(Color(0xFFA9DFBF), RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Surplus (+)", style = MaterialTheme.typography.labelSmall, color = GrayTextSecondary)

            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.size(9.dp).background(Color(0xFFF5B7B1), RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Deficit (-)", style = MaterialTheme.typography.labelSmall, color = GrayTextSecondary)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(start = 45.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                .pointerInput(labels.size) {
                    detectTapGestures { offset ->
                        val n = labels.size
                        if (n > 0) {
                            val w = size.width
                            val tappedIdx = ((offset.x / w) * n).toInt().coerceIn(0, n - 1)
                            selectedIndex = if (selectedIndex == tappedIdx) null else tappedIdx
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val n = labels.size
            val barWidth = (w / n) * 0.55f

            val zeroY = h - (h * ((0.0 - minVal) / range).toFloat())

            // Grid lines
            for (i in 0..4) {
                val y = h - (h * (i.toFloat() / 4))
                val v = minVal + (range * (i.toDouble() / 4))
                drawLine(color = GrayBorder, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1.dp.toPx())
                val yText = "$currencySymbol${formatCompact(v)}"
                val yLayout = textMeasurer.measure(
                    text = AnnotatedString(yText),
                    style = TextStyle(fontSize = 9.sp, color = GrayTextMuted)
                )
                drawText(textLayoutResult = yLayout, topLeft = Offset(-yLayout.size.width - 4.dp.toPx(), y - yLayout.size.height / 2f))
            }

            // Zero baseline
            drawLine(color = GrayTextMuted, start = Offset(0f, zeroY), end = Offset(w, zeroY), strokeWidth = 1.5.dp.toPx())

            // Draw Balance Bars (Light Green for positive, Light Red for negative)
            for (i in 0 until n) {
                val bal = balanceList.getOrNull(i)
                if (bal != null) {
                    val centerX = (w / n) * (i + 0.5f)
                    val barY = h - (h * ((bal - minVal) / range).toFloat())
                    val barTop = minOf(zeroY, barY)
                    val barHeight = kotlin.math.abs(zeroY - barY)
                    val isSelected = selectedIndex == i

                    // Positive: Light Green, Negative: Light Red
                    val barColor = if (bal >= 0) {
                        if (isSelected) Color(0xFF58D68D) else Color(0xFFA9DFBF)
                    } else {
                        if (isSelected) Color(0xFFEC7063) else Color(0xFFF5B7B1)
                    }
                    val textColor = if (bal >= 0) Color(0xFF196F3D) else Color(0xFF922B21)

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(centerX - barWidth / 2f, barTop),
                        size = Size(barWidth, maxOf(2f, barHeight)),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )

                    // Value label above or below bar
                    val valText = formatCompact(bal)
                    val valLayout = textMeasurer.measure(
                        text = AnnotatedString(valText),
                        style = TextStyle(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    )
                    val valY = if (bal >= 0) barTop - valLayout.size.height - 2.dp.toPx() else barTop + barHeight + 2.dp.toPx()
                    drawText(
                        textLayoutResult = valLayout,
                        topLeft = Offset(centerX - valLayout.size.width / 2f, valY.coerceIn(0f, h - valLayout.size.height))
                    )
                }
            }

            // Draw Month Labels
            for (i in 0 until n) {
                val x = (w / n) * (i + 0.5f)
                val label = labels[i]
                val isSelected = selectedIndex == i
                val layout = textMeasurer.measure(
                    text = AnnotatedString(label),
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) NavySidebar else GrayTextSecondary
                    )
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x - layout.size.width / 2f, h + 4.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun FinancialHorizontalBarChart(
    items: List<BarItemData>,
    currencySymbol: String = "$",
    emptyLabel: String = "No Data",
    modifier: Modifier = Modifier
) {
    if (items.isEmpty() || items.all { it.value <= 0.0 }) {
        Box(
            modifier = modifier.fillMaxWidth().height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emptyLabel, color = GrayTextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val maxVal = items.maxOfOrNull { it.value } ?: 1.0
    var selectedLabel by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.filter { it.value > 0.0 }.sortedByDescending { it.value }.forEach { item ->
            val fraction = (item.value / maxVal).toFloat().coerceIn(0.02f, 1f)
            val isSelected = selectedLabel == item.label

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedLabel = if (selectedLabel == item.label) null else item.label
                    }
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) PrimaryBlue else NavySidebar
                        )
                        if (!item.subtitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${item.subtitle})",
                                style = MaterialTheme.typography.labelSmall,
                                color = GrayTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = "$currencySymbol${formatCompact(item.value)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = item.color
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(if (isSelected) NavySidebar else item.color, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun SparklineTrend(
    values: List<Double>,
    color: Color = PrimaryBlue,
    modifier: Modifier = Modifier.size(width = 80.dp, height = 24.dp)
) {
    if (values.isEmpty() || values.all { it == 0.0 }) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("-", color = GrayTextMuted, fontSize = 11.sp)
        }
        return
    }

    val maxVal = values.maxOrNull() ?: 1.0
    val minVal = values.minOrNull() ?: 0.0
    val range = if (maxVal == minVal) 1.0 else (maxVal - minVal)

    Canvas(modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
        val w = size.width
        val h = size.height
        val n = values.size
        val path = Path()
        var lastPt = Offset.Zero

        for (i in values.indices) {
            val v = values[i]
            val x = if (n > 1) (w * (i.toFloat() / (n - 1))) else w / 2f
            val y = h - (h * ((v - minVal) / range).toFloat())
            val pt = Offset(x, y)
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            lastPt = pt
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color = color, radius = 2.dp.toPx(), center = lastPt)
    }
}

fun formatCompact(v: Double): String {
    val abs = kotlin.math.abs(v)
    return when {
        abs >= 1_000_000_000 -> "%.1fB".format(v / 1_000_000_000)
        abs >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
        abs >= 1_000 -> "%.0fK".format(v / 1_000)
        else -> "%.0f".format(v)
    }
}

fun formatAmount(v: Double, symbol: String): String {
    return "%s%,.2f".format(symbol, v)
}
