package com.netscope.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs

data class PingResult(val avgMs: Double, val jitterMs: Double, val lossPct: Int)

object SpeedTester {
    suspend fun download(url: String, durationMs: Long = 8000, onProgress: (Double) -> Unit): Double =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000; c.useCaches = false
            try {
                val ins = c.inputStream
                val buf = ByteArray(65536)
                var total = 0L
                val t0 = System.nanoTime()
                ins.use {
                    while (isActive) {
                        val n = it.read(buf)
                        if (n < 0) break
                        total += n
                        val el = (System.nanoTime() - t0) / 1e9
                        if (el > 0.3) onProgress(total * 8 / el / 1e6)
                        if (el * 1000 >= durationMs) break
                    }
                }
                val el = ((System.nanoTime() - t0) / 1e9).coerceAtLeast(0.001)
                total * 8 / el / 1e6
            } finally { c.disconnect() }
        }

    suspend fun upload(url: String, bytes: Int = 10_000_000, onProgress: (Double) -> Unit): Double =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.useCaches = false
            c.connectTimeout = 8000; c.readTimeout = 15000
            c.setFixedLengthStreamingMode(bytes.toLong())
            c.setRequestProperty("Content-Type", "application/octet-stream")
            try {
                val chunk = ByteArray(65536)
                val t0 = System.nanoTime()
                c.outputStream.use { os ->
                    var sent = 0
                    while (sent < bytes && isActive) {
                        val n = minOf(chunk.size, bytes - sent)
                        os.write(chunk, 0, n); sent += n
                        val el = (System.nanoTime() - t0) / 1e9
                        if (el > 0.3) onProgress(sent * 8 / el / 1e6)
                    }
                }
                c.responseCode
                val el = ((System.nanoTime() - t0) / 1e9).coerceAtLeast(0.001)
                bytes * 8 / el / 1e6
            } finally { c.disconnect() }
        }

    /** TCP-ulanish asosidagi ping (ICMP root talab qiladi, shuning uchun socket ishlatiladi). */
    suspend fun ping(host: String, port: Int = 443, count: Int = 10): PingResult =
        withContext(Dispatchers.IO) {
            val addr = InetAddress.getByName(host)
            val times = mutableListOf<Double>()
            repeat(count) {
                val t = System.nanoTime()
                try {
                    Socket().use { it.connect(InetSocketAddress(addr, port), 2000) }
                    times += (System.nanoTime() - t) / 1e6
                } catch (e: IOException) { /* yo'qotilgan paket */ }
                delay(100)
            }
            if (times.isEmpty()) PingResult(-1.0, 0.0, 100)
            else {
                val jitter = if (times.size > 1) times.zipWithNext { a, b -> abs(b - a) }.average() else 0.0
                PingResult(times.average(), jitter, 100 - times.size * 100 / count)
            }
        }
}
