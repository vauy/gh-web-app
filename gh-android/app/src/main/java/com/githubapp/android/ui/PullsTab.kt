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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/** Pull Requests 列表（仓库内） */
@Composable
fun PullsTab(owner: String, name: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var prs by remember { mutableStateOf(listOf<JsonObject>()) }
    var filter by remember { mutableStateOf("open") }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            prs = ApiClient.get("repos/$owner/$name/pulls?state=$filter&per_page=50").arr().map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(filter) { loaded = false; load() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = filter == "open", onClick = { filter = "open" }, label = { Text("进行中") })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = filter == "closed", onClick = { filter = "closed" }, label = { Text("已合并/关闭") })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("全部") })
            Spacer(Modifier.weight(1f))
            Button(onClick = { showNew = true }) { Text("+ 新建 PR") }
        }
        Spacer(Modifier.height(6.dp))

        when {
            err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
            !loaded -> LoadingBox()
            prs.isEmpty() -> EmptyBox("没有 Pull Requests")
            else -> LazyColumn {
                items(prs, key = { it.i("number") }) { p ->
                    Card(
                        onClick = { Nav.show(Screen.Pr(owner, name, p.i("number"))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${if (p.s("state") == "open") "↔" else "✓"} ${p.s("title")} #${p.i("number")}",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${p.s("head.ref")} → ${p.s("base.ref")} · 由 ${p.o("user").s("login")} · ${fmtTime(p.s("created_at"))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewPRDialog(owner, name,
            onDismiss = { showNew = false },
            onDone = { showNew = false; scope.launch { load() } },
        )
    }
}

/** 新建 PR（含分支选择） */
@Composable
private fun NewPRDialog(owner: String, name: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var branches by remember { mutableStateOf(listOf<String>()) }
    var base by remember { mutableStateOf("") }
    var head by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<String?>(null) } // "base" | "head"

    LaunchedEffect(Unit) {
        try {
            val r = ApiClient.get("repos/$owner/$name").obj()
            base = r.s("default_branch").ifEmpty { "main" }
            branches = ApiClient.get("repos/$owner/$name/branches?per_page=100").arr()
                .map { it.asJsonObject.s("name") }
        } catch (e: ApiClient.ApiException) {
            toast(ctx, e.message ?: "加载分支失败")
        }
    }

    AppDialog(
        title = "新建 Pull Request",
        onDismiss = onDismiss,
        confirmText = "创建 PR",
        confirmEnabled = base.isNotBlank() && head.isNotBlank() && title.isNotBlank() && !saving,
        onConfirm = {
            scope.launch {
                saving = true
                try {
                    ApiClient.post("repos/$owner/$name/pulls", JsonObject().apply {
                        addProperty("title", title)
                        addProperty("body", body)
                        addProperty("head", head)
                        addProperty("base", base)
                    })
                    toast(ctx, "PR 已创建")
                    onDone()
                } catch (e: ApiClient.ApiException) {
                    saving = false
                    toast(ctx, e.message ?: "创建失败")
                }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { choosing = "base" }) { Text("基分支: $base") }
            Spacer(Modifier.width(6.dp))
            Text("←", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = { choosing = "head" }) { Text("头分支: $head") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(title, { title = it }, label = { Text("标题 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(body, { body = it }, label = { Text("内容") }, modifier = Modifier.fillMaxWidth().height(120.dp))
    }

    val c = choosing
    if (c != null && branches.isNotEmpty()) {
        AppDialog(
            title = if (c == "base") "选择基分支" else "选择头分支",
            onDismiss = { choosing = null },
            confirmText = "确定",
            onConfirm = { choosing = null },
        ) {
            branches.forEach { b ->
                TextButton(
                    onClick = {
                        if (c == "base") base = b else head = b
                        choosing = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(b) }
            }
        }
    }
}

/** PR 详情 */
@Composable
fun PrScreen(owner: String, name: String, number: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pr by remember { mutableStateOf<JsonObject?>(null) }
    var files by remember { mutableStateOf(listOf<JsonObject>()) }
    var comments by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var commentText by remember { mutableStateOf("") }

    suspend fun load() {
        try {
            pr = ApiClient.get("repos/$owner/$name/pulls/$number").obj()
            files = ApiClient.get("repos/$owner/$name/pulls/$number/files").arr().map { it.asJsonObject }
            comments = ApiClient.get("repos/$owner/$name/issues/$number/comments").arr().map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
    }
    LaunchedEffect(Unit) { load() }

    val p = pr
    when {
        err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
        p == null -> LoadingBox()
        else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
            Column(Modifier.fillMaxSize().weight(1f)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#$number ${p.s("title")}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            if (p.s("state") == "open") "🟢 进行中" else "🔴 已关闭",
                            color = if (p.s("state") == "open") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "${p.s("head.ref")} → ${p.s("base.ref")} · 由 ${p.o("user").s("login")} · ${fmtTime(p.s("created_at"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (p.b("merged")) Text("✓ 已合并", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(p.s("body").ifEmpty { "（无内容）" })
                    Spacer(Modifier.height(10.dp))
                    if (p.s("state") == "open") {
                        Row {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        val msg = commentText.ifBlank { "LGTM ✅" }
                                        ApiClient.post("repos/$owner/$name/pulls/$number/reviews", JsonObject().apply {
                                            addProperty("body", msg)
                                            addProperty("event", "APPROVE")
                                        })
                                        toast(ctx, "已批准 ✅")
                                        commentText = ""
                                        load()
                                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "操作失败") }
                                }
                            }) { Text("✓ 批准") }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        val msg = commentText.ifBlank { "请修改以下问题" }
                                        ApiClient.post("repos/$owner/$name/pulls/$number/reviews", JsonObject().apply {
                                            addProperty("body", msg)
                                            addProperty("event", "REQUEST_CHANGES")
                                        })
                                        toast(ctx, "已请求更改")
                                        commentText = ""
                                        load()
                                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "操作失败") }
                                }
                            }) { Text("✗ 请求更改") }
                            Spacer(Modifier.width(6.dp))
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        ApiClient.put("repos/$owner/$name/pulls/$number/merge", JsonObject().apply {
                                            addProperty("merge_method", "squash")
                                        })
                                        toast(ctx, "已合并 🎉")
                                        load()
                                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "合并失败（可能需先通过检查）") }
                                }
                            }) { Text("合并") }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("变更文件（${files.size}）", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                files.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("📄", Modifier.padding(end = 8.dp))
                        Text(f.s("filename"), modifier = Modifier.weight(1f))
                        Text("+${f.i("additions")}", color = MaterialTheme.colorScheme.primary)
                        Text(" -${f.i("deletions")}", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("评论（${comments.size}）", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                comments.forEach { c ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "${c.o("user").s("login")} · ${fmtTime(c.s("created_at"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(c.s("body"))
                    }
                }
            }
            OutlinedTextField(
                commentText, { commentText = it },
                label = { Text("评论内容…（用于批准/请求更改/普通评论）") },
                modifier = Modifier.fillMaxWidth().height(90.dp),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        try {
                            ApiClient.post("repos/$owner/$name/issues/$number/comments", JsonObject().apply { addProperty("body", commentText) })
                            toast(ctx, "评论成功")
                            commentText = ""
                            load()
                        } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "评论失败") }
                    }
                },
                enabled = commentText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("发表评论") }
        }
    }
}
