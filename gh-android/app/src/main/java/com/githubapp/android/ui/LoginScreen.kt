package com.githubapp.android.ui

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.githubapp.android.App
import com.githubapp.android.api.ApiClient
import com.githubapp.android.api.b
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 登录页：内嵌 WebView 打开后端 OAuth 授权地址。
// 登录页可直接修改服务地址（无需先登录），并展示连接错误提示。
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showServer by remember { mutableStateOf(false) }
    var loadUrl by remember { mutableStateOf("${App.baseUrl}/auth/login") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                val desc = error?.description?.toString() ?: "未知错误"
                                loadError = "无法连接服务：$desc\n请点上方「修改服务地址」检查地址是否正确"
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (checking) return
                                checking = true
                                scope.launch {
                                    var attempts = 0
                                    while (attempts < 60) {
                                        try {
                                            val s = ApiClient.session()
                                            if (s.b("loggedIn")) {
                                                AppState.loggedIn = true
                                                AppState.user = s.s("user").ifEmpty { "已登录" }
                                                AppState.authType = s.s("type").ifEmpty { "user" }
                                                toast(context, "登录成功，欢迎 ${AppState.user}！")
                                                onLoggedIn()
                                                return@launch
                                            }
                                        } catch (e: Exception) {
                                            // 后端未就绪，继续轮询
                                        }
                                        delay(1200)
                                        attempts++
                                    }
                                    toast(context, "等待登录超时，请重试")
                                    checking = false
                                }
                            }
                        }
                        loadUrl(loadUrl)
                    }
                },
                update = { web ->
                    if (web.url != loadUrl) {
                        checking = false
                        loadError = null
                        web.loadUrl(loadUrl)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "服务地址：${App.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showServer = true }) { Text("修改") }
                }
                loadError?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            Text(
                "正在打开 GitHub 授权页面…\n如未自动跳转，请检查上方服务地址（模拟器用 10.0.2.2:3000，真机用电脑局域网 IP）",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            )
        }
    }

    if (showServer) {
        var url by remember { mutableStateOf(App.baseUrl) }
        AppDialog(
            title = "修改服务地址",
            onDismiss = { showServer = false },
            confirmText = "保存并重连",
            onConfirm = {
                val newUrl = url.trimEnd('/')
                if (newUrl.isNotEmpty()) {
                    (context.applicationContext as App).saveBaseUrl(newUrl)
                    loadUrl = "$newUrl/auth/login"
                    showServer = false
                }
            },
        ) {
            OutlinedTextField(
                url, { url = it },
                label = { Text("后端地址（不含末尾 /）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "模拟器：http://10.0.2.2:3000\n真机：http://电脑局域网IP:3000（同一 Wi-Fi）\n也可用内网穿透的 https 地址",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
