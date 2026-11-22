package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.account.AhAuth
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.GlassChip
import com.dengying.player.ui.components.GlassBackButton
import com.dengying.player.ui.components.GlassTextField
import com.dengying.player.ui.components.GlassActionButton
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.glassPageBackdrop
import com.dengying.player.ui.theme.Glass
import com.dengying.player.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * AH 账号体系登录页（文档 §4 / §5 的 UI 文案）。
 *
 * 「我的」页点「登录」跳到这里，而不是就地展开表单：登录/注册/QQ 三条路径
 * 与错误提示都需要比窗格更完整的纵向空间，且返回后「我的」页能重新读取
 * 登录态快照。全页沿用玻璃浮层 + Palette + DyIcons，不引第三方 SDK。
 *
 * 【release 无令牌】登录服务要求客户端持有编译期签名令牌（`app_auth_token`），
 * 目前只有 debug 通道带令牌。release 不渲染注定失败的账号密码表单，也不展示
 * 令牌说明/入口，直接告知「登录暂不可用」——与其让用户白填一遍再看报错，
 * 不如一开始就说清楚。表单与令牌设置只在 debug 构建渲染（`UiDeps.isDebug`）。
 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onOpenRegister: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isDebug = com.dengying.player.ui.UiDepsHolder.get().isDebug
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf("") }
    var success by rememberSaveable { mutableStateOf(false) }
    // 令牌设置：签名令牌（app_auth_token，300 秒内有效）与会话令牌（refresh_token，不需签名）
    var showTokenSheet by rememberSaveable { mutableStateOf(false) }
    var tokenDraft by rememberSaveable { mutableStateOf("") }
    var sessionDraft by rememberSaveable { mutableStateOf("") }
    // 【令牌状态镜像】提示文案要随保存动作刷新。直接读 AhAuth.savedAppAuthToken() 不是 State，
    // 保存后本页不会重组，会出现「已保存却仍提示未取得」的错位。
    var hasSavedToken by rememberSaveable { mutableStateOf(AhAuth.savedAppAuthToken().isNotEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 【透射源】纯色底换成柔光渐变层——玻璃件才有真实内容可透（见 GlassPageBackdrop）
            .glassPageBackdrop()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        // ---- 返回顶栏：与子页统一 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBackButton(onClick = onBack, modifier = Modifier.padding(end = 10.dp))
            Text(
                text = "登录",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(44.dp))
        }

        Spacer(Modifier.height(18.dp))
        // ---- 品牌区 ----
        GlassSurface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Palette.Brand),
                    contentAlignment = Alignment.Center,
                ) {
                    DyIcon(icon = DyIcons.Person, tint = Palette.AppBg, size = 30.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "账号 / 邮箱",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                )
                Text(
                    text = if (isDebug) "登录以继续探索精彩内容" else "现在不用登录也能看片",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (isDebug) {
            // ---- 登录表单 ----
            GlassSurface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    GlassTextField(
                        value = account,
                        onValueChange = { account = it; feedback = "" },
                        placeholder = "请输入账号",
                        modifier = Modifier.fillMaxWidth(),
                        imeAction = ImeAction.Next,
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassTextField(
                        value = password,
                        onValueChange = { password = it; feedback = "" },
                        placeholder = "请输入密码",
                        modifier = Modifier.fillMaxWidth(),
                        password = !showPassword,
                        imeAction = ImeAction.Done,
                        trailingIcon = if (showPassword) DyIcons.EyeOff else DyIcons.Eye,
                        onTrailingClick = { showPassword = !showPassword },
                    )
                    if (feedback.isNotEmpty()) {
                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (success) Palette.BrandSecondary else Palette.Danger,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    GlassActionButton(
                        text = "登 录",
                        onClick = {
                                scope.launch {
                                    busy = true
                                    feedback = "登录中.."
                                    success = false
                                    when (val outcome = AhAuth.login(account, password)) {
                                        is AhAuth.Outcome.Ok -> {
                                            feedback = outcome.message
                                            success = true
                                        }
                                        is AhAuth.Outcome.Error -> {
                                        // 原始消息含令牌/HTTP 码等内部信息，只进日志
                                        com.dengying.player.platform.Log.w(
                                            "DY/Auth",
                                            "auth failed: ${outcome.message}",
                                        )
                                        feedback = com.dengying.player.ui.account.authErrorMessage(outcome.message)
                                    }
                                    }
                                    busy = false
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        busy = busy,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassChip(text = "忘记密码", quiet = true, onClick = {
                            feedback = "暂不支持找回，请牢记你的账号与密码"
                            success = false
                        })
                        GlassChip(text = "注册", onClick = onOpenRegister)
                    }
                }
            }
        } else {
            // ---- release：登录通道未接通，别让用户白填一遍 ----
            GlassSurface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = com.dengying.player.ui.account.AUTH_UNAVAILABLE_TEXT,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.TextPrimary,
                    )
                    Text(
                        text = "不用登录也能搜索和播放。",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // ---- 其他登录方式 ----
        GlassSurface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "其他登录方式",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                GlassSurface(
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    fill = Glass.Fill,
                    elevation = 6.dp,
                    liquid = false,
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                // FR-615/617：入口和图标保留，但 QQ 授权这一步还没接，
                                // 只说结论；接口路径与 appid 不进界面。
                                feedback = "QQ 登录暂未开放"
                                success = false
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DyIcon(
                            icon = DyIcons.QQ,
                            tint = Color.Unspecified,
                            size = 30.dp,
                            contentDescription = "QQ 登录",
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = "QQ 登录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextPrimary,
                        )
                    }
                }
            }
        }

        // 令牌说明与「令牌设置」只在 debug 出现：release 没有构建期令牌，
        // 让用户填一个注定无效的令牌只会换来一串看不懂的报错。
        if (isDebug) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (!hasSavedToken) {
                    "登录服务要求签名令牌（app_auth_token），未取得时无法登录或注册；已登录设备可用会话令牌（refresh_token）直接恢复"
                } else {
                    "已保存签名令牌（只在 300 秒内有效，过期需重新获取）"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            GlassChip(
                text = "令牌设置",
                quiet = true,
                onClick = {
                    tokenDraft = AhAuth.savedAppAuthToken()
                    sessionDraft = ""
                    showTokenSheet = true
                },
            )
        }
    }

    if (showTokenSheet && isDebug) {
        AlertDialog(
            onDismissRequest = { showTokenSheet = false },
            containerColor = Palette.CardBg,
            title = {
                Text("登录服务令牌", style = MaterialTheme.typography.titleSmall)
            },
            text = {
                Column {
                    Text(
                        "签名令牌 app_auth_token（与 ts 绑定，300 秒内有效）",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassTextField(
                        value = tokenDraft,
                        onValueChange = { tokenDraft = it },
                        placeholder = "粘贴 app_auth_token（可留空）",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "会话令牌 refresh_token（不需要签名，换新访问令牌）",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassTextField(
                        value = sessionDraft,
                        onValueChange = { sessionDraft = it },
                        placeholder = "粘贴 refresh_token（可留空）",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tokenDraft.isNotBlank()) AhAuth.submitAppAuthToken(tokenDraft)
                        hasSavedToken = AhAuth.savedAppAuthToken().isNotEmpty()
                        if (sessionDraft.isNotBlank()) {
                            scope.launch {
                                busy = true
                                feedback = "登录中.."
                                success = false
                                when (val outcome = AhAuth.submitRefreshToken(sessionDraft)) {
                                    is AhAuth.Outcome.Ok -> {
                                        feedback = outcome.message
                                        success = true
                                    }
                                    is AhAuth.Outcome.Error -> {
                                        // 原始消息含令牌/HTTP 码等内部信息，只进日志
                                        com.dengying.player.platform.Log.w(
                                            "DY/Auth",
                                            "auth failed: ${outcome.message}",
                                        )
                                        feedback = com.dengying.player.ui.account.authErrorMessage(outcome.message)
                                    }
                                }
                                busy = false
                            }
                        } else {
                            feedback = if (tokenDraft.isNotBlank()) {
                                "已保存签名令牌，请立即点「登 录」（300 秒内有效）"
                            } else {
                                "未填写任何令牌"
                            }
                            success = false
                        }
                        showTokenSheet = false
                    },
                ) {
                    Text(if (sessionDraft.isNotBlank()) "保存并登录" else "保存", color = Palette.Brand)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenSheet = false }) {
                    Text("取消", color = Palette.TextSecondary)
                }
            },
        )
    }
}
