package com.githubapp.android.api

import android.webkit.CookieManager
import com.githubapp.android.App
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// 统一请求入口：代理到后端 /api/gh/（后端再转发到 GitHub REST API），
// 与登录 WebView 共享 Cookie，实现账号 / 应用双身份。
object ApiClient {

    private val json = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        CookieManager.getInstance().setAcceptCookie(true)
        OkHttpClient.Builder()
            .cookieJar(WebViewCookieJar())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun request(method: String, path: String, body: JsonObject? = null, accept: String? = null): JsonElementResult =
        withContext(Dispatchers.IO) {
            val url = App.baseUrl + "/api/gh/" + path
            val rb = body?.toString()?.toRequestBody(json)
            val b = Request.Builder().url(url)
            if (accept != null) b.header("Accept", accept)
            val req = b.method(method, if (method == "GET" || method == "HEAD") null else rb)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    val msg = try {
                        val j = JsonParser.parseString(text)
                        if (j.isJsonObject) {
                            val m = j.asJsonObject.get("message")
                            if (m != null && m.isJsonPrimitive) m.asString else "HTTP ${resp.code}"
                        } else text.take(200)
                    } catch (e: JsonSyntaxException) { "HTTP ${resp.code}: ${text.take(200)}" }
                    throw ApiException(resp.code, msg.toString())
                }
                JsonElementResult(text)
            }
        }

    suspend fun get(path: String, accept: String? = null): JsonElementResult = request("GET", path, accept = accept)
    suspend fun post(path: String, body: JsonObject): JsonElementResult = request("POST", path, body)
    suspend fun put(path: String, body: JsonObject): JsonElementResult = request("PUT", path, body)
    suspend fun patch(path: String, body: JsonObject): JsonElementResult = request("PATCH", path, body)
    suspend fun delete(path: String, body: JsonObject? = null): JsonElementResult = request("DELETE", path, body)

    // 文本响应（源码 / 日志等）
    suspend fun getText(path: String, accept: String? = null): String = withContext(Dispatchers.IO) {
        val url = App.baseUrl + "/api/gh/" + path
        val b = Request.Builder().url(url)
        if (accept != null) b.header("Accept", accept)
        client.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw ApiException(resp.code, text.take(300))
            text
        }
    }

    // 会话状态
    suspend fun session(): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(App.baseUrl + "/api/session").build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            if (text.isBlank()) JsonObject() else JsonParser.parseString(text).asJsonObject
        }
    }

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
    }

    class ApiException(val code: Int, message: String) : Exception(message)

    // OkHttp 与 WebView 共享 Cookie，保证登录会话一致
    class WebViewCookieJar : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookies = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
            return cookies.split(';').mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null
                else Cookie.Builder().domain(url.host)
                    .name(part.substring(0, idx).trim())
                    .value(part.substring(idx + 1).trim())
                    .path("/").build()
            }
        }
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { c ->
                CookieManager.getInstance().setCookie(url.toString(), "${c.name}=${c.value}; Path=/")
            }
        }
    }
}

// 包装响应文本，按需解析
class JsonElementResult(private val text: String) {
    val raw: String get() = text
    fun obj(): JsonObject =
        if (text.isBlank()) JsonObject() else JsonParser.parseString(text).let { if (it.isJsonObject) it.asJsonObject else JsonObject() }
    fun arr(): JsonArray =
        if (text.isBlank()) JsonArray() else JsonParser.parseString(text).let { if (it.isJsonArray) it.asJsonArray else JsonArray() }
    fun textOr(): String = text
}

// ---- JsonObject 便捷访问 ----
fun JsonObject.s(key: String): String =
    get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString ?: ""
fun JsonObject.i(key: String): Int =
    get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asInt ?: 0
fun JsonObject.b(key: String): Boolean =
    get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asBoolean ?: false
fun JsonObject.a(key: String): JsonArray =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
fun JsonObject.o(key: String): JsonObject =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
