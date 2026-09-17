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
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.style.until
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.api.AuditQuery
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.DataTable
import uz.abumme.harfgame.admin.components.DateField
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PagingState
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.SelectField
import uz.abumme.harfgame.admin.components.TableColumn
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.components.endOfLocalDay
import uz.abumme.harfgame.admin.components.formatDateTime
import uz.abumme.harfgame.admin.components.startOfLocalDay
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.auditActionsFor
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.audit.ActorKind
import uz.abumme.harfgame.data.admin.audit.AuditActions
import uz.abumme.harfgame.data.admin.audit.AuditEntryDto
import uz.abumme.harfgame.data.admin.audit.AuditParams
import uz.abumme.harfgame.data.admin.audit.AuditTargets
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.api.ApiResult

val AuditFiltersStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "grid",
            "grid-template-columns" to "repeat(auto-fit, minmax(170px, 1fr))",
            "gap" to "14px",
            "padding" to "16px",
            "align-items" to "end",
        )
    }
    until(Breakpoint.SM) { Modifier.css("grid-template-columns" to "minmax(0, 1fr)") }
}

/** `/audit`: the whole log for an ADMIN, the member's own activity (no actor filter) for a WORDER. */
@Page
@Composable
fun AuditPage() {
    RequireSession(PageAccess.AUDIT) { me ->
        val all = Permission.AUDIT_READ_ALL in me.permissions
        AdminShell(me, if (all) NavSection.AUDIT_LOG else NavSection.ACTIVITY) { AuditLog(me, all) }
    }
}

private data class AuditFilters(
    val actor: String = "",
    val action: String = "",
    val lang: String = "",
    val from: String = "",
    val to: String = "",
)

@Composable
private fun AuditLog(me: MeDto, all: Boolean) {
    var filters by remember { mutableStateOf(AuditFilters()) }
    var paging by remember { mutableStateOf(PagingState(size = AuditParams.DEFAULT_SIZE)) }
    var entries by remember { mutableStateOf<List<AuditEntryDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actors by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var languages by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (all) {
            val staff = AdminApp.api.staffList()
            if (staff is ApiResult.Success) actors = staff.data.map { it.id to (it.displayName?.let { name -> "$name (${it.username})" } ?: it.username) }
        }
        val langs = AdminApp.api.languages()
        if (langs is ApiResult.Success) languages = langs.data
    }

    LaunchedEffect(filters, paging.page) {
        loading = true
        val query = AuditQuery(
            actor = filters.actor.ifEmpty { null }.takeIf { all },
            action = filters.action.ifEmpty { null },
            lang = filters.lang.ifEmpty { null },
            from = startOfLocalDay(filters.from),
            to = endOfLocalDay(filters.to),
            page = paging.page,
            size = paging.size,
        )
        when (val result = AdminApp.api.audit(query)) {
            is ApiResult.Success -> {
                entries = result.data.items
                paging = paging.withTotal(result.data.total)
                error = null
            }
            is ApiResult.Error -> {
                entries = emptyList()
                error = generalMessage(result)
            }
        }
        loading = false
    }

    fun change(update: AuditFilters) {
        filters = update
        paging = paging.resetForFilterChange()
    }

    PageHeader(if (all) Strings.Audit.TITLE else Strings.Audit.ACTIVITY_TITLE)
    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "14px").toAttrs()) {
        Div(PanelStyle.toModifier().then(AuditFiltersStyle.toModifier()).toAttrs { attr("role", "search") }) {
            if (all) {
                SelectField("filter-actor", Strings.Audit.FILTER_ACTOR, filters.actor, listOf("" to Strings.Audit.ANY) + actors, { change(filters.copy(actor = it)) })
            }
            SelectField(
                "filter-action",
                Strings.Audit.FILTER_ACTION,
                filters.action,
                listOf("" to Strings.Audit.ANY) + auditActionsFor(me.permissions).map { it to Strings.Audit.action(it) },
                { change(filters.copy(action = it)) },
            )
            SelectField(
                "filter-lang",
                Strings.Audit.FILTER_LANG,
                filters.lang,
                listOf("" to Strings.Audit.ANY) + languages.map { it to Strings.Languages.label(it) },
                { change(filters.copy(lang = it)) },
            )
            DateField("filter-from", Strings.Audit.FILTER_FROM, filters.from, { change(filters.copy(from = it)) })
            DateField("filter-to", Strings.Audit.FILTER_TO, filters.to, { change(filters.copy(to = it)) })
            if (filters != AuditFilters()) {
                Div { ActionButton(Strings.Audit.RESET_FILTERS, onClick = { change(AuditFilters()) }) }
            }
        }
        Notice(error, NoticeTone.ERROR)
        DataTable(
            caption = if (all) Strings.Audit.TITLE else Strings.Audit.ACTIVITY_TITLE,
            columns = auditColumns(all),
            rows = entries,
            emptyText = if (error != null) "" else Strings.Audit.EMPTY,
            loading = loading,
            paging = paging,
            onPageChange = { paging = it },
        )
    }
}

