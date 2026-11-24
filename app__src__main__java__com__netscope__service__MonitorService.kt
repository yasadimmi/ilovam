package com.netscope.service

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.netscope.data.AppDb
import com.netscope.data.UptimeEntity
import com.netscope.util.*
import kotlinx.coroutines.*

/** Foydalanuvchining O'Z Wi-Fi shlyuzini davriy tekshiradi. Boshqa qurilmalarga hech narsa yuborilmaydi. */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.ongoing(this, "Monitoring boshlanmoqda…")
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        Prefs.monitorRunning = true
        if (job == null) job = scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel(); scope.cancel()
        Prefs.monitorRunning = false
        super.onDestroy()
    }

    private suspend fun loop() {
        val db = AppDb.get(this)
        val nm = getSystemService(NotificationManager::class.java)
        var lastScan = 0L
        var lastAlert = ""
        var lastPrune = 0L
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val gw = Net.gateway(this)
            if (gw == null) {
                nm.notify(1, Notifier.ongoing(this, "Wi-Fi tarmog'iga ulanmagan — kutilmoqda"))
            } else {
                val ms = Net.pingGateway(gw)
                val up = ms >= 0
                db.uptime().insert(UptimeEntity(ts = now, up = up, latencyMs = ms.coerceAtLeast(0), gateway = gw))
                nm.notify(1, Notifier.ongoing(this, if (up) "Shlyuz $gw: $ms ms" else "Shlyuz $gw javob bermayapti"))
                if (up != Prefs.lastUp) {
                    Prefs.lastUp = up
                    Notifier.alert(this, if (up) "Aloqa tiklandi" else "Aloqa uzildi", "Shlyuz: $gw")
                }
            }
            if (now - lastPrune > 3_600_000) { lastPrune = now; db.uptime().deleteBefore(now - 7 * 86_400_000L) }
            if (Prefs.alertsOn && now - lastScan > 300_000 && granted(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                lastScan = now
                WifiUtil.startScan(this)
                delay(4000)
                val msg = DeviceAlert.evaluate(WifiUtil.readResults(this))
                if (msg != null && msg != lastAlert) { lastAlert = msg; Notifier.alert(this, "Wi-Fi: g'ayrioddiy holat", msg) }
                if (msg == null) lastAlert = ""
            }
            delay(Prefs.monitorInterval.coerceAtLeast(10) * 1000L)
        }
    }
}
