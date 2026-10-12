package com.githubapp.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { content() } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 轻量提示 */
fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

/** 支持长按复制的文本 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CopyText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                clipboard.setText(AnnotatedString(text))
                toast(ctx, "已复制「${text.take(24)}…」")
            },
        ),
    )
}

private val avatarColors = listOf(
    Color(0xFF4493F8), Color(0xFF3FB950), Color(0xFFD29922),
    Color(0xFFA371F7), Color(0xFFF778BA), Color(0xFFDB6D28), Color(0xFF58A6FF),
)

@Composable
fun Avatar(login: String, size: Int = 40) {
    val color = avatarColors[Math.abs(login.hashCode()) % avatarColors.size]
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            login.take(1).uppercase(Locale.getDefault()),
            color = Color.White,
            fontSize = (size * 0.42f).sp,
        )
    }
}

/** 把 GitHub ISO 时间转成本地显示 */
fun fmtTime(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        p.timeZone = TimeZone.getTimeZone("UTC")
        val d = p.parse(iso) ?: return iso
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(d)
    } catch (e: Exception) {
        iso
    }
}

/** 数字缩写：1234 -> 1.2k */
fun fmtCount(n: Int): String = when {
    n >= 1000000 -> String.format(Locale.US, "%.1fm", n / 1000000f)
    n >= 1000 -> String.format(Locale.US, "%.1fk", n / 1000f)
    else -> n.toString()
}
