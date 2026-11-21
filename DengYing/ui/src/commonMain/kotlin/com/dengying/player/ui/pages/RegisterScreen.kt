package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.account.AhAuth
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.GlassBackButton
import com.dengying.player.ui.components.GlassTextField
import com.dengying.player.ui.components.GlassActionButton
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.glassPageBackdrop
import com.dengying.player.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * AH 账号体系注册页（文档 §4 / §5）。
 *
 * 字段按接口文档落地：账号、密码（不少于 6 位）、重复密码；请求体由
 * [AhAuth.register] 统一构造，附带稳定的 16 位 hex `device_id`。
 *
 * 【暂无邀请码 UI】`AhAuth.register` 仍保留 `inviteCode` 参数（服务端字段存在、
 * 以后要用），但当前不渲染输入框和邀请进度规则——没有端点支撑的规则文案
 * 只会让人以为有积分可攒。
 *
 * 【release 无令牌】与登录页同理：release 显示「登录暂不可用」，不渲染表单。
 */
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isDebug = com.dengying.player.ui.UiDepsHolder.get().isDebug
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf("") }
    var success by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 【透射源】纯色底换成柔光渐变层——玻璃件才有真实内容可透（见 GlassPageBackdrop）
            .glassPageBackdrop()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBackButton(onClick = onBack, modifier = Modifier.padding(end = 10.dp))
            Text(
                text = "创建账号",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))
        if (isDebug) {
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
                    Text(
                        text = "账号用于登录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.TextPrimary,
                    )
                    Text(
                        text = "本机保存登录状态和观看记录，暂不支持找回密码",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(14.dp))
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
                        placeholder = "密码（不少于6位）",
                        modifier = Modifier.fillMaxWidth(),
                        password = !showPassword,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                        trailingIcon = if (showPassword) DyIcons.EyeOff else DyIcons.Eye,
                        onTrailingClick = { showPassword = !showPassword },
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; feedback = "" },
                        placeholder = "重复新密码",
                        modifier = Modifier.fillMaxWidth(),
                        password = !showPassword,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    )
                    Spacer(Modifier.height(10.dp))
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
                        text = "注 册",
                        onClick = {
                                scope.launch {
                                    busy = true
                                    feedback = ""
                                    success = false
                                    // 邀请码字段暂不展示，服务端字段保留：传空串＝请求体不附带
                                    when (
                                        val outcome = AhAuth.register(
                                            account,
                                            password,
                                            confirmPassword,
                                            "",
                                        )
                                    ) {
                                        is AhAuth.Outcome.Ok -> {
                                            feedback = outcome.message
                                            success = true
                                        }
                                        is AhAuth.Outcome.Error -> {
                                            // 原始消息含令牌/HTTP 码等内部信息，只进日志
                                            com.dengying.player.platform.Log.w(
                                                "DY/Auth",
                                                "register failed: ${outcome.message}",
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
    }
}
