package com.netscope.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.netscope.R
import com.netscope.data.AppDb
import com.netscope.data.BleEntity
import com.netscope.util.Net
import com.netscope.util.btPerms
import com.netscope.util.granted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BtDev(val address: String, val name: String, val rssi: Int, val kind: String)

@SuppressLint("MissingPermission")
@Composable
fun BluetoothScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDb.get(ctx) }
    val perms = remember { btPerms() }
    var ok by remember { mutableStateOf(perms.all { granted(ctx, it) }) }
    val ask = rememberPermissionAsker(perms, stringResource(R.string.perm_bt_title), stringResource(R.string.perm_bt_why)) { ok = it }
    val devices: SnapshotStateMap<String, BtDev> = remember { mutableStateMapOf() }
    var scanning by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var registered by remember { mutableStateOf(false) }
    val adapter = remember { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter }

    val bleCb = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                val d = r.device
                val name = try { r.scanRecord?.deviceName ?: d.name } catch (e: SecurityException) { null }
                devices["BLE:" + d.address] = BtDev(d.address, name ?: "Noma'lum", r.rssi, "BLE")
            }
        }
    }
    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null || intent.action != BluetoothDevice.ACTION_FOUND) return
                val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                val name = try { d.name } catch (e: SecurityException) { null }
                devices["BT:" + d.address] = BtDev(d.address, name ?: "Noma'lum", rssi, "Classic")
            }
        }
    }

    fun stop(save: Boolean) {
        try { adapter?.bluetoothLeScanner?.stopScan(bleCb) } catch (e: Exception) { }
        try { adapter?.cancelDiscovery() } catch (e: Exception) { }
        if (registered) { try { ctx.unregisterReceiver(receiver) } catch (e: IllegalArgumentException) { }; registered = false }
        scanning = false
        if (save && devices.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val list = devices.values.map { BleEntity(0, now, it.name, it.address, it.rssi, it.kind) }
            scope.launch(Dispatchers.IO) { db.ble().insertAll(list) }
        }
    }

    fun start() {
        if (adapter == null) { info = "Bu qurilmada Bluetooth yo'q."; return }
        if (!adapter.isEnabled) { info = "Bluetooth o'chiq. Uni telefon sozlamalaridan yoqing."; return }
        info = null
        devices.clear()
        try {
            adapter.bluetoothLeScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), bleCb)
            ContextCompat.registerReceiver(ctx, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            adapter.startDiscovery()
            scanning = true
        } catch (e: SecurityException) { info = "Ruxsat yetarli emas."; ok = false }
    }

    LaunchedEffect(scanning) { if (scanning) { delay(15_000); stop(true) } }
    DisposableEffect(Unit) { onDispose { stop(false) } }

    if (!ok) { PermissionCard(stringResource(R.string.perm_bt_title), stringResource(R.string.perm_bt_why), ask); return }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Bluetooth skaner", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Button(onClick = { if (scanning) stop(true) else start() }) { Text(if (scanning) "To'xtatish" else "Skanerlash (15 s)") }
                Spacer(Modifier.width(12.dp))
                if (scanning) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Text("${devices.size} ta qurilma", fontSize = 13.sp)
            }
            if (info != null) Text(info!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.bt_mac_note), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.bt_distance_note), fontSize = 12.sp)
                }
            }
        }
        items(devices.values.sortedByDescending { it.rssi }) { d ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(d.name, fontWeight = FontWeight.Bold)
                    Text("${d.address} • ${d.kind}", fontSize = 12.sp)
                    Text("${d.rssi} dBm • ${Net.distanceText(d.rssi)}", fontSize = 12.sp)
                }
            }
        }
    }
}
