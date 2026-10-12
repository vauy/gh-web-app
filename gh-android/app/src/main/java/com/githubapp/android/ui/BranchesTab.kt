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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.githubapp.android.api.ApiClient
import com.githubapp.android.api.o
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

/** 分支管理 */
@Composable
fun BranchesTab(owner: String, name: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var branches by remember { mutableStateOf(listOf<JsonObject>()) }
    var defaultBranch by remember { mutableStateOf("") }
    var protections by remember { mutableStateOf(mapOf<String, JsonObject?>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showProtection by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            defaultBranch = ApiClient.get("repos/$owner/$name").obj().s("default_branch")
            branches = ApiClient.get("repos/$owner/$name/branches?per_page=100").arr().map { it.asJsonObject }
            protections = branches.associate { b ->
                val prot = try {
                    ApiClient.get("repos/$owner/$name/branches/${java.net.URLEncoder.encode(b.s("name"), "UTF-8")}/protection").obj()
                } catch (e: ApiClient.ApiException) {
                    null
                }
                b.s("name") to prot
            }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("分支管理", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { showNew = true }) { Text("+ 新建分支") }
        }
        Spacer(Modifier.height(6.dp))
        when {
            err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
            !loaded -> LoadingBox()
            else -> LazyColumn {
                items(branches, key = { it.s("name") }) { b ->
                    val isDefault = b.s("name") == defaultBranch
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🌿")
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            CopyText(b.s("name"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text(
                                b.o("commit").s("sha").take(7),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (isDefault) {
                            Text("默认", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        } else if (protections[b.s("name")] != null) {
                            Text("受保护", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (protections[b.s("name")] != null) {
                            TextButton(onClick = { showProtection = b.s("name") }) { Text("规则") }
                        }
                        if (!isDefault) {
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        ApiClient.delete("repos/$owner/$name/git/refs/heads/${java.net.URLEncoder.encode(b.s("name"), "UTF-8")}")
                                        toast(ctx, "已删除分支")
                                        load()
                                    } catch (e: ApiClient.ApiException) { toast(ctx, e.message ?: "删除失败") }
                                }
                            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showNew) {
        NewBranchDialog(owner, name, defaultBranch, branches,
            onDismiss = { showNew = false },
            onDone = { showNew = false; scope.launch { load() } },
        )
    }
    showProtection?.let { b ->
        val prot = protections[b]
        if (prot != null) {
            AppDialog(
                title = "分支保护规则：$b",
                onDismiss = { showProtection = null },
                confirmText = "关闭",
                onConfirm = { showProtection = null },
            ) {
                Text("必须 PR 评审：${prot.o("required_pull_request_reviews").let { it.s("enabled").ifEmpty { "否" } }.ifBlank { "否" }}")
                Text("强制推送：${prot.o("allow_force_pushes").s("enabled").ifEmpty { "否" }}")
                Text("分支可被删除：${prot.o("allow_deletions").s("enabled").ifEmpty { "否" }}")
            }
        } else {
            showProtection = null
        }
    }
}

@Composable
private fun NewBranchDialog(
    owner: String,
    name: String,
    defaultBranch: String,
    branches: List<JsonObject>,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var branchName by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(defaultBranch) }
    var saving by remember { mutableStateOf(false) }

    AppDialog(
        title = "新建分支",
        onDismiss = onDismiss,
        confirmText = "创建",
        confirmEnabled = branchName.isNotBlank() && !saving,
        onConfirm = {
            scope.launch {
                saving = true
                try {
                    val refObj = ApiClient.get("repos/$owner/$name/git/ref/heads/${java.net.URLEncoder.encode(from, "UTF-8")}").obj()
                    val sha = refObj.o("object").s("sha")
                    ApiClient.post("repos/$owner/$name/git/refs", JsonObject().apply {
                        addProperty("ref", "refs/heads/${branchName.trim()}")
                        addProperty("sha", sha)
                    })
                    toast(ctx, "分支已创建")
                    onDone()
                } catch (e: ApiClient.ApiException) {
                    saving = false
                    toast(ctx, e.message ?: "创建失败")
                }
            }
        },
    ) {
        OutlinedTextField(branchName, { branchName = it }, label = { Text("分支名 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("基于分支（点击选择）")
        branches.forEach { b ->
            TextButton(
                onClick = { from = b.s("name") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("${if (from == b.s("name")) "✓ " else ""}${b.s("name")}") }
        }
    }
}
