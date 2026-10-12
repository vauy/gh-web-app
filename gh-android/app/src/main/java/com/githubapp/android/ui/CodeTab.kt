package com.githubapp.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.githubapp.android.api.ApiClient
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

/** 目录路径编码（保留 /，逐段 URL 编码） */
fun encPath(path: String): String =
    path.split('/').filter { it.isNotEmpty() }
        .joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

private fun queryDisplayName(ctx: Context, uri: Uri): String {
    val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
    return name ?: "upload_${System.currentTimeMillis()}"
}

/** 代码浏览：目录 / 文件 / 新建 / 上传 / 编辑 / 删除 */
@Composable
fun CodeTab(owner: String, name: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var path by remember { mutableStateOf("") }
    var viewFile by remember { mutableStateOf<String?>(null) }
    var defaultBranch by remember { mutableStateOf("main") }
    var showNew by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    val uploader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val displayName = queryDisplayName(ctx, uri)
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val target = if (path.isEmpty()) displayName else "$path/$displayName"
                    ApiClient.put("repos/$owner/$name/contents/${encPath(target)}", JsonObject().apply {
                        addProperty("message", "上传文件 $target")
                        addProperty("content", b64)
                        addProperty("branch", defaultBranch)
                    })
                    toast(ctx, "上传成功")
                    refreshKey++
                } catch (e: ApiClient.ApiException) {
                    toast(ctx, e.message ?: "上传失败")
                }
            }
        }
    }

    suspend fun loadDir() {
        try {
            entries = ApiClient.get("repos/$owner/$name/contents/${encPath(path)}").arr().map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(path, refreshKey) {
        if (viewFile == null) { loaded = false; loadDir() }
    }
    LaunchedEffect(Unit) {
        try {
            defaultBranch = ApiClient.get("repos/$owner/$name").obj().s("default_branch").ifEmpty { "main" }
        } catch (_: Exception) {}
    }

    val vf = viewFile
    if (vf != null) {
        FileViewScreen(owner, name, vf, defaultBranch,
            onBack = { viewFile = null; refreshKey++ },
            onChanged = { viewFile = null; refreshKey++ },
        )
        return
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Breadcrumb(repoName = name, path = path) { p -> path = p }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showNew = true }) { Text("+ 新建") }
            TextButton(onClick = { uploader.launch(arrayOf("*/*")) }) { Text("上传") }
        }
        Spacer(Modifier.height(4.dp))

        when {
            err != null -> ErrorBox(err!!) { scope.launch { err = null; loadDir() } }
            !loaded -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            entries.isEmpty() -> Text("目录为空", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> entries.forEach { e ->
                EntryRow(
                    e,
                    onOpen = {
                        if (e.s("type") == "dir") path = e.s("path") else viewFile = e.s("path")
                    },
                    onEdit = { editTarget = e.s("path") },
                    onDelete = { deleteTarget = e.s("path") },
                )
                HorizontalDivider()
            }
        }
    }

    editTarget?.let { p ->
        EditFileDialog(owner, name, p, defaultBranch,
            onDismiss = { editTarget = null },
            onDone = { editTarget = null; refreshKey++ },
        )
    }
    deleteTarget?.let { p ->
        DeleteConfirmDialog(owner, name, p, defaultBranch,
            onDismiss = { deleteTarget = null },
            onDone = { deleteTarget = null; refreshKey++ },
        )
    }
    if (showNew) {
        NewFileDialog(owner, name, path, defaultBranch,
            onDismiss = { showNew = false },
            onDone = { showNew = false; refreshKey++ },
        )
    }
}

@Composable
private fun Breadcrumb(repoName: String, path: String, onNavigate: (String) -> Unit) {
    val segs = path.split('/').filter { it.isNotEmpty() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            repoName,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { onNavigate("") },
        )
        var prefix = ""
        segs.forEach { seg ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            prefix = if (prefix.isEmpty()) seg else "$prefix/$seg"
            Text(
                seg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onNavigate(prefix) },
            )
        }
    }
}

