package com.netscope.ui

import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.netscope.R
import com.netscope.util.Prefs
import com.netscope.util.QrUtil
import com.netscope.util.granted
import java.util.concurrent.Executors

@Composable
fun QrShareScreen(onBack: () -> Unit) {
    var ssid by remember { mutableStateOf(Prefs.qrSsid) }
    var pass by remember { mutableStateOf(Prefs.qrPass) }
    var sec by remember { mutableStateOf(Prefs.qrSec) }
    var show by remember { mutableStateOf(false) }
    val bmp = remember(ssid, pass, sec) {
        if (ssid.isBlank() || (sec != "nopass" && pass.isBlank())) null
        else try { QrUtil.bitmap(QrUtil.wifiPayload(ssid, pass, sec)) } catch (e: Exception) { null }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("← Orqaga") }
        Text("Wi-Fi ulashish (QR)", style = MaterialTheme.typography.headlineSmall)
        Text("SSID va parol faqat shu telefonda saqlanadi va hech qayerga yuborilmaydi. QR kod ham shu yerning o'zida yaratiladi.", fontSize = 12.sp)
        OutlinedTextField(ssid, { ssid = it }, label = { Text("Wi-Fi nomi (SSID)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            pass, { pass = it }, label = { Text("Parol") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Yashir" else "Ko'rsat") } }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("WPA" to "WPA/WPA2/WPA3", "WEP" to "WEP", "nopass" to "Ochiq").forEach { (k, l) ->
                FilterChip(selected = sec == k, onClick = { sec = k }, label = { Text(l) })
            }
        }
        Button(onClick = { Prefs.qrSsid = ssid; Prefs.qrPass = pass; Prefs.qrSec = sec }) { Text("Telefonda saqlash") }
        if (bmp != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(bmp.asImageBitmap(), "Wi-Fi QR kodi", Modifier.size(280.dp).background(Color.White).padding(8.dp))
            }
            Text("Mehmon kamerani shu kodga tutsa, Android Wi-Fi'ga ulanishni taklif qiladi.", fontSize = 12.sp)
        }
    }
}

@Composable
fun QrScanScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var camOk by remember { mutableStateOf(granted(ctx, android.Manifest.permission.CAMERA)) }
    val ask = rememberPermissionAsker(listOf(android.Manifest.permission.CAMERA), stringResource(R.string.perm_camera_title), stringResource(R.string.perm_camera_why)) { camOk = it }
    var result by remember { mutableStateOf<Barcode?>(null) }
    val addLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Toast.makeText(ctx, "Tarmoq qo'shish oynasi yopildi. Wi-Fi sozlamalarini tekshiring.", Toast.LENGTH_LONG).show()
    }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("← Orqaga") }
        if (!camOk) PermissionCard(stringResource(R.string.perm_camera_title), stringResource(R.string.perm_camera_why), ask)
        else {
            Text("Wi-Fi QR kodini ramkaga tushiring", Modifier.padding(horizontal = 16.dp), fontSize = 14.sp)
            Box(Modifier.fillMaxSize().padding(12.dp)) { CameraScanner { if (result == null) result = it } }
        }
    }

    val b = result
    if (b != null) {
        val w = b.wifi
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(if (w != null) "Wi-Fi tarmog'i topildi" else "QR kod topildi") },
            text = {
                if (w != null) Text("Nomi: ${w.ssid}\nHimoya: ${when (w.encryptionType) { Barcode.WiFi.TYPE_OPEN -> "Ochiq"; Barcode.WiFi.TYPE_WEP -> "WEP"; else -> "WPA/WPA2/WPA3" }}")
                else Text((b.rawValue ?: "").take(300) + "\n\nBu Wi-Fi QR kodi emas, shuning uchun hech narsa ochilmaydi.")
            },
            confirmButton = {
                if (w != null) TextButton(onClick = {
                    connectWifi(ctx, w.ssid ?: "", w.password ?: "", w.encryptionType) { addLauncher.launch(it) }
                    result = null
                }) { Text("Ulanish") }
                else TextButton(onClick = { result = null }) { Text("OK") }
            },
            dismissButton = { if (w != null) TextButton(onClick = { result = null }) { Text("Bekor qilish") } }
        )
    }
}

@Suppress("DEPRECATION")
private fun connectWifi(ctx: android.content.Context, ssid: String, pass: String, enc: Int, launch: (Intent) -> Unit) {
    if (ssid.isBlank()) { Toast.makeText(ctx, "SSID bo'sh", Toast.LENGTH_SHORT).show(); return }
    if (enc == Barcode.WiFi.TYPE_WEP && Build.VERSION.SDK_INT >= 29) {
        Toast.makeText(ctx, "WEP tarmoqlarini Android 10+ da qo'shib bo'lmaydi.", Toast.LENGTH_LONG).show(); return
    }
    val wm = ctx.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager
    if (Build.VERSION.SDK_INT >= 29) {
        val sb = WifiNetworkSuggestion.Builder().setSsid(ssid)
        if (enc != Barcode.WiFi.TYPE_OPEN) sb.setWpa2Passphrase(pass)
        val sug = sb.build()
        if (Build.VERSION.SDK_INT >= 30) {
            launch(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(sug)))
        } else {
            val r = wm.addNetworkSuggestions(listOf(sug))
            Toast.makeText(ctx, if (r == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) "Tarmoq taklifi qo'shildi (bildirishnomadan tasdiqlang)" else "Qo'shib bo'lmadi (kod $r)", Toast.LENGTH_LONG).show()
        }
    } else {
        val cfg = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            when (enc) {
                Barcode.WiFi.TYPE_OPEN -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                Barcode.WiFi.TYPE_WEP -> { allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE); wepKeys[0] = "\"$pass\""; wepTxKeyIndex = 0 }
                else -> preSharedKey = "\"$pass\""
            }
        }
        val id = wm.addNetwork(cfg)
        if (id >= 0) { wm.enableNetwork(id, true); Toast.makeText(ctx, "Ulanilmoqda…", Toast.LENGTH_SHORT).show() }
        else Toast.makeText(ctx, "Tarmoqni qo'shib bo'lmadi", Toast.LENGTH_LONG).show()
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScanner(onCode: (Barcode) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val cb by rememberUpdatedState(onCode)
    val previewView = remember { PreviewView(ctx) }
    DisposableEffect(owner) {
        val exec = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(exec) { proxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { list -> list.firstOrNull()?.let { cb(it) } }
                    .addOnCompleteListener { proxy.close() }
            }
            try {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) { /* kamera band */ }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { provider?.unbindAll(); exec.shutdown(); scanner.close() }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}
