package com.githubapp.android.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githubapp.android.App
import com.githubapp.android.api.ApiClient
import com.githubapp.android.api.i
import com.githubapp.android.api.s
import com.githubapp.android.nav.AppState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 登录页：GitHub 设备码（Device Flow）。
// 无需后端、无需回调地址；首次登录在任意设备浏览器打开 github.com/login/device 输入用户码授权即可。
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var clientId by remember { mutableStateOf(App.clientId) }
    var step by remember { mutableStateOf("id") } // id -> code -> polling
    var userCode by remember { mutableStateOf("") }
    var verifyUri by remember { mutableStateOf("") }
    var deviceCode by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf(5) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }

    fun startDeviceFlow() {
        val id = clientId.trim()
        if (id.isEmpty()) { error = "请先填写 Client ID"; return }
        App.saveClientId(id)
        error = null
        scope.launch {
            status = "正在获取设备码…"
            try {
                val d = ApiClient.requestDeviceCode(id)
                deviceCode = d.s("device_code")
                userCode = d.s("user_code")
                verifyUri = d.s("verification_uri").ifEmpty { "https://github.com/login/device" }
                interval = d.i("interval").coerceAtLeast(5)
                step = "code"
            } catch (e: ApiClient.ApiException) {
                error = e.message ?: "获取设备码失败"
            }
            status = ""
        }
    }

    fun startPolling() {
        error = null
        scope.launch {
            step = "polling"
            var intervalMs = interval * 1000L
            var attempts = 0
            while (attempts < 200) { // 最长约 15 分钟
                delay(intervalMs)
                try {
                    val r = ApiClient.pollAccessToken(App.clientId, deviceCode)
                    val token = r.s("access_token")
                    if (token.isNotEmpty()) {
                        App.saveToken(token)
                        AppState.loggedIn = true
                        toast(context, "登录成功，欢迎回来！")
                        onLoggedIn()
                        return@launch
                    }
                    when (val err = r.s("error")) {
                        "authorization_pending" -> {} // 用户尚未在浏览器完成授权，继续等待
                        "slow_down" -> intervalMs += 5000
                        "expired_token", "access_denied" -> {
                            error = if (err == "expired_token") "设备码已过期，请重新开始" else "授权被拒绝，请重试"
                            step = "code"
                            return@launch
                        }
                        "" -> { /* 无 error 字段，继续等待 */ }
                        else -> {
                            error = r.s("error_description").ifEmpty { err }
                            step = "code"
                            return@launch
                        }
                    }
                } catch (e: ApiClient.ApiException) {
                    error = e.message ?: "获取令牌失败"
                    step = "code"
                    return@launch
                }
                attempts++
            }
            error = "等待授权超时，请重新开始"
            step = "code"
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Text("👋 欢迎使用 GitHub 客户端", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "纯原生 App，直连 GitHub。登录使用设备码方式，无需任何后端。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(28.dp))

            when (step) {
                "id" -> {
                    OutlinedTextField(
                        clientId, { clientId = it },
                        label = { Text("GitHub 应用 Client ID *") },
                        placeholder = { Text("例如 Iv1.xxxxxxxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "在 GitHub → Settings → Developer settings → OAuth Apps（或 GitHub Apps）新建应用，复制 Client ID 填入（无需 Secret）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { startDeviceFlow() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (status.isNotEmpty()) status else "获取授权代码")
                    }
                }

                "code" -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("请在其他设备浏览器打开：", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                verifyUri,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("然后输入代码：", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                userCode,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("（或在本机浏览器访问上面网址）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { step = "id" }) { Text("← 返回重新填写 Client ID") }
                            Button(onClick = { startPolling() }, modifier = Modifier.fillMaxWidth()) {
                                Text("我已授权，开始连接")
                            }
                        }
                    }
                }

                else -> { // polling
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("等待授权完成…\n请确认已在浏览器输入代码 $userCode 并授权", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }

            error?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "权限范围：仓库 / 工作流 / 通知 / Gists / 组织只读",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
