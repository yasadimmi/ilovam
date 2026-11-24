package com.netscope.util

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Oddiy klient ko'ra oladigan ma'lumot asosida: uy Wi-Fi SSID'ining ko'rinadigan nuqtalari (BSSID) soni
 * va yangi (notanish) BSSID paydo bo'lishi. Router ichidagi ulangan qurilmalar ro'yxati O'QILMAYDI.
 */
object DeviceAlert {
    fun addSample(nets: List<WifiNet>, ssid: String): Int {
        val m = nets.filter { it.ssid == ssid }
        Prefs.samples = (Prefs.samples + m.size).takeLast(20)
        Prefs.knownBssids = Prefs.knownBssids + m.map { it.bssid }
        return m.size
    }

    fun reset() { Prefs.samples = emptyList(); Prefs.knownBssids = emptySet() }

    fun baseline(): Int? = Prefs.samples.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    fun evaluate(nets: List<WifiNet>): String? {
        val ssid = Prefs.homeSsid
        val b = baseline() ?: return null
        if (ssid.isBlank()) return null
        val m = nets.filter { it.ssid == ssid }
        val unknown = m.map { it.bssid }.filter { it !in Prefs.knownBssids }
        val parts = mutableListOf<String>()
        if (unknown.isNotEmpty()) parts += "\"$ssid\" uchun yangi BSSID topildi: ${unknown.joinToString()}"
        if (abs(m.size - b) > Prefs.tolerance) parts += "\"$ssid\" nuqtalari soni ${m.size} (odatiy ~$b)"
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }
}
