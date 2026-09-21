package uz.abumme.harfgame.admin

import com.varabyte.kobweb.core.AppGlobals
import com.varabyte.kobweb.navigation.BasePath
import com.varabyte.kobweb.navigation.Router
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.navigation.remove
import kotlinx.browser.document
import kotlinx.browser.window
import uz.abumme.harfgame.admin.api.AdminApi
import uz.abumme.harfgame.admin.api.CredentialsMode
import uz.abumme.harfgame.admin.api.FetchTransport
import uz.abumme.harfgame.admin.session.SessionFlow
import uz.abumme.harfgame.admin.session.SessionStore

/** Where the admin API lives: `/harf` in a RELEASE export, the local backend in a DEBUG build (see build.gradle.kts). */
val AppGlobals.apiBase: String get() = AppGlobals["apiBase"] ?: "/harf"

/** `same-origin` in a RELEASE export, `include` for the dev server calling the backend on another port. */
val AppGlobals.apiCredentials: CredentialsMode
    get() = if (AppGlobals["apiCredentials"] == CredentialsMode.INCLUDE.fetchValue) CredentialsMode.INCLUDE else CredentialsMode.SAME_ORIGIN

/** The panel's single API client, session state and session flow, shared by every page. */
object AdminApp {
    /** Set once from `@InitKobweb`. */
    lateinit var router: Router

    val api: AdminApi by lazy {
        AdminApi(AppGlobals.apiBase, AppGlobals.apiCredentials, FetchTransport(), { document.cookie }).also { api ->
            api.onUnauthorized = { flow.onUnauthorized() }
        }
    }

    val session: SessionStore by lazy { SessionStore { api.me() } }

    val flow: SessionFlow by lazy {
        SessionFlow(
            store = session,
            navigate = { route, replace ->
                router.navigateTo(route, if (replace) UpdateHistoryMode.REPLACE else UpdateHistoryMode.PUSH)
            },
            currentRoute = { currentRoute() },
        )
    }

    /** The current panel route without the base path, with its query, e.g. `/audit?page=2`. */
    fun currentRoute(): String = BasePath.remove(window.location.pathname).ifEmpty { "/" } + window.location.search
}
