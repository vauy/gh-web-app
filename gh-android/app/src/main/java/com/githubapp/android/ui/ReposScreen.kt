package com.githubapp.android.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.githubapp.android.nav.Nav
import com.githubapp.android.nav.Screen
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

@Composable
fun ReposScreen() {
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf(listOf<JsonObject>()) }
    var filter by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            repos = ApiClient.get("user/repos?per_page=100&sort=updated").arr().map { it.asJsonObject }
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
    }
    LaunchedEffect(Unit) { load() }

    if (err != null) {
        ErrorBox(err!!) { scope.launch { err = null; load() } }
        return
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("过滤仓库…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { showNew = true }) { Text("+ 新建") }
        }
        Spacer(Modifier.height(8.dp))
        if (repos.isEmpty()) {
            EmptyBox("暂无仓库，点击右上角新建")
        } else {
            val filtered = repos.filter {
                filter.isBlank() ||
                    it.s("name").contains(filter, true) ||
                    it.s("description").contains(filter, true)
            }
            LazyColumn {
                items(filtered, key = { it.s("full_name") }) { r ->
                    RepoRow(r, onStarDone = { scope.launch { load() } })
                    HorizontalDivider()
                }
            }
        }
    }

    if (showNew) {
        NewRepoDialog(
            onDismiss = { showNew = false },
            onCreated = { showNew = false; scope.launch { load() } },
        )
    }
}

@Composable
private fun RepoRow(r: JsonObject, onStarDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var starring by remember { mutableStateOf(false) }
    Card(
        onClick = { Nav.show(Screen.Repo(r.o("owner").s("login"), r.s("name"))) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${r.o("owner").s("login")}/${r.s("name")}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    r.s("description").ifEmpty { "暂无描述" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                Text(
                    "⭐ ${fmtCount(r.i("stargazers_count"))} · 🍴 ${fmtCount(r.i("forks_count"))} · ${r.s("language")} · ${if (r.b("private")) "私有" else "公开"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                enabled = !starring,
                onClick = {
                    scope.launch {
                        starring = true
                        try {
                            val starred = r.i("stargazers_count") > 0
                            if (starred) ApiClient.delete("user/starred/${r.s("full_name")}")
                            else ApiClient.put("user/starred/${r.s("full_name")}", JsonObject())
                            toast(ctx, if (starred) "已取消收藏" else "已收藏 ⭐")
                            onStarDone()
                        } catch (e: ApiClient.ApiException) {
                            toast(ctx, e.message ?: "操作失败")
                        } finally {
                            starring = false
                        }
                    }
                },
            ) { Text(if (r.i("stargazers_count") > 0) "取消收藏" else "⭐ 收藏") }
        }
    }
}

@Composable
private fun NewRepoDialog(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var withReadme by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    AppDialog(
        title = "新建仓库",
        onDismiss = onDismiss,
        confirmText = "创建",
        confirmEnabled = name.isNotBlank() && !saving,
        onConfirm = {
            scope.launch {
                saving = true
                try {
                    val body = JsonObject().apply {
                        addProperty("name", name.trim())
                        addProperty("description", desc)
                        addProperty("private", isPrivate)
                        addProperty("auto_init", withReadme)
                    }
                    ApiClient.post("user/repos", body)
                    toast(ctx, "仓库已创建")
                    onCreated()
                } catch (e: ApiClient.ApiException) {
                    saving = false
                    toast(ctx, e.message ?: "创建失败")
                }
            }
        },
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("仓库名称 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(desc, { desc = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("私有仓库")
            Switch(isPrivate, { isPrivate = it })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("自动创建 README")
            Switch(withReadme, { withReadme = it })
        }
    }
}