private fun auditColumns(all: Boolean): List<TableColumn<AuditEntryDto>> = buildList {
    add(TableColumn(Strings.Audit.COLUMN_TIME) { entry ->
        Span(Modifier.css("white-space" to "nowrap").toAttrs()) { Text(formatDateTime(entry.at)) }
    })
    if (all) add(TableColumn(Strings.Audit.COLUMN_ACTOR) { entry -> Text(actorLabel(entry)) })
    add(TableColumn(Strings.Audit.COLUMN_ACTION) { entry -> Text(Strings.Audit.action(entry.action)) })
    add(TableColumn(Strings.Audit.COLUMN_TARGET) { entry ->
        Text(entry.targetLabel ?: entry.targetId?.takeIf { entry.targetType != AuditTargets.STAFF } ?: Strings.Common.NOT_SET)
    })
    add(TableColumn(Strings.Audit.COLUMN_LANG) { entry -> Text(entry.lang?.let(Strings.Languages::label) ?: Strings.Common.NOT_SET) })
    add(TableColumn(Strings.Audit.COLUMN_DETAILS) { entry -> Details(entry.details) })
}

private fun actorLabel(entry: AuditEntryDto): String = when (entry.actorKind) {
    ActorKind.SYSTEM -> Strings.Audit.SYSTEM
    ActorKind.TELEGRAM -> entry.actorUsername?.let { "${Strings.Audit.TELEGRAM}: $it" } ?: Strings.Audit.TELEGRAM
    ActorKind.STAFF -> entry.actorUsername ?: Strings.Audit.DELETED_ACTOR
}

/** `{"field": {"from": …, "to": …}}` as one line per field; plain facts as `field: value`. */
@Composable
private fun Details(details: JsonObject?) {
    if (details == null || details.isEmpty()) {
        Span(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Common.NOT_SET) }
        return
    }
    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "2px", "font-size" to "13px").toAttrs()) {
        details.forEach { (field, value) ->
            val change = value as? JsonObject
            val text = if (change != null && change.keys == setOf("from", "to")) {
                "${Strings.Audit.detailField(field)}: ${display(change.getValue("from"))} → ${display(change.getValue("to"))}"
            } else {
                "${Strings.Audit.detailField(field)}: ${display(value)}"
            }
            Span { Text(text) }
        }
    }
}

private fun display(value: JsonElement): String = when (value) {
    is JsonNull -> Strings.Common.NOT_SET
    is JsonPrimitive -> {
        val content = value.content
        if (Strings.Languages.label(content) != content) Strings.Languages.label(content) else Strings.Audit.detailValue(content)
    }
    is JsonArray -> if (value.isEmpty()) Strings.Common.NOT_SET else value.joinToString(", ") { display(it) }
    is JsonObject -> value.toString()
}
