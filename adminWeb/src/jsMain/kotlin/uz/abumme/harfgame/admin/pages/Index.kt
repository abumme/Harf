package uz.abumme.harfgame.admin.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import uz.abumme.harfgame.admin.Strings
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.LoadingShell
import uz.abumme.harfgame.admin.components.MutedTextStyle
import uz.abumme.harfgame.admin.components.PageHeader
import uz.abumme.harfgame.admin.components.PanelStyle
import uz.abumme.harfgame.admin.components.RequireSession
import uz.abumme.harfgame.admin.components.css
import uz.abumme.harfgame.admin.session.homeRedirect

/** `/`: sends a member to their home page — analytics for an ADMIN, the words for a WORDER. */
@Page
@Composable
fun HomePage() {
    val ctx = rememberPageContext()
    RequireSession { me ->
        val redirect = homeRedirect(me.permissions)
        if (redirect != null) {
            LaunchedEffect(redirect) { ctx.router.navigateTo(redirect, UpdateHistoryMode.REPLACE) }
            LoadingShell()
        } else {
            AdminShell(me, active = null) {
                PageHeader(Strings.Home.TITLE, Strings.Home.greeting(me.displayName ?: me.username, me.role))
                Div(PanelStyle.toModifier().css("padding" to "20px", "max-width" to "640px").toAttrs()) {
                    P(MutedTextStyle.toModifier().css("font-size" to "15px").toAttrs()) { Text(Strings.Home.NOTHING_AVAILABLE) }
                }
            }
        }
    }
}
