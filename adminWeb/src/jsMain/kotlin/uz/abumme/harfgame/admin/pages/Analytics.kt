package uz.abumme.harfgame.admin.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.AppGlobals
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.silk.components.forms.Switch
import com.varabyte.kobweb.silk.components.icons.lucide.LucideDownload
import com.varabyte.kobweb.silk.components.layout.SimpleGrid
import com.varabyte.kobweb.silk.components.layout.numColumns
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.style.until
import kotlinx.serialization.KSerializer
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.analytics.AnalyticsFilter
import uz.abumme.harfgame.admin.analytics.csvHref
import uz.abumme.harfgame.admin.analytics.daysInRange
import uz.abumme.harfgame.admin.analytics.dimmedDays
import uz.abumme.harfgame.admin.analytics.formatCount
import uz.abumme.harfgame.admin.analytics.formatDecimal
import uz.abumme.harfgame.admin.analytics.formatDelta
import uz.abumme.harfgame.admin.analytics.formatDuration
import uz.abumme.harfgame.admin.analytics.formatPercent
import uz.abumme.harfgame.admin.analytics.lastClosedDay
import uz.abumme.harfgame.admin.analytics.nextWordSort
import uz.abumme.harfgame.admin.analytics.perDay
import uz.abumme.harfgame.admin.analytics.retentionShade
import uz.abumme.harfgame.admin.analytics.share
import uz.abumme.harfgame.admin.apiBase
import uz.abumme.harfgame.admin.components.ActionButton
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.Badge
import uz.abumme.harfgame.admin.components.DataTable
import uz.abumme.harfgame.admin.components.DateField
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.Notice
import uz.abumme.harfgame.admin.components.NoticeTone
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.QuietButtonStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.SectionTitleStyle
import uz.abumme.harfgame.admin.components.SelectField
import uz.abumme.harfgame.admin.components.TableColumn
import uz.abumme.harfgame.admin.components.TableSort
import uz.abumme.harfgame.admin.components.TileTone
import uz.abumme.harfgame.admin.components.Tokens
import uz.abumme.harfgame.admin.components.charts.BarChart
import uz.abumme.harfgame.admin.components.charts.ChartSeries
import uz.abumme.harfgame.admin.components.charts.Histogram
import uz.abumme.harfgame.admin.components.charts.LineChart
import uz.abumme.harfgame.admin.components.charts.StackedBarChart
import uz.abumme.harfgame.admin.components.charts.WIDE
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.forms.generalMessage
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.data.admin.analytics.AccountsDto
import uz.abumme.harfgame.data.admin.analytics.ActivityDto
import uz.abumme.harfgame.data.admin.analytics.AnalyticsTable
import uz.abumme.harfgame.data.admin.analytics.CohortDto
import uz.abumme.harfgame.data.admin.analytics.ContentDayDto
import uz.abumme.harfgame.data.admin.analytics.ContentDto
import uz.abumme.harfgame.data.admin.analytics.KpiDto
import uz.abumme.harfgame.data.admin.analytics.OutcomesDto
import uz.abumme.harfgame.data.admin.analytics.OverviewDto
import uz.abumme.harfgame.data.admin.analytics.RetentionDto
import uz.abumme.harfgame.data.admin.analytics.StaffActivityDto
import uz.abumme.harfgame.data.admin.analytics.StaffDayDto
import uz.abumme.harfgame.data.admin.analytics.StreaksDto
import uz.abumme.harfgame.data.admin.analytics.SuggestionDayDto
import uz.abumme.harfgame.data.admin.analytics.SuggestionsAnalyticsDto
import uz.abumme.harfgame.data.admin.analytics.TodaySoFarDto
import uz.abumme.harfgame.data.admin.analytics.WordDayDto
import uz.abumme.harfgame.data.admin.analytics.WordDifficultyDto
import uz.abumme.harfgame.data.admin.calendar.DaySource
import uz.abumme.harfgame.data.api.ApiResult
import kotlin.js.Date

val AnalyticsSectionStyle = CssStyle.base {
    Modifier.css("padding" to "18px 20px", "display" to "flex", "flex-direction" to "column", "gap" to "16px", "min-width" to "0")
}

val AnalyticsFiltersStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "grid",
            "grid-template-columns" to "repeat(auto-fit, minmax(170px, 1fr))",
            "gap" to "14px",
            "padding" to "16px",
            "align-items" to "end",
            "position" to "sticky",
            "top" to "0",
            "z-index" to "5",
        )
    }
    until(Breakpoint.MD) { Modifier.css("position" to "static") }
}

