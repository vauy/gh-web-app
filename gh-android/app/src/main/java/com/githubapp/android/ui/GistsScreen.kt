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
import com.githubapp.android.api.a
import com.githubapp.android.api.b
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

/** Gists */
@Composable
fun GistsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var gists by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var viewGist by remember { mutableStateOf<JsonObject?>(null) }

    suspend fun load() {
        try {
            gists = ApiClient.get("gists?per_page=30").arr().map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row {
            Text("我的 Gists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { showNew = true }) { Text("+ 新建 Gist") }
        }
        Spacer(Modifier.height(6.dp))

        when {
            err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
            !loaded -> LoadingBox()
            gists.isEmpty() -> EmptyBox("暂无 Gist")
            else -> LazyColumn {
                items(gists, key = { it.s("id") }) { g ->
                    Card(
                        onClick = { viewGist = g },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                g.s("description").ifEmpty { g.a("files").size().toString() + " 个文件" },
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${g.a("files").size()} 个文件 · ${fmtTime(g.s("created_at"))} · ${if (g.b("public")) "公开" else "私密"}",
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

    if (showNew) {
        NewGistDialog(
            onDismiss = { showNew = false },
            onDone = { showNew = false; scope.launch { load() } },
        )
    }
    viewGist?.let { g ->
        AppDialog(
            title = g.s("description").ifEmpty { "Gist" },
            onDismiss = { viewGist = null },
            confirmText = "关闭",
            onConfirm = { viewGist = null },
        ) {
            g.a("files").forEach { f ->
                val file = f.asJsonObject
                Text(file.s("filename"), fontWeight = FontWeight.SemiBold)
                Text(
                    file.s("content"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 20,
                )
            }
        }
    }
}

@Composable
private fun NewGistDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var desc by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    AppDialog(
        title = "新建 Gist",
        onDismiss = onDismiss,
        confirmText = "创建",
        confirmEnabled = fileName.isNotBlank() && !saving,
        onConfirm = {
            scope.launch {
                saving = true
                try {
                    val files = JsonObject().apply {
                        add(fileName.trim(), JsonObject().apply { addProperty("content", content) })
                    }
                    ApiClient.post("gists", JsonObject().apply {
                        addProperty("description", desc)
                        addProperty("public", isPublic)
                        add("files", files)
                    })
                    toast(ctx, "Gist 已创建")
                    onDone()
                } catch (e: ApiClient.ApiException) {
                    saving = false
                    toast(ctx, e.message ?: "创建失败")
                }
            }
        },
    ) {
        OutlinedTextField(desc, { desc = it }, label = { Text("描述") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(fileName, { fileName = it }, label = { Text("文件名 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(content, { content = it }, label = { Text("内容") }, modifier = Modifier.fillMaxWidth().height(120.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("公开")
            Switch(isPublic, { isPublic = it })
        }
    }
}
