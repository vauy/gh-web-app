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

/** Issues 列表（仓库内） */
@Composable
fun IssuesTab(owner: String, name: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var issues by remember { mutableStateOf(listOf<JsonObject>()) }
    var filter by remember { mutableStateOf("open") }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            issues = ApiClient.get("repos/$owner/$name/issues?state=$filter&per_page=50").arr().map { it.asJsonObject }
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
            FilterChip(selected = filter == "closed", onClick = { filter = "closed" }, label = { Text("已关闭") })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("全部") })
            Spacer(Modifier.weight(1f))
            Button(onClick = { showNew = true }) { Text("+ 新建 Issue") }
        }
        Spacer(Modifier.height(6.dp))

        when {
            err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
            !loaded -> LoadingBox()
            issues.isEmpty() -> EmptyBox("没有 Issues")
            else -> LazyColumn {
                items(issues, key = { it.i("number") }) { it2 ->
                    IssueRow(it2, onClick = { Nav.show(Screen.Issue(owner, name, it2.i("number"))) })
                    HorizontalDivider()
                }
            }
        }
    }

    if (showNew) {
        NewIssueDialog(owner, name,
            onDismiss = { showNew = false },
            onDone = { showNew = false; scope.launch { load() } },
        )
    }
}

@Composable
private fun IssueRow(it: JsonObject, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(if (it.s("state") == "open") "🟢" else "🔴")
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "${it.s("title")} #${it.i("number")}",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "#${it.i("number")} 由 ${it.o("user").s("login")} 于 ${fmtTime(it.s("created_at"))} 提出 · ${it.i("comments")} 条评论",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NewIssueDialog(owner: String, name: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    AppDialog(
        title = "新建 Issue",
        onDismiss = onDismiss,
        confirmText = "提交",
        confirmEnabled = title.isNotBlank() && !saving,
        onConfirm = {
            scope.launch {
                saving = true
                try {
                    val r = ApiClient.post("repos/$owner/$name/issues", JsonObject().apply {
                        addProperty("title", title)
                        addProperty("body", body)
                    })
                    toast(ctx, "Issue #${r.obj().i("number")} 已创建")
                    onDone()
                } catch (e: ApiClient.ApiException) {
                    saving = false
                    toast(ctx, e.message ?: "创建失败")
                }
            }
        },
    ) {
        OutlinedTextField(title, { title = it }, label = { Text("标题 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(body, { body = it }, label = { Text("内容") }, modifier = Modifier.fillMaxWidth().height(140.dp))
    }
}

/** Issue 详情 */
@Composable
fun IssueScreen(owner: String, name: String, number: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var issue by remember { mutableStateOf<JsonObject?>(null) }
    var comments by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }

    suspend fun load() {
        try {
            issue = ApiClient.get("repos/$owner/$name/issues/$number").obj()
            comments = ApiClient.get("repos/$owner/$name/issues/$number/comments").arr().map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
    }
    LaunchedEffect(Unit) { load() }

    val i = issue
    when {
        err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
        i == null -> LoadingBox()
        else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
            Column(Modifier.fillMaxSize().weight(1f)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CopyText("#$number ${i.s("title")}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            if (i.s("state") == "open") "🟢 进行中" else "🔴 已关闭",
                            color = if (i.s("state") == "open") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "由 ${i.o("user").s("login")} 于 ${fmtTime(i.s("created_at"))} 提出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    CopyText(i.s("body").ifEmpty { "（无内容）" })
                    Spacer(Modifier.height(10.dp))
                    Row {
                        OutlinedButton(onClick = { showEdit = true }) { Text("编辑") }
                        Spacer(Modifier.width(8.dp))
                        if (i.s("state") == "open") {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        ApiClient.patch("repos/$owner/$name/issues/$number", JsonObject().apply { addProperty("state", "closed") })
                                        toast(ctx, "已关闭")
                                        load()
                                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "操作失败") }
                                }
                            }) { Text("关闭") }
                        } else {
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        ApiClient.patch("repos/$owner/$name/issues/$number", JsonObject().apply { addProperty("state", "open") })
                                        toast(ctx, "已重新打开")
                                        load()
                                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "操作失败") }
                                }
                            }) { Text("重新打开") }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("评论（${comments.size}）", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LazyColumn {
                    items(comments, key = { it.i("id") }) { c ->
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "${c.o("user").s("login")} · ${fmtTime(c.s("created_at"))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(c.s("body"))
                        }
                        HorizontalDivider()
                    }
                }
            }
            OutlinedTextField(
                commentText, { commentText = it },
                label = { Text("添加评论…") },
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

    if (showEdit) {
        val it = issue
        if (it != null) {
            var etitle by remember { mutableStateOf(it.s("title")) }
            var ebody by remember { mutableStateOf(it.s("body")) }
            AppDialog(
                title = "编辑 Issue #$number",
                onDismiss = { showEdit = false },
                confirmText = "保存",
                onConfirm = {
                    scope.launch {
                        try {
                            ApiClient.patch("repos/$owner/$name/issues/$number", JsonObject().apply {
                                addProperty("title", etitle)
                                addProperty("body", ebody)
                            })
                            toast(ctx, "已保存")
                            showEdit = false
                            load()
                        } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "保存失败") }
                    }
                },
            ) {
                OutlinedTextField(etitle, { etitle = it }, label = { Text("标题 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(ebody, { ebody = it }, label = { Text("内容") }, modifier = Modifier.fillMaxWidth().height(120.dp))
            }
        }
    }
}