val KpiTileStyle = CssStyle.base {
    Modifier.css(
        "padding" to "14px 16px",
        "display" to "flex",
        "flex-direction" to "column",
        "gap" to "4px",
        "min-width" to "0",
        "border" to "1px solid ${Tokens.LINE}",
        "border-radius" to "8px",
        "background-color" to Tokens.SURFACE,
    )
}

val ChartGridStyle = CssStyle {
    base { Modifier.css("display" to "grid", "grid-template-columns" to "repeat(2, minmax(0, 1fr))", "gap" to "24px", "align-items" to "start") }
    until(Breakpoint.LG) { Modifier.css("grid-template-columns" to "minmax(0, 1fr)") }
}

/**
 * `/analytics` (ADMIN home): aggregated analytics for a date range and language. The filter lives in the query string,
 * so a reload or a shared link keeps it; a range the server would refuse is refused here before any request. Every
 * section loads on its own and offers its CSV export for the same filter.
 */
@Page
@Composable
fun AnalyticsPage() {
    RequireSession(PageAccess.ANALYTICS) { me ->
        AdminShell(me, NavSection.ANALYTICS) { Dashboard() }
    }
}

@Composable
private fun Dashboard() {
    val ctx = rememberPageContext()
    val lastClosed = remember { lastClosedDay(Date.now()) }
    val initial = remember { AnalyticsFilter.fromRoute(ctx.route.params, lastClosed) }
    var filter by remember { mutableStateOf(initial) }
    var applied by remember { mutableStateOf(initial.takeIf { it.isValid }) }
    var languages by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) {
        (AdminApp.api.languages() as? ApiResult.Success)?.let { languages = it.data }
    }
    LaunchedEffect(filter) {
        if (filter.isValid) {
            applied = filter
            ctx.router.navigateTo(Routes.ANALYTICS + filter.toRouteQuery(), UpdateHistoryMode.REPLACE)
        }
    }

    PageHeader(Strings.Analytics.TITLE, Strings.Analytics.SUBTITLE)
    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "18px").toAttrs()) {
        Div(PanelStyle.toModifier().then(AnalyticsFiltersStyle.toModifier()).toAttrs { attr("role", "search") }) {
            DateField("analytics-from", Strings.Analytics.FILTER_FROM, filter.from, { filter = filter.withFrom(it) })
            DateField("analytics-to", Strings.Analytics.FILTER_TO, filter.to, { filter = filter.withTo(it) })
            SelectField(
                "analytics-lang",
                Strings.Analytics.FILTER_LANGUAGE,
                filter.lang ?: AnalyticsFilter.ALL_LANGUAGES,
                listOf(AnalyticsFilter.ALL_LANGUAGES to Strings.Analytics.ALL_LANGUAGES) + languages.map { it to Strings.Languages.label(it) },
                { filter = filter.withLang(it) },
            )
            Div {
                ActionButton(
                    Strings.Analytics.DEFAULT_RANGE,
                    onClick = { filter = AnalyticsFilter.default(lastClosed).copy(lang = filter.lang) },
                )
            }
        }
        filter.problem?.let { Notice(Strings.Analytics.rangeProblem(it.reason), NoticeTone.ERROR) }

        OverviewSection()
        applied?.let { current ->
            Div(ChartGridStyle.toModifier().toAttrs()) {
                AccountsSection(current)
                ActivitySection(current)
            }
            RetentionSection(current)
            Div(ChartGridStyle.toModifier().toAttrs()) {
                StreaksSection(current)
                OutcomesSection(current)
            }
            WordsSection(current)
            SuggestionsSection(current)
            ContentSection(current)
            StaffSection(current)
        }
    }
}

// --- section frame -----------------------------------------------------------------------------------------------

/**
 * A titled panel that loads its data whenever [key] changes and shows [content] once it has it. [csv] adds the table's
 * download link; [notByLanguage] labels metrics the language filter does not apply to.
 */
@Composable
private fun <T> Section(
    title: String,
    key: Any?,
    load: suspend () -> ApiResult<T>,
    csv: String? = null,
    notByLanguage: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    var result by remember { mutableStateOf<ApiResult<T>?>(null) }
    LaunchedEffect(key) {
        result = null
        result = load()
    }
    Div(PanelStyle.toModifier().then(AnalyticsSectionStyle.toModifier()).toAttrs()) {
        Div(Modifier.css("display" to "flex", "justify-content" to "space-between", "align-items" to "center", "gap" to "10px", "flex-wrap" to "wrap").toAttrs()) {
            Div(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "10px", "flex-wrap" to "wrap").toAttrs()) {
                H2(SectionTitleStyle.toModifier().toAttrs()) { Text(title) }
                if (notByLanguage) Badge(Strings.Analytics.NOT_BY_LANGUAGE, TileTone.OPEN)
            }
            csv?.let { CsvLink(it) }
        }
        when (val current = result) {
            null -> P(MutedTextStyle.toModifier().toAttrs { attr("aria-busy", "true") }) { Text(Strings.Analytics.LOADING) }
            is ApiResult.Error -> Notice(generalMessage(current), NoticeTone.ERROR)
            is ApiResult.Success -> content(current.data)
        }
    }
}

