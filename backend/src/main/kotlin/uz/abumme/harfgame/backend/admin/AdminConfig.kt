package uz.abumme.harfgame.backend.admin

import uz.abumme.harfgame.backend.config.ServerConfig
import java.io.File

/**
 * Deployment settings of the staff admin API and panel, read from the environment by [fromEnv].
 *
 * - `ADMIN_COOKIE_PATH`: Path of the session and anti-forgery cookies; `/` by default, `/harf` in production so the
 *   cookies are not sent to the other apps on the same host.
 * - `TRUST_PROXY_HEADERS=true`: take the client address from `X-Forwarded-For` (behind Caddy only).
 * - `ADMIN_DEV_ORIGIN`: origin of the local Kobweb dev server allowed by CORS; ignored in production.
 * - `ADMIN_WEB_DIR`: directory of the exported panel, served at `/admin`; nothing is served when unset.
 */
data class AdminConfig(
    val production: Boolean = false,
    val cookiePath: String = "/",
    val trustProxyHeaders: Boolean = false,
    val devOrigin: String? = null,
    val webDir: File? = null,
    val loginAttemptsPerMinute: Int = 10,
) {
    /** Cookies are `Secure` only in production; the local dev panel runs over plain HTTP. */
    val secureCookies: Boolean get() = production

    /** The origin development CORS allows, or null when CORS must not be installed (always null in production). */
    val devCorsOrigin: String? get() = devOrigin.takeUnless { production }

    companion object {
        fun fromEnv(
            env: (String) -> String? = System::getenv,
            production: Boolean = ServerConfig.isProduction,
        ) = AdminConfig(
            production = production,
            cookiePath = env("ADMIN_COOKIE_PATH")?.trim()?.takeIf { it.startsWith("/") } ?: "/",
            trustProxyHeaders = env("TRUST_PROXY_HEADERS")?.trim().equals("true", ignoreCase = true),
            devOrigin = env("ADMIN_DEV_ORIGIN")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() },
            webDir = env("ADMIN_WEB_DIR")?.trim()?.takeIf { it.isNotEmpty() }?.let(::File),
        )
    }
}
