package com.netscope.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.netscope.R
import com.netscope.util.granted

/**
 * Avval o'zbekcha tushuntirish dialogini ko'rsatadi, keyin tizim ruxsat oynasini ochadi.
 * Qaytgan lambda'ni tugma bosilganda chaqiring.
 */
@Composable
fun rememberPermissionAsker(perms: List<String>, title: String, why: String, onResult: (Boolean) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val cb by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        cb(perms.all { r[it] == true || granted(ctx, it) })
    }
    var show by remember { mutableStateOf(false) }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(title) },
            text = { Text(why) },
            confirmButton = {
                TextButton(onClick = { show = false; launcher.launch(perms.toTypedArray()) }) { Text(stringResource(R.string.btn_continue)) }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
    return {
        if (perms.all { granted(ctx, it) }) { cb(true) } else { show = true }
    }
}

@Composable
fun PermissionCard(title: String, why: String, onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(why, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.btn_grant)) }
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
}