/** A same-origin download of the table's CSV; the server names the file. */
@Composable
private fun CsvLink(href: String) {
    A(href = href, attrs = QuietButtonStyle.toModifier().css("height" to "32px", "font-size" to "13px").toAttrs {
        attr("download", "")
    }) {
        LucideDownload(Modifier.css("width" to "15px", "height" to "15px"))
        Text(Strings.Analytics.EXPORT_CSV)
    }
}

private fun csv(table: AnalyticsTable, filter: AnalyticsFilter, sort: TableSort? = null) = csvHref(AppGlobals.apiBase, table, filter, sort)

private suspend fun <T> load(table: AnalyticsTable, filter: AnalyticsFilter, serializer: KSerializer<T>, sort: TableSort? = null): ApiResult<T> =
    AdminApp.api.analytics(table, filter.queryFor(table, sort), serializer)

private val countFormat: (Double) -> String = { formatCount(it) }
private val percentFormat: (Double) -> String = { formatPercent(it / 100) }

// --- overview ------------------------------------------------------------------------------------------------------

@Composable
private fun OverviewSection() {
    Section(Strings.Analytics.OVERVIEW, key = Unit, load = { AdminApp.api.analyticsOverview() }) { overview: OverviewDto ->
        SimpleGrid(numColumns(base = 1, sm = 2, md = 4)) {
            Kpi(Strings.Analytics.KPI_DAU, overview.dau)
            Kpi(Strings.Analytics.KPI_WAU, overview.wau)
            Kpi(Strings.Analytics.KPI_MAU, overview.mau)
            Kpi(Strings.Analytics.KPI_GAMES, overview.games)
            Kpi(Strings.Analytics.KPI_WIN_RATE, overview.winRate, share = true)
            Kpi(Strings.Analytics.KPI_NEW_ACCOUNTS, overview.newAccounts)
            Kpi(Strings.Analytics.KPI_TOTAL_ACCOUNTS, overview.totalAccounts)
            Kpi(Strings.Analytics.KPI_BACKLOG, overview.backlog)
        }
        TodayTiles(overview.today)
    }
}

@Composable
private fun Kpi(label: String, kpi: KpiDto, share: Boolean = false) {
    Div(KpiTileStyle.toModifier().toAttrs()) {
        Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(label) }
        Span(Modifier.css("font-size" to "24px", "font-weight" to "700", "font-variant-numeric" to "tabular-nums").toAttrs {
            if (kpi.provisional) attr("title", Strings.Analytics.PROVISIONAL_NOTE)
        }) { Text(if (share) formatPercent(kpi.value) else formatCount(kpi.value)) }
        Span(MutedTextStyle.toModifier().css("font-size" to "12px").toAttrs()) {
            Text(Strings.Analytics.delta(formatDelta(kpi.value, kpi.previous, share)) + " · " + Strings.Analytics.dayOf(kpi.day))
        }
        if (kpi.provisional) Badge(Strings.Analytics.PROVISIONAL, TileTone.OPEN, Modifier.css("align-self" to "flex-start", "height" to "20px", "font-size" to "12px"))
    }
}

/** Today's partial values per language; a [wide] panel fits five tiles in a row. */
@Composable
private fun TodayTiles(today: List<TodaySoFarDto>, wide: Boolean = true) {
    if (today.isEmpty()) return
    SimpleGrid(if (wide) numColumns(base = 1, sm = 2, md = 5) else numColumns(base = 1, sm = 2)) {
        today.forEach { entry ->
            Div(KpiTileStyle.toModifier().css("background-color" to Tokens.SURFACE_SUNKEN).toAttrs()) {
                Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(Strings.Analytics.todayTile(entry.lang)) }
                Span(Modifier.css("font-size" to "18px", "font-weight" to "650").toAttrs()) {
                    Text(Strings.Analytics.todayValue(formatCount(entry.players), formatCount(entry.games)))
                }
                if (entry.partial) Badge(Strings.Analytics.PARTIAL, TileTone.PRESENT, Modifier.css("align-self" to "flex-start", "height" to "20px", "font-size" to "12px"))
            }
        }
    }
}

