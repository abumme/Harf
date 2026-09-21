package uz.abumme.harfgame.admin.pages

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.core.Page
import uz.abumme.harfgame.admin.components.AdminShell
import uz.abumme.harfgame.admin.components.ForbiddenView
import uz.abumme.harfgame.admin.components.RequireSession

/** The not-permitted page as an address of its own; protected pages show the same view in place. */
@Page
@Composable
fun ForbiddenPage() {
    RequireSession { me -> AdminShell(me, active = null) { ForbiddenView() } }
}
