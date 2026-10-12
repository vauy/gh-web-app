package com.githubapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githubapp.android.api.s
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Webhook 事件日志（由后端机器人/集成接收） */
@Composable
fun EventsScreen() {
    var events by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            events = withContext(Dispatchers.IO) {
                val cm = android.webkit.CookieManager.getInstance()
                val cookie = cm.getCookie(com.githubapp.android.App.baseUrl) ?: ""
                val conn = java.net.URL(com.githubapp.android.App.baseUrl + "/api/webhooks/events").openConnection()
                if (cookie.isNotEmpty()) conn.setRequestProperty("Cookie", cookie)
                val resp = conn.inputStream.bufferedReader().readText()
                if (resp.isBlank()) emptyList()
                else com.google.gson.JsonParser.parseString(resp).asJsonArray.map { it.asJsonObject }
            }
        } catch (e: Exception) {
            err = e.message
        }
    }

    if (err != null) {
        ErrorBox(err!!)
        return
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("⚡ Webhook 事件", style = MaterialTheme.typography.titleMedium)
        Text(
            "应用安装后，仓库事件会推送到后端 /webhook 并由机器人处理。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (events.isEmpty()) {
            EmptyBox("还没有收到事件")
        } else {
            events.forEach { e ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row {
                        Text(e.s("event"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(" · ${e.s("action")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(fmtTime(e.s("at")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${e.s("repo")} · ${e.s("sender")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}
