package com.jianke.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jianke.schedule.data.CredentialStore
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「自动更新」的账号设置。
 *
 * 这里第一次把学校账号落到本机：写盘前经 [CredentialStore] 用
 * Android Keystore 的 AES-GCM 加密，明文不落盘、不进日志。
 * 打码平台（图鉴）的账号是可选的——不填就走「TGT 续期」，不消耗打码次数。
 */
@Composable
fun AccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val savedAccount = remember { CredentialStore.account(context) }
    val savedSolver = remember { CredentialStore.solver(context) }

    var user by remember { mutableStateOf(savedAccount?.username.orEmpty()) }
    var pass by remember { mutableStateOf(savedAccount?.password.orEmpty()) }
    var solverUser by remember { mutableStateOf(savedSolver?.first.orEmpty()) }
    var solverPass by remember { mutableStateOf(savedSolver?.second.orEmpty()) }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "自动更新设置", style = MiuixTheme.textStyles.title3)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "账号只保存在本机（加密存储），仅用于直接连学校接口拉课表。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(14.dp))

                Field("学号", user, false) { user = it }
                Spacer(Modifier.height(10.dp))
                Field("统一身份认证密码", pass, true) { pass = it }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "打码平台（图鉴，可选）",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(8.dp))
                Field("图鉴用户名", solverUser, false) { solverUser = it }
                Spacer(Modifier.height(10.dp))
                Field("图鉴密码", solverPass, true) { solverPass = it }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "不填也能用：拿到 TGT 后靠它续期，不需要验证码；只有 TGT 过期时才需要打码。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )

                if (error.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        if (user.isBlank() || pass.isBlank()) {
                            error = "学号和密码不能为空"
                            return@Button
                        }
                        CredentialStore.saveAccount(context, user, pass)
                        if (solverUser.isNotBlank() && solverPass.isNotBlank()) {
                            CredentialStore.saveSolver(context, solverUser, solverPass)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "保存", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    text = "清除已保存的账号",
                    onClick = {
                        CredentialStore.clearAll(context)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    secret: Boolean,
    onChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            ),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** 无头更新的进度 / 结果提示。旧课表在这期间一直留在内存里，不换界面。 */
@Composable
fun AutoUpdateDialog(text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = text, style = MiuixTheme.textStyles.body2)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "知道了", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
