package com.githubapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githubapp.android.api.ApiClient
import com.githubapp.android.api.i
import com.githubapp.android.api.l
import com.githubapp.android.api.o
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.githubapp.android.nav.Nav
import com.githubapp.android.nav.Screen
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

/** 通知 */
@Composable
fun NotificationsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var notifs by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            notifs = ApiClient.get("notifications?per_page=50").arr().map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row {
            Text("通知（${notifs.size} 未读）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        ApiClient.put("notifications", JsonObject())
                        toast(ctx, "已全部标记为已读")
                        load()
                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "操作失败") }
                }
            }) { Text("全部已读") }
        }
        Spacer(Modifier.height(6.dp))

        when {
            err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
            !loaded -> LoadingBox()
            notifs.isEmpty() -> EmptyBox("🎉 没有未读通知")
            else -> LazyColumn {
                items(notifs, key = { it.l("id") }) { n ->
                    val repo = n.o("repository").s("full_name")
                    val type = n.o("subject").s("type")
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(n.o("subject").s("title"), fontWeight = FontWeight.SemiBold)
                        Text(
                            "$repo · ${type} · ${fmtTime(n.s("updated_at"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.padding(top = 4.dp)) {
                            OutlinedButton(onClick = {
                                val tab = when (type) {
                                    "Issue" -> "issues"
                                    "PullRequest" -> "pulls"
                                    else -> "code"
                                }
                                Nav.show(Screen.Repo(repo.split("/")[0], repo.split("/")[1]))
                            }) { Text("查看") }
                            Spacer(Modifier.padding(start = 6.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        ApiClient.patch("notifications/threads/${n.l("id")}", JsonObject())
                                        toast(ctx, "已标记已读")
                                        load()
                                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "操作失败") }
                                }
                            }) { Text("标记已读") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
