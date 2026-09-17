package uz.abumme.harfgame.admin.components

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
import com.varabyte.kobweb.core.isExporting
import com.varabyte.kobweb.navigation.Anchor
import com.varabyte.kobweb.silk.components.icons.lucide.LucideBookOpen
import com.varabyte.kobweb.silk.components.icons.lucide.LucideCalendarDays
import com.varabyte.kobweb.silk.components.icons.lucide.LucideGamepad2
import com.varabyte.kobweb.silk.components.icons.lucide.LucideListChecks
import com.varabyte.kobweb.silk.components.icons.lucide.LucideHistory
import com.varabyte.kobweb.silk.components.icons.lucide.LucideInbox
import com.varabyte.kobweb.silk.components.icons.lucide.LucideLayoutDashboard
import com.varabyte.kobweb.silk.components.icons.lucide.LucideLogOut
import com.varabyte.kobweb.silk.components.icons.lucide.LucideMoon
import com.varabyte.kobweb.silk.components.icons.lucide.LucideScrollText
import com.varabyte.kobweb.silk.components.icons.lucide.LucideSun
import com.varabyte.kobweb.silk.components.icons.lucide.LucideUserRound
import com.varabyte.kobweb.silk.components.icons.lucide.LucideUsers
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.selectors.hover
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.style.until
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.saveToLocalStorage
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Aside
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.Nav
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.AdminApp
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.admin.session.SessionStatus
import uz.abumme.harfgame.admin.session.navigationFor
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.auth.MeDto

val ShellStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "grid",
            "grid-template-columns" to "248px minmax(0, 1fr)",
            "min-height" to "100vh",
        )
    }
    // Phones and narrow windows: the sidebar becomes a header above the page.
    until(Breakpoint.MD) { Modifier.css("grid-template-columns" to "minmax(0, 1fr)", "min-height" to "auto") }
}

/** The sidebar's grid column: it stretches with the page, so its surface and rule reach the bottom. */
val SidebarColumnStyle = CssStyle {
    base { Modifier.css("background-color" to Tokens.SURFACE, "border-right" to "1px solid ${Tokens.LINE}") }
    until(Breakpoint.MD) { Modifier.css("border-right" to "none", "border-bottom" to "1px solid ${Tokens.LINE}") }
}

/** The sidebar itself stays in view while the page scrolls. */
val SidebarStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "flex",
            "flex-direction" to "column",
            "gap" to "24px",
            "padding" to "22px 16px",
            "position" to "sticky",
            "top" to "0",
            "height" to "100vh",
        )
    }
    until(Breakpoint.MD) {
        Modifier.css("position" to "static", "height" to "auto", "gap" to "14px", "padding" to "14px 16px")
    }
}

val BrandLinkStyle = CssStyle.base {
    Modifier.css("display" to "inline-flex", "flex-direction" to "column", "gap" to "8px", "text-decoration" to "none", "color" to "inherit")
}

val NavListStyle = CssStyle {
    base { Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "2px") }
    until(Breakpoint.MD) { Modifier.css("flex-direction" to "row", "flex-wrap" to "wrap", "gap" to "4px") }
}

val NavLinkStyle = CssStyle {
    base {
        Modifier.css(
            "display" to "flex",
            "align-items" to "center",
            "gap" to "10px",
            "padding" to "8px 10px",
            "border-radius" to "6px",
            "color" to Tokens.MUTED,
            "text-decoration" to "none",
            "font-weight" to "550",
            "border-left" to "3px solid transparent",
        )
    }
    hover { Modifier.css("color" to Tokens.INK, "background-color" to Tokens.SURFACE_SUNKEN) }
    cssRule("[aria-current=\"page\"]") {
        Modifier.css(
            "color" to Tokens.INK,
            "background-color" to Tokens.SURFACE_SUNKEN,
            "border-left-color" to Tokens.CORRECT,
            "font-weight" to "650",
        )
    }
}

val MainAreaStyle = CssStyle {
    base { Modifier.css("padding" to "32px 40px 48px", "max-width" to "1120px", "width" to "100%") }
    until(Breakpoint.MD) { Modifier.css("padding" to "20px 16px 40px") }
}

val PageHeaderStyle = CssStyle.base {
    Modifier.css(
        "display" to "flex",
        "align-items" to "flex-end",
        "justify-content" to "space-between",
        "gap" to "16px",
        "flex-wrap" to "wrap",
        "margin-bottom" to "24px",
    )
}