// --- accounts and activity ---------------------------------------------------------------------------------------

@Composable
private fun AccountsSection(filter: AnalyticsFilter) {
    Section(
        Strings.Analytics.ACCOUNTS,
        key = filter.queryFor(AnalyticsTable.ACCOUNTS),
        load = { load(AnalyticsTable.ACCOUNTS, filter, AccountsDto.serializer()) },
        csv = csv(AnalyticsTable.ACCOUNTS, filter),
        notByLanguage = filter.lang != null,
    ) { accounts ->
        val days = daysInRange(filter.from, filter.to)
        val byDay = accounts.days.associateBy { it.date }
        val dimmed = dimmedDays(days, accounts.days.filter { it.provisional }.map { it.date }.toSet())
        val labels = days.map(Strings.Analytics::shortDate)
        StackedBarChart(
            Strings.Analytics.ACCOUNTS_TOTALS,
            labels,
            listOf(
                ChartSeries(Strings.Analytics.LINKED, perDay(days, byDay) { it.linked?.toDouble() }, Tokens.CHART[0]),
                ChartSeries(Strings.Analytics.ANONYMOUS, perDay(days, byDay) { it.anonymous?.toDouble() }, Tokens.CHART[5]),
            ),
            dimmed,
            countFormat,
        )
        LineChart(
            Strings.Analytics.ACCOUNTS_FLOW,
            labels,
            listOf(
                ChartSeries(Strings.Analytics.NEW_ACCOUNTS, perDay(days, byDay) { it.newAccounts.toDouble() }, Tokens.CHART[2]),
                ChartSeries(Strings.Analytics.DELETIONS, perDay(days, byDay) { it.deletions.toDouble() }, Tokens.CHART[3]),
            ),
            dimmed,
            countFormat,
        )
        LineChart(
            Strings.Analytics.ACCOUNTS_LINKS,
            labels,
            listOf(
                ChartSeries(Strings.Analytics.GOOGLE, perDay(days, byDay) { it.linksGoogle?.toDouble() }, Tokens.CHART[2]),
                ChartSeries(Strings.Analytics.APPLE, perDay(days, byDay) { it.linksApple?.toDouble() }, Tokens.CHART[4]),
            ),
            dimmed,
            countFormat,
        )
        P(MutedTextStyle.toModifier().css("font-size" to "12px").toAttrs()) { Text(Strings.Analytics.ACCOUNTS_NOTE) }
    }
}

@Composable
private fun ActivitySection(filter: AnalyticsFilter) {
    Section(
        Strings.Analytics.ACTIVITY,
        key = filter.queryFor(AnalyticsTable.ACTIVITY),
        load = { load(AnalyticsTable.ACTIVITY, filter, ActivityDto.serializer()) },
        csv = csv(AnalyticsTable.ACTIVITY, filter),
    ) { activity ->
        val days = daysInRange(filter.from, filter.to)
        val labels = days.map(Strings.Analytics::shortDate)
        val languages = activity.languages.map { it.lang }.distinct().sorted()
        LineChart(
            Strings.Analytics.PLAYERS_BY_LANGUAGE,
            labels,
            languages.mapIndexed { index, lang ->
                val byDay = activity.languages.filter { it.lang == lang }.associateBy { it.day }
                ChartSeries(Strings.Languages.label(lang), perDay(days, byDay) { it.players.toDouble() }, Tokens.CHART[index % Tokens.CHART.size])
            },
            dimmedDays(days, activity.languages.filter { it.provisional }.map { it.day }.toSet()),
            countFormat,
        )
        val global = activity.global.associateBy { it.day }
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "6px").toAttrs()) {
            if (filter.lang != null) Badge(Strings.Analytics.NOT_BY_LANGUAGE, TileTone.OPEN, Modifier.css("align-self" to "flex-start"))
            LineChart(
                Strings.Analytics.ACTIVE_PLAYERS,
                labels,
                listOf(
                    ChartSeries("DAU", perDay(days, global) { it.dau.toDouble() }, Tokens.CHART[0]),
                    ChartSeries("WAU", perDay(days, global) { it.wau.toDouble() }, Tokens.CHART[1]),
                    ChartSeries("MAU", perDay(days, global) { it.mau.toDouble() }, Tokens.CHART[2]),
                ),
                dimmedDays(days, activity.global.filter { it.provisional }.map { it.day }.toSet()),
                countFormat,
            )
        }
        TodayTiles(activity.today, wide = false)
    }
}

// --- retention ---------------------------------------------------------------------------------------------------

