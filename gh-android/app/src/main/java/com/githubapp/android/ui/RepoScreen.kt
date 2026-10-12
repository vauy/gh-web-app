package com.githubapp.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.githubapp.android.api.b
import com.githubapp.android.api.i
import com.githubapp.android.api.o
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

private val TABS = listOf(
    "代码" to "code",
    "Issues" to "issues",
    "PR" to "pulls",
    "提交" to "commits",
    "分支" to "branches",
    "Releases" to "releases",
    "Actions" to "actions",
)

@Composable
fun RepoScreen(owner: String, name: String) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var repo by remember { mutableStateOf<JsonObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("code") }

    suspend fun load() {
        try {
            repo = ApiClient.get("repos/$owner/$name").obj()
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
    }
    LaunchedEffect(Unit) { load() }

    val r = repo
    when {
        err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
        r == null -> LoadingBox()
        else -> Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${r.o("owner").s("login")}/${r.s("name")}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (r.b("private")) "私有" else "公开",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (r.s("description").isNotEmpty()) {
                    Text(r.s("description"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("⭐ ${fmtCount(r.i("stargazers_count"))}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(14.dp))
                    Text("🍴 ${fmtCount(r.i("forks_count"))}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(14.dp))
                    Text("🐛 ${fmtCount(r.i("open_issues_count"))}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(14.dp))
                    Text("🌿 ${r.s("default_branch")}", style = MaterialTheme.typography.bodySmall)
                    if (r.s("language").isNotEmpty()) {
                        Spacer(Modifier.width(14.dp))
                        Text(r.s("language"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    OutlinedButton(onClick = {
                        scope.launch {
                            try {
                                val full = r.s("full_name")
                                val starred = r.i("stargazers_count") > 0
                                if (starred) ApiClient.delete("user/starred/$full")
                                else ApiClient.put("user/starred/$full", JsonObject())
                                toast(ctx, if (starred) "已取消收藏" else "已收藏 ⭐")
                                load()
                            } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "操作失败") }
                        }
                    }) { Text(if (r.i("stargazers_count") > 0) "取消收藏" else "⭐ 收藏") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            try {
                                ApiClient.put("user/subscriptions/${r.s("full_name")}", JsonObject())
                                toast(ctx, "已关注 👀")
                            } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "操作失败") }
                        }
                    }) { Text("👀 关注") }
                }
            }
            TabRow(selectedTabIndex = TABS.indexOfFirst { it.second == tab }.coerceAtLeast(0)) {
                TABS.forEach { (label, key) ->
                    Tab(selected = tab == key, onClick = { tab = key }, text = { Text(label) })
                }
            }
            Box(Modifier.fillMaxSize().weight(1f)) {
                when (tab) {
                    "code" -> CodeTab(owner, name)
                    "issues" -> IssuesTab(owner, name)
                    "pulls" -> PullsTab(owner, name)
                    "commits" -> CommitsTab(owner, name)
                    "branches" -> BranchesTab(owner, name)
                    "releases" -> ReleasesTab(owner, name)
                    "actions" -> ActionsTab(owner, name)
                }
            }
        }
    }
}
