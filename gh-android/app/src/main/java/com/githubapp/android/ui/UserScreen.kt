package com.githubapp.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

// 用户主页：个人信息、粉丝/关注列表、仓库列表、关注/取关
@Composable
fun UserScreen(login: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<JsonObject?>(null) }
    var repos by remember { mutableStateOf(listOf<JsonObject>()) }
    var me by remember { mutableStateOf<String?>(null) }
    var following by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            user = ApiClient.get("users/$login").obj()
            repos = ApiClient.get("users/$login/repos?per_page=30&sort=updated").arr().map { it.asJsonObject }
            me = try { ApiClient.get("user").obj().s("login") } catch (_: Exception) { null }
            following = if (me == login) {
                false
            } else {
                try {
                    ApiClient.request("GET", "user/following/$login")
                    true
                } catch (e: ApiClient.ApiException) {
                    false
                }
            }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    val u = user
    when {
        err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
        !loaded -> LoadingBox()
        u == null -> EmptyBox("未找到用户")
        else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u.s("login"), 64)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(u.s("name").ifEmpty { u.s("login") }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("@${u.s("login")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (u.s("bio").isNotEmpty()) Text(u.s("bio"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (u.s("location").isNotEmpty()) Text("📍 ${u.s("location")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("仓库 ${fmtCount(u.i("public_repos"))}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 14.dp))
                    Text(
                        "粉丝 ${fmtCount(u.i("followers"))}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 14.dp).clickable { Nav.show(Screen.PeopleList("followers", login, "")) },
                    )
                    Text(
                        "关注 ${fmtCount(u.i("following"))}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { Nav.show(Screen.PeopleList("following", login, "")) },
                    )
                    Spacer(Modifier.weight(1f))
                    if (me != login) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                try {
                                    if (following) ApiClient.delete("user/following/$login")
                                    else ApiClient.put("user/following/$login", JsonObject())
                                    toast(ctx, if (following) "已取消关注" else "已关注 @$login")
                                    following = !following
                                } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "操作失败") }
                            }
                        }) { Text(if (following) "已关注 ✓" else "+ 关注") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("仓库（${repos.size}）", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            if (repos.isEmpty()) {
                Text("暂无公开仓库", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    items(repos, key = { it.s("full_name") }) { r ->
                        Card(
                            onClick = { Nav.show(Screen.Repo(r.o("owner").s("login"), r.s("name"))) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(r.s("full_name"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    r.s("description").ifEmpty { "暂无描述" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                                Text(
                                    "⭐ ${fmtCount(r.i("stargazers_count"))} · 🍴 ${fmtCount(r.i("forks_count"))} · ${r.s("language")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
