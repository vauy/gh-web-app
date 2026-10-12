package com.githubapp.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githubapp.android.api.ApiClient
import com.githubapp.android.api.a
import com.githubapp.android.api.b
import com.githubapp.android.api.i
import com.githubapp.android.api.o
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

// 仓库概览：描述、主页、语言统计、Topics
@Composable
fun OverviewTab(owner: String, name: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var repo by remember { mutableStateOf<JsonObject?>(null) }
    var languages by remember { mutableStateOf<JsonObject?>(null) }
    var topics by remember { mutableStateOf(listOf<String>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            repo = ApiClient.get("repos/$owner/$name").obj()
            languages = ApiClient.get("repos/$owner/$name/languages").obj()
            topics = try {
                ApiClient.get("repos/$owner/$name/topics", accept = "application/vnd.github.mercy-preview+json")
                    .obj().a("names").map { it.asString }
            } catch (_: Exception) { emptyList() }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    val r = repo
    when {
        err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
        !loaded -> LoadingBox()
        r == null -> EmptyBox("加载失败")
        else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
            if (r.s("description").isNotEmpty()) {
                Text(r.s("description"))
                Spacer(Modifier.height(8.dp))
            }
            if (r.s("homepage").isNotEmpty()) {
                Row {
                    Text("主页：", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { openBrowser(ctx, r.s("homepage")) }) { Text(r.s("homepage")) }
                }
            }
            Text("创建于 ${fmtTime(r.s("created_at"))} · 最近推送 ${fmtTime(r.s("pushed_at"))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("大小 ${r.i("size") / 1024} MB · 默认分支 ${r.s("default_branch")} · ${if (r.b("private")) "私有" else "公开"} · ${r.s("license").ifEmpty { "无许可证" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.i("open_issues_count") > 0) {
                Text("进行中的 Issue：${r.i("open_issues_count")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            if (topics.isNotEmpty()) {
                Text("Topics", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row {
                    topics.forEach { t ->
                        Text(
                            t,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            val lang = languages
            if (lang != null && lang.size() > 0) {
                Text("语言统计", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                val total = lang.entrySet().sumOf { it.value.asLong }
                lang.entrySet().forEach { (key, value) ->
                    val pct = if (total > 0) value.asLong * 100 / total else 0
                    Text(
                        "$key：$pct%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Text("开源协议：${r.s("license")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
