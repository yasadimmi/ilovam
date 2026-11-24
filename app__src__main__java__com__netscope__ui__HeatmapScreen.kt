package com.netscope.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netscope.data.AppDb
import com.netscope.data.HeatMapEntity
import com.netscope.data.HeatPointEntity
import com.netscope.util.WifiNet
import com.netscope.util.WifiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

fun heatColor(rssi: Int): Color {
    val t = ((rssi + 90) / 60f).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(120f * t, 0.85f, 0.95f)))
}

private fun loadBitmap(path: String): ImageBitmap? = try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    var s = 1
    while (o.outWidth / s > 1600 || o.outHeight / s > 1600) s *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
} catch (e: Exception) { null }

private fun copyPlan(ctx: Context, uri: Uri): String? = try {
    val f = File(ctx.filesDir, "plan_${System.currentTimeMillis()}.img")
    ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    f.absolutePath
} catch (e: Exception) { null }

@Composable
fun HeatmapScreen(nets: List<WifiNet>) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    val maps by db.heat().maps().collectAsState(emptyList())
    var open by remember { mutableStateOf<HeatMapEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val o = open
    if (o != null) {
        HeatEditor(o) { open = null }
        return
    }

    if (creating) CreateMapDialog(nets, onDismiss = { creating = false }) { name, net, uri ->
        creating = false
        scope.launch(Dispatchers.IO) {
            val path = uri?.let { copyPlan(ctx, it) }
            db.heat().insertMap(HeatMapEntity(0, name, path, net.bssid, net.ssid, System.currentTimeMillis()))
        }
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Signal issiqlik xaritasi", style = MaterialTheme.typography.titleMedium)
            Text("Xona bo'ylab yuring va xaritaning turgan joyingizga mos nuqtasini bosing — shu nuqtadagi signal kuchi yoziladi.", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { creating = true }) { Text("Yangi xarita") }
        }
        items(maps) { m ->
            Card(Modifier.fillMaxWidth().clickable { open = m }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontWeight = FontWeight.Bold)
                        Text("${m.ssid} • ${m.bssid}", fontSize = 12.sp)
                        Text(fmtTime(m.createdAt), fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            db.heat().deletePointsOf(m.id); db.heat().deleteMap(m.id)
                            m.imagePath?.let { File(it).delete() }
                        }
                    }) { Text("O'chirish") }
                }
            }
        }
        if (maps.isEmpty()) item { Text("Hali xarita yo'q.") }
    }
}

@Composable
private fun CreateMapDialog(nets: List<WifiNet>, onDismiss: () -> Unit, onCreate: (String, WifiNet, Uri?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<WifiNet?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var menu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yangi xarita") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nomi (masalan: Mehmonxona)") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(sel?.let { "${it.ssid} (${it.bssid})" } ?: "Tarmoqni tanlang") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        nets.sortedByDescending { it.rssi }.forEach {
                            DropdownMenuItem(text = { Text("${it.ssid} • ${it.rssi} dBm") }, onClick = { sel = it; menu = false })
                        }
                    }
                }
                if (nets.isEmpty()) Text("Avval \"Tarmoqlar\" bo'limida skanerlang.", fontSize = 12.sp)
                OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (uri == null) "Reja rasmini tanlash (ixtiyoriy)" else "Rasm tanlandi ✓") }
                Text("Rasm tanlanmasa, bo'sh setka ishlatiladi.", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && sel != null, onClick = { onCreate(name.trim(), sel!!, uri) }) { Text("Yaratish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } }
    )
}

@Composable
private fun HeatEditor(map: HeatMapEntity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    val points by db.heat().points(map.id).collectAsState(emptyList())
    val bmp = remember(map.imagePath) { map.imagePath?.let { loadBitmap(it) } }
    val aspect = bmp?.let { it.width.toFloat() / it.height } ?: (4f / 3f)
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val gridC = MaterialTheme.colorScheme.outlineVariant

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Orqaga") }
            Text(map.name, style = MaterialTheme.typography.titleMedium)
        }
        Text("Kuzatilayotgan tarmoq: ${map.ssid}", fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(aspect).pointerInput(map.id) {
                detectTapGestures { off ->
                    val x = (off.x / size.width).coerceIn(0f, 1f)
                    val y = (off.y / size.height).coerceIn(0f, 1f)
                    val r = WifiUtil.currentRssi(ctx, map.bssid)
                    if (r == null) {
                        Toast.makeText(ctx, "Tarmoq signali topilmadi. Tarmoq diapazonida ekanligingizni tekshiring.", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch(Dispatchers.IO) { db.heat().insertPoint(HeatPointEntity(0, map.id, x, y, r, System.currentTimeMillis())) }
                        WifiUtil.startScan(ctx)
                    }
                }
            }
        ) {
            if (bmp != null) drawImage(bmp, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            else {
                drawRect(bg)
                for (i in 1 until 10) {
                    drawLine(gridC, Offset(size.width * i / 10, 0f), Offset(size.width * i / 10, size.height), 1f)
                    drawLine(gridC, Offset(0f, size.height * i / 10), Offset(size.width, size.height * i / 10), 1f)
                }
            }
            points.forEach { p ->
                val c = heatColor(p.rssi)
                val center = Offset(p.x * size.width, p.y * size.height)
                val rad = size.width * 0.14f
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.75f), c.copy(alpha = 0f)), center, rad), rad, center)
                drawCircle(Color.White, 5.dp.toPx(), center)
                drawCircle(c, 3.5.dp.toPx(), center)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("-90", fontSize = 11.sp)
            Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 6.dp)) {
                drawRect(Brush.horizontalGradient((0..10).map { heatColor(-90 + it * 6) }))
            }
            Text("-30 dBm", fontSize = 11.sp)
        }
        Text("Nuqtalar: ${points.size}" + if (points.isNotEmpty()) " • o'rtacha ${points.map { it.rssi }.average().toInt()} dBm" else "", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = points.isNotEmpty(), onClick = {
                scope.launch(Dispatchers.IO) { points.lastOrNull()?.let { db.heat().deletePoint(it.id) } }
            }) { Text("Oxirgisini o'chirish") }
            OutlinedButton(enabled = points.isNotEmpty(), onClick = {
                scope.launch(Dispatchers.IO) { db.heat().deletePointsOf(map.id) }
            }) { Text("Tozalash") }
        }
    }
}