@Composable
private fun RetentionSection(filter: AnalyticsFilter) {
    Section(
        Strings.Analytics.RETENTION,
        key = filter.queryFor(AnalyticsTable.RETENTION),
        load = { load(AnalyticsTable.RETENTION, filter, RetentionDto.serializer()) },
        csv = csv(AnalyticsTable.RETENTION, filter),
        notByLanguage = filter.lang != null,
    ) { retention ->
        P(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(Strings.Analytics.RETENTION_NOTE) }
        Div(Modifier.css("max-height" to "420px", "overflow-y" to "auto").toAttrs()) {
            DataTable(
                caption = Strings.Analytics.RETENTION,
                columns = listOf(
                    TableColumn<CohortDto>(Strings.Analytics.COLUMN_COHORT) { cohort -> DayCell(cohort.day, cohort.provisional) },
                    TableColumn(Strings.Analytics.COLUMN_SIZE, numeric = true) { cohort -> Text(formatCount(cohort.size)) },
                    TableColumn("D1", numeric = true) { cohort -> RetentionCell(cohort.d1, cohort.size) },
                    TableColumn("D7", numeric = true) { cohort -> RetentionCell(cohort.d7, cohort.size) },
                    TableColumn("D30", numeric = true) { cohort -> RetentionCell(cohort.d30, cohort.size) },
                ),
                rows = retention.cohorts.sortedByDescending { it.day },
                emptyText = Strings.Analytics.NO_DATA,
            )
        }
    }
}

/** A retention rate shaded by its value; "—" (unshaded) while the target day has not closed. */
@Composable
private fun RetentionCell(returned: Long?, size: Long) {
    // An empty cohort has no rate to show.
    val known = if (size == 0L) null else returned
    val rate = share(known, size)
    val shade = if (known == null) null else retentionShade(rate ?: 0.0)
    val alpha = listOf(0.0, 0.14, 0.28, 0.45, 0.65)
    Span(Modifier.css(
        "display" to "inline-block",
        "min-width" to "76px",
        "padding" to "2px 8px",
        "border-radius" to "4px",
        "background-color" to if (shade == null || shade == 0) "transparent" else "color-mix(in srgb, ${Tokens.CORRECT} ${(alpha[shade] * 100).toInt()}%, transparent)",
    ).toAttrs {
        if (returned == null) attr("title", Strings.Analytics.NOT_YET)
    }) {
        Text(if (known == null) Strings.Common.NOT_SET else "${formatPercent(rate)} (${formatCount(known)})")
    }
}

@Composable
private fun DayCell(day: String, provisional: Boolean) {
    Span(Modifier.css("white-space" to "nowrap").toAttrs()) {
        Text(day)
        if (provisional) Span(MutedTextStyle.toModifier().css("font-size" to "12px").toAttrs()) { Text(" · ${Strings.Analytics.PROVISIONAL}") }
    }
}

// --- streaks and outcomes ----------------------------------------------------------------------------------------

@Composable
private fun StreaksSection(filter: AnalyticsFilter) {
    Section(
        Strings.Analytics.STREAKS,
        key = filter.queryFor(AnalyticsTable.STREAKS),
        load = { load(AnalyticsTable.STREAKS, filter, StreaksDto.serializer()) },
        csv = csv(AnalyticsTable.STREAKS, filter),
    ) { streaks ->
        val lastDay = streaks.days.maxOfOrNull { it.day }
        if (lastDay == null) {
            P(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Analytics.NO_DATA) }
        } else {
            val rows = streaks.days.filter { it.day == lastDay }
            BarChart(
                Strings.Analytics.streaksOn(lastDay),
                Strings.Analytics.STREAK_BUCKETS,
                listOf(rows.sumOf { it.streak1 }, rows.sumOf { it.streak2to6 }, rows.sumOf { it.streak7to29 }, rows.sumOf { it.streak30plus }).map { it.toDouble() },
                Tokens.CHART[1],
                dimmed = List(4) { rows.any { it.provisional } },
                format = countFormat,
                seriesName = Strings.Analytics.ACCOUNTS_SERIES,
            )
        }
    }
}

