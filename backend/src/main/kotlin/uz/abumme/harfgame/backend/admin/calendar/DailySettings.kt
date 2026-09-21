package uz.abumme.harfgame.backend.admin.calendar

import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Settings of the daily-word calendars.
 *
 * - `DAILY_HISTORY_START` ([historyStart], ISO date, optional): the public launch date. Days before it never count as
 *   used, so words played during the test period are free again. Unset: each calendar's first run
 *   (`calendar_state.initialized_on`).
 *
 * [random] chooses automatic picks; tests pass a seeded one.
 */
data class DailySettings(
    val historyStart: LocalDate? = null,
    val random: Random = SecureRandom().asKotlinRandom(),
) {
    companion object {
        /** Reads the environment; a malformed `DAILY_HISTORY_START` stops startup rather than counting the wrong days. */
        fun fromEnv(env: (String) -> String? = System::getenv): DailySettings {
            val raw = env("DAILY_HISTORY_START")?.trim()?.takeIf { it.isNotEmpty() }
            val start = raw?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: DateTimeParseException) {
                    throw IllegalStateException("DAILY_HISTORY_START must be an ISO date (yyyy-mm-dd), got '$it'", e)
                }
            }
            return DailySettings(historyStart = start)
        }
    }
}
