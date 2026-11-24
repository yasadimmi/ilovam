package com.netscope.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrUtil {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace(":", "\\:").replace("\"", "\\\"")

    /** Standart Wi-Fi QR formati: WIFI:T:WPA;S:ssid;P:parol;; */
    fun wifiPayload(ssid: String, pass: String, sec: String): String =
        if (sec == "nopass") "WIFI:T:nopass;S:${esc(ssid)};;"
        else "WIFI:T:$sec;S:${esc(ssid)};P:${esc(pass)};;"

    fun bitmap(text: String, size: Int = 720): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"))
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) px[y * size + x] = if (m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }
}
