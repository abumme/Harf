package uz.abumme.harfgame.data.wordpack

import kotlinx.serialization.Serializable

/**
 * A release build's copy of a language's published calendar (`composeResources/files/<lang>_calendar.json`): the answers
 * and schedule of the server pack when the build was prepared, without guesses (the build already bundles the guess
 * dictionary). A never-synced device plays it, combined with the bundled guesses, when it passes [WordPackIntegrity].
 */
@Serializable
data class CalendarSnapshotDto(
    val lang: String,
    val version: String,
    val anchorEpochDay: Long,
    val answers: List<String>,
    val schedule: List<String>,
    val firstPublicEpochDay: Long? = null,
) {
    val firstPublicDay: Long get() = firstPublicEpochDay ?: anchorEpochDay
    val firstPublishedDay: Long get() = firstPublicDay

    /** The pack a device adopts from this snapshot and the build's [guesses]. */
    fun toPack(guesses: List<String>): WordPackDto =
        WordPackDto(
            lang = lang,
            version = version,
            effectiveFrom = anchorEpochDay,
            anchorEpochDay = anchorEpochDay,
            answers = answers,
            guesses = guesses,
            schedule = schedule,
            firstPublicEpochDay = firstPublicEpochDay,
        )

    companion object {
        /** The snapshot of a published [pack]. */
        fun of(pack: WordPackDto) = CalendarSnapshotDto(
            pack.lang,
            pack.version,
            pack.anchorEpochDay,
            pack.answers,
            pack.schedule,
            pack.firstPublicEpochDay,
        )

        /** The resource path of [lang]'s snapshot, relative to the Compose resources root. */
        fun resourcePath(lang: String) = "files/${lang}_calendar.json"
    }
}
