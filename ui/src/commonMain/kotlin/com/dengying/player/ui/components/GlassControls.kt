package com.dengying.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.theme.Glass
import com.dengying.player.ui.theme.Palette

/**
 * 统一玻璃交互控件（v1.2.7 视觉归并）。
 *
 * 此前返回箭头、输入框、主按钮、清空按钮在 8 个页面各写一份：灰底、墨色实底、
 * 裸字形混着用，改一处就要改八处。这里按「圆环玻璃 / 玻璃输入框 / 玻璃动作按钮」
 * 三种原子件收口，页面只传文案与事件；玻璃 token 统一走 [GlassSurface]，不复制
 * 颜色与描边。内容卡一律 liquid=false——全屏采样会把滚动帧率拖穿，见
 * [GlassBackdrop.kt] 的注释。
 *
 * 热区按共享域约定的 MIN_TOUCH_DP（44dp）取，视觉尺寸与热区解耦；
 * ui 模块不直接依赖 shared 的 domain 包，这里保持同一数值即可。
 */
private val ControlSize = 44.dp
private val ControlShape = RoundedCornerShape(16.dp)
private val CircleGlassShape = CircleShape

/**
 * 圆环玻璃图标按钮：返回、设置、清空等单点动作共用。
 *
 * @param dark 播放器暗玻璃控制层用：面改 Glass.DarkFill，图标固定白色。
 */
@Composable
fun GlassCircleButton(
    icon: DyIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    tint: Color = if (dark) Color.White else Palette.TextPrimary,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    enabled: Boolean = true,
) {
    GlassSurface(
        modifier = modifier
            .size(ControlSize)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = MutableInteractionSource(),
                onClick = onClick,
            ),
        shape = CircleGlassShape,
        fill = if (dark) Glass.DarkFill else Glass.Fill,
        elevation = 6.dp,
        liquid = true,
    ) {
        DyIcon(
            icon = icon,
            tint = tint,
            size = iconSize,
            contentDescription = contentDescription,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** 统一的返回圆环：所有二级页顶栏都用它，不再各自画裸箭头。 */
@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    GlassCircleButton(
        icon = DyIcons.ArrowBack,
        contentDescription = "返回",
        onClick = onClick,
        modifier = modifier,
        dark = dark,
    )
}

/** 清空按钮：玻璃胶囊 + 圆环垃圾桶图标，一眼看出是破坏性操作。 */
@Composable
fun GlassClearButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassSurface(
        modifier = modifier
            .height(ControlSize)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = MutableInteractionSource(),
                onClick = onClick,
            ),
        // 【形状必须是圆角胶囊，不能是 CircleShape】宽度由内容撑开（图标 + 文字），
        // 用 CircleShape 时圆按**短边**（44dp 高）画，横向再长就变成「圆里塞着
        // 溢出的文字」——实测「清空」两个字直接戳出圆外。圆角半径取高度一半，
        // 单图标时仍是一个正圆，带文字时自然变成胶囊。
        shape = RoundedCornerShape(ControlSize / 2),
        fill = Glass.Fill,
        elevation = 6.dp,
        liquid = true,
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DyIcon(
                icon = DyIcons.ClearAll,
                tint = Palette.Danger,
                size = 16.dp,
                contentDescription = null,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Danger,
            )
        }
    }
}

/**
 * 玻璃输入框：搜索页 / 登录页 / 注册页共用。
 *
 * 容器色用 Glass.ChipFill（半透明）而不是 Palette.SearchBg（不透明灰），
 * 圆角与高度固定，外层 [GlassSurface] 提供细边与高光，避免「玻璃卡里再塞灰块」。
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onSearch: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    /** 尾部图标：与 [trailingText] 二选一，图标优先 */
    trailingIcon: DyIcon? = null,
) {
    GlassSurface(
        modifier = modifier.height(48.dp),
        shape = ControlShape,
        fill = Glass.ChipFill,
        elevation = 4.dp,
        liquid = true,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                )
            },
            singleLine = true,
            shape = ControlShape,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch?.invoke() },
                onDone = { onSearch?.invoke() },
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = Palette.TextPrimary,
                focusedTextColor = Palette.TextPrimary,
                unfocusedTextColor = Palette.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = when {
                trailingIcon != null && onTrailingClick != null -> {
                    {
                        DyIcon(
                            icon = trailingIcon,
                            tint = Palette.TextSecondary,
                            size = 20.dp,
                            modifier = Modifier
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = onTrailingClick,
                                )
                                .padding(horizontal = 12.dp),
                        )
                    }
                }
                trailingText != null && onTrailingClick != null -> {
                    {
                        Text(
                            text = trailingText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextSecondary,
                            modifier = Modifier
                                .clickable(onClick = onTrailingClick)
                                .padding(horizontal = 10.dp),
                        )
                    }
                }
                else -> null
            },
            modifier = Modifier
                .matchParentSize()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
    }
}

/**
 * 玻璃主按钮：搜索 / 登录 / 注册共用，48dp 与 [GlassTextField] 严格等高。
 *
 * 面与输入框、清空按钮同一套透射玻璃（[Glass.Fill] + liquid），文字用主色。
 * 不另做实色底：实色底在粉彩柔光上会变成一块不透的板，和旁边的输入框对不上。
 */
@Composable
fun GlassActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    GlassSurface(
        modifier = modifier
            .height(48.dp)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = MutableInteractionSource(),
                onClick = onClick,
            ),
        shape = ControlShape,
        // 主按钮用近实底：与输入框同为玻璃时，60% 白叠 60% 白只差 1~2 色阶，轮廓消失
        fill = Glass.ActionFill,
        elevation = 8.dp,
        // 主按钮不走液态采样：采样后的模糊会把描边和投影一起柔掉，边界又没了
        liquid = false,
    ) {
        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Glass.ActionText,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Glass.ActionText,
                )
            }
        }
    }
}