/**
 * The signed-in frame of every page: navigation limited to what the member may open, who is signed in, the color
 * mode switch and sign-out. [active] marks the current section.
 */
@Composable
fun AdminShell(me: MeDto, active: NavSection?, content: @Composable () -> Unit) {
    Div(ShellStyle.toModifier().toAttrs()) {
        Div(SidebarColumnStyle.toModifier().toAttrs()) { Sidebar(me, active) }
        Main(MainAreaStyle.toModifier().toAttrs()) { content() }
    }
}

@Composable
private fun Sidebar(me: MeDto, active: NavSection?) {
    Aside(SidebarStyle.toModifier().toAttrs()) {
        Anchor(Routes.HOME, BrandLinkStyle.toModifier().toAttrs()) {
            Wordmark(tileSize = 28)
            Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(Strings.PANEL) }
        }
        Nav({ attr("aria-label", Strings.Nav.LABEL) }) {
            Div(NavListStyle.toModifier().toAttrs()) {
                navigationFor(me.permissions).forEach { section ->
                    Anchor(section.route, NavLinkStyle.toModifier().toAttrs {
                        if (section == active) attr("aria-current", "page")
                    }) {
                        NavIcon(section)
                        Span { Text(Strings.Nav.label(section)) }
                    }
                }
            }
        }
        Div(Modifier.css("margin-top" to "auto", "display" to "flex", "flex-direction" to "column", "gap" to "12px").toAttrs()) {
            Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "4px", "min-width" to "0").toAttrs()) {
                Span(Modifier.css("font-weight" to "650", "overflow-wrap" to "anywhere").toAttrs()) {
                    Text(me.displayName ?: me.username)
                }
                if (me.displayName != null) {
                    Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(me.username) }
                }
                Span(MutedTextStyle.toModifier().css("font-size" to "13px").toAttrs()) { Text(Strings.Roles.label(me.role)) }
            }
            Div(Modifier.css("display" to "flex", "gap" to "8px").toAttrs()) {
                SignOutButton()
                ColorModeButton()
            }
        }
    }
}

@Composable
private fun NavIcon(section: NavSection) {
    when (section) {
        NavSection.ANALYTICS -> LucideLayoutDashboard()
        NavSection.WORDS -> LucideBookOpen()
        NavSection.SUGGESTIONS -> LucideInbox()
        NavSection.CALENDAR -> LucideCalendarDays()
        NavSection.ANSWER_POOL -> LucideListChecks()
        NavSection.PLAYERS -> LucideGamepad2()
        NavSection.STAFF -> LucideUsers()
        NavSection.AUDIT_LOG -> LucideScrollText()
        NavSection.ACTIVITY -> LucideHistory()
        NavSection.ACCOUNT -> LucideUserRound()
    }
}

@Composable
private fun SignOutButton() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    ActionButton(
        text = Strings.Common.SIGN_OUT,
        onClick = {
            busy = true
            scope.launch { AdminApp.flow.signOut { AdminApp.api.logout() } }
        },
        enabled = !busy,
        icon = { LucideLogOut() },
        modifier = Modifier.css("flex" to "1"),
    )
}

@Composable
private fun ColorModeButton() {
    var colorMode by ColorMode.currentState
    val label = if (colorMode.isLight) Strings.Common.THEME_TO_DARK else Strings.Common.THEME_TO_LIGHT
    IconButton(label, onClick = {
        colorMode = colorMode.opposite
        try {
            colorMode.saveToLocalStorage()
        } catch (e: Throwable) {
            // Storage blocked (private mode): the choice lasts for this page load only.
        }
    }) {
        if (colorMode.isLight) LucideMoon() else LucideSun()
    }
}

/** Page title with optional actions on the right. */
@Composable
fun PageHeader(title: String, subtitle: String? = null, actions: (@Composable () -> Unit)? = null) {
    Div(PageHeaderStyle.toModifier().toAttrs()) {
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "6px").toAttrs()) {
            H1(PageTitleStyle.toModifier().toAttrs()) { Text(title) }
            subtitle?.let { P(MutedTextStyle.toModifier().toAttrs()) { Text(it) } }
        }
        actions?.let { Div(Modifier.css("display" to "flex", "gap" to "10px", "flex-wrap" to "wrap").toAttrs()) { it() } }
    }
}

