package com.githubapp.android.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.net.URLEncoder

// 管理：标签 / 里程碑 / 协作者
@Composable
fun ManageTab(owner: String, name: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var labels by remember { mutableStateOf(listOf<JsonObject>()) }
    var milestones by remember { mutableStateOf(listOf<JsonObject>()) }
    var collabs by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showNewLabel by remember { mutableStateOf(false) }
    var showNewMilestone by remember { mutableStateOf(false) }
    var showAddCollab by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            labels = ApiClient.get("repos/$owner/$name/labels?per_page=100").arr().map { it.asJsonObject }
            milestones = ApiClient.get("repos/$owner/$name/milestones?per_page=100").arr().map { it.asJsonObject }
            collabs = ApiClient.get("repos/$owner/$name/collaborators?per_page=50").arr().map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        when {
            err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
            !loaded -> LoadingBox()
            else -> LazyColumn {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏷️ 标签（${labels.size}）", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Button(onClick = { showNewLabel = true }) { Text("+ 新建标签") }
                    }
                }
                if (labels.isEmpty()) item { Text("暂无标签", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp)) }
                items(labels, key = { it.s("name") }) { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(14.dp).height(14.dp).clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor("#${l.s("color")}")))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.s("name"))
                            if (l.s("description").isNotEmpty()) {
                                Text(l.s("description"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    ApiClient.delete("repos/$owner/$name/labels/${URLEncoder.encode(l.s("name"), "UTF-8")}")
                                    toast(ctx, "已删除标签")
                                    load()
                                } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "删除失败") }
                            }
                        }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider()
                }

                item {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎯 里程碑（${milestones.size}）", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Button(onClick = { showNewMilestone = true }) { Text("+ 新建里程碑") }
                    }
                }
                if (milestones.isEmpty()) item { Text("暂无里程碑", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp)) }
                items(milestones, key = { it.i("number") }) { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${m.s("title")}（${m.i("open_issues")} 开 / ${m.i("closed_issues")} 关）", fontWeight = FontWeight.SemiBold)
                            if (m.s("description").isNotEmpty()) {
                                Text(m.s("description"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (m.s("due_on").isNotEmpty()) {
                                Text("截止 ${fmtTime(m.s("due_on"))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    val state = if (m.s("state") == "open") "closed" else "open"
                                    ApiClient.patch("repos/$owner/$name/milestones/${m.i("number")}", JsonObject().apply { addProperty("state", state) })
                                    toast(ctx, "已${if (state == "closed") "关闭" else "重新打开"}")
                                    load()
                                } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "操作失败") }
                            }
                        }) { Text(if (m.s("state") == "open") "关闭" else "重开") }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    ApiClient.delete("repos/$owner/$name/milestones/${m.i("number")}")
                                    toast(ctx, "已删除里程碑")
                                    load()
                                } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "删除失败") }
                            }
                        }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider()
                }

                item {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👥 协作者（${collabs.size}）", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Button(onClick = { showAddCollab = true }) { Text("+ 添加") }
                    }
                }
                if (collabs.isEmpty()) item { Text("暂无协作者", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp)) }
                items(collabs, key = { it.s("login") }) { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.s("login"), 36)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("@${c.s("login")}")
                            Text(
                                permissionText(c),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    ApiClient.delete("repos/$owner/$name/collaborators/${c.s("login")}")
                                    toast(ctx, "已移除协作者")
                                    load()
                                } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "移除失败（可能需要管理员权限）") }
                            }
                        }) { Text("移除", color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showNewLabel) {
        var lName by remember { mutableStateOf("") }
        var lColor by remember { mutableStateOf("4493f8") }
        var lDesc by remember { mutableStateOf("") }
        AppDialog(
            title = "新建标签",
            onDismiss = { showNewLabel = false },
            confirmText = "创建",
            confirmEnabled = lName.isNotBlank(),
            onConfirm = {
                scope.launch {
                    try {
                        ApiClient.post("repos/$owner/$name/labels", JsonObject().apply {
                            addProperty("name", lName.trim())
                            addProperty("color", lColor.trim().removePrefix("#"))
                            addProperty("description", lDesc)
                        })
                        toast(ctx, "标签已创建")
                        showNewLabel = false
                        load()
                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "创建失败") }
                }
            },
        ) {
            OutlinedTextField(lName, { lName = it }, label = { Text("标签名 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(lColor, { lColor = it }, label = { Text("颜色（hex，如 4493f8）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(lDesc, { lDesc = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showNewMilestone) {
        var mTitle by remember { mutableStateOf("") }
        var mDesc by remember { mutableStateOf("") }
        AppDialog(
            title = "新建里程碑",
            onDismiss = { showNewMilestone = false },
            confirmText = "创建",
            confirmEnabled = mTitle.isNotBlank(),
            onConfirm = {
                scope.launch {
                    try {
                        ApiClient.post("repos/$owner/$name/milestones", JsonObject().apply {
                            addProperty("title", mTitle.trim())
                            addProperty("description", mDesc)
                        })
                        toast(ctx, "里程碑已创建")
                        showNewMilestone = false
                        load()
                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "创建失败") }
                }
            },
        ) {
            OutlinedTextField(mTitle, { mTitle = it }, label = { Text("标题 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(mDesc, { mDesc = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showAddCollab) {
        var username by remember { mutableStateOf("") }
        AppDialog(
            title = "添加协作者",
            onDismiss = { showAddCollab = false },
            confirmText = "添加",
            confirmEnabled = username.isNotBlank(),
            onConfirm = {
                scope.launch {
                    try {
                        ApiClient.put("repos/$owner/$name/collaborators/${username.trim()}", JsonObject())
                        toast(ctx, "已添加协作者 @${username.trim()}")
                        showAddCollab = false
                        load()
                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "添加失败（可能需要管理员权限）") }
                }
            },
        ) {
            OutlinedTextField(username, { username = it }, label = { Text("GitHub 用户名 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun permissionText(c: JsonObject): String {
    val p = c.o("permissions")
    return when {
        p.b("admin") -> "管理员"
        p.b("push") -> "写入"
        p.b("pull") -> "只读"
        else -> "成员"
    }
}
