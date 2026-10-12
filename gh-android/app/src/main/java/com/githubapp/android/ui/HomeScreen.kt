package com.githubapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githubapp.android.api.ApiClient
import com.githubapp.android.api.i
import com.githubapp.android.api.o
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.githubapp.android.nav.Nav
import com.githubapp.android.nav.Screen
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<JsonObject?>(null) }
    var starred by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            me = ApiClient.get("user").obj()
            starred = ApiClient.get("user/starred?per_page=10").arr().map { it.asJsonObject }
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
    }
    LaunchedEffect(Unit) { load() }

    val m = me
    when {
        err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
        m == null -> LoadingBox()
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.s("login"), 64)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(m.s("name").ifEmpty { m.s("login") }, style = MaterialTheme.typography.headlineSmall)
                    Text("@${m.s("login")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (m.s("bio").isNotEmpty()) {
                        Text(m.s("bio"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row {
                StatItem("仓库", fmtCount(m.i("public_repos")))
                Spacer(Modifier.width(28.dp))
                StatItem("粉丝", fmtCount(m.i("followers")))
                Spacer(Modifier.width(28.dp))
                StatItem("关注", fmtCount(m.i("following")))
            }
            Spacer(Modifier.height(16.dp))
            Row {
                OutlinedButton(onClick = { Nav.show(Screen.MyIssues) }) { Text("我的 Issues") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { Nav.show(Screen.MyPulls) }) { Text("我的 Pull Requests") }
            }
            Spacer(Modifier.height(20.dp))
            Text("⭐ 收藏的仓库", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (starred.isEmpty()) {
                Text("暂无收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                starred.forEach { r ->
                    Card(
                        onClick = { Nav.show(Screen.Repo(r.o("owner").s("login"), r.s("name"))) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${r.o("owner").s("login")}/${r.s("name")}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                r.s("description").ifEmpty { "暂无描述" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                            Text(
                                "⭐ ${fmtCount(r.i("stargazers_count"))} · ${r.s("language")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
