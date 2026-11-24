package com.netscope.util

import com.netscope.data.*
import org.json.JSONArray
import org.json.JSONObject

object Exporter {
    private fun q(s: String): String {
        var v = s
        if (v.isNotEmpty() && v[0] in "=+-@\t\r") v = "'$v" // CSV-injection'dan himoya
        return "\"" + v.replace("\"", "\"\"") + "\""
    }

    fun wifiCsv(l: List<WifiScanEntity>) = buildString {
        appendLine("ts,ssid,bssid,rssi_dbm,freq_mhz,channel,band,security,vendor")
        l.forEach { appendLine("${it.ts},${q(it.ssid)},${q(it.bssid)},${it.rssi},${it.freq},${it.channel},${q(it.band)},${q(it.security)},${q(it.vendor)}") }
    }

    fun bleCsv(l: List<BleEntity>) = buildString {
        appendLine("ts,name,address,rssi_dbm,kind")
        l.forEach { appendLine("${it.ts},${q(it.name)},${q(it.address)},${it.rssi},${q(it.kind)}") }
    }

    fun speedCsv(l: List<SpeedEntity>) = buildString {
        appendLine("ts,down_mbps,up_mbps,ping_ms,jitter_ms,endpoint")
        l.forEach { appendLine("${it.ts},${it.downMbps},${it.upMbps},${it.pingMs},${it.jitterMs},${q(it.endpoint)}") }
    }

    private fun wrap(type: String, items: JSONArray) =
        JSONObject().put("app", "NetScope").put("version", 1).put("type", type).put("items", items).toString(2)

    fun wifiJson(l: List<WifiScanEntity>) = wrap("wifi", JSONArray(l.map {
        JSONObject().put("ts", it.ts).put("ssid", it.ssid).put("bssid", it.bssid).put("rssi", it.rssi)
            .put("freq", it.freq).put("channel", it.channel).put("band", it.band)
            .put("security", it.security).put("vendor", it.vendor)
    }))

    fun bleJson(l: List<BleEntity>) = wrap("ble", JSONArray(l.map {
        JSONObject().put("ts", it.ts).put("name", it.name).put("address", it.address)
            .put("rssi", it.rssi).put("kind", it.kind)
    }))

    fun speedJson(l: List<SpeedEntity>) = wrap("speed", JSONArray(l.map {
        JSONObject().put("ts", it.ts).put("down", it.downMbps).put("up", it.upMbps)
            .put("ping", it.pingMs).put("jitter", it.jitterMs).put("endpoint", it.endpoint)
    }))

    /** Oldin eksport qilingan JSON'ni tiklaydi. Qaytaradi: qo'shilgan yozuvlar soni. */
    suspend fun import(db: AppDb, text: String): Int {
        val o = JSONObject(text)
        require(o.optString("app") == "NetScope") { "Bu NetScope fayli emas" }
        val a = o.getJSONArray("items")
        val objs = (0 until a.length()).map { a.getJSONObject(it) }
        when (o.getString("type")) {
            "wifi" -> db.wifi().insertAll(objs.map {
                WifiScanEntity(0, it.getLong("ts"), it.getString("ssid"), it.getString("bssid"), it.getInt("rssi"),
                    it.getInt("freq"), it.getInt("channel"), it.getString("band"), it.getString("security"), it.getString("vendor"))
            })
            "ble" -> db.ble().insertAll(objs.map {
                BleEntity(0, it.getLong("ts"), it.getString("name"), it.getString("address"), it.getInt("rssi"), it.getString("kind"))
            })
            "speed" -> db.speed().insertAll(objs.map {
                SpeedEntity(0, it.getLong("ts"), it.getDouble("down"), it.getDouble("up"),
                    it.getDouble("ping"), it.getDouble("jitter"), it.getString("endpoint"))
            })
            else -> throw IllegalArgumentException("Noma'lum fayl turi")
        }
        return objs.size
    }
}
