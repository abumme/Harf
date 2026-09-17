package uz.abumme.harfgame.admin.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.HiddenUsernameField
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.SectionTitleStyle
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.components.errorFor
import uz.abumme.harfgame.admin.forms.FieldErrors
import uz.abumme.harfgame.admin.forms.PasswordChangeState
import uz.abumme.harfgame.admin.forms.formFailure
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.auth.ChangePasswordRequest
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.api.ApiResult

@Page
@Composable
fun AccountPage() {
    RequireSession(PageAccess.ACCOUNT) { me ->
        AdminShell(me, NavSection.ACCOUNT) { Account(me) }
    }
}

@Composable
private fun Account(me: MeDto) {
    PageHeader(Strings.Account.TITLE)
    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "18px", "max-width" to "560px").toAttrs()) {
        Div(PanelStyle.toModifier().css(
            "padding" to "20px 24px",
            "margin" to "0",
            "display" to "grid",
            "grid-template-columns" to "max-content minmax(0, 1fr)",
            "gap" to "10px 24px",
        ).toAttrs()) {
            AccountFact(Strings.Account.USERNAME, me.username)
            AccountFact(Strings.Account.ROLE, Strings.Roles.label(me.role))
            AccountFact(
                Strings.Account.LANGUAGES,
                if (me.role == Role.ADMIN) Strings.Account.ALL_LANGUAGES else Strings.Languages.list(me.languages),
            )
        }
        PasswordChange(me.username)
    }
}

@Composable
private fun AccountFact(term: String, value: String) {
    Span(MutedTextStyle.toModifier().toAttrs()) { Text(term) }
    Span(Modifier.css("font-weight" to "550", "overflow-wrap" to "anywhere").toAttrs()) { Text(value) }
}

@Composable
private fun PasswordChange(username: String) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PasswordChangeState()) }
    var errors by remember { mutableStateOf<FieldErrors>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        done = false
        message = null
        errors = state.validate()
        // The two new passwords must match before anything is sent.
        if (errors.isNotEmpty()) return
        busy = true
        scope.launch {
            when (val result = AdminApp.api.changePassword(ChangePasswordRequest(state.currentPassword, state.newPassword))) {
                is ApiResult.Success -> {
                    state = PasswordChangeState()
                    done = true
                }
                is ApiResult.Error -> formFailure(result, PasswordChangeState.FIELDS).let {
                    errors = it.fieldErrors
                    message = it.message
                }
            }
            busy = false
        }
    }

    Form(attrs = PanelStyle.toModifier().css("padding" to "24px", "display" to "flex", "flex-direction" to "column", "gap" to "16px").toAttrs {
        attr("novalidate", "")
        onSubmit { event ->
            event.preventDefault()
            submit()
        }
    }) {
        H2(SectionTitleStyle.toModifier().toAttrs()) { Text(Strings.Account.CHANGE_PASSWORD) }
        P(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Account.CHANGE_PASSWORD_NOTE) }
        if (done) Notice(Strings.Account.DONE, NoticeTone.SUCCESS)
        Notice(message, NoticeTone.ERROR)
        HiddenUsernameField(username)
        TextField(
            id = "currentPassword",
            label = Strings.Account.CURRENT_PASSWORD,
            value = state.currentPassword,
            onValueChange = { state = state.copy(currentPassword = it) },
            error = errorFor(errors, "currentPassword"),
            password = true,
            autoComplete = "current-password",
            enabled = !busy,
        )
        TextField(
            id = "newPassword",
            label = Strings.Account.NEW_PASSWORD,
            value = state.newPassword,
            onValueChange = { state = state.copy(newPassword = it) },
            error = errorFor(errors, "newPassword"),
            hint = Strings.Staff.PASSWORD_HINT,
            password = true,
            autoComplete = "new-password",
            enabled = !busy,
        )
        TextField(
            id = "repeatPassword",
            label = Strings.Account.REPEAT_PASSWORD,
            value = state.repeatPassword,
            onValueChange = { state = state.copy(repeatPassword = it) },
            error = errorFor(errors, "repeatPassword"),
            password = true,
            autoComplete = "new-password",
            enabled = !busy,
        )
        Div {
            ActionButton(Strings.Account.SUBMIT, onClick = {}, kind = ButtonKind.PRIMARY, enabled = !busy, submit = true)
        }
    }
}
