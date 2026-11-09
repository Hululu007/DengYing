package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dengying.player.data.source.adapters.BttwoAuth
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.GlassChip
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.theme.Glass
import com.dengying.player.ui.theme.Palette
import com.dengying.player.ui.theme.ThemeMode
import com.dengying.player.ui.theme.ThemePrefs
import kotlinx.coroutines.launch

/**
 * 「我的」窗格（v5 重排：个人信息聚合页）。
 *
 * 【v5 形态】顶栏（品牌+搜索）在本页隐藏（由 [IndexScreen] 负责），本页自带
 * 页头 = 标题 + 右上角设置钮。内容自上而下：个人信息卡（头像/姓名/登录态）→
 * 收藏/历史统计 → 主题风格 → 源状态入口。点卡片或「登录」胶囊进入独立登录页。
 *
 * 【登录态】[BttwoAuth] 是进程级单例（kv 落盘），本页用本地 state 镜像它的
 * 快照；从登录页返回后重新读取一次，避免返回栈复用旧组合时不刷新。
 */
@Composable
fun MineScreen(
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    /** 右上角设置钮：目前直达源状态页（源的启用/探测） */
    onOpenSettings: () -> Unit,
    /** 个人信息卡 / 登录胶囊：跳独立登录页 */
    onOpenLogin: () -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(BttwoAuth.isLoggedIn()) }
    var userName by remember { mutableStateOf(BttwoAuth.userName()) }
    var favCount by remember { mutableStateOf(0) }
    var histCount by remember { mutableStateOf(0) }
    // 登录页/注册页在独立 destination；其 NavBackStackEntry 回到 RESUMED 时才刷新。
    // 「我的」是 Pager 窗格，不随路由切换重组，不能靠 LaunchedEffect(Unit) 兜底。
    val backStackEntry = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current
        as? androidx.navigation.NavBackStackEntry

    fun refreshCounts() {
        scope.launch {
            val deps = UiDepsHolder.get()
            favCount = deps.store.getFavorites().size
            histCount = deps.store.getHistory().size
        }
    }
    LaunchedEffect(Unit) { refreshCounts() }
    androidx.compose.runtime.DisposableEffect(backStackEntry) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                loggedIn = BttwoAuth.isLoggedIn()
                userName = BttwoAuth.userName()
                refreshCounts()
            }
        }
        backStackEntry?.lifecycle?.addObserver(observer)
        onDispose { backStackEntry?.lifecycle?.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topPadding, bottom = bottomPadding),
    ) {
        // ---- 页头：标题 + 设置钮（v5：共享顶栏在本页隐藏，改由右上角设置） ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            GlassSurface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpenSettings() },
                    contentAlignment = Alignment.Center,
                ) {
                    DyIcon(icon = DyIcons.Sliders, tint = Palette.TextPrimary, size = 19.dp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---- 个人信息卡：头像 + 姓名 + 登录态 ----
        GlassSurface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !loggedIn, onClick = onOpenLogin)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 头像：登录取用户名首字符，未登录给通用人形
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Palette.Brand),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loggedIn && userName.isNotBlank()) {
                        Text(
                            text = userName.trim().firstOrNull()?.uppercase() ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Palette.AppBg,
                        )
                    } else {
                        DyIcon(icon = DyIcons.Person, tint = Palette.AppBg, size = 26.dp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (loggedIn) userName else "未登录",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                    )
                    Text(
                        text = if (loggedIn) "两个BT · 4K 高清线路已解锁" else "登录两个BT 解锁 4K 高清线路",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                    )
                }
                if (!loggedIn) {
                    GlassChip(text = "登录", selected = false, onClick = onOpenLogin)
                }
            }
        }

        // ---- 收藏 / 历史统计 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clickable { onOpenFavorites() },
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "$favCount",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                    )
                    Text(
                        text = "收藏",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextSecondary,
                    )
                }
            }
            GlassSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clickable { onOpenHistory() },
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "$histCount",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                    )
                    Text(
                        text = "观看历史",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextSecondary,
                    )
                }
            }
        }

        // ---- 主题风格（v5：跟随系统 / 浅色 / 深色，即时生效并落盘） ----
        GlassSurface(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = "主题风格",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextPrimary,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { m ->
                        GlassChip(
                            text = m.label,
                            selected = ThemePrefs.mode == m,
                            onClick = { ThemePrefs.set(m) },
                        )
                    }
                }
            }
        }

        // ---- 源状态入口 ----
        GlassSurface(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.SearchBg),
                    contentAlignment = Alignment.Center,
                ) {
                    DyIcon(icon = DyIcons.Refresh, tint = Palette.Brand, size = 18.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "源状态",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.TextPrimary,
                    )
                    Text(
                        text = "影片源启用与探测",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextSecondary,
                    )
                }
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextSecondary,
                )
            }
        }
    }
}
