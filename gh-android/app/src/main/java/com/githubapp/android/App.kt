package com.githubapp.android

import android.app.Application

// 纯独立原生 App：直连 GitHub API，不依赖任何后端。
// 登录使用 GitHub 设备码（Device Flow），只需 OAuth App / GitHub App 的 Client ID。
class App : Application() {

    companion object {
        const val SCOPE = "repo workflow notifications gist read:org"
        lateinit var instance: App
        var token: String = ""
        var clientId: String = ""

        private fun prefs() = instance.getSharedPreferences("gh_prefs", MODE_PRIVATE)

        fun saveToken(t: String) {
            token = t
            prefs().edit().putString("token", t).apply()
        }

        fun saveClientId(id: String) {
            clientId = id
            prefs().edit().putString("client_id", id).apply()
        }

        fun clearAuth() {
            token = ""
            prefs().edit().remove("token").apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val p = getSharedPreferences("gh_prefs", MODE_PRIVATE)
        token = p.getString("token", "") ?: ""
        clientId = p.getString("client_id", "") ?: ""
    }
}
