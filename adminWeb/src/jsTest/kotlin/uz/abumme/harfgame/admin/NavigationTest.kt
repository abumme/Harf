package uz.abumme.harfgame.admin

import uz.abumme.harfgame.admin.session.NavSection
import uz.abumme.harfgame.admin.session.PageAccess
import uz.abumme.harfgame.admin.session.Routes
import uz.abumme.harfgame.admin.session.auditActionsFor
import uz.abumme.harfgame.admin.session.homeRedirect
import uz.abumme.harfgame.admin.session.navigationFor
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.audit.AuditActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationTest {
    // What the server sends for each role today (the backend's Role.permissions).
    private val adminPermissions = Permission.entries.toSet()
    private val worderPermissions = setOf(
        Permission.WORDS_READ,
        Permission.WORDS_WRITE,
        Permission.SUGGESTIONS_REVIEW,
        Permission.AUDIT_READ_OWN,
        Permission.ACCOUNT_SELF,
    )

    @Test
    fun worderGetsWordsSuggestionsActivityAndAccount() {
        assertEquals(
            listOf(NavSection.WORDS, NavSection.SUGGESTIONS, NavSection.ACTIVITY, NavSection.ACCOUNT),
            navigationFor(worderPermissions),
        )
        assertEquals(Routes.WORDS, NavSection.WORDS.route)
        assertEquals(Routes.SUGGESTIONS, NavSection.SUGGESTIONS.route)
    }

    @Test
    fun adminGetsAnalyticsWordsSuggestionsCalendarAnswerPoolPlayersStaffAuditLogAndAccount() {
        assertEquals(
            listOf(
                NavSection.ANALYTICS, NavSection.WORDS, NavSection.SUGGESTIONS, NavSection.CALENDAR, NavSection.ANSWER_POOL,
                NavSection.PLAYERS, NavSection.STAFF, NavSection.AUDIT_LOG, NavSection.ACCOUNT,
            ),
            navigationFor(adminPermissions),
        )
        assertEquals(Routes.CALENDAR, NavSection.CALENDAR.route)
        assertEquals(Routes.ANSWER_POOL, NavSection.ANSWER_POOL.route)
    }

    @Test
    fun aWorderSeesNothingAboutDailyWords() {
        assertTrue(NavSection.CALENDAR !in navigationFor(worderPermissions))
        assertTrue(NavSection.ANSWER_POOL !in navigationFor(worderPermissions))
        assertFalse(PageAccess.allows(PageAccess.CALENDAR, worderPermissions))
        assertFalse(PageAccess.allows(PageAccess.ANSWER_POOL, worderPermissions))
        assertTrue(PageAccess.allows(PageAccess.CALENDAR, adminPermissions))
        assertTrue(PageAccess.allows(PageAccess.ANSWER_POOL, adminPermissions))
        assertTrue(auditActionsFor(worderPermissions).none { it.startsWith(AuditActions.DAILY_PREFIX) })
        assertTrue(AuditActions.DAILY_WORD_PICKED in auditActionsFor(adminPermissions))
        assertEquals(AuditActions.ALL.size, auditActionsFor(adminPermissions).size)
    }

    @Test
    fun onlyAnAdminSeesPlayers() {
        assertTrue(NavSection.PLAYERS in navigationFor(adminPermissions))
        assertEquals(Routes.PLAYERS, NavSection.PLAYERS.route)
        assertTrue(NavSection.PLAYERS !in navigationFor(worderPermissions))
        // A WORDER opening /players or /players/{id} directly gets the forbidden view.
        assertFalse(PageAccess.allows(PageAccess.PLAYERS, worderPermissions))
        assertTrue(PageAccess.allows(PageAccess.PLAYERS, adminPermissions))
        assertTrue(auditActionsFor(worderPermissions).none { it.startsWith(AuditActions.PLAYER_PREFIX) })
        assertTrue(AuditActions.PLAYER_DELETED in auditActionsFor(adminPermissions))
    }

    @Test
    fun noPermissionsNoEntries() {
        assertEquals(emptyList(), navigationFor(emptySet()))
    }

    @Test
    fun pageAccessFollowsPermissions() {
        assertFalse(PageAccess.allows(PageAccess.STAFF, worderPermissions))
        assertTrue(PageAccess.allows(PageAccess.STAFF, adminPermissions))
        assertTrue(PageAccess.allows(PageAccess.AUDIT, worderPermissions))
        assertTrue(PageAccess.allows(PageAccess.ACCOUNT, worderPermissions))
        assertTrue(PageAccess.allows(PageAccess.HOME, emptySet()))
        assertTrue(PageAccess.allows(PageAccess.WORDS, worderPermissions))
        assertTrue(PageAccess.allows(PageAccess.SUGGESTIONS, worderPermissions))
        assertFalse(PageAccess.allows(PageAccess.WORDS, setOf(Permission.ACCOUNT_SELF)))
        assertFalse(PageAccess.allows(PageAccess.SUGGESTIONS, setOf(Permission.WORDS_READ)))
    }

    @Test
    fun theAdminHomeIsAnalyticsAndTheWorderHomeIsTheWordList() {
        assertEquals(Routes.ANALYTICS, homeRedirect(adminPermissions))
        assertEquals("/analytics", NavSection.ANALYTICS.route)
        assertEquals(Routes.WORDS, homeRedirect(worderPermissions))
        assertEquals(Routes.ACCOUNT, homeRedirect(setOf(Permission.ACCOUNT_SELF)))
        assertNull(homeRedirect(emptySet()))
        // Sign-in without a `next` goes straight to the member's home.
        assertEquals(Routes.ANALYTICS, Routes.afterLogin(null, homeRedirect(adminPermissions)!!))
        assertEquals("/audit?page=2", Routes.afterLogin("/audit?page=2", homeRedirect(adminPermissions)!!))
    }

    @Test
    fun onlyAnAdminSeesAnalytics() {
        assertEquals(NavSection.ANALYTICS, navigationFor(adminPermissions).first())
        assertTrue(NavSection.ANALYTICS !in navigationFor(worderPermissions))
        assertTrue(navigationFor(worderPermissions + Permission.AUDIT_READ_ALL).none { it == NavSection.ANALYTICS })
        // A WORDER opening /analytics directly gets the forbidden view.
        assertFalse(PageAccess.allows(PageAccess.ANALYTICS, worderPermissions))
        assertTrue(PageAccess.allows(PageAccess.ANALYTICS, adminPermissions))
    }

    @Test
    fun loginRouteCarriesOnlySafeNextTargets() {
        assertEquals("/login?next=%2Faudit%3Fpage%3D2", Routes.login("/audit?page=2"))
        assertEquals("/login", Routes.login("/"))
        assertEquals("/login", Routes.login(null))
        assertEquals("/login", Routes.login("https://evil.example/"))
        assertEquals("/login", Routes.login("//evil.example"))
        assertEquals("/login", Routes.login("/login?next=%2Fstaff"))

        assertEquals("/audit?page=2", Routes.afterLogin("/audit?page=2"))
        assertEquals("/", Routes.afterLogin(null))
        assertEquals("/", Routes.afterLogin("//evil.example"))
        assertEquals("/", Routes.afterLogin("javascript:alert(1)"))
        assertEquals("/staff/edit?id=a%2Fb", Routes.staffEdit("a/b"))
    }
}
