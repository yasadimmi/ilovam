package com.netscope.util

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.pow

data class WifiNet(
    val ssid: String, val bssid: String, val rssi: Int, val freq: Int,
    val channel: Int, val band: String, val security: String, val vendor: String
) {
    val bars: Int get() = WifiUtil.bars(rssi)
    val isOpen: Boolean get() = security == "Ochiq"
}

/** MAC OUI prefiksidan ishlab chiqaruvchini topish (assets/oui.csv, oflayn, qisqa jadval). */
object Oui {
    private var map: Map<String, String> = emptyMap()

    fun load(ctx: Context) {
        map = try {
            ctx.assets.open("oui.csv").bufferedReader().useLines { lines ->
                lines.filter { it.contains(',') }.associate {
                    val p = it.split(',', limit = 2)
                    p[0].trim().uppercase() to p[1].trim()
                }
            }
        } catch (e: IOException) { emptyMap() }
    }

    fun lookup(mac: String): String {
        val c = mac.uppercase().replace(":", "").replace("-", "")
        if (c.length < 6) return "Noma'lum"
        val first = c.substring(0, 2).toIntOrNull(16) ?: return "Noma'lum"
        if ((first and 2) != 0) return "Lokal/tasodifiy MAC"
        return map[c.substring(0, 6)] ?: "Noma'lum"
    }
}

@Suppress("DEPRECATION")
object WifiUtil {
    fun band(f: Int): String = when {
        f in 2400..2500 -> "2.4 GHz"
        f in 4900..5900 -> "5 GHz"
        f >= 5925 -> "6 GHz"
        else -> "?"
    }

    fun channel(f: Int): Int = when {
        f == 2484 -> 14
        f in 2400..2500 -> (f - 2407) / 5
        f in 4900..5900 -> (f - 5000) / 5
        f >= 5925 -> (f - 5950) / 5
        else -> 0
    }

    fun security(cap: String): String {
        val c = cap.uppercase()
        return when {
            "EAP" in c -> "Enterprise (802.1X)"
            "SAE" in c && "PSK" in c -> "WPA2/WPA3"
            "SAE" in c -> "WPA3"
            "OWE" in c -> "OWE (shifrlangan ochiq)"
            "WPA2" in c || "RSN" in c -> "WPA2"
            "WPA" in c -> "WPA"
            "WEP" in c -> "WEP"
            else -> "Ochiq"
        }
    }

    fun bars(rssi: Int): Int = when {
        rssi >= -55 -> 5
        rssi >= -65 -> 4
        rssi >= -75 -> 3
        rssi >= -85 -> 2
        else -> 1
    }

    private fun wm(ctx: Context) = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun isEnabled(ctx: Context): Boolean = wm(ctx).isWifiEnabled

    fun toNet(r: ScanResult): WifiNet {
        val bssid = (r.BSSID ?: "").uppercase()
        val ssid = (r.SSID ?: "").ifBlank { "(Yashirin tarmoq)" }
        return WifiNet(ssid, bssid, r.level, r.frequency, channel(r.frequency), band(r.frequency),
            security(r.capabilities ?: ""), Oui.lookup(bssid))
    }

    fun readResults(ctx: Context): List<WifiNet> = try {
        wm(ctx).scanResults.map { toNet(it) }
    } catch (e: SecurityException) { emptyList() }

    /** Android qisqa vaqt ichida skanerlashni cheklaydi; false = cheklangan. */
    fun startScan(ctx: Context): Boolean = try { wm(ctx).startScan() } catch (e: SecurityException) { false }

    /** Hozirgi RSSI: ulangan bo'lsa aynan shu tarmoqniki, aks holda oxirgi skan natijasi. */
    fun currentRssi(ctx: Context, bssid: String): Int? {
        try {
            val ci = wm(ctx).connectionInfo
            if (ci != null && ci.bssid.equals(bssid, true) && ci.rssi > -127) return ci.rssi
        } catch (e: SecurityException) { /* e'tiborsiz */ }
        return readResults(ctx).firstOrNull { it.bssid.equals(bssid, true) }?.rssi
    }
}

