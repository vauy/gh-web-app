package com.githubapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githubapp.android.api.ApiClient
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.githubapp.android.nav.Nav
import com.githubapp.android.nav.Screen
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

// 我的（设置）
@Composable
fun MoreScreen() {
    val ctx = LocalContext.current
    var me by remember { mutableStateOf<JsonObject?>(null) }

    LaunchedEffect(Unit) {
        try { me = ApiClient.get("user").obj() } catch (_: Exception) {}
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val m = me
        if (m != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.s("login"), 56)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(m.s("name").ifEmpty { m.s("login") }, fontWeight = FontWeight.Bold)
                    Text("@${m.s("login")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "身份：GitHub 账号（设备码登录）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { Nav.show(Screen.Gists) }, modifier = Modifier.fillMaxWidth()) { Text("📝 我的 Gists") }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                ApiClient.logout()
                AppState.on401()
                toast(ctx, "已退出登录")
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("退出登录") }
        Spacer(Modifier.height(8.dp))
        Text(
            "登录采用 GitHub 设备码方式，无需后端服务；令牌仅保存在本机。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
