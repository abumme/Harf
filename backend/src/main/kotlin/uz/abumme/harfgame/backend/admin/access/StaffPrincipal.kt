package uz.abumme.harfgame.backend.admin.access

import uz.abumme.harfgame.backend.admin.AdminApiException
import uz.abumme.harfgame.data.admin.Permission
import uz.abumme.harfgame.data.admin.Role

/**
 * The signed-in staff member of one admin request. It is rebuilt from the database on every request, so role,
 * status and language changes apply to the member's next request with no extra mechanism.
 */
data class StaffPrincipal(
    val staffId: String,
    val username: String,
    val displayName: String?,
    val role: Role,
    /** Stored language assignments; an ADMIN has every language regardless. */
    val assignedLanguages: Set<String>,
    val sessionId: String,
) {
    val permissions: Set<Permission> get() = role.permissions

    fun has(permission: Permission): Boolean = permission in permissions

    fun canUseLanguage(lang: String): Boolean = role == Role.ADMIN || lang in assignedLanguages

    /**
     * Refuses as forbidden unless the member may work in [lang]. Language-specific handlers call it before any read
     * or write, whatever the panel shows.
     */
    fun requireLanguage(lang: String) {
        if (!canUseLanguage(lang)) throw AdminApiException.forbidden("Language $lang is not assigned to you")
    }

    /** The subset of [available] languages the member may use: all of them for an ADMIN. */
    fun languagesWithin(available: List<String>): List<String> =
        if (role == Role.ADMIN) available else available.filter { it in assignedLanguages }
}
