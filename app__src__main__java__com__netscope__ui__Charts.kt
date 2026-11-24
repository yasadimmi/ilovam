package com.netscope.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtTime(ts: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(ts))
private fun fmtShort(ts: Long): String = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ts))

@Composable
fun SignalBars(level: Int, modifier: Modifier = Modifier) {
    val on = when { level >= 4 -> Color(0xFF2E9E4F); level == 3 -> Color(0xFFE0A800); else -> Color(0xFFD64545) }
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(28.dp, 24.dp)) {
        val w = size.width / 9f
        for (i in 0 until 5) {
            val h = size.height * (i + 1) / 5f
            drawRect(if (i < level) on else off, Offset(i * w * 1.8f, size.height - h), Size(w, h))
        }
    }
}

@Composable
fun BarChart(
    labels: List<String>, values: List<Int>, modifier: Modifier = Modifier,
    highlight: Set<Int> = emptySet(), minSlot: Dp = 28.dp
) {
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val bar = MaterialTheme.colorScheme.primary
    val good = Color(0xFF2E9E4F)
    val axis = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current
    val paint = remember(onSurface, density) {
        Paint().apply { color = onSurface; textAlign = Paint.Align.CENTER; isAntiAlias = true; textSize = with(density) { 10.sp.toPx() } }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = maxOf(maxWidth, minSlot * labels.size)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(w).height(200.dp)) {
                val n = labels.size.coerceAtLeast(1)
                val maxV = (values.maxOrNull() ?: 1).coerceAtLeast(1)
                val bottom = size.height - 24.dp.toPx()
                val top = 18.dp.toPx()
                val slot = size.width / n
                val bw = slot * 0.6f
                values.forEachIndexed { i, v ->
                    val h = (bottom - top) * v / maxV
                    val x = slot * i + (slot - bw) / 2
                    drawRect(if (i in highlight) good else bar, Offset(x, bottom - h), Size(bw, h))
                    drawContext.canvas.nativeCanvas.drawText(labels[i], x + bw / 2, size.height - 6.dp.toPx(), paint)
                    if (v > 0) drawContext.canvas.nativeCanvas.drawText(v.toString(), x + bw / 2, bottom - h - 4.dp.toPx(), paint)
                }
                drawLine(axis, Offset(0f, bottom), Offset(size.width, bottom), 2f)
            }
        }
    }
}

data class Pt(val t: Long, val v: Float, val bad: Boolean = false)

@Composable
fun LineChart(points: List<Pt>, modifier: Modifier = Modifier, unit: String = "") {
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current
    val paint = remember(onSurface, density) {
        Paint().apply { color = onSurface; isAntiAlias = true; textSize = with(density) { 10.sp.toPx() } }
    }
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text("Ma'lumot yo'q") }
        return
    }
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        val minT = points.first().t
        val maxT = points.last().t.coerceAtLeast(minT + 1)
        var minV = points.minOf { it.v }
        var maxV = points.maxOf { it.v }
        if (maxV - minV < 1f) { maxV += 1f; minV -= 1f }
        val padL = 8.dp.toPx(); val padR = 8.dp.toPx()
        val top = 18.dp.toPx(); val bottom = size.height - 22.dp.toPx()
        fun px(t: Long) = padL + (size.width - padL - padR) * (t - minT) / (maxT - minT).toFloat()
        fun py(v: Float) = bottom - (bottom - top) * (v - minV) / (maxV - minV)
        drawLine(axis, Offset(padL, bottom), Offset(size.width - padR, bottom), 2f)
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p.t), py(p.v)) else path.lineTo(px(p.t), py(p.v)) }
        drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
        points.forEach { p ->
            if (p.bad) drawCircle(Color(0xFFD64545), 4.dp.toPx(), Offset(px(p.t), py(p.v)))
            else if (points.size <= 40) drawCircle(line, 2.5.dp.toPx(), Offset(px(p.t), py(p.v)))
        }
        val c = drawContext.canvas.nativeCanvas
        c.drawText("${maxV.toInt()}$unit", padL, 12.dp.toPx(), paint)
        c.drawText("${minV.toInt()}$unit", padL, bottom - 4.dp.toPx(), paint)
        c.drawText(fmtShort(minT), padL, size.height - 4.dp.toPx(), paint)
        val endLabel = fmtShort(maxT)
        c.drawText(endLabel, size.width - padR - paint.measureText(endLabel), size.height - 4.dp.toPx(), paint)
    }
}