object Net {
    private fun cm(ctx: Context) = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isWifiActive(ctx: Context): Boolean {
        val c = cm(ctx)
        val n = c.activeNetwork ?: return false
        return c.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    /** Faqat Wi-Fi'ga ulangan bo'lsa, foydalanuvchining O'Z shlyuzi (router) IP'sini qaytaradi. */
    fun gateway(ctx: Context): String? {
        if (!isWifiActive(ctx)) return null
        val c = cm(ctx)
        val n = c.activeNetwork ?: return null
        val lp = c.getLinkProperties(n) ?: return null
        val gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        return gw?.takeIf { it != "0.0.0.0" }
    }

    /** Yengil "socket ping": shlyuzga TCP ulanish vaqti (ms). Rad etilgan ulanish ham host javobi hisoblanadi. -1 = javob yo'q. */
    fun pingGateway(host: String): Int {
        for (port in intArrayOf(80, 443, 53)) {
            val t = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), 1000) }
                return ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
            } catch (e: ConnectException) {
                if (e.message?.contains("refused", true) == true)
                    return ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
            } catch (e: Exception) { /* keyingi port */ }
        }
        return -1
    }

    fun prefixToMask(p: Int): String {
        val m = if (p <= 0) 0 else (-1 shl (32 - p))
        return "${(m ushr 24) and 255}.${(m ushr 16) and 255}.${(m ushr 8) and 255}.${m and 255}"
    }

    @Suppress("DEPRECATION")
    fun info(ctx: Context): List<Pair<String, String>> {
        val c = cm(ctx)
        val n = c.activeNetwork ?: return listOf("Holat" to "Tarmoqqa ulanmagan")
        val caps = c.getNetworkCapabilities(n)
        val lp = c.getLinkProperties(n)
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val out = mutableListOf<Pair<String, String>>()
        out += "Ulanish turi" to when {
            wifi -> "Wi-Fi"
            cell -> "Mobil internet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Boshqa"
        }
        if (lp != null) {
            val v4 = lp.linkAddresses.firstOrNull { it.address is Inet4Address }
            out += "Lokal IP" to (v4?.address?.hostAddress ?: "—")
            out += "Subnet mask" to (v4?.let { prefixToMask(it.prefixLength) } ?: "—")
            val gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
            out += "Shlyuz (gateway)" to (gw?.takeIf { it != "0.0.0.0" } ?: "—")
            out += "DNS serverlar" to (lp.dnsServers.joinToString { it.hostAddress ?: "" }.ifBlank { "—" })
            out += "Interfeys" to (lp.interfaceName ?: "—")
        }
        if (wifi) {
            try {
                val ci = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
                if (ci != null) {
                    val ssid = ci.ssid?.trim('"') ?: ""
                    out += "SSID" to (if (ssid.isBlank() || ssid == "<unknown ssid>") "Joylashuv ruxsati kerak" else ssid)
                    out += "Ulanish tezligi" to (if (ci.linkSpeed > 0) "${ci.linkSpeed} Mbps" else "—")
                    out += "Signal" to "${ci.rssi} dBm"
                    if (ci.frequency > 0) out += "Chastota" to "${ci.frequency} MHz (${WifiUtil.band(ci.frequency)}, kanal ${WifiUtil.channel(ci.frequency)})"
                }
            } catch (e: SecurityException) { /* e'tiborsiz */ }
        }
        if (cell) out += mobileRows(ctx)
        return out
    }

    @Suppress("DEPRECATION")
    private fun mobileRows(ctx: Context): List<Pair<String, String>> {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val out = mutableListOf<Pair<String, String>>()
        out += "Operator" to tm.networkOperatorName.ifBlank { "Noma'lum" }
        if (!granted(ctx, Manifest.permission.READ_PHONE_STATE)) {
            out += "Tarmoq turi" to "Telefon holati ruxsati kerak"
            out += "Mobil signal" to "Telefon holati ruxsati kerak"
            return out
        }
        try {
            out += "Tarmoq turi" to netTypeName(tm.dataNetworkType)
            if (Build.VERSION.SDK_INT >= 29) {
                val cs = tm.signalStrength?.cellSignalStrengths?.firstOrNull()
                out += "Mobil signal" to (if (cs != null) "${cs.dbm} dBm (daraja ${cs.level}/4)" else "Noma'lum")
            }
        } catch (e: SecurityException) {
            out += "Tarmoq turi" to "Ruxsat rad etildi"
        }
        return out
    }

    private fun netTypeName(t: Int): String = when (t) {
        20 -> "5G (NR)"
        19 -> "4G+ (LTE-CA)"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G (LTE)"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Noma'lum"
        else -> "Boshqa ($t)"
    }

    /** RSSI'dan taxminiy masofa oralig'i (log-distance modeli, tx=-59 dBm @1m, n=2). */
    fun distanceText(rssi: Int): String {
        val d = 10.0.pow((-59 - rssi) / 20.0)
        val lo = d * 0.6
        val hi = d * 1.6
        fun f(x: Double) = if (x < 10) String.format("%.1f", x) else x.toInt().toString()
        return "~${f(lo)}–${f(hi)} m (taxminiy)"
    }

    fun isPrivateIpv4(ip: String): Boolean {
        val p = ip.trim().split('.')
        if (p.size != 4) return false
        val n = p.map { it.toIntOrNull() ?: return false }
        if (n.any { it !in 0..255 }) return false
        return n[0] == 10 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] in 16..31)
    }
}
