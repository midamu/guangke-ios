package com.jianke.schedule.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 统一的小部件：卡片的圆角、按钮高度、输入框底色都收在这一个文件里。 */

private val CARD_CORNER = 16.dp
private val ROW_CORNER = 10.dp

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.body,
    color: Color? = null,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val palette = LocalPalette.current
    val merged = style.copy(
        color = color ?: if (style.color == Color.Unspecified) palette.text else style.color,
        textAlign = align ?: style.textAlign,
    )
    BasicText(text = text, modifier = modifier, style = merged, maxLines = maxLines)
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    // 有壁纸时半透明，让下面的照片透出来一点
    val surface = if (LocalWallpaperActive.current) palette.surface.copy(alpha = 0.9f) else palette.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(surface)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = Type.caption,
        color = LocalPalette.current.subText,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) palette.primary else palette.divider)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = Type.headline, color = Color.White, align = TextAlign.Center)
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val tint = if (danger) palette.danger else palette.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = Type.body, color = tint, align = TextAlign.Center)
    }
}

@Composable
fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalPalette.current.divider),
    )
}

/** 带标题的输入框。密码用圆点遮起来。 */
@Composable
fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    placeholder: String = "",
    onChange: (String) -> Unit,
) {
    val palette = LocalPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = Type.tiny, color = palette.subText)
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(ROW_CORNER))
                .background(palette.divider.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(text = placeholder, style = Type.body, color = palette.subText.copy(alpha = 0.6f))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Type.body.copy(color = palette.text),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 一行「标题 + 右侧说明」。 */
@Composable
fun InfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = Type.body)
        Box(Modifier.width(12.dp))
        Text(text = value, style = Type.body, color = LocalPalette.current.subText)
    }
}
