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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Actions：工作流 + 运行记录 */
@Composable
fun ActionsTab(owner: String, name: String) {
    var workflows by remember { mutableStateOf(listOf<JsonObject>()) }
    var runs by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            workflows = ApiClient.get("repos/$owner/$name/actions/workflows?per_page=50").obj().a("workflows").map { it.asJsonObject }
            runs = ApiClient.get("repos/$owner/$name/actions/runs?per_page=20").obj().a("workflow_runs").map { it.asJsonObject }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    when {
        err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
        !loaded -> LoadingBox()
        else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text("工作流（${workflows.size}）", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            workflows.forEach { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("⚙️", Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.s("name"))
                        Text(w.s("path"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (w.s("state") == "active") "运行中" else "已停用",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (w.s("state") == "active") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("最近运行（${runs.size}）", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            LazyColumn {
                items(runs, key = { it.i("id") }) { run ->
                    Card(
                        onClick = { Nav.show(Screen.Run(owner, name, run.i("id").toLong())) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(if (run.s("status") == "completed") "✓" else if (run.s("status") == "in_progress") "⟳" else "⏸")
                            Spacer(Modifier.padding(start = 6.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${run.s("name")} #${run.i("run_number")}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${run.s("head_branch")} · ${run.s("head_sha").take(7)} · ${fmtTime(run.s("created_at"))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val concl = run.s("conclusion")
                            Text(
                                (concl.ifEmpty { run.s("status") }).let {
                                    when (it) {
                                        "success" -> "成功"
                                        "failure" -> "失败"
                                        "in_progress" -> "进行中"
                                        else -> it
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = when (concl) {
                                    "success" -> MaterialTheme.colorScheme.primary
                                    "failure" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** 运行详情：Jobs + 日志 */
@Composable
fun RunScreen(owner: String, name: String, runId: Long) {
    var run by remember { mutableStateOf<JsonObject?>(null) }
    var jobs by remember { mutableStateOf(listOf<JsonObject>()) }
    var log by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            run = ApiClient.get("repos/$owner/$name/actions/runs/$runId").obj()
            jobs = ApiClient.get("repos/$owner/$name/actions/runs/$runId/jobs").obj().a("jobs").map { it.asJsonObject }
            log = try {
                ApiClient.getText("repos/$owner/$name/actions/runs/$runId/logs")
            } catch (_: Exception) { "（日志不可用）" }
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
    }
    LaunchedEffect(Unit) { load() }

    val r = run
    when {
        err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
        r == null -> LoadingBox()
        else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text("${r.s("name")} #${r.i("run_number")}", fontWeight = FontWeight.Bold)
            Text(
                "分支 ${r.s("head_branch")} · 提交 ${r.s("head_sha").take(7)} · ${fmtTime(r.s("created_at"))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val concl = r.s("conclusion")
            Text(
                "状态：${(concl.ifEmpty { r.s("status") }).let { when (it) { "success" -> "成功"; "failure" -> "失败"; "in_progress" -> "进行中"; else -> it } }}",
                color = when (concl) { "success" -> MaterialTheme.colorScheme.primary; "failure" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant },
            )
            Spacer(Modifier.height(10.dp))
            Text("Jobs", fontWeight = FontWeight.SemiBold)
            jobs.forEach { j ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("⚙️", Modifier.padding(end = 8.dp))
                    Text(j.s("name"), modifier = Modifier.weight(1f))
                    Text(
                        j.s("conclusion").ifEmpty { j.s("status") }.let { if (it == "success") "成功" else it },
                        color = if (j.s("conclusion") == "success") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("日志（前 300 行）", fontWeight = FontWeight.SemiBold)
            val l = log
            if (l != null) {
                Text(
                    l.split('\n').take(300).joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                )
            }
        }
    }
}
