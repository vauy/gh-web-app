package com.githubapp.android.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.githubapp.android.api.ApiClient
import com.githubapp.android.api.a
import com.githubapp.android.api.i
import com.githubapp.android.api.o
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.githubapp.android.nav.Nav
import com.githubapp.android.nav.Screen
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

private val SEARCH_TYPES = listOf(
    "仓库" to "repositories",
    "用户" to "users",
    "Issues" to "issues",
    "PR" to "pullrequests",
    "代码" to "code",
)

/** 搜索 */
@Composable
fun SearchScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("repositories") }
    var results by remember { mutableStateOf(listOf<JsonObject>()) }
    var searching by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    suspend fun doSearch() {
        if (query.isBlank()) return
        searching = true
        try {
            val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            results = when (type) {
                "repositories" -> ApiClient.get("search/repositories?q=$q&per_page=30").obj().a("items").map { it.asJsonObject }
                "users" -> ApiClient.get("search/users?q=$q&per_page=30").obj().a("items").map { it.asJsonObject }
                "issues" -> ApiClient.get("search/issues?q=$q&per_page=30").obj().a("items").map { it.asJsonObject }
                "pullrequests" -> ApiClient.get("search/issues?q=$q+type:pr&per_page=30").obj().a("items").map { it.asJsonObject }
                "code" -> ApiClient.get("search/code?q=$q&per_page=30").obj().a("items").map { it.asJsonObject }
                else -> emptyList()
            }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        searching = false
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("输入关键词…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { scope.launch { doSearch() } }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row {
            SEARCH_TYPES.forEach { (label, key) ->
                FilterChip(selected = type == key, onClick = { type = key }, label = { Text(label) })
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { scope.launch { doSearch() } },
            enabled = query.isNotBlank() && !searching,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (searching) "搜索中…" else "搜索") }

        when {
            err != null -> ErrorBox(err!!) { scope.launch { doSearch() } }
            searching -> LoadingBox()
            results.isEmpty() -> EmptyBox(if (query.isBlank()) "输入关键词开始搜索" else "没有结果")
            else -> LazyColumn {
                items(results, key = { it.s("id") }) { item -> SearchRow(type, item) }
            }
        }
    }
}

@Composable
private fun SearchRow(type: String, it: JsonObject) {
    Card(
        onClick = { searchNavigate(type, it) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            when (type) {
                "repositories" -> {
                    Text(
                        "${it.o("owner").s("login")}/${it.s("name")}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    Text(it.s("description").ifEmpty { "暂无描述" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("⭐ ${fmtCount(it.i("stargazers_count"))} · ${it.s("language")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "users" -> Text("@${it.s("login")}（${it.s("type")}）", fontWeight = FontWeight.SemiBold)
                "issues" -> Text("#${it.i("number")} ${it.s("title")}", fontWeight = FontWeight.SemiBold)
                "pullrequests" -> Text("PR #${it.i("number")} ${it.s("title")}", fontWeight = FontWeight.SemiBold)
                "code" -> {
                    Text(it.s("path"), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text(it.o("repository").s("full_name"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    HorizontalDivider()
}

/** 搜索结果点击跳转 */
private fun searchNavigate(type: String, it: JsonObject) {
    when (type) {
        "repositories" -> Nav.show(Screen.Repo(it.o("owner").s("login"), it.s("name")))
        "issues" -> {
            val p = it.s("repository_url").substringAfter("/repos/").split("/")
            if (p.size == 2) Nav.show(Screen.Issue(p[0], p[1], it.i("number")))
        }
        "pullrequests" -> {
            val p = it.s("repository_url").substringAfter("/repos/").split("/")
            if (p.size == 2) Nav.show(Screen.Pr(p[0], p[1], it.i("number")))
        }
        "code" -> {
            val f = it.o("repository").s("full_name").split("/")
            if (f.size == 2) Nav.show(Screen.Repo(f[0], f[1]))
        }
        "users" -> Nav.show(Screen.User(it.s("login")))
    }
}
