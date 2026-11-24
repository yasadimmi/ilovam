package com.netscope.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netscope.data.*
import com.netscope.util.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var from by remember { mutableLongStateOf(0L) }
    var to by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var rangeIdx by remember { mutableIntStateOf(3) }
    var showPicker by remember { mutableStateOf(false) }
    var selBssid by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }

    val wifiAll by remember(from) { db.wifi().since(from) }.collectAsState(emptyList())
    val bleAll by remember(from) { db.ble().since(from) }.collectAsState(emptyList())
    val speedAll by remember(from) { db.speed().since(from) }.collectAsState(emptyList())
    val wifi = wifiAll.filter { it.ts <= to }
    val ble = bleAll.filter { it.ts <= to }
    val speed = speedAll.filter { it.ts <= to }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    fun write(uri: Uri?) {
        val data = pending; pending = null
        if (uri == null || data == null) return
        scope.launch(Dispatchers.IO) {
            val msg = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                "Fayl saqlandi"
            } catch (e: Exception) { "Saqlashda xato: ${e.message}" }
            withContext(Dispatchers.Main) { toast(msg) }
        }
    }
    val csvL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { write(it) }
    val jsonL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { write(it) }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val msg = try {
                val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                "Tiklandi: ${Exporter.import(db, text)} ta yozuv"
            } catch (e: Exception) { "Import xatosi: ${e.message}" }
            withContext(Dispatchers.Main) { toast(msg) }
        }
    }

    fun export(json: Boolean) {
        val names = listOf("wifi", "bluetooth", "tezlik")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        pending = if (json) when (tab) { 0 -> Exporter.wifiJson(wifi); 1 -> Exporter.bleJson(ble); else -> Exporter.speedJson(speed) }
        else when (tab) { 0 -> Exporter.wifiCsv(wifi); 1 -> Exporter.bleCsv(ble); else -> Exporter.speedCsv(speed) }
        val name = "netscope_${names[tab]}_$stamp.${if (json) "json" else "csv"}"
        if (json) jsonL.launch(name) else csvL.launch(name)
    }

    if (showPicker) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = st.selectedStartDateMillis
                    if (s != null) {
                        from = s; to = (st.selectedEndDateMillis ?: s) + 86_400_000L - 1; rangeIdx = 4
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Bekor qilish") } }
        ) { DateRangePicker(state = st, modifier = Modifier.height(480.dp)) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Tarix")
        TabRow(selectedTabIndex = tab) {
            listOf("Wi-Fi", "Bluetooth", "Tezlik").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val day = 86_400_000L
            listOf("24 soat" to day, "7 kun" to 7 * day, "30 kun" to 30 * day, "Hammasi" to 0L).forEachIndexed { i, (label, span) ->
                FilterChip(selected = rangeIdx == i, onClick = {
                    rangeIdx = i; to = Long.MAX_VALUE
                    from = if (span == 0L) 0L else System.currentTimeMillis() - span
                }, label = { Text(label) })
            }
            FilterChip(selected = rangeIdx == 4, onClick = { showPicker = true }, label = { Text("Sana tanlash") })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { export(false) }) { Text("CSV eksport") }
            OutlinedButton(onClick = { export(true) }) { Text("JSON eksport") }
            OutlinedButton(onClick = { importL.launch(arrayOf("*/*")) }) { Text("JSON import") }
        }

        when (tab) {
            0 -> {
                val groups = wifi.groupBy { it.bssid }.values.map { g -> g.sortedBy { it.ts } }.sortedByDescending { it.last().ts }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sel = groups.firstOrNull { it.first().bssid == selBssid }
                    if (sel != null) item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${sel.last().ssid} — signal tendensiyasi (dBm)", fontWeight = FontWeight.Bold)
                                LineChart(sel.map { Pt(it.ts, it.rssi.toFloat()) }, unit = "")
                            }
                        }
                    }
                    items(groups) { g ->
                        val l = g.last()
                        Card(Modifier.fillMaxWidth().clickable { selBssid = l.bssid }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(l.ssid, fontWeight = FontWeight.Bold)
                                Text("${l.bssid} • ${l.vendor}", fontSize = 12.sp)
                                Text("Oxirgi: ${l.rssi} dBm • ${l.band} • kanal ${l.channel} • ${l.security}", fontSize = 12.sp)
                                Text("${g.size} marta ko'rilgan • ${fmtTime(l.ts)}", fontSize = 12.sp)
                            }
                        }
                    }
                    if (groups.isEmpty()) item { Text("Ma'lumot yo'q.") }
                }
            }
            1 -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ble.take(300)) { b ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(b.name, fontWeight = FontWeight.Bold)
                            Text("${b.address} • ${b.kind} • ${b.rssi} dBm", fontSize = 12.sp)
                            Text(fmtTime(b.ts), fontSize = 12.sp)
                        }
                    }
                }
                if (ble.isEmpty()) item { Text("Ma'lumot yo'q.") }
                if (ble.size > 300) item { Text("Ro'yxatda oxirgi 300 ta ko'rsatilgan; eksport hammasini saqlaydi.", fontSize = 12.sp) }
            }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Yuklab olish tezligi (Mbps)", fontWeight = FontWeight.Bold)
                            LineChart(speed.sortedBy { it.ts }.map { Pt(it.ts, it.downMbps.toFloat()) })
                        }
                    }
                }
                items(speed) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("↓ ${String.format("%.1f", s.downMbps)}  ↑ ${String.format("%.1f", s.upMbps)} Mbps", fontWeight = FontWeight.Bold)
                            Text("Ping ${String.format("%.0f", s.pingMs)} ms • jitter ${String.format("%.1f", s.jitterMs)} ms", fontSize = 12.sp)
                            Text(fmtTime(s.ts), fontSize = 12.sp)
                        }
                    }
                }
                if (speed.isEmpty()) item { Text("Ma'lumot yo'q.") }
            }
        }
    }
}
