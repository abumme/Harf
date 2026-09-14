package uz.abumme.harfgame.backend

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * The server merges the backend copy of each guess dictionary; offline apps play from the bundled copy. The word-list
 * builder writes both, and this guards against the two drifting apart.
 */
class BundledGuessCopiesTest {

    @Test
    fun backendAndBundledGuessDictionariesAreIdentical() {
        val repo = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) } ?: return
        val bundled = repo.resolve("sharedUI/src/commonMain/composeResources/files")
        if (!Files.isDirectory(bundled)) return // backend-only checkout: no client tree to compare against

        val backendCopies: List<Path> = repo.resolve("backend/src/main/resources/wordpacks").listDirectoryEntries("*_guess.txt")
        assertTrue(backendCopies.isNotEmpty(), "no backend guess dictionaries found")
        for (backendCopy in backendCopies) {
            assertContentEquals(
                Files.readAllBytes(backendCopy),
                Files.readAllBytes(bundled.resolve(backendCopy.name)),
                "${backendCopy.name}: backend and bundled copies differ — rebuild with tools/wordlists",
            )
        }
    }
}
