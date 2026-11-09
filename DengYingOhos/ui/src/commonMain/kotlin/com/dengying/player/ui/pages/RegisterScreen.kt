package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.account.AhAuth
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * AH 账号体系注册页（文档 §4 / §5）。
 *
 * 字段与文案按接口文档落地：账号、密码（不少于 6 位）、重复密码、可选邀请码；
 * 请求体由 [AhAuth.register] 统一构造，附带稳定的 16 位 hex `device_id`。
 * 邀请码规则文案只作说明，不实现「分享 / 累计进度」这类尚未有端点的功能。
 */
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf("") }
    var success by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.AppBg)
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
            DyIcon(
                icon = DyIcons.ArrowBack,
                tint = Palette.TextPrimary,
                contentDescription = "返回",
                size = 26.dp,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(start = 4.dp, end = 10.dp),
            )
            Text(
                text = "创建账号",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))
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
                    text = "注册后可畅享记录云同步",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextPrimary,
                )
                Text(
                    text = "暂不支持找回，请牢记你的账号与密码",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(14.dp))
                val fieldColors = TextFieldDefaults.colors(
                    focusedContainerColor = Palette.SearchBg,
                    unfocusedContainerColor = Palette.SearchBg,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
                val fieldModifier = Modifier.fillMaxWidth().height(52.dp)
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it; feedback = "" },
                    placeholder = {
                        Text("请输入账号", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = fieldColors,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = fieldModifier,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; feedback = "" },
                    placeholder = {
                        Text("密码（不少于6位）", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    trailingIcon = {
                        Text(
                            text = if (showPassword) "隐藏" else "显示",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextSecondary,
                            modifier = Modifier
                                .clickable { showPassword = !showPassword }
                                .padding(horizontal = 10.dp),
                        )
                    },
                    colors = fieldColors,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = fieldModifier,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; feedback = "" },
                    placeholder = {
                        Text("重复新密码", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    colors = fieldColors,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = fieldModifier,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it; feedback = "" },
                    placeholder = {
                        Text("邀请码（选填）", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = fieldColors,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = fieldModifier,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Palette.Brand)
                        .clickable(enabled = !busy) {
                            scope.launch {
                                busy = true
                                feedback = ""
                                success = false
                                when (val outcome = AhAuth.register(account, password, confirmPassword, inviteCode)) {
                                    is AhAuth.Outcome.Ok -> {
                                        feedback = outcome.message
                                        success = true
                                    }
                                    is AhAuth.Outcome.Error -> feedback = outcome.message
                                }
                                busy = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Palette.AppBg,
                        )
                    } else {
                        Text(
                            text = "注 册",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.AppBg,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // ---- 邀请码规则（文档 §5 的规则文案，仅展示，不实现未文档化的端点） ----
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
            ) {
                Text(
                    text = "分享我的邀请码",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextPrimary,
                )
                Text(
                    text = "邀请新用户完成注册并验证即可累计进度",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                listOf(
                    "同一账号只计算 1 次",
                    "新用户需在注册 3 天内完成验证",
                    "新用户必须使用不同的设备登录",
                ).forEach { line ->
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        Text(
                            text = "· ",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextSecondary,
                        )
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextSecondary,
                        )
                    }
                }
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
