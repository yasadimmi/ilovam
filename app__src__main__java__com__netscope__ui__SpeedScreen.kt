package com.netscope.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netscope.data.AppDb
import com.netscope.data.SpeedEntity
import com.netscope.util.Prefs
import com.netscope.util.SpeedTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SpeedScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var stage by remember { mutableStateOf("Tayyor") }
    var live by remember { mutableDoubleStateOf(0.0) }
    var down by remember { mutableStateOf<Double?>(null) }
    var up by remember { mutableStateOf<Double?>(null) }
    var ping by remember { mutableStateOf<Double?>(null) }
    var jitter by remember { mutableStateOf<Double?>(null) }
    var loss by remember { mutableStateOf<Int?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }

    fun start() {
        down = null; up = null; ping = null; jitter = null; loss = null; err = null; live = 0.0
        running = true
        job = scope.launch {
            val errors = mutableListOf<String>()
            try {
                stage = "Ping / jitter o'lchanmoqda…"
                runCatching { SpeedTester.ping(Prefs.pingHost) }.onSuccess {
                    if (it.avgMs >= 0) { ping = it.avgMs; jitter = it.jitterMs }; loss = it.lossPct
                }.onFailure { if (it is CancellationException) throw it; errors += "Ping: ${it.message}" }

                stage = "Yuklab olish tezligi…"; live = 0.0
                runCatching { SpeedTester.download(Prefs.dlUrl) { live = it } }
                    .onSuccess { down = it }
                    .onFailure { if (it is CancellationException) throw it; errors += "Yuklab olish: ${it.message}" }

                stage = "Yuklash (upload) tezligi…"; live = 0.0
                runCatching { SpeedTester.upload(Prefs.ulUrl) { live = it } }
                    .onSuccess { up = it }
                    .onFailure { if (it is CancellationException) throw it; errors += "Yuklash: ${it.message}" }

                stage = "Tayyor"; live = 0.0
                if (errors.isNotEmpty()) err = errors.joinToString("\n")
                if (down != null || up != null || ping != null) {
                    db.speed().insert(SpeedEntity(0, System.currentTimeMillis(), down ?: 0.0, up ?: 0.0, ping ?: 0.0, jitter ?: 0.0, Prefs.dlUrl))
                }
            } catch (e: CancellationException) {
                stage = "To'xtatildi"; throw e
            } finally {
                running = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Tezlik testi", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (running) job?.cancel() else start() }) { Text(if (running) "To'xtatish" else "Testni boshlash") }
        Spacer(Modifier.height(12.dp))
        Text(stage, fontSize = 14.sp)
        if (running) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (live > 0) Text(String.format("%.1f Mbps", live), style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultCard("Yuklab olish", down?.let { String.format("%.1f", it) }, "Mbps", Modifier.weight(1f))
            ResultCard("Yuklash", up?.let { String.format("%.1f", it) }, "Mbps", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultCard("Ping", ping?.let { String.format("%.0f", it) }, "ms", Modifier.weight(1f))
            ResultCard("Jitter", jitter?.let { String.format("%.1f", it) }, "ms", Modifier.weight(1f))
            ResultCard("Yo'qotish", loss?.toString(), "%", Modifier.weight(1f))
        }
        if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
        Text("Test manzillari Sozlamalar bo'limida o'zgartiriladi. Natijalar avtomatik Tarixga saqlanadi.", fontSize = 12.sp)
    }
}

@Composable
private fun ResultCard(title: String, value: String?, unit: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp)
            Text(value ?: "—", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(unit, fontSize = 12.sp)
        }
    }
}
