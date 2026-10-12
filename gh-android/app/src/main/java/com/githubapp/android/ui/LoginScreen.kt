package com.githubapp.android.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

/**
 * 登录页：内嵌 WebView 打开后端 OAuth 授权地址，
 * 登录成功后 Cookie 写入系统 CookieManager（与 OkHttp 共享），
 * 轮询 /api/session 确认会话后进入主界面。
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
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
                        loadUrl("${App.baseUrl}/auth/login")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                "正在打开 GitHub 授权页面…\n如未自动跳转，请检查服务地址（设置 → 服务地址）",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            )
        }
    }
}