@Composable
private fun OutcomesSection(filter: AnalyticsFilter) {
    Section(
        Strings.Analytics.OUTCOMES,
        key = filter.queryFor(AnalyticsTable.OUTCOMES),
        load = { load(AnalyticsTable.OUTCOMES, filter, OutcomesDto.serializer()) },
        csv = csv(AnalyticsTable.OUTCOMES, filter),
    ) { outcomes ->
        val total = outcomes.totals.firstOrNull { it.lang == filter.lang } ?: outcomes.totals.firstOrNull()
        if (total != null) {
            SimpleGrid(numColumns(base = 2, md = 4)) {
                SmallTile(Strings.Analytics.GAMES, formatCount(total.games))
                SmallTile(Strings.Analytics.KPI_WIN_RATE, formatPercent(total.winRate))
                SmallTile(Strings.Analytics.AVG_ATTEMPTS, formatDecimal(total.avgAttempts))
                SmallTile(Strings.Analytics.LOSSES, formatCount(total.losses))
            }
            Histogram(
                Strings.Analytics.DISTRIBUTION,
                (1..6).map { it.toString() },
                total.distribution.map { it.toDouble() },
                Tokens.CHART[0],
                countFormat,
                seriesName = Strings.Analytics.WINS,
            )
        }
        val days = daysInRange(filter.from, filter.to)
        val perDayTotals = outcomes.days.groupBy { it.day }.mapValues { (_, rows) -> rows.sumOf { it.wins } to rows.sumOf { it.games } }
        LineChart(
            Strings.Analytics.WIN_RATE_BY_DAY,
            days.map(Strings.Analytics::shortDate),
            listOf(ChartSeries(Strings.Analytics.KPI_WIN_RATE, perDay(days, perDayTotals) { (wins, games) -> share(wins, games)?.times(100) }, Tokens.CHART[0])),
            dimmedDays(days, outcomes.days.filter { it.provisional }.map { it.day }.toSet()),
            percentFormat,
        )
    }
}

@Composable
private fun SmallTile(label: String, value: String) {
    Div(KpiTileStyle.toModifier().css("padding" to "10px 12px").toAttrs()) {
        Span(MutedTextStyle.toModifier().css("font-size" to "12px").toAttrs()) { Text(label) }
        Span(Modifier.css("font-size" to "18px", "font-weight" to "650").toAttrs()) { Text(value) }
    }
}

// --- word difficulty and suggestions -----------------------------------------------------------------------------

@Composable
private fun WordsSection(filter: AnalyticsFilter) {
    var sort by remember { mutableStateOf<TableSort?>(null) }
    Section(
        Strings.Analytics.WORDS,
        key = filter.queryFor(AnalyticsTable.WORDS, sort),
        load = { load(AnalyticsTable.WORDS, filter, WordDifficultyDto.serializer(), sort) },
        csv = csv(AnalyticsTable.WORDS, filter, sort),
    ) { words ->
        Div(Modifier.css("max-height" to "480px", "overflow-y" to "auto").toAttrs()) {
            DataTable(
                caption = Strings.Analytics.WORDS,
                columns = listOf(
                    TableColumn<WordDayDto>(Strings.Analytics.COLUMN_DAY) { row -> DayCell(row.day, row.provisional) },
                    TableColumn(Strings.Analytics.COLUMN_CALENDAR) { row -> Text(Strings.Languages.label(row.calendar)) },
                    TableColumn(Strings.Analytics.COLUMN_WORD) { row ->
                        Span(Modifier.css("font-weight" to "650").toAttrs()) { Text(row.wordCyrl?.let { "${row.word} / $it" } ?: row.word.ifEmpty { Strings.Common.NOT_SET }) }
                    },
                    TableColumn(Strings.Analytics.COLUMN_MARKER) { row -> WordMarkers(row) },
                    TableColumn(Strings.Analytics.COLUMN_PLAYERS, numeric = true, sortKey = "players") { row -> Text(formatCount(row.players)) },
                    TableColumn(Strings.Analytics.COLUMN_WIN_RATE, numeric = true, sortKey = "winRate") { row -> Text(formatPercent(row.winRate)) },
                    TableColumn(Strings.Analytics.COLUMN_ATTEMPTS, numeric = true, sortKey = "avgAttempts") { row -> Text(formatDecimal(row.avgAttempts)) },
                ),
                rows = words.rows,
                emptyText = Strings.Analytics.NO_DATA,
                sort = sort,
                onSort = { key -> sort = nextWordSort(sort, key) },
            )
        }
    }
}

@Composable
private fun WordMarkers(row: WordDayDto) {
    Div(Modifier.css("display" to "flex", "gap" to "4px", "flex-wrap" to "wrap").toAttrs()) {
        when (row.marker) {
            DaySource.MANUAL -> Badge(Strings.Analytics.MARKER_MANUAL, TileTone.CORRECT)
            DaySource.AUTO -> Badge(Strings.Analytics.MARKER_AUTO, TileTone.OPEN)
            else -> Unit
        }
        if (row.repeat) Badge(Strings.Analytics.MARKER_REPEAT, TileTone.PRESENT)
        if (row.marker == null && !row.repeat) Text(Strings.Common.NOT_SET)
    }
}

