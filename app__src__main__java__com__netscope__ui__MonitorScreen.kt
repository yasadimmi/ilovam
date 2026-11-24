package com.netscope.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.netscope.R
import com.netscope.data.AppDb
import com.netscope.service.MonitorService
import com.netscope.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MonitorScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    val since = remember { System.currentTimeMillis() - 24 * 3_600_000L }
    val data by remember { db.uptime().since(since) }.collectAsState(emptyList())

    fun startService() {
        ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
    }
    val askNotif = rememberPermissionAsker(
        notifPerms(), stringResource(R.string.perm_notif_title), stringResource(R.string.perm_notif_why)
    ) { startService() } // rad etilsa ham xizmat ishlaydi, faqat bildirishnoma ko'rinmasligi mumkin

    // Qurilma/nuqta ogohlantirishi
    var homeSsid by remember { mutableStateOf(Prefs.homeSsid) }
    var samples by remember { mutableStateOf(Prefs.samples) }
    var tol by remember { mutableIntStateOf(Prefs.tolerance) }
    var alerts by remember { mutableStateOf(Prefs.alertsOn) }
    var router by remember { mutableStateOf(Prefs.routerIp) }
    var status by remember { mutableStateOf<String?>(null) }
    var locOk by remember { mutableStateOf(granted(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION)) }
    val askLoc = rememberPermissionAsker(
        listOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
        stringResource(R.string.perm_location_title), stringResource(R.string.perm_location_why)
    ) { locOk = it }

    fun scanNow(then: (List<WifiNet>) -> Unit) {
        scope.launch {
            status = "Skanerlanmoqda…"
            WifiUtil.startScan(ctx)
            delay(3500)
            val r = WifiUtil.readResults(ctx)
            status = null
            then(r)
        }
    }

    val upCount = data.count { it.up }
    val pct = if (data.isEmpty()) null else upCount * 100.0 / data.size
    val downEvents = data.zipWithNext().filter { (a, b) -> a.up && !b.up }.map { it.second.ts }.takeLast(5).reversed()

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Tarmoq sifati monitori", style = MaterialTheme.typography.headlineSmall)
            Text("Faqat o'zingiz ulangan Wi-Fi'ning shlyuzi (router) davriy tekshiriladi.", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    if (Prefs.monitorRunning) ctx.stopService(Intent(ctx, MonitorService::class.java)) else askNotif()
                }) { Text(if (Prefs.monitorRunning) "To'xtatish" else "Ishga tushirish") }
                Spacer(Modifier.width(12.dp))
                Text(if (Prefs.monitorRunning) "● Ishlayapti" else "○ To'xtagan", fontSize = 13.sp)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Oxirgi 24 soat — kechikish (ms)", fontWeight = FontWeight.Bold)
                    LineChart(data.map { Pt(it.ts, if (it.up) it.latencyMs.toFloat() else 0f, bad = !it.up) }, unit = "")
                    Text("Qizil nuqta — shlyuz javob bermagan paytlar.", fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(if (pct == null) "Hali ma'lumot yo'q" else "Ish vaqti (uptime): ${String.format("%.1f", pct)}% • ${data.size} ta tekshiruv", fontSize = 13.sp)
                }
            }
        }
        if (downEvents.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Oxirgi uzilishlar", fontWeight = FontWeight.Bold)
                    downEvents.forEach { Text(fmtTime(it), fontSize = 13.sp) }
                }
            }
        }
        item { HorizontalDivider() }
        item {
            Text("Wi-Fi nuqtalari ogohlantirishi", style = MaterialTheme.typography.titleMedium)
            Text(
                "Oddiy telefon boshqa foydalanuvchilarning qurilmalarini routerdan o'qiy olmaydi. Shuning uchun uy Wi-Fi'ingizning ko'rinadigan nuqtalari (BSSID) soni va yangi notanish nuqta paydo bo'lishi kuzatiladi " +
                    "(masalan, soxta \"egizak\" tarmoq).", fontSize = 12.sp
            )
        }
        item {
            OutlinedTextField(homeSsid, { homeSsid = it; Prefs.homeSsid = it }, label = { Text("Uy Wi-Fi nomi (SSID)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (!locOk) askLoc()
                    else if (homeSsid.isBlank()) Toast.makeText(ctx, "Avval SSID kiriting", Toast.LENGTH_SHORT).show()
                    else scanNow { nets ->
                        val n = DeviceAlert.addSample(nets, homeSsid)
                        samples = Prefs.samples
                        Toast.makeText(ctx, "Namuna qo'shildi: $n ta nuqta", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Namuna qo'shish") }
                OutlinedButton(onClick = { DeviceAlert.reset(); samples = emptyList() }) { Text("Tiklash") }
            }
            val b = DeviceAlert.baseline()
            Text(
                if (b == null) "Odatiy qiymat hali yo'q. Turli vaqtlarda 3–5 marta \"Namuna qo'shish\" ni bosing."
                else "Odatiy qiymat: $b ta nuqta (${samples.size} ta namuna) • ma'lum BSSID: ${Prefs.knownBssids.size}",
                fontSize = 13.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ruxsat etilgan og'ish: ±$tol", Modifier.weight(1f), fontSize = 13.sp)
                OutlinedButton(onClick = { if (tol > 0) { tol--; Prefs.tolerance = tol } }) { Text("−") }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { if (tol < 10) { tol++; Prefs.tolerance = tol } }) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monitoring xizmati ichida ogohlantirish", Modifier.weight(1f), fontSize = 13.sp)
                Switch(alerts, { alerts = it; Prefs.alertsOn = it })
            }
            OutlinedButton(onClick = {
                if (!locOk) askLoc() else scanNow { nets ->
                    val msg = DeviceAlert.evaluate(nets)
                    if (msg == null) Toast.makeText(ctx, "Hammasi odatdagidek", Toast.LENGTH_SHORT).show()
                    else Notifier.alert(ctx, "Wi-Fi: g'ayrioddiy holat", msg).also { status = msg }
                }
            }) { Text("Hozir tekshirish") }
            if (status != null) Text(status!!, fontSize = 12.sp)
        }
        item { HorizontalDivider() }
        item {
            Text("Router boshqaruv sahifasi (ixtiyoriy)", style = MaterialTheme.typography.titleMedium)
            Text("O'z routeringizning mahalliy IP manzilini kiriting (masalan 192.168.1.1). Ilova faqat brauzerda ochadi, hech narsa o'qimaydi.", fontSize = 12.sp)
            OutlinedTextField(router, { router = it; Prefs.routerIp = it.trim() }, label = { Text("Router IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                val ip = router.trim().ifBlank { Net.gateway(ctx) ?: "" }
                if (!Net.isPrivateIpv4(ip)) Toast.makeText(ctx, "Faqat mahalliy (private) IP kiritilishi mumkin", Toast.LENGTH_LONG).show()
                else try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$ip")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: Exception) { Toast.makeText(ctx, "Brauzer topilmadi", Toast.LENGTH_SHORT).show() }
            }) { Text("Brauzerda ochish") }
        }
    }
}
