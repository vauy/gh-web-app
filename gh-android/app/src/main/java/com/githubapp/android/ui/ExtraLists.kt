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
import androidx.compose.ui.text.font.FontFamily
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

private fun peopleTitle(kind: String): String = when (kind) {
    "followers" -> "粉丝"
    "following" -> "关注"
    "stargazers" -> "⭐ 收藏者"
    "watchers" -> "👀 关注者"
    "forks" -> "🍴 Forks"
    else -> "列表"
}

// 通用用户/仓库列表：粉丝、关注、收藏者、关注者、Forks
@Composable
fun PeopleListScreen(kind: String, a: String, b: String) {
    var items by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val path = when (kind) {
                "followers" -> "users/$a/followers?per_page=100"
                "following" -> "users/$a/following?per_page=100"
                "stargazers" -> "repos/$a/$b/stargazers?per_page=100"
                "watchers" -> "repos/$a/$b/subscribers?per_page=100"
                "forks" -> "repos/$a/$b/forks?per_page=100"
                else -> ""
            }
            items = ApiClient.get(path).arr().map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(peopleTitle(kind), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        when {
            err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
            !loaded -> LoadingBox()
            items.isEmpty() -> EmptyBox("暂无数据")
            else -> LazyColumn {
                items(items, key = { it.s("id").ifEmpty { it.s("full_name").ifEmpty { it.s("login") } } }) { it2 ->
                    if (kind == "forks") {
                        Card(
                            onClick = { Nav.show(Screen.Repo(it2.o("owner").s("login"), it2.s("name"))) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(it2.s("full_name"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "⭐ ${fmtCount(it2.i("stargazers_count"))} · ${it2.s("language")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { Nav.show(Screen.User(it2.s("login"))) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(it2.s("login"), 40)
                            Spacer(Modifier.width(12.dp))
                            Text("@${it2.s("login")}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// 文件提交历史
@Composable
fun FileHistoryScreen(owner: String, name: String, path: String) {
    var commits by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            commits = ApiClient.get("repos/$owner/$name/commits?path=${encPath(path)}&per_page=30").arr().map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("文件历史：$path", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        when {
            err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
            !loaded -> LoadingBox()
            commits.isEmpty() -> EmptyBox("暂无提交记录")
            else -> LazyColumn {
                items(commits, key = { it.s("sha") }) { c ->
                    Card(
                        onClick = { Nav.show(Screen.Commit(owner, name, c.s("sha"))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.o("commit").s("message").split('\n')[0], fontWeight = FontWeight.SemiBold)
                            Text(
                                "${c.o("commit").o("author").s("name")} · ${fmtTime(c.o("commit").o("author").s("date"))} · ${c.s("sha").take(7)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
