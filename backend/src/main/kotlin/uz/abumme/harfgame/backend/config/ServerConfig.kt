package uz.abumme.harfgame.backend.config

/**
 * Reads deployment-mode config from the environment and enforces that a production process is not
 * started with insecure development defaults. `main()` calls [requireSecureProductionConfig] before
 * binding the server; test harnesses call `module()` directly and are unaffected, so the isolated
 * unit/integration tests keep running with the dev JWT secret.
 */
object ServerConfig {

    /** Development is the default; production must declare itself with `HARF_ENV=production`. */
    val isProduction: Boolean =
        System.getenv("HARF_ENV")?.trim()?.lowercase() == "production"

    /**
     * Fail fast if a production process would run on insecure defaults. Returns the list of missing
     * settings for logging/testing; throws when running in production and any are missing.
     */
    fun requireSecureProductionConfig(
        env: (String) -> String? = System::getenv,
        production: Boolean = isProduction,
    ) {
        if (!production) return
        val missing = buildList {
            if (env("JWT_SECRET").isNullOrBlank()) add("JWT_SECRET")
        }
        check(missing.isEmpty()) {
            "Refusing to start in production (HARF_ENV=production) without: ${missing.joinToString()}. " +
                "Set these secrets or run with HARF_ENV=development for local dev defaults."
        }
    }
}
