package com.netscope.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.netscope.R
import com.netscope.data.AppDb
import com.netscope.data.WifiScanEntity
import com.netscope.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun WifiHost() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDb.get(ctx) }
    var hasPerm by remember { mutableStateOf(granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var nets by remember { mutableStateOf(emptyList<WifiNet>()) }
    var msg by remember { mutableStateOf<String?>(null) }
    var lastSave by remember { mutableLongStateOf(0L) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ask = rememberPermissionAsker(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION),
        stringResource(R.string.perm_location_title), stringResource(R.string.perm_location_why)
    ) { hasPerm = it }

    fun load() {
        val list = WifiUtil.readResults(ctx)
        nets = list
        val now = System.currentTimeMillis()
        if (list.isNotEmpty() && now - lastSave > 15_000) {
            lastSave = now
            scope.launch(Dispatchers.IO) {
                db.wifi().insertAll(list.map {
                    WifiScanEntity(0, now, it.ssid, it.bssid, it.rssi, it.freq, it.channel, it.band, it.security, it.vendor)
                })
            }
        }
    }

    fun scan() {
        if (!hasPerm) return
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!WifiUtil.isEnabled(ctx)) {
            msg = "Wi-Fi o'chirilgan. Uni yoqing."
        } else if (!LocationManagerCompat.isLocationEnabled(lm)) {
            msg = "Telefonda Joylashuv (GPS) xizmati o'chiq — Android shuning uchun skan natijasini bermaydi. Uni yoqing."
        } else {
            msg = if (WifiUtil.startScan(ctx)) null
            else "Android skanerlash sonini cheklaydi (taxminan 2 daqiqada 4 marta). Oxirgi natijalar ko'rsatilmoqda."
            load()
        }
    }

    DisposableEffect(hasPerm) {
        if (!hasPerm) return@DisposableEffect onDispose { }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { load() }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }
    LaunchedEffect(hasPerm) {
        if (hasPerm) {
            load()
            while (true) { scan(); delay(30_000) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("Tarmoqlar", "Kanallar", "Xarita", "Ulanish").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1, fontSize = 13.sp) })
            }
        }
        when {
            tab == 3 -> ConnectionTab()
            !hasPerm -> PermissionCard(stringResource(R.string.perm_location_title), stringResource(R.string.perm_location_why), ask)
            tab == 0 -> NetworkList(nets, msg) { scan() }
            tab == 1 -> ChannelTab(nets)
            else -> HeatmapScreen(nets)
        }
    }
}

@Composable
private fun NetworkList(nets: List<WifiNet>, msg: String?, onScan: () -> Unit) {
    var band by remember { mutableStateOf("Hammasi") }
    var sec by remember { mutableIntStateOf(0) }
    var sort by remember { mutableIntStateOf(0) }
    val filtered = nets.filter {
        (band == "Hammasi" || it.band == band) &&
            (sec == 0 || (sec == 1 && it.isOpen) || (sec == 2 && !it.isOpen))
    }
    val shown = if (sort == 0) filtered.sortedByDescending { it.rssi } else filtered.sortedBy { it.ssid.lowercase() }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Topilgan: ${shown.size} / ${nets.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onScan) { Text("Skanerlash") }
            }
            if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Hammasi", "2.4 GHz", "5 GHz", "6 GHz").forEach {
                    FilterChip(selected = band == it, onClick = { band = it }, label = { Text(it) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Barchasi", "Ochiq", "Himoyalangan").forEachIndexed { i, t ->
                    FilterChip(selected = sec == i, onClick = { sec = i }, label = { Text(t) })
                }
                FilterChip(selected = sort == 0, onClick = { sort = 0 }, label = { Text("Signal bo'yicha") })
                FilterChip(selected = sort == 1, onClick = { sort = 1 }, label = { Text("Nom bo'yicha") })
            }
        }
        items(shown) { n -> NetCard(n) }
        if (shown.isEmpty()) item { Text("Tarmoq topilmadi.", Modifier.padding(16.dp)) }
    }
}

@Composable
private fun NetCard(n: WifiNet) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SignalBars(n.bars)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.ssid, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${n.bssid} • ${n.vendor}", fontSize = 12.sp)
                Text("${n.rssi} dBm (${n.bars}/5) • kanal ${n.channel} • ${n.band}", fontSize = 12.sp)
                Text(n.security, fontSize = 12.sp,
                    color = if (n.isOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private val CH5 = listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
private val CH5_FREE = listOf(36, 40, 44, 48, 149, 153, 157, 161, 165) // DFS bo'lmagan kanallar

@Composable
private fun ChannelTab(nets: List<WifiNet>) {
    val n24 = nets.filter { it.band == "2.4 GHz" }
    val n5 = nets.filter { it.band == "5 GHz" }
    val ch24 = (1..13).toList()
    val counts24 = ch24.map { c -> n24.count { it.channel == c } }
    fun score(c: Int) = n24.sumOf { val d = abs(it.channel - c); if (d <= 4) 5 - d else 0 }
    val rec24 = listOf(1, 6, 11).minByOrNull { score(it) } ?: 1
    val counts5 = CH5.map { c -> n5.count { it.channel == c } }
    val rec5 = CH5_FREE.minByOrNull { c -> n5.count { it.channel == c } } ?: 36

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("2.4 GHz kanallar bandligi", style = MaterialTheme.typography.titleMedium)
            Text("Har bir ustun — shu kanaldagi tarmoqlar soni. Yashil — tavsiya etilgan kanal.", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            BarChart(ch24.map { it.toString() }, counts24, highlight = setOf(rec24 - 1), minSlot = 22.dp)
            Text(
                if (n24.isEmpty()) "2.4 GHz tarmoq topilmadi." else "Tavsiya: $rec24-kanal (qo'shni kanallar bilan kesishish hisobga olingan; 1, 6, 11 bir-biriga xalaqit bermaydi).",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))
            Text("5 GHz kanallar bandligi", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            BarChart(CH5.map { it.toString() }, counts5, highlight = setOf(CH5.indexOf(rec5)), minSlot = 30.dp)
            Text(
                if (n5.isEmpty()) "5 GHz tarmoq topilmadi." else "Tavsiya: $rec5-kanal (DFS talab qilmaydigan kanallar orasidan eng bo'sh).",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConnectionTab() {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf(Net.info(ctx)) }
    var phoneOk by remember { mutableStateOf(granted(ctx, Manifest.permission.READ_PHONE_STATE)) }
    val ask = rememberPermissionAsker(
        listOf(Manifest.permission.READ_PHONE_STATE),
        stringResource(R.string.perm_phone_title), stringResource(R.string.perm_phone_why)
    ) { phoneOk = it; rows = Net.info(ctx) }
    LaunchedEffect(Unit) { while (true) { rows = Net.info(ctx); delay(3000) } }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Joriy ulanish", style = MaterialTheme.typography.titleMedium) }
        items(rows) { (k, v) ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Text(k, Modifier.weight(0.45f), fontSize = 13.sp)
                    Text(v, Modifier.weight(0.55f), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
        }
        if (!phoneOk) item {
            Spacer(Modifier.height(8.dp))
            Text("Mobil tarmoq turi va signal darajasi uchun ruxsat kerak. Rad etilsa, faqat operator nomi ko'rsatiladi.", fontSize = 12.sp)
            OutlinedButton(onClick = ask) { Text("Telefon holati ruxsatini berish") }
        }
    }
}