val CenteredStyle = CssStyle.base {
    Modifier.css("min-height" to "100vh", "display" to "grid", "place-items" to "center", "padding" to "24px")
}

/** Shown while `me` loads, and in exported snapshots (which never hold staff data). */
@Composable
fun LoadingShell() {
    Div(CenteredStyle.toModifier().toAttrs { attr("aria-busy", "true") }) {
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "align-items" to "center", "gap" to "14px").toAttrs()) {
            Wordmark(tileSize = 36)
            P(MutedTextStyle.toModifier().toAttrs()) { Text(Strings.Common.LOADING) }
        }
    }
}

/**
 * Renders [content] for a signed-in member who has at least one of [anyOf]; otherwise sends them to login (keeping
 * this page as `next`) or shows the forbidden view inside the shell. Nothing is requested while exporting.
 */
@Composable
fun RequireSession(anyOf: Set<Permission> = PageAccess.HOME, active: NavSection? = null, content: @Composable (MeDto) -> Unit) {
    if (AppGlobals.isExporting) {
        LoadingShell()
        return
    }
    val session = AdminApp.session
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { session.load() }
    when (val status = session.status) {
        SessionStatus.Unknown, SessionStatus.Loading -> LoadingShell()
        SessionStatus.SignedOut -> {
            LaunchedEffect(status) { AdminApp.flow.onUnauthorized() }
            LoadingShell()
        }
        is SessionStatus.Failed -> Div(CenteredStyle.toModifier().toAttrs()) {
            Div(Modifier.css("display" to "flex", "flex-direction" to "column", "align-items" to "center", "gap" to "14px", "max-width" to "420px", "text-align" to "center").toAttrs()) {
                Wordmark(tileSize = 36)
                P { Text(if (status.code == "network_error") Strings.Common.NETWORK_ERROR else Strings.Common.SERVER_ERROR) }
                ActionButton(Strings.Common.RETRY, onClick = { scope.launch { session.load(force = true) } }, kind = ButtonKind.PRIMARY)
            }
        }
        is SessionStatus.SignedIn -> {
            if (PageAccess.allows(anyOf, status.me.permissions)) {
                content(status.me)
            } else {
                AdminShell(status.me, active = null) { ForbiddenView() }
            }
        }
    }
}

/** The not-permitted page: no data of the section is loaded or shown. */
@Composable
fun ForbiddenView() {
    Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "16px", "max-width" to "560px", "padding-top" to "8vh").toAttrs()) {
        TileWord("403", TileTone.ABSENT)
        H1(PageTitleStyle.toModifier().toAttrs()) { Text(Strings.Forbidden.TITLE) }
        P(MutedTextStyle.toModifier().css("font-size" to "15px").toAttrs()) { Text(Strings.Forbidden.BODY) }
        Anchor(Routes.HOME, PrimaryButtonStyle.toModifier().css("align-self" to "flex-start").toAttrs()) { Text(Strings.Forbidden.HOME) }
    }
}

/** The Russian not-found page for unknown panel addresses. */
@Composable
fun NotFoundView() {
    Div(CenteredStyle.toModifier().toAttrs()) {
        Div(Modifier.css("display" to "flex", "flex-direction" to "column", "gap" to "16px", "max-width" to "480px").toAttrs()) {
            TileWord("404", TileTone.PRESENT)
            H1(PageTitleStyle.toModifier().toAttrs()) { Text(Strings.NotFound.TITLE) }
            P(MutedTextStyle.toModifier().css("font-size" to "15px").toAttrs()) { Text(Strings.NotFound.BODY) }
            Anchor(Routes.HOME, PrimaryButtonStyle.toModifier().css("align-self" to "flex-start").toAttrs()) { Text(Strings.NotFound.HOME) }
        }
    }
}

/** A short code spelled in tiles of one tone, like an unsolved row. */
@Composable
fun TileWord(text: String, tone: TileTone) {
    Div(WordmarkStyle.toModifier().toAttrs { attr("aria-hidden", "true") }) {
        text.forEach { char ->
            Span(WordmarkTileStyle.toModifier().css(
                "width" to "40px",
                "height" to "40px",
                "font-size" to "22px",
                "background-color" to tone.background,
                "color" to tone.ink,
            ).toAttrs()) { Text(char.toString()) }
        }
    }
}
