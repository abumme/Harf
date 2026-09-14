package uz.abumme.harfgame.backend

import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uz.abumme.harfgame.backend.db.*
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseSchemaTest {
    @Test
    fun testSchemaCreation() {
        val db = DatabaseFactory.init()
        transaction(db) {
            assertTrue(UsersTable.exists())
            assertTrue(OAuthIdentitiesTable.exists())
            assertTrue(RefreshTokensTable.exists())
            assertTrue(UserStatsTable.exists())
            assertTrue(WordSuggestionsTable.exists())
            assertTrue(SuggestionReportsTable.exists())
        }
    }
}
