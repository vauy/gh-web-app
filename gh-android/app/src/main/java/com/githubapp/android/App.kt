package com.githubapp.android

import android.app.Application

class App : Application() {
    companion object {
        const val DEFAULT_BASE = "http://10.0.2.2:3000"
        var baseUrl: String = DEFAULT_BASE
    }

    override fun onCreate() {
        super.onCreate()
        baseUrl = getSharedPreferences("gh_prefs", MODE_PRIVATE)
            .getString("base_url", DEFAULT_BASE) ?: DEFAULT_BASE
    }

    fun saveBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
        getSharedPreferences("gh_prefs", MODE_PRIVATE)
            .edit().putString("base_url", baseUrl).apply()
    }
}
