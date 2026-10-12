package com.githubapp.android.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 页面路由 */
sealed class Screen {
    object Login : Screen()
    object Home : Screen()
    object Repos : Screen()
    object Search : Screen()
    object Notifications : Screen()
    object More : Screen()

    object MyIssues : Screen()
    object MyPulls : Screen()
    object Gists : Screen()

    data class Repo(val owner: String, val name: String) : Screen()
    data class Issue(val owner: String, val name: String, val number: Int) : Screen()
    data class Pr(val owner: String, val name: String, val number: Int) : Screen()
    data class Commit(val owner: String, val name: String, val sha: String) : Screen()
    data class Run(val owner: String, val name: String, val runId: Long) : Screen()

    data class User(val login: String) : Screen()
    /** kind: followers|following|stargazers|forks|watchers；粉丝/关注用 a=login，仓库列表用 a=owner,b=name */
    data class PeopleList(val kind: String, val a: String, val b: String) : Screen()
    data class FileHistory(val owner: String, val name: String, val path: String) : Screen()
}

/** 全局导航（简单栈式管理） */
object Nav {
    var current by mutableStateOf<Screen>(Screen.Login)
    private val stack = mutableStateListOf<Screen>()

    fun show(s: Screen) {
        stack.add(current)
        current = s
    }

    /** 返回上一页；已在根时返回 false */
    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        current = stack.removeAt(stack.size - 1)
        return true
    }

    fun reset(s: Screen) {
        stack.clear()
        current = s
    }

    val isRoot: Boolean get() = stack.isEmpty()
}

/** 全局登录状态 */
object AppState {
    var loggedIn by mutableStateOf(false)
    var user by mutableStateOf<String?>(null)
    var authType by mutableStateOf("user")

    /** 会话失效（401）时回到登录页 */
    fun on401() {
        loggedIn = false
        user = null
        Nav.reset(Screen.Login)
    }
}
