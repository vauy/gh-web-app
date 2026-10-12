package com.githubapp.android.api

import com.githubapp.android.App
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// 统一请求入口：直连 GitHub REST API（https://api.github.com/），
// 使用 GitHub 设备码（Device Flow）获取的用户令牌认证，不依赖任何后端。
object ApiClient {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val form = "application/x-www-form-urlencoded".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun request(method: String, path: String, body: JsonObject? = null, accept: String? = null): JsonElementResult =
        withContext(Dispatchers.IO) {
            val url = "https://api.github.com/" + path
            val rb = body?.toString()?.toRequestBody(json)
            val b = Request.Builder().url(url)
            b.header("Authorization", "Bearer ${App.token}")
            b.header("User-Agent", "GhAndroid")
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
        val url = "https://api.github.com/" + path
        val b = Request.Builder().url(url)
        b.header("Authorization", "Bearer ${App.token}")
        b.header("User-Agent", "GhAndroid")
        if (accept != null) b.header("Accept", accept)
        client.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw ApiException(resp.code, text.take(300))
            text
        }
    }

    // ---- GitHub 设备码（Device Flow）----

    // 第一步：申请设备码与用户码
    suspend fun requestDeviceCode(clientId: String): JsonObject = withContext(Dispatchers.IO) {
        val body = "client_id=$clientId&scope=${URLEncoder.encode(App.SCOPE, "UTF-8")}".toRequestBody(form)
        val req = Request.Builder()
            .url("https://github.com/login/device/code")
            .post(body)
            .header("Accept", "application/json")
            .header("User-Agent", "GhAndroid")
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            val j = try { JsonParser.parseString(text).asJsonObject } catch (e: JsonSyntaxException) { JsonObject() }
            if (!resp.isSuccessful) {
                val msg = j.s("error_description").ifEmpty { j.s("error").ifEmpty { "HTTP ${resp.code}" } }
                throw ApiException(resp.code, msg)
            }
            j
        }
    }

    // 第二步：轮询换取访问令牌（authorization_pending 表示用户尚未授权）
    suspend fun pollAccessToken(clientId: String, deviceCode: String): JsonObject = withContext(Dispatchers.IO) {
        val body = ("client_id=$clientId&device_code=${URLEncoder.encode(deviceCode, "UTF-8")}" +
                "&grant_type=urn:ietf:params:oauth:grant-type:device_code").toRequestBody(form)
        val req = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .post(body)
            .header("Accept", "application/json")
            .header("User-Agent", "GhAndroid")
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            val j = try { JsonParser.parseString(text).asJsonObject } catch (e: JsonSyntaxException) { JsonObject() }
            if (!resp.isSuccessful) {
                val msg = j.s("error_description").ifEmpty { j.s("error").ifEmpty { "HTTP ${resp.code}" } }
                throw ApiException(resp.code, msg)
            }
            j
        }
    }

    fun logout() {
        App.clearAuth()
    }

    class ApiException(val code: Int, message: String) : Exception(message)
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
fun JsonObject.l(key: String): Long =
    get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asLong ?: 0L
fun JsonObject.b(key: String): Boolean =
    get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asBoolean ?: false
fun JsonObject.a(key: String): JsonArray =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
fun JsonObject.o(key: String): JsonObject =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
