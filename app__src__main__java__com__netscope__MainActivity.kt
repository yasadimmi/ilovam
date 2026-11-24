package com.netscope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.netscope.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NetScopeTheme { NetScopeRoot() } }
    }
}

@Composable
fun NetScopeRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        stringResource(R.string.nav_wifi) to Icons.Default.Wifi,
        stringResource(R.string.nav_bt) to Icons.Default.Bluetooth,
        stringResource(R.string.nav_speed) to Icons.Default.Speed,
        stringResource(R.string.nav_history) to Icons.Default.History,
        stringResource(R.string.nav_monitor) to Icons.Default.MonitorHeart,
        stringResource(R.string.nav_settings) to Icons.Default.Settings
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).statusBarsPadding().fillMaxSize()) {
            when (tab) {
                0 -> WifiHost()
                1 -> BluetoothScreen()
                2 -> SpeedScreen()
                3 -> HistoryScreen()
                4 -> MonitorScreen()
                else -> SettingsScreen()
            }
        }
    }
}
