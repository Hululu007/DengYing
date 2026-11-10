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
 * 【签名未取得】页面会真实调用 [AhAuth.login]（统一 HttpClient），服务端按
 * 文档返回 `403 Invalid app_auth_token` 时给出明确提示，绝不本地伪造成功。
 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onOpenRegister: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
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
                    text = "登录以继续探索精彩内容",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
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
                    trailingText = if (showPassword) "隐藏" else "显示",
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
                                    is AhAuth.Outcome.Error -> feedback = outcome.message
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
                                when (val qq = AhAuth.qqLoginNotReady()) {
                                    is AhAuth.Outcome.Ok -> feedback = qq.message
                                    is AhAuth.Outcome.Error -> feedback = qq.message
                                }
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
                Text(
                    text = "QQ 授权（appid 101983842）换取登录令牌的后端路径待实测",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "登录服务签名令牌（app_auth_token）尚未取得；当前请求会如实返回服务端校验结果",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