@Composable
private fun SuggestionsSection(filter: AnalyticsFilter) {
    Section(
        Strings.Analytics.SUGGESTIONS,
        key = filter.queryFor(AnalyticsTable.SUGGESTIONS),
        load = { load(AnalyticsTable.SUGGESTIONS, filter, SuggestionsAnalyticsDto.serializer()) },
        csv = csv(AnalyticsTable.SUGGESTIONS, filter),
    ) { suggestions ->
        val active = suggestions.days.filter { it.submitted + it.autoAccepted + it.editorAccepted + it.rejected + it.backlogEnd > 0 }
        Div(Modifier.css("max-height" to "420px", "overflow-y" to "auto").toAttrs()) {
            DataTable(
                caption = Strings.Analytics.SUGGESTIONS,
                columns = listOf(
                    TableColumn<SuggestionDayDto>(Strings.Analytics.COLUMN_DATE) { row -> DayCell(row.date, row.provisional) },
                    TableColumn(Strings.Analytics.COLUMN_LANGUAGE) { row -> Text(Strings.Languages.label(row.lang)) },
                    TableColumn(Strings.Analytics.COLUMN_SUBMITTED, numeric = true) { row -> Text(formatCount(row.submitted)) },
                    TableColumn(Strings.Analytics.COLUMN_AUTO, numeric = true) { row -> Text(formatCount(row.autoAccepted)) },
                    TableColumn(Strings.Analytics.COLUMN_EDITOR, numeric = true) { row -> Text(formatCount(row.editorAccepted)) },
                    TableColumn(Strings.Analytics.COLUMN_REJECTED, numeric = true) { row -> Text(formatCount(row.rejected)) },
                    TableColumn(Strings.Analytics.COLUMN_BACKLOG, numeric = true) { row -> Text(formatCount(row.backlogEnd)) },
                    TableColumn(Strings.Analytics.COLUMN_MEDIAN_AUTO, numeric = true) { row -> Text(formatDuration(row.medianAutoSeconds)) },
                    TableColumn(Strings.Analytics.COLUMN_MEDIAN_EDITOR, numeric = true) { row -> Text(formatDuration(row.medianEditorSeconds)) },
                ),
                rows = active.sortedWith(compareByDescending<SuggestionDayDto> { it.date }.thenBy { it.lang }),
                emptyText = Strings.Analytics.NO_DATA,
            )
        }
    }
}

// --- content and staff -------------------------------------------------------------------------------------------

