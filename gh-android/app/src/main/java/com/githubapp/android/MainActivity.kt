package com.githubapp.android

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.githubapp.android.nav.AppState
import com.githubapp.android.nav.Nav
import com.githubapp.android.nav.Screen
import com.githubapp.android.ui.CommitScreen
import com.githubapp.android.ui.EventsScreen
import com.githubapp.android.ui.GistsScreen
import com.githubapp.android.ui.HomeScreen
import com.githubapp.android.ui.IssueScreen
import com.githubapp.android.ui.LoginScreen
import com.githubapp.android.ui.MoreScreen
import com.githubapp.android.ui.MyIssuesScreen
import com.githubapp.android.ui.MyPullsScreen
import com.githubapp.android.ui.NotificationsScreen
import com.githubapp.android.ui.PrScreen
import com.githubapp.android.ui.RepoScreen
import com.githubapp.android.ui.ReposScreen
import com.githubapp.android.ui.RunScreen
import com.githubapp.android.ui.SearchScreen

private val GhDark = darkColorScheme(
    primary = Color(0xFF4493F8),
    onPrimary = Color.White,
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF30363D),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
    error = Color(0xFFF85149),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = GhDark) {
                MainRoot()
            }
        }
    }
}

@Composable
fun MainRoot() {
    val context = LocalContext.current
    BackHandler {
        if (Nav.pop()) {
            // 已返回上一页
        } else if (AppState.loggedIn) {
            (context as? Activity)?.finish()
        }
    }

    if (!AppState.loggedIn) {
        LoginScreen(onLoggedIn = { Nav.reset(Screen.Home) })
    } else {
        Scaffold(
            topBar = { TopBar() },
            bottomBar = { BottomNav() },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = Nav.current) {
                    Screen.Home -> HomeScreen()
                    Screen.Repos -> ReposScreen()
                    Screen.Search -> SearchScreen()
                    Screen.Notifications -> NotificationsScreen()
                    Screen.More -> MoreScreen()
                    Screen.MyIssues -> MyIssuesScreen()
                    Screen.MyPulls -> MyPullsScreen()
                    Screen.Gists -> GistsScreen()
                    Screen.Events -> EventsScreen()
                    is Screen.Repo -> RepoScreen(s.owner, s.name)
                    is Screen.Issue -> IssueScreen(s.owner, s.name, s.number)
                    is Screen.Pr -> PrScreen(s.owner, s.name, s.number)
                    is Screen.Commit -> CommitScreen(s.owner, s.name, s.sha)
                    is Screen.Run -> RunScreen(s.owner, s.name, s.runId)
                    Screen.Login -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    val title = when (val s = Nav.current) {
        Screen.Home -> "首页"
        Screen.Repos -> "我的仓库"
        Screen.Search -> "搜索"
        Screen.Notifications -> "通知"
        Screen.More -> "我的"
        Screen.MyIssues -> "我的 Issues"
        Screen.MyPulls -> "我的 Pull Requests"
        Screen.Gists -> "我的 Gists"
        Screen.Events -> "Webhook 事件"
        is Screen.Repo -> "${s.owner}/${s.name}"
        is Screen.Issue -> "Issue #${s.number}"
        is Screen.Pr -> "Pull Request #${s.number}"
        is Screen.Commit -> "提交详情"
        is Screen.Run -> "运行详情"
        Screen.Login -> ""
    }
    CenterAlignedTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (!Nav.isRoot) {
                IconButton(onClick = { Nav.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
    )
}

private data class TabItem(val label: String, val icon: String, val screen: Screen)

@Composable
fun BottomNav() {
    val items = listOf(
        TabItem("首页", "🏠", Screen.Home),
        TabItem("仓库", "📦", Screen.Repos),
        TabItem("搜索", "🔍", Screen.Search),
        TabItem("通知", "🔔", Screen.Notifications),
        TabItem("我的", "👤", Screen.More),
    )
    NavigationBar {
        items.forEach { item ->
            val selected = Nav.current == item.screen
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) Nav.reset(item.screen) else Nav.reset(item.screen)
                },
                icon = { Text(item.icon) },
                label = { Text(item.label) },
            )
        }
    }
}
