package uz.abumme.harfgame.admin.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.AppGlobals
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.isExporting
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.CenteredStyle
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageTitleStyle
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.Wordmark
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.forms.loginMessage
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.admin.session.SessionStatus
import uz.abumme.harfgame.admin.session.homeRedirect
import uz.abumme.harfgame.data.admin.auth.LoginRequest
import uz.abumme.harfgame.data.api.ApiResult

val LoginColumnStyle = CssStyle.base {
    Modifier.css("width" to "min(380px, 100%)", "display" to "flex", "flex-direction" to "column", "gap" to "22px")
}

@Page
@Composable
fun LoginPage() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val next = ctx.route.params["next"]
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Already signed in (e.g. Back to the login page): go where the member was heading.
    LaunchedEffect(AdminApp.session.status) {
        val status = AdminApp.session.status
        if (!AppGlobals.isExporting && status is SessionStatus.SignedIn) {
            ctx.router.navigateTo(Routes.afterLogin(next, homeRedirect(status.me.permissions) ?: Routes.HOME), UpdateHistoryMode.REPLACE)
        }
    }

    fun submit() {
        if (busy || username.isBlank() || password.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            when (val result = AdminApp.api.login(LoginRequest(username, password))) {
                is ApiResult.Success -> {
                    password = ""
                    AdminApp.session.signedIn(result.data)
                    ctx.router.navigateTo(Routes.afterLogin(next, homeRedirect(result.data.permissions) ?: Routes.HOME), UpdateHistoryMode.REPLACE)
                }
                is ApiResult.Error -> {
                    // Keep the username; clear the password so the next attempt starts fresh.
                    password = ""
                    error = loginMessage(result)
                }
            }
            busy = false
        }
    }

    Div(CenteredStyle.toModifier().toAttrs()) {
        Div(LoginColumnStyle.toModifier().toAttrs()) {
            Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
                Wordmark(tileSize = 52)
                P(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.PANEL) }
            }
            Form(attrs = PanelStyle.toModifier().css("padding" to "24px", "display" to "flex", "flex-direction" to "column", "gap" to "16px").toAttrs {
                attr("method", "post")
                attr("novalidate", "")
                onSubmit { event ->
                    event.preventDefault()
                    submit()
                }
            }) {
                H1(PageTitleStyle.toModifier().css("font-size" to "21px").toAttrs()) { Text(Strings.Login.TITLE) }
                if (next != null && error == null) Notice(Strings.Login.EXPIRED, NoticeTone.INFO)
                Notice(error, NoticeTone.ERROR)
                TextField(
                    id = "username",
                    label = Strings.Login.USERNAME,
                    value = username,
                    onValueChange = { username = it },
                    autoComplete = "username",
                    enabled = !busy,
                )
                TextField(
                    id = "password",
                    label = Strings.Login.PASSWORD,
                    value = password,
                    onValueChange = { password = it },
                    password = true,
                    autoComplete = "current-password",
                    enabled = !busy,
                )
                ActionButton(
                    text = if (busy) Strings.Login.SUBMITTING else Strings.Login.SUBMIT,
                    onClick = {},
                    kind = ButtonKind.PRIMARY,
                    enabled = !busy,
                    submit = true,
                    modifier = Modifier.css("width" to "100%", "height" to "42px"),
                )
            }
        }
    }
}
