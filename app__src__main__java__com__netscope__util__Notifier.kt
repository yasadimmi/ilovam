package com.netscope.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.netscope.MainActivity
import com.netscope.R

object Notifier {
    const val CH_MON = "monitor"
    const val CH_ALERT = "alerts"

    fun init(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MON, "Monitoring", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Ogohlantirishlar", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun pi(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun ongoing(ctx: Context, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_MON)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("NetScope monitoring")
            .setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(pi(ctx)).build()

    @SuppressLint("MissingPermission")
    fun alert(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && !granted(ctx, Manifest.permission.POST_NOTIFICATIONS)) return
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(pi(ctx)).build()
        NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n)
    }
}
