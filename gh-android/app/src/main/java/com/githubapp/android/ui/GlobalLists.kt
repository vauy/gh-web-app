package com.githubapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githubapp.android.api.ApiClient
import com.githubapp.android.api.a
import com.githubapp.android.api.i
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.githubapp.android.nav.Nav
import com.githubapp.android.nav.Screen
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

/** 我的 Issues（跨仓库） */
@Composable
fun MyIssuesScreen() {
    GlobalIssueListScreen(pr = false)
}

/** 我的 Pull Requests（跨仓库） */
@Composable
fun MyPullsScreen() {
    GlobalIssueListScreen(pr = true)
}

@Composable
private fun GlobalIssueListScreen(pr: Boolean) {
    var items by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val q = java.net.URLEncoder.encode(if (pr) "is:pr author:@me" else "is:issue author:@me", "UTF-8")
            items = ApiClient.get("search/issues?q=$q&sort=updated&order=desc&per_page=30").obj().a("items").map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    when {
        err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
        !loaded -> LoadingBox()
        items.isEmpty() -> EmptyBox(if (pr) "暂无 Pull Requests" else "暂无 Issues")
        else -> LazyColumn {
            items(items, key = { it.i("id") }) { it2 ->
                val repoPath = it2.s("repository_url").substringAfter("/repos/")
                Card(
                    onClick = {
                        val parts = repoPath.split("/")
                        if (parts.size == 2) Nav.show(Screen.Repo(parts[0], parts[1]))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${if (pr) "PR" else "Issue"} #${it2.i("number")} ${it2.s("title")}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "$repoPath · ${it2.s("state")}",
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