@Composable
private fun ContentSection(filter: AnalyticsFilter) {
    var showBundled by remember { mutableStateOf(false) }
    Section(
        Strings.Analytics.CONTENT,
        key = filter.queryFor(AnalyticsTable.CONTENT),
        load = { load(AnalyticsTable.CONTENT, filter, ContentDto.serializer()) },
        csv = csv(AnalyticsTable.CONTENT, filter),
    ) { content ->
        SimpleGrid(numColumns(base = 1, sm = 2, md = 4)) {
            content.calendars.forEach { calendar ->
                Div(KpiTileStyle.toModifier().toAttrs()) {
                    Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text("${Strings.Analytics.POOL} · ${Strings.Languages.label(calendar.calendar)}") }
                    Span(Modifier.css("font-size" to "18px", "font-weight" to "650").toAttrs()) {
                        Text("${formatCount(calendar.poolSize)} ${Strings.Analytics.POOL_SIZE}")
                    }
                    Span(MutedTextStyle.toModifier().css("font-size" to "12px").toAttrs()) {
                        Text(Strings.Analytics.poolDetails(formatCount(calendar.unusedLeft), formatCount(calendar.repeatDays), formatCount(calendar.upcomingRepeatDays)))
                    }
                }
            }
        }
        if (content.current.isNotEmpty()) {
            P(Modifier.css("font-size" to "14px").toAttrs()) {
                Text("${Strings.Analytics.CURRENT_ACTIVE}: " + content.current.joinToString(" · ") { "${Strings.Languages.label(it.lang)} ${formatCount(it.activeWords)}" })
            }
        }
        val days = daysInRange(filter.from, filter.to)
        val byDay = content.days.groupBy { it.date }
        fun sum(pick: (ContentDayDto) -> Long) = perDay(days, byDay) { rows -> rows.sumOf(pick).toDouble() }
        Div(Modifier.css("display" to "flex", "align-items" to "center", "gap" to "10px").toAttrs()) {
            Switch(checked = showBundled, onCheckedChange = { showBundled = it })
            Span(Modifier.css("font-size" to "14px", "cursor" to "pointer").toAttrs { onClick { showBundled = !showBundled } }) { Text(Strings.Analytics.SHOW_BUNDLED) }
        }
        StackedBarChart(
            Strings.Analytics.ADDED_BY_SOURCE,
            days.map(Strings.Analytics::shortDate),
            listOfNotNull(
                ChartSeries(Strings.Analytics.BUNDLED, sum { it.addedBundled }, Tokens.CHART[5]).takeIf { showBundled },
                ChartSeries(Strings.Analytics.SUGGESTION, sum { it.addedSuggestion }, Tokens.CHART[2]),
                ChartSeries(Strings.Analytics.AUTO, sum { it.addedAuto }, Tokens.CHART[1]),
                ChartSeries(Strings.Analytics.STAFF_SOURCE, sum { it.addedStaff }, Tokens.CHART[0]),
            ),
            dimmedDays(days, content.days.filter { it.provisional }.map { it.date }.toSet()),
            countFormat,
            width = WIDE,
        )
        val changed = content.days.filter { it.addedBundled + it.addedSuggestion + it.addedAuto + it.addedStaff + it.removed + it.restored > 0 || it.activeWords != null }
        Div(Modifier.css("max-height" to "360px", "overflow-y" to "auto").toAttrs()) {
            DataTable(
                caption = Strings.Analytics.CONTENT,
                columns = listOf(
                    TableColumn<ContentDayDto>(Strings.Analytics.COLUMN_DATE) { row -> DayCell(row.date, row.provisional) },
                    TableColumn(Strings.Analytics.COLUMN_LANGUAGE) { row -> Text(Strings.Languages.label(row.lang)) },
                    TableColumn(Strings.Analytics.COLUMN_ACTIVE, numeric = true) { row -> Text(formatCount(row.activeWords)) },
                    TableColumn(Strings.Analytics.COLUMN_BUNDLED, numeric = true) { row -> Text(formatCount(row.addedBundled)) },
                    TableColumn(Strings.Analytics.COLUMN_SUGGESTION, numeric = true) { row -> Text(formatCount(row.addedSuggestion)) },
                    TableColumn(Strings.Analytics.COLUMN_AUTO, numeric = true) { row -> Text(formatCount(row.addedAuto)) },
                    TableColumn(Strings.Analytics.COLUMN_STAFF, numeric = true) { row -> Text(formatCount(row.addedStaff)) },
                    TableColumn(Strings.Analytics.COLUMN_REMOVED, numeric = true) { row -> Text(formatCount(row.removed)) },
                    TableColumn(Strings.Analytics.COLUMN_RESTORED, numeric = true) { row -> Text(formatCount(row.restored)) },
                ),
                rows = changed.sortedWith(compareByDescending<ContentDayDto> { it.date }.thenBy { it.lang }),
                emptyText = Strings.Analytics.NO_DATA,
            )
        }
    }
}

@Composable
private fun StaffSection(filter: AnalyticsFilter) {
    Section(
        Strings.Analytics.STAFF,
        key = filter.queryFor(AnalyticsTable.STAFF),
        load = { load(AnalyticsTable.STAFF, filter, StaffActivityDto.serializer()) },
        csv = csv(AnalyticsTable.STAFF, filter),
    ) { staff ->
        Div(Modifier.css("max-height" to "420px", "overflow-y" to "auto").toAttrs()) {
            DataTable(
                caption = Strings.Analytics.STAFF,
                columns = listOf(
                    TableColumn<StaffDayDto>(Strings.Analytics.COLUMN_DATE) { row -> DayCell(row.date, row.provisional) },
                    TableColumn(Strings.Analytics.COLUMN_STAFF_MEMBER) { row ->
                        Text(if (row.telegramUnlinked) Strings.Analytics.TELEGRAM_UNLINKED else row.staff?.let { it.displayName ?: it.username } ?: Strings.Common.NOT_SET)
                    },
                    TableColumn(Strings.Analytics.COLUMN_LANGUAGE) { row -> Text(Strings.Languages.label(row.lang)) },
                    TableColumn(Strings.Analytics.COLUMN_ADDED, numeric = true) { row -> Text(formatCount(row.added)) },
                    TableColumn(Strings.Analytics.COLUMN_EDITED, numeric = true) { row -> Text(formatCount(row.edited)) },
                    TableColumn(Strings.Analytics.COLUMN_REMOVED_BY, numeric = true) { row -> Text(formatCount(row.removed)) },
                    TableColumn(Strings.Analytics.COLUMN_DECIDED, numeric = true) { row -> Text(formatCount(row.decided)) },
                ),
                rows = staff.rows.sortedWith(compareByDescending<StaffDayDto> { it.date }),
                emptyText = Strings.Analytics.NO_DATA,
            )
        }
    }
}
