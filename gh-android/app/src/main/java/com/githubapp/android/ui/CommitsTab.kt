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

/** 提交历史 */
@Composable
fun CommitsTab(owner: String, name: String) {
    var commits by remember { mutableStateOf(listOf<JsonObject>()) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            commits = ApiClient.get("repos/$owner/$name/commits?per_page=30").arr().map { it.asJsonObject }
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
        commits.isEmpty() -> EmptyBox("暂无提交")
        else -> LazyColumn {
            items(commits, key = { it.s("sha") }) { c ->
                Card(
                    onClick = { Nav.show(Screen.Commit(owner, name, c.s("sha"))) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.o("commit").s("message").split('\n')[0], fontWeight = FontWeight.SemiBold)
                        Text(
                            "${c.o("commit").o("author").s("name")} · ${fmtTime(c.o("commit").o("author").s("date"))} · ${c.s("sha").take(7)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** 提交详情（含 diff） */
@Composable
fun CommitScreen(owner: String, name: String, sha: String) {
    val ctx = LocalContext.current
    var commit by remember { mutableStateOf<JsonObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            commit = ApiClient.get("repos/$owner/$name/commits/$sha").obj()
            err = null
        } catch (e: ApiClient.ApiException) {
            if (e.code == 401) AppState.on401() else err = e.message
        }
    }
    LaunchedEffect(Unit) { load() }

    val c = commit
    when {
        err != null -> ErrorBox(err!!) { scope.launch { err = null; load() } }
        c == null -> LoadingBox()
        else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(c.o("commit").s("message"), fontWeight = FontWeight.Bold)
            Text(
                "${c.o("commit").o("author").s("name")} · ${fmtTime(c.o("commit").o("author").s("date"))} · ${sha.take(7)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyColumn {
                items(c.a("files").map { it.asJsonObject }, key = { it.s("filename") }) { f ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "${f.s("filename")}   +${f.i("additions")}  -${f.i("deletions")}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val patch = f.s("patch")
                        if (patch.isNotEmpty()) {
                            Text(
                                patch,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 40,
                            )
                        } else {
                            Text("（二进制文件）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
