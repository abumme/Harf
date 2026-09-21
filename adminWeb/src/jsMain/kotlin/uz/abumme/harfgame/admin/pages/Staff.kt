package uz.abumme.harfgame.admin.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.Anchor
import com.varabyte.kobweb.silk.components.icons.lucide.LucidePlus
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.Badge
import uz.abumme.harfgame.admin.components.DataTable
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PrimaryButtonStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.TableColumn
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.components.formatDateTime
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.data.admin.Role
import uz.abumme.harfgame.data.admin.staff.StaffDto
import uz.abumme.harfgame.data.admin.staff.StaffStatus
import uz.abumme.harfgame.data.api.ApiResult

@Page
@Composable
fun StaffPage() {
    RequireSession(PageAccess.STAFF) { me ->
        AdminShell(me, NavSection.STAFF) { StaffList() }
    }
}

@Composable
private fun StaffList() {
    val ctx = rememberPageContext()
    var staff by remember { mutableStateOf<List<StaffDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // A confirmation carried over from the create form (`?created=username`).
    val created = ctx.route.params["created"]

    LaunchedEffect(Unit) {
        when (val result = AdminApp.api.staffList()) {
            is ApiResult.Success -> staff = result.data
            is ApiResult.Error -> error = generalMessage(result)
        }
        loading = false
    }

    PageHeader(Strings.Staff.TITLE) {
        Anchor(Routes.STAFF_NEW, PrimaryButtonStyle.toModifier().toAttrs()) {
            LucidePlus()
            Text(Strings.Staff.ADD)
        }
    }
    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
        if (created != null) Notice(Strings.Staff.CREATED, NoticeTone.SUCCESS)
        Notice(error, NoticeTone.ERROR)
        DataTable(
            caption = Strings.Staff.TITLE,
            columns = staffColumns(),
            rows = staff,
            emptyText = if (error != null) "" else Strings.Staff.EMPTY,
            loading = loading,
        )
    }
}

private fun staffColumns(): List<TableColumn<StaffDto>> = listOf(
    TableColumn(Strings.Staff.COLUMN_USERNAME) { member ->
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "2px").toAttrs()) {
            Anchor(Routes.staffEdit(member.id), Modifier.css("color" to Tokens.INK, "font-weight" to "650").toAttrs()) {
                Text(member.username)
            }
            member.displayName?.let { Span(MutedTextStyle.toModifier().toAttrs()) { Text(it) } }
        }
    },
    TableColumn(Strings.Staff.COLUMN_ROLE) { member ->
        Badge(Strings.Roles.label(member.role), TileTone.OPEN)
    },
    TableColumn(Strings.Staff.COLUMN_STATUS) { member -> StaffStateBadges(member) },
    TableColumn(Strings.Staff.COLUMN_LANGUAGES) { member ->
        Text(if (member.role == Role.ADMIN) Strings.Account.ALL_LANGUAGES else Strings.Languages.list(member.languages))
    },
    TableColumn(Strings.Staff.COLUMN_TELEGRAM, numeric = true) { member ->
        Text(member.telegramUserId?.toString() ?: Strings.Common.NOT_SET)
    },
    TableColumn(Strings.Staff.COLUMN_LAST_LOGIN) { member ->
        Text(member.lastLoginAt?.let(::formatDateTime) ?: Strings.Staff.NEVER_SIGNED_IN)
    },
)

/** Status in tile colors: active green, disabled grey, and an amber tile while sign-in is locked. */
@Composable
fun StaffStateBadges(member: StaffDto) {
    Div(Modifier.css("display" to "flex", "gap" to "6px", "flex-wrap" to "wrap").toAttrs()) {
        Badge(
            Strings.Staff.status(member.status),
            if (member.status == StaffStatus.ACTIVE) TileTone.CORRECT else TileTone.ABSENT,
        )
        member.lockedUntil?.let { until ->
            Span(Modifier.toAttrs { attr("title", Strings.Staff.lockedUntil(formatDateTime(until))) }) {
                Badge(Strings.Staff.LOCKED, TileTone.PRESENT)
            }
        }
    }
}
