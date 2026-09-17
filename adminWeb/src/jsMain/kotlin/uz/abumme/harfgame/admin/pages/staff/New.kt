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
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.ButtonKind
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.QuietButtonStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.forms.FieldErrors
import uz.abumme.harfgame.admin.forms.StaffFormState
import uz.abumme.harfgame.admin.forms.formFailure
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.data.api.ApiResult

@Page
@Composable
fun NewStaffPage() {
    RequireSession(PageAccess.STAFF) { me ->
        AdminShell(me, NavSection.STAFF) { NewStaffForm() }
    }
}

@Composable
private fun NewStaffForm() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(StaffFormState()) }
    var errors by remember { mutableStateOf<FieldErrors>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var languages by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        when (val result = AdminApp.api.languages()) {
            is ApiResult.Success -> languages = result.data
            is ApiResult.Error -> message = generalMessage(result)
        }
    }

    fun submit() {
        if (busy) return
        errors = state.validate(creating = true)
        message = null
        if (errors.isNotEmpty()) return
        busy = true
        scope.launch {
            when (val result = AdminApp.api.createStaff(state.toCreateRequest())) {
                is ApiResult.Success -> ctx.router.navigateTo("${Routes.STAFF}?created=${result.data.username}")
                is ApiResult.Error -> formFailure(result, StaffFormState.CREATE_FIELDS).let {
                    errors = it.fieldErrors
                    message = it.message
                }
            }
            busy = false
        }
    }

    PageHeader(Strings.Staff.NEW_TITLE)
    Form(attrs = PanelStyle.toModifier().css("padding" to "24px", "max-width" to "760px").toAttrs {
        attr("novalidate", "")
        onSubmit { event ->
            event.preventDefault()
            submit()
        }
    }) {
        Notice(message, NoticeTone.ERROR, Modifier.css("margin-bottom" to "18px"))
        StaffFields(state, { state = it }, languages, errors, creating = true, enabled = !busy)
        Div(Modifier.css("display" to "flex", "gap" to "10px", "margin-top" to "24px", "flex-wrap" to "wrap").toAttrs()) {
            ActionButton(Strings.Staff.CREATE, onClick = {}, kind = ButtonKind.PRIMARY, enabled = !busy, submit = true)
            Anchor(Routes.STAFF, QuietButtonStyle.toModifier().toAttrs()) { Text(Strings.Common.CANCEL) }
        }
    }
}
