package uz.abumme.harfgame.backend.admin.analytics

import org.jetbrains.exposed.v1.core.statements.StatementType
import java.time.Instant
import java.time.LocalDate

/** Account events analytics counts without keeping who: explicit deletions per Asia/Tashkent date. */
object AccountEvents {
    /**
     * Adds one deletion to the date of [now]. Runs inside the deleting transaction, so only a deletion that commits is
     * counted.
     */
    fun countDeletion(now: Instant) {
        AnalyticsSql.update(
            "INSERT INTO account_events_daily (date, deletions) VALUES (:date, 1) " +
                "ON CONFLICT (date) DO UPDATE SET deletions = account_events_daily.deletions + 1",
            mapOf("date" to LocalDate.ofInstant(now, AnalyticsDays.EVENTS_ZONE)),
            StatementType.INSERT,
        )
    }
}