@Composable
private fun EntryRow(
    e: JsonObject,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (e.s("type") == "dir") "📁" else "📄")
        Spacer(Modifier.width(10.dp))
        Text(
            e.s("name"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (e.s("type") == "dir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (e.s("type") == "file") {
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** 文件查看：用 WebView 渲染 GitHub 返回的 HTML（README/Markdown）或源码 */
@Composable
fun FileViewScreen(
    owner: String,
    name: String,
    filePath: String,
    branch: String,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var html by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        try {
            html = ApiClient.getText(
                "repos/$owner/$name/contents/${encPath(filePath)}",
                accept = "application/vnd.github.html+json",
            )
        } catch (e: ApiClient.ApiException) {
            err = e.message
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(filePath, modifier = Modifier.weight(1f), maxLines = 1)
            TextButton(onClick = { showEdit = true }) { Text("编辑") }
            TextButton(onClick = { showDelete = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
        val content = html
        when {
            err != null -> ErrorBox(err!!)
            content == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> AndroidView(
                factory = { WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                } },
                update = { it.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showEdit) {
        EditFileDialog(owner, name, filePath, branch,
            onDismiss = { showEdit = false },
            onDone = { showEdit = false; onChanged() },
        )
    }
    if (showDelete) {
        DeleteConfirmDialog(owner, name, filePath, branch,
            onDismiss = { showDelete = false },
            onDone = { showDelete = false; onChanged() },
        )
    }
}

/** 编辑文件 */
@Composable
fun EditFileDialog(
    owner: String,
    name: String,
    filePath: String,
    branch: String,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf<String?>(null) }
    var sha by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("更新 ${filePath.split('/').last()}") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val meta = ApiClient.get("repos/$owner/$name/contents/${encPath(filePath)}").obj()
            sha = meta.s("sha")
            val b64 = meta.s("content")
            if (b64.isNotEmpty()) {
                content = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
            }
        } catch (e: ApiClient.ApiException) {
            toast(ctx, e.message ?: "加载文件失败")
            onDismiss()
        }
    }

    AppDialog(
        title = "编辑 ${filePath.split('/').last()}",
        onDismiss = onDismiss,
        confirmText = "提交更改",
        confirmEnabled = content != null && !saving,
        onConfirm = {
            scope.launch {
                saving = true
                try {
                    val c = content ?: ""
                    ApiClient.put("repos/$owner/$name/contents/${encPath(filePath)}", JsonObject().apply {
                        addProperty("message", msg)
                        addProperty("content", android.util.Base64.encodeToString(c.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
                        addProperty("sha", sha)
                        addProperty("branch", branch)
                    })
                    toast(ctx, "已提交更改")
                    onDone()
                } catch (e: ApiClient.ApiException) {
                    saving = false
                    toast(ctx, e.message ?: "提交失败")
                }
            }
        },
    ) {
        OutlinedTextField(msg, { msg = it }, label = { Text("提交信息") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        val c = content
        if (c == null) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            OutlinedTextField(
                value = c,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().height(300.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            )
        }
    }
}

/** 新建文件 */
@Composable
fun NewFileDialog(
    owner: String,
    name: String,
    dir: String,
    branch: String,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("创建新文件") }
    var saving by remember { mutableStateOf(false) }

    AppDialog(
        title = "新建文件（${if (dir.isEmpty()) "根目录" else dir}/）",
        onDismiss = onDismiss,
        confirmText = "创建",
        confirmEnabled = fileName.isNotBlank() && !saving,
        onConfirm = {
            scope.launch {
                saving = true
                try {
                    val target = if (dir.isEmpty()) fileName else "$dir/$fileName"
                    ApiClient.put("repos/$owner/$name/contents/${encPath(target)}", JsonObject().apply {
                        addProperty("message", msg)
                        addProperty("content", android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
                        addProperty("branch", branch)
                    })
                    toast(ctx, "文件已创建")
                    onDone()
                } catch (e: ApiClient.ApiException) {
                    saving = false
                    toast(ctx, e.message ?: "创建失败")
                }
            }
        },
    ) {
        OutlinedTextField(fileName, { fileName = it }, label = { Text("文件名（可含子目录）*") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(msg, { msg = it }, label = { Text("提交信息") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            content, { content = it },
            modifier = Modifier.fillMaxWidth().height(200.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        )
    }
}

/** 删除文件确认 */
@Composable
fun DeleteConfirmDialog(
    owner: String,
    name: String,
    filePath: String,
    branch: String,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sha by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            sha = ApiClient.get("repos/$owner/$name/contents/${encPath(filePath)}").obj().s("sha")
        } catch (e: ApiClient.ApiException) {
            toast(ctx, e.message ?: "获取文件信息失败")
            onDismiss()
        }
    }

    AppDialog(
        title = "删除 $filePath ？",
        onDismiss = onDismiss,
        confirmText = "删除",
        confirmEnabled = sha.isNotEmpty() && !deleting,
        onConfirm = {
            scope.launch {
                deleting = true
                try {
                    ApiClient.delete("repos/$owner/$name/contents/${encPath(filePath)}", JsonObject().apply {
                        addProperty("message", "删除 $filePath")
                        addProperty("sha", sha)
                        addProperty("branch", branch)
                    })
                    toast(ctx, "已删除")
                    onDone()
                } catch (e: ApiClient.ApiException) {
                    deleting = false
                    toast(ctx, e.message ?: "删除失败")
                }
            }
        },
    ) {
        Text("该操作会直接提交到仓库。")
    }
}
