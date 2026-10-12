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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.githubapp.android.api.l
import com.githubapp.android.api.o
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

/** Releases 列表 */
@Composable
fun ReleasesTab(owner: String, name: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            releases = ApiClient.get("repos/$owner/$name/releases?per_page=30").arr().map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Releases", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { showNew = true }) { Text("+ 新建 Release") }
        }
        Spacer(Modifier.height(6.dp))
        when {
            err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
            !loaded -> LoadingBox()
            releases.isEmpty() -> EmptyBox("暂无 Release")
            else -> LazyColumn {
                items(releases, key = { it.l("id") }) { r ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("🏷️ ${r.s("tag_name")}  ${r.s("name")}", fontWeight = FontWeight.SemiBold)
                            Text(
                                "发布者 ${r.o("author").s("login")} · ${fmtTime(r.s("published_at"))}${if (r.b("prerelease")) " · 预发布" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (r.s("body").isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(r.s("body"))
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showNew) {
        NewReleaseDialog(owner, name, defaultBranch = null,
            onDismiss = { showNew = false },
            onDone = { showNew = false; scope.launch { load() } },
        )
    }
}

@Composable
private fun NewReleaseDialog(
    owner: String,
    name: String,
    defaultBranch: String?,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tag by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var prerelease by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(defaultBranch ?: "") }

    LaunchedEffect(Unit) {
        if (target.isEmpty()) {
            try { target = ApiClient.get("repos/$owner/$name").obj().s("default_branch") }
            catch (_: Exception) {}
        }
    }

    AppDialog(
        title = "新建 Release",
        onDismiss = onDismiss,
        confirmText = "发布",
        confirmEnabled = tag.isNotBlank() && !saving,
        onConfirm = {
            scope.launch {
                saving = true
                try {
                    ApiClient.post("repos/$owner/$name/releases", JsonObject().apply {
                        addProperty("tag_name", tag.trim())
                        addProperty("target_commitish", target)
                        addProperty("name", title)
                        addProperty("body", body)
                        addProperty("prerelease", prerelease)
                    })
                    toast(ctx, "Release 已发布 🎉")
                    onDone()
                } catch (e: ApiClient.ApiException) {
                    saving = false
                    toast(ctx, e.message ?: "发布失败")
                }
            }
        },
    ) {
        OutlinedTextField(tag, { tag = it }, label = { Text("标签 *（如 v1.0.0）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(body, { body = it }, label = { Text("说明") }, modifier = Modifier.fillMaxWidth().height(100.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("预发布")
            Switch(prerelease, { prerelease = it })
        }
        Text("发布到分支：$target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
