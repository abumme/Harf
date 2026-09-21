package uz.abumme.harfgame.admin.pages.staff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.Anchor
import com.varabyte.kobweb.silk.components.icons.lucide.LucideChevronLeft
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.api.fieldError
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.ConfirmDialog
import uz.abumme.harfgame.admin.components.HiddenUsernameField
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.SectionTitleStyle
import uz.abumme.harfgame.admin.components.TextField
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.forms.FieldErrors
import uz.abumme.harfgame.admin.forms.StaffFormState
import uz.abumme.harfgame.admin.forms.formFailure
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.pages.StaffStateBadges
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.FieldReasons
import uz.abumme.harfgame.data.admin.StaffRules
import uz.abumme.harfgame.data.admin.staff.StaffDto
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import uz.abumme.harfgame.data.api.ApiResult

/** `/staff/edit?id=…`: a query parameter rather than a dynamic route, so the page is exported like every other. */
@Page
@Composable
fun EditStaffPage() {
    RequireSession(PageAccess.STAFF) { me ->
        AdminShell(me, NavSection.STAFF) { EditStaff(rememberPageContext().route.params["id"].orEmpty()) }
    }
}

private enum class PendingAction { RESET_PASSWORD, DISABLE, ENABLE }

@Composable
private fun EditStaff(id: String) {
    val scope = rememberCoroutineScope()
    var staff by remember { mutableStateOf<StaffDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var languages by remember { mutableStateOf<List<String>>(emptyList()) }
    var state by remember { mutableStateOf(StaffFormState()) }
    var errors by remember { mutableStateOf<FieldErrors>(emptyMap()) }
    var formMessage by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingAction?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        when (val result = AdminApp.api.staff(id)) {
            is ApiResult.Success -> {
                staff = result.data
                state = StaffFormState.from(result.data)
            }
            is ApiResult.Error -> loadError = if (result.code == AdminErrors.NOT_FOUND) Strings.Staff.NOT_FOUND else generalMessage(result)
        }
        when (val result = AdminApp.api.languages()) {
            is ApiResult.Success -> languages = result.data
            is ApiResult.Error -> {}
        }
    }

    val member = staff
    PageHeader(member?.username ?: Strings.Staff.EDIT_TITLE, member?.displayName) {
        Anchor(Routes.STAFF, Modifier.css("display" to "inline-flex", "align-items" to "center", "gap" to "6px", "color" to Tokens.MUTED).toAttrs()) {
            LucideChevronLeft()
            Text(Strings.Staff.TITLE)
        }
    }
    if (member == null) {
        Notice(loadError ?: Strings.Common.LOADING, if (loadError != null) NoticeTone.ERROR else NoticeTone.INFO)
        return
    }

    fun save() {
        if (busy) return
        errors = state.validate(creating = false)
        formMessage = null
        saved = null
        if (errors.isNotEmpty()) return
        busy = true
        scope.launch {
            when (val result = AdminApp.api.updateStaff(member.id, state.toUpdateRequest(member))) {
                is ApiResult.Success -> {
                    staff = result.data
                    state = StaffFormState.from(result.data)
                    saved = Strings.Staff.SAVED
                }
                is ApiResult.Error -> formFailure(result, StaffFormState.EDIT_FIELDS).let {
                    errors = it.fieldErrors
                    formMessage = it.message
                }
            }
            busy = false
        }
    }

    fun runPending(action: PendingAction) {
        busy = true
        actionError = null
        scope.launch {
            when (action) {
                PendingAction.RESET_PASSWORD -> when (val result = AdminApp.api.resetPassword(member.id, newPassword)) {
                    is ApiResult.Success -> {
                        newPassword = ""
                        saved = Strings.Staff.RESET_DONE
                        AdminApp.api.staff(member.id).let { if (it is ApiResult.Success) staff = it.data }
                    }
                    is ApiResult.Error -> actionError = result.fieldError()?.let { Strings.fieldReason("newPassword", it.reason) } ?: generalMessage(result)
                }
                PendingAction.DISABLE, PendingAction.ENABLE -> {
                    val result = if (action == PendingAction.DISABLE) AdminApp.api.disableStaff(member.id) else AdminApp.api.enableStaff(member.id)
                    when (result) {
                        is ApiResult.Success -> {
                            staff = result.data
                            saved = if (action == PendingAction.DISABLE) Strings.Staff.DISABLED_DONE else Strings.Staff.ENABLED_DONE
                        }
                        is ApiResult.Error -> actionError = generalMessage(result)
                    }
                }
            }
            busy = false
            if (actionError == null) pending = null
        }
    }

    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "18px", "max-width" to "760px").toAttrs()) {
        Notice(saved, NoticeTone.SUCCESS)
        Div(Modifier.css("display" to "flex", "gap" to "10px", "align-items" to "center", "flex-wrap" to "wrap").toAttrs()) {
            StaffStateBadges(member)
        }

        Form(attrs = PanelStyle.toModifier().css("padding" to "24px").toAttrs {
            attr("novalidate", "")
            onSubmit { event ->
                event.preventDefault()
                save()
            }
        }) {
            Notice(formMessage, NoticeTone.ERROR, Modifier.css("margin-bottom" to "18px"))
            StaffFields(state, { state = it }, languages, errors, creating = false, enabled = !busy)
            Div(Modifier.css("margin-top" to "24px").toAttrs()) {
                ActionButton(Strings.Common.SAVE, onClick = {}, kind = ButtonKind.PRIMARY, enabled = !busy, submit = true)
            }
        }

        fun askToResetPassword() {
            if (!StaffRules.isValidPassword(newPassword)) {
                passwordError = Strings.fieldReason("newPassword", FieldReasons.LENGTH)
            } else {
                actionError = null
                pending = PendingAction.RESET_PASSWORD
            }
        }

        Form(attrs = PanelStyle.toModifier().css("padding" to "24px", "display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs {
            attr("novalidate", "")
            attr("aria-labelledby", "password-section")
            onSubmit { event ->
                event.preventDefault()
                if (!busy) askToResetPassword()
            }
        }) {
            H2(SectionTitleStyle.toModifier().toAttrs { id("password-section") }) { Text(Strings.Staff.PASSWORD_SECTION) }
            HiddenUsernameField(member.username)
            TextField(
                id = "newPassword",
                label = Strings.Staff.NEW_PASSWORD,
                value = newPassword,
                onValueChange = { newPassword = it; passwordError = null },
                error = passwordError,
                hint = Strings.Staff.PASSWORD_HINT,
                password = true,
                autoComplete = "new-password",
                enabled = !busy,
            )
            Div {
                ActionButton(Strings.Staff.RESET_PASSWORD, onClick = {}, enabled = !busy, submit = true)
            }
        }

        Section(PanelStyle.toModifier().css("padding" to "24px", "display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
            H2(SectionTitleStyle.toModifier().toAttrs()) { Text(Strings.Staff.ACCESS_SECTION) }
            if (member.status == StaffStatus.ACTIVE) {
                Div { ActionButton(Strings.Staff.DISABLE, onClick = { actionError = null; pending = PendingAction.DISABLE }, kind = ButtonKind.DANGER, enabled = !busy) }
            } else {
                P(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Staff.DISABLED_NOTE) }
                Div { ActionButton(Strings.Staff.ENABLE, onClick = { actionError = null; pending = PendingAction.ENABLE }, enabled = !busy) }
            }
        }
    }

    when (pending) {
        PendingAction.RESET_PASSWORD -> ConfirmDialog(
            title = Strings.Staff.RESET_CONFIRM_TITLE,
            body = Strings.Staff.RESET_CONFIRM_BODY,
            confirmLabel = Strings.Staff.RESET_PASSWORD,
            onConfirm = { runPending(PendingAction.RESET_PASSWORD) },
            onDismiss = { pending = null },
            busy = busy,
            extra = { Notice(actionError, NoticeTone.ERROR) },
        )
        PendingAction.DISABLE -> ConfirmDialog(
            title = Strings.Staff.DISABLE_CONFIRM_TITLE,
            body = Strings.Staff.DISABLE_CONFIRM_BODY,
            confirmLabel = Strings.Staff.DISABLE,
            onConfirm = { runPending(PendingAction.DISABLE) },
            onDismiss = { pending = null },
            danger = true,
            busy = busy,
            extra = { Notice(actionError, NoticeTone.ERROR) },
        )
        PendingAction.ENABLE -> ConfirmDialog(
            title = Strings.Staff.ENABLE_CONFIRM_TITLE,
            body = Strings.Staff.ENABLE_CONFIRM_BODY,
            confirmLabel = Strings.Staff.ENABLE,
            onConfirm = { runPending(PendingAction.ENABLE) },
            onDismiss = { pending = null },
            busy = busy,
            extra = { Notice(actionError, NoticeTone.ERROR) },
        )
        null -> {}
    }
}
