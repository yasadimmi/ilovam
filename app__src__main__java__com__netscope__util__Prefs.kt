package com.netscope.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Barcha sozlamalar faqat shu qurilmadagi shaxsiy SharedPreferences'da saqlanadi. */
object Prefs {
    const val DEFAULT_DL = "https://speed.cloudflare.com/__down?bytes=25000000"
    const val DEFAULT_UL = "https://speed.cloudflare.com/__up"
    const val DEFAULT_PING = "1.1.1.1"

    private lateinit var sp: SharedPreferences

    var themeMode by mutableIntStateOf(0)
        private set
    var monitorRunning by mutableStateOf(false)

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("netscope", Context.MODE_PRIVATE)
        themeMode = sp.getInt("theme", 0)
    }

    fun setTheme(m: Int) { themeMode = m; sp.edit().putInt("theme", m).apply() }

    private fun s(k: String, d: String): String = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) { sp.edit().putString(k, v).apply() }

    var dlUrl: String
        get() = s("dl", DEFAULT_DL)
        set(v) = put("dl", v)
    var ulUrl: String
        get() = s("ul", DEFAULT_UL)
        set(v) = put("ul", v)
    var pingHost: String
        get() = s("ping", DEFAULT_PING)
        set(v) = put("ping", v)
    var routerIp: String
        get() = s("router", "")
        set(v) = put("router", v)
    var homeSsid: String
        get() = s("homeSsid", "")
        set(v) = put("homeSsid", v)
    var qrSsid: String
        get() = s("qrSsid", "")
        set(v) = put("qrSsid", v)
    var qrPass: String
        get() = s("qrPass", "")
        set(v) = put("qrPass", v)
    var qrSec: String
        get() = s("qrSec", "WPA")
        set(v) = put("qrSec", v)
    var monitorInterval: Int
        get() = sp.getInt("interval", 30)
        set(v) { sp.edit().putInt("interval", v).apply() }
    var tolerance: Int
        get() = sp.getInt("tolerance", 2)
        set(v) { sp.edit().putInt("tolerance", v).apply() }
    var alertsOn: Boolean
        get() = sp.getBoolean("alertsOn", true)
        set(v) { sp.edit().putBoolean("alertsOn", v).apply() }
    var lastUp: Boolean
        get() = sp.getBoolean("lastUp", true)
        set(v) { sp.edit().putBoolean("lastUp", v).apply() }
    var samples: List<Int>
        get() = s("samples", "").split(',').mapNotNull { it.trim().toIntOrNull() }
        set(v) = put("samples", v.joinToString(","))
    var knownBssids: Set<String>
        get() = (sp.getStringSet("known", emptySet()) ?: emptySet()).toSet()
        set(v) { sp.edit().putStringSet("known", v.toSet()).apply() }
}
