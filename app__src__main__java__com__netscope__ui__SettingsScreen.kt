package com.netscope.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netscope.data.AppDb
import com.netscope.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    var page by rememberSaveable { mutableStateOf("main") }
    BackHandler(page != "main") { page = "main" }
    when (page) {
        "share" -> QrShareScreen { page = "main" }
        "scan" -> QrScanScreen { page = "main" }
        else -> SettingsMain { page = it }
    }
}

@Composable
private fun SettingsMain(go: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dl by remember { mutableStateOf(Prefs.dlUrl) }
    var ul by remember { mutableStateOf(Prefs.ulUrl) }
    var ping by remember { mutableStateOf(Prefs.pingHost) }
    var interval by remember { mutableStateOf(Prefs.monitorInterval.toString()) }
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Barcha tarixni o'chirish?") },
        text = { Text("Wi-Fi, Bluetooth, tezlik, monitoring va issiqlik xaritalari o'chadi. Bu amalni qaytarib bo'lmaydi.") },
        confirmButton = {
            TextButton(onClick = {
                confirmClear = false
                scope.launch(Dispatchers.IO) { AppDb.get(ctx).clearAllTables() }
            }) { Text("O'chirish") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Bekor qilish") } }
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sozlamalar", style = MaterialTheme.typography.headlineSmall)

        Text("Mavzu", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Tizim" to 0, "Och" to 1, "Qorong'u" to 2).forEach { (l, m) ->
                FilterChip(selected = Prefs.themeMode == m, onClick = { Prefs.setTheme(m) }, label = { Text(l) })
            }
        }
        HorizontalDivider()

        Text("QR orqali Wi-Fi", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { go("share") }) { Text("QR yaratish") }
            OutlinedButton(onClick = { go("scan") }) { Text("QR skanerlash") }
        }
        HorizontalDivider()

        Text("Tezlik testi manzillari", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(dl, { dl = it }, label = { Text("Yuklab olish URL (https)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ul, { ul = it }, label = { Text("Yuklash (upload) URL — POST qabul qilishi kerak") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ping, { ping = it }, label = { Text("Ping uchun host (TCP 443)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (!dl.startsWith("https://") || !ul.startsWith("https://") || ping.isBlank()) {
                    Toast.makeText(ctx, "URL'lar https:// bilan boshlanishi kerak", Toast.LENGTH_LONG).show()
                } else {
                    Prefs.dlUrl = dl.trim(); Prefs.ulUrl = ul.trim(); Prefs.pingHost = ping.trim()
                    Toast.makeText(ctx, "Saqlandi", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Saqlash") }
            OutlinedButton(onClick = {
                dl = Prefs.DEFAULT_DL; ul = Prefs.DEFAULT_UL; ping = Prefs.DEFAULT_PING
                Prefs.dlUrl = dl; Prefs.ulUrl = ul; Prefs.pingHost = ping
            }) { Text("Standart") }
        }
        HorizontalDivider()

        Text("Monitoring oralig'i (soniya, 10–600)", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(3) }, singleLine = true, modifier = Modifier.width(120.dp))
            Button(onClick = {
                val v = interval.toIntOrNull()?.coerceIn(10, 600) ?: 30
                interval = v.toString(); Prefs.monitorInterval = v
                Toast.makeText(ctx, "Saqlandi (xizmatni qayta ishga tushiring)", Toast.LENGTH_SHORT).show()
            }) { Text("Saqlash") }
        }
        HorizontalDivider()

        Text("Ma'lumotlar", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { confirmClear = true }) { Text("Barcha tarixni o'chirish") }
        Text("Eksport/import \"Tarix\" bo'limida.", fontSize = 12.sp)
        HorizontalDivider()

        Text("NetScope 1.0.0 haqida", style = MaterialTheme.typography.titleMedium)
        Text(
            "NetScope faqat passiv kuzatuv vositasi: u hujum qilmaydi, boshqa qurilmalarni skanerlamaydi, paket yubormaydi. " +
                "Faqat Android ochiq API'lari beradigan ma'lumot o'qiladi; tekshirish faqat o'z telefoningiz va ulangan routeringizga (shlyuz) qaratilgan. " +
                "Hamma ma'lumot oflayn, faqat shu telefonda saqlanadi. Internet faqat tezlik testi uchun ishlatiladi.",
            fontSize = 12.sp
        )
    }
}
