package uz.abumme.harfgame.admin.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import uz.abumme.harfgame.data.admin.AdminErrors
import uz.abumme.harfgame.data.admin.AdminRoutes
import uz.abumme.harfgame.data.admin.FieldError
import uz.abumme.harfgame.data.admin.PageDto
import uz.abumme.harfgame.data.admin.analytics.AnalyticsTable
import uz.abumme.harfgame.data.admin.analytics.OverviewDto
import uz.abumme.harfgame.data.admin.answerpool.CreatePairRequest
import uz.abumme.harfgame.data.admin.answerpool.CyrlStatusDto
import uz.abumme.harfgame.data.admin.answerpool.MarkItemResultDto
import uz.abumme.harfgame.data.admin.answerpool.MarkWordsRequest
import uz.abumme.harfgame.data.admin.answerpool.PairDto
import uz.abumme.harfgame.data.admin.answerpool.PoolCandidateDto
import uz.abumme.harfgame.data.admin.answerpool.PoolPageDto
import uz.abumme.harfgame.data.admin.answerpool.PoolParams
import uz.abumme.harfgame.data.admin.audit.AuditEntryDto
import uz.abumme.harfgame.data.admin.audit.AuditParams
import uz.abumme.harfgame.data.admin.auth.ChangePasswordRequest
import uz.abumme.harfgame.data.admin.auth.LoginRequest
import uz.abumme.harfgame.data.admin.auth.MeDto
import uz.abumme.harfgame.data.admin.calendar.CalendarCandidateDto
import uz.abumme.harfgame.data.admin.calendar.CalendarNoticeDto
import uz.abumme.harfgame.data.admin.calendar.DayDto
import uz.abumme.harfgame.data.admin.calendar.PickDayRequest
import uz.abumme.harfgame.data.admin.players.DeletePlayerRequest
import uz.abumme.harfgame.data.admin.players.EndSessionsResultDto
import uz.abumme.harfgame.data.admin.players.PlayerDetailDto
import uz.abumme.harfgame.data.admin.players.PlayerSearchPageDto
import uz.abumme.harfgame.data.admin.players.PlayerSearchQuery
import uz.abumme.harfgame.data.admin.staff.CreateStaffRequest
import uz.abumme.harfgame.data.admin.staff.ResetPasswordRequest
import uz.abumme.harfgame.data.admin.staff.StaffDto
import uz.abumme.harfgame.data.admin.staff.UpdateStaffRequest
import uz.abumme.harfgame.data.admin.suggestions.DecisionRequest
import uz.abumme.harfgame.data.admin.suggestions.SuggestionDto
import uz.abumme.harfgame.data.admin.suggestions.SuggestionPageDto
import uz.abumme.harfgame.data.admin.words.AddWordRequest
import uz.abumme.harfgame.data.admin.words.BulkAddRequest
import uz.abumme.harfgame.data.admin.words.BulkAddResult
import uz.abumme.harfgame.data.admin.words.CheckWordRequest
import uz.abumme.harfgame.data.admin.words.CheckWordResult
import uz.abumme.harfgame.data.admin.words.EditWordRequest
import uz.abumme.harfgame.data.admin.words.WordDto
import uz.abumme.harfgame.data.admin.words.WordPageDto
import uz.abumme.harfgame.data.api.ApiErrorResponse
import uz.abumme.harfgame.data.api.ApiResult

/** `same-origin` for the exported panel served by the backend; `include` for the dev server calling another port. */
enum class CredentialsMode(val fetchValue: String) {
    SAME_ORIGIN("same-origin"),
    INCLUDE("include"),
}

/** Error codes the client adds for failures that carry no `ApiErrorResponse`. */
object ClientErrors {
    const val NETWORK = "network_error"
    const val SERVER = "server_error"
    const val UNEXPECTED_RESPONSE = "unexpected_response"
}

/** Filters of one audit log request; `from`/`to` are epoch milliseconds, `to` exclusive. */
data class AuditQuery(
    val actor: String? = null,
    val action: String? = null,
    val lang: String? = null,
    val from: Long? = null,
    val to: Long? = null,
    val page: Int = 0,
    val size: Int = AuditParams.DEFAULT_SIZE,
) {
    fun toQueryString(): String = queryString(
        AuditParams.ACTOR to actor,
        AuditParams.ACTION to action,
        AuditParams.LANG to lang,
        AuditParams.FROM to from?.toString(),
        AuditParams.TO to to?.toString(),
        AuditParams.PAGE to page.toString(),
        AuditParams.SIZE to size.toString(),
    )
}

/**
 * The panel's client for the staff admin API, typed by the shared `:sharedData` DTOs.
 *
 * - Sends the session cookie ([CredentialsMode]) and, on every POST/PUT/PATCH/DELETE, echoes the `XSRF-TOKEN` cookie
 *   in `X-XSRF-TOKEN`. No framework does this for us: this class is the one place it happens.
 * - Maps every failure to [ApiResult.Error]: the server's `ApiErrorResponse(error, message)` when present (a
 *   `field: reason` message can be split with [fieldError]), otherwise a code derived from the status.
 * - A 401 from anything but the login call invokes [onUnauthorized], which clears the session and goes to login.
 */
class AdminApi(
    private val baseUrl: String,
    private val credentials: CredentialsMode,
    private val transport: HttpTransport,
    /** The current `document.cookie` string. */
    private val cookies: () -> String,
    var onUnauthorized: () -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun login(request: LoginRequest): ApiResult<MeDto> =
        send("POST", AdminRoutes.AUTH_LOGIN, encode(LoginRequest.serializer(), request), MeDto.serializer(), isLogin = true)

    suspend fun logout(): ApiResult<Unit> = send("POST", AdminRoutes.AUTH_LOGOUT, null, null)

    suspend fun me(): ApiResult<MeDto> = send("GET", AdminRoutes.AUTH_ME, null, MeDto.serializer())

    suspend fun changePassword(request: ChangePasswordRequest): ApiResult<Unit> =
        send("POST", AdminRoutes.AUTH_PASSWORD, encode(ChangePasswordRequest.serializer(), request), null)

    suspend fun languages(): ApiResult<List<String>> =
        send("GET", AdminRoutes.LANGUAGES, null, ListSerializer(String.serializer()))

    suspend fun staffList(): ApiResult<List<StaffDto>> =
        send("GET", AdminRoutes.STAFF, null, ListSerializer(StaffDto.serializer()))

    suspend fun staff(id: String): ApiResult<StaffDto> =
        send("GET", AdminRoutes.staff(encodeQueryComponent(id)), null, StaffDto.serializer())

    suspend fun createStaff(request: CreateStaffRequest): ApiResult<StaffDto> =
        send("POST", AdminRoutes.STAFF, encode(CreateStaffRequest.serializer(), request), StaffDto.serializer())

    suspend fun updateStaff(id: String, request: UpdateStaffRequest): ApiResult<StaffDto> =
        send("PATCH", AdminRoutes.staff(encodeQueryComponent(id)), encode(UpdateStaffRequest.serializer(), request), StaffDto.serializer())

    suspend fun resetPassword(id: String, newPassword: String): ApiResult<Unit> =
        send("POST", AdminRoutes.staffPassword(encodeQueryComponent(id)), encode(ResetPasswordRequest.serializer(), ResetPasswordRequest(newPassword)), null)

    suspend fun disableStaff(id: String): ApiResult<StaffDto> =
        send("POST", AdminRoutes.staffDisable(encodeQueryComponent(id)), null, StaffDto.serializer())

    suspend fun enableStaff(id: String): ApiResult<StaffDto> =
        send("POST", AdminRoutes.staffEnable(encodeQueryComponent(id)), null, StaffDto.serializer())

    suspend fun audit(query: AuditQuery): ApiResult<PageDto<AuditEntryDto>> =
        send("GET", AdminRoutes.AUDIT + query.toQueryString(), null, PageDto.serializer(AuditEntryDto.serializer()))

    /** One page of catalog words; [query] is a `?…` string of `WordParams` (see `WordsQueryState.toApiQuery`). */
    suspend fun words(query: String): ApiResult<WordPageDto> =
        send("GET", AdminRoutes.WORDS + query, null, PageDto.serializer(WordDto.serializer()))

    suspend fun checkWord(lang: String, text: String): ApiResult<CheckWordResult> =
        send("POST", AdminRoutes.WORDS_CHECK, encode(CheckWordRequest.serializer(), CheckWordRequest(lang, text)), CheckWordResult.serializer())

    suspend fun addWord(lang: String, text: String): ApiResult<WordDto> =
        send("POST", AdminRoutes.WORDS, encode(AddWordRequest.serializer(), AddWordRequest(lang, text)), WordDto.serializer())

    suspend fun addWords(lang: String, lines: List<String>): ApiResult<BulkAddResult> =
        send("POST", AdminRoutes.WORDS_BULK, encode(BulkAddRequest.serializer(), BulkAddRequest(lang, lines)), BulkAddResult.serializer())

    suspend fun editWord(id: String, text: String): ApiResult<WordDto> =
        send("PATCH", AdminRoutes.word(encodeQueryComponent(id)), encode(EditWordRequest.serializer(), EditWordRequest(text)), WordDto.serializer())

    suspend fun removeWord(id: String): ApiResult<WordDto> =
        send("POST", AdminRoutes.wordRemove(encodeQueryComponent(id)), null, WordDto.serializer())

    suspend fun restoreWord(id: String): ApiResult<WordDto> =
        send("POST", AdminRoutes.wordRestore(encodeQueryComponent(id)), null, WordDto.serializer())

    /** One page of suggestions; [query] is a `?…` string of `SuggestionParams`. */
    suspend fun suggestions(query: String): ApiResult<SuggestionPageDto> =
        send("GET", AdminRoutes.SUGGESTIONS + query, null, PageDto.serializer(SuggestionDto.serializer()))

    suspend fun decideSuggestion(id: String, accept: Boolean): ApiResult<SuggestionDto> =
        send("POST", AdminRoutes.suggestionDecision(encodeQueryComponent(id)), encode(DecisionRequest.serializer(), DecisionRequest(accept)), SuggestionDto.serializer())

    /** One page of a calendar's answer pool with its never-used counter; [query] is a `?…` string of `PoolParams`. */
    suspend fun answerPool(calendar: String, query: String): ApiResult<PoolPageDto> =
        send("GET", AdminRoutes.answerPool(encodeQueryComponent(calendar)) + query, null, PoolPageDto.serializer())

    suspend fun poolCandidates(calendar: String, query: String): ApiResult<PageDto<PoolCandidateDto>> =
        send("GET", AdminRoutes.answerPoolCandidates(encodeQueryComponent(calendar)) + query, null, PageDto.serializer(PoolCandidateDto.serializer()))

    suspend fun markWords(calendar: String, request: MarkWordsRequest): ApiResult<List<MarkItemResultDto>> =
        send("POST", AdminRoutes.answerPoolWords(encodeQueryComponent(calendar)), encode(MarkWordsRequest.serializer(), request), ListSerializer(MarkItemResultDto.serializer()))

    suspend fun unmarkWord(calendar: String, wordId: String): ApiResult<Unit> =
        send("DELETE", AdminRoutes.answerPoolWord(encodeQueryComponent(calendar), encodeQueryComponent(wordId)), null, null)

    suspend fun cyrlStatus(cyrl: String): ApiResult<CyrlStatusDto> =
        send("GET", AdminRoutes.ANSWER_POOL_UZ_CYRL_STATUS + queryString(PoolParams.CYRL to cyrl), null, CyrlStatusDto.serializer())

    suspend fun createPair(request: CreatePairRequest): ApiResult<PairDto> =
        send("POST", AdminRoutes.ANSWER_POOL_UZ_PAIRS, encode(CreatePairRequest.serializer(), request), PairDto.serializer())

    suspend fun removePair(pairId: String): ApiResult<Unit> =
        send("DELETE", AdminRoutes.answerPoolUzPair(encodeQueryComponent(pairId)), null, null)

    /** A calendar's days; [query] is a `?…` string of `CalendarParams`. */
    suspend fun calendarDays(calendar: String, query: String): ApiResult<PageDto<DayDto>> =
        send("GET", AdminRoutes.calendar(encodeQueryComponent(calendar)) + query, null, PageDto.serializer(DayDto.serializer()))

    suspend fun calendarCandidates(calendar: String, query: String): ApiResult<PageDto<CalendarCandidateDto>> =
        send("GET", AdminRoutes.calendarCandidates(encodeQueryComponent(calendar)) + query, null, PageDto.serializer(CalendarCandidateDto.serializer()))

    suspend fun pickDay(calendar: String, day: String, request: PickDayRequest): ApiResult<DayDto> =
        send("PUT", AdminRoutes.calendarDay(encodeQueryComponent(calendar), encodeQueryComponent(day)), encode(PickDayRequest.serializer(), request), DayDto.serializer())

    suspend fun unpickDay(calendar: String, day: String): ApiResult<DayDto> =
        send("DELETE", AdminRoutes.calendarDay(encodeQueryComponent(calendar), encodeQueryComponent(day)), null, DayDto.serializer())

    suspend fun calendarNotices(): ApiResult<List<CalendarNoticeDto>> =
        send("GET", AdminRoutes.CALENDAR_NOTICES, null, ListSerializer(CalendarNoticeDto.serializer()))

    suspend fun dismissNotice(id: String): ApiResult<Unit> =
        send("POST", AdminRoutes.calendarNoticeDismiss(encodeQueryComponent(id)), null, null)

    /** One page of player accounts, newest first; [query] carries the filters and the previous page's cursor. */
    suspend fun players(query: PlayerSearchQuery): ApiResult<PlayerSearchPageDto> =
        send("GET", AdminRoutes.PLAYERS + queryString(*query.toQueryParameters().toTypedArray()), null, PlayerSearchPageDto.serializer())

    suspend fun player(id: String): ApiResult<PlayerDetailDto> =
        send("GET", AdminRoutes.player(encodeQueryComponent(id)), null, PlayerDetailDto.serializer())

    /** Deletes the account; the server refuses unless [request] names it again. */
    suspend fun deletePlayer(id: String, request: DeletePlayerRequest): ApiResult<Unit> =
        send("POST", AdminRoutes.playerDelete(encodeQueryComponent(id)), encode(DeletePlayerRequest.serializer(), request), null)

    suspend fun endPlayerSessions(id: String): ApiResult<EndSessionsResultDto> =
        send("POST", AdminRoutes.playerEndSessions(encodeQueryComponent(id)), null, EndSessionsResultDto.serializer())

    suspend fun blockPlayerSuggestions(id: String): ApiResult<PlayerDetailDto> =
        send("PUT", AdminRoutes.playerSuggestionBlock(encodeQueryComponent(id)), null, PlayerDetailDto.serializer())

    suspend fun unblockPlayerSuggestions(id: String): ApiResult<PlayerDetailDto> =
        send("DELETE", AdminRoutes.playerSuggestionBlock(encodeQueryComponent(id)), null, PlayerDetailDto.serializer())

    suspend fun clearPlayerDisplayName(id: String): ApiResult<PlayerDetailDto> =
        send("DELETE", AdminRoutes.playerDisplayName(encodeQueryComponent(id)), null, PlayerDetailDto.serializer())

    /** The analytics overview tiles (ADMIN). */
    suspend fun analyticsOverview(): ApiResult<OverviewDto> =
        send("GET", AdminRoutes.ANALYTICS_OVERVIEW, null, OverviewDto.serializer())

    /** One analytics table as JSON; [query] is the filter's `?…` string for that table (`AnalyticsFilter.queryFor`). */
    suspend fun <T> analytics(table: AnalyticsTable, query: String, serializer: KSerializer<T>): ApiResult<T> =
        send("GET", AdminRoutes.analytics(table) + query, null, serializer)

    /** The request [send] would issue; exposed for tests of headers and credentials. */
    internal fun buildRequest(method: String, path: String, body: String?): HttpRequest {
        val headers = LinkedHashMap<String, String>()
        headers["Accept"] = "application/json"
        if (body != null) headers["Content-Type"] = "application/json"
        if (method.uppercase() in MUTATING_METHODS) {
            readCookie(cookies(), AdminRoutes.XSRF_COOKIE)?.let { headers[AdminRoutes.XSRF_HEADER] = it }
        }
        return HttpRequest(method.uppercase(), baseUrl.trimEnd('/') + path, headers, body, credentials.fetchValue)
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T): String = json.encodeToString(serializer, value)

    private suspend fun <T> send(
        method: String,
        path: String,
        body: String?,
        responseSerializer: KSerializer<T>?,
        isLogin: Boolean = false,
    ): ApiResult<T> {
        val response = transport.send(buildRequest(method, path, body))
        val result = toResult(response, responseSerializer)
        if (response.status == 401 && !isLogin) onUnauthorized()
        return result
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T> toResult(response: HttpResponse, responseSerializer: KSerializer<T>?): ApiResult<T> {
        if (response.status in 200..299) {
            if (responseSerializer == null) return ApiResult.Success(Unit as T)
            return try {
                ApiResult.Success(json.decodeFromString(responseSerializer, response.body))
            } catch (e: Exception) {
                ApiResult.Error(ClientErrors.UNEXPECTED_RESPONSE, "Unreadable response")
            }
        }
        if (response.status == 0) return ApiResult.Error(ClientErrors.NETWORK, "No response")
        val parsed = try {
            json.decodeFromString(ApiErrorResponse.serializer(), response.body)
        } catch (e: Exception) {
            null
        }
        val code = parsed?.error ?: codeForStatus(response.status)
        return ApiResult.Error(code, parsed?.message ?: code)
    }

    private fun codeForStatus(status: Int): String = when (status) {
        401 -> AdminErrors.UNAUTHORIZED
        403 -> AdminErrors.FORBIDDEN
        404 -> AdminErrors.NOT_FOUND
        409 -> AdminErrors.CONFLICT
        422 -> AdminErrors.VALIDATION_FAILED
        423 -> AdminErrors.LOCKED
        429 -> AdminErrors.RATE_LIMITED
        else -> ClientErrors.SERVER
    }

    private companion object {
        val MUTATING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}

/** The field a validation or conflict error names (`field: reason`), if any. */
fun ApiResult.Error.fieldError(): FieldError? = FieldError.parse(message)

/** The value of cookie [name] in a `document.cookie` string, or null. */
fun readCookie(cookieString: String, name: String): String? =
    cookieString.split(';')
        .map { it.trim() }
        .firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")
        ?.takeIf { it.isNotEmpty() }

/** `?a=1&b=2` from the non-null pairs, or an empty string when there are none. */
fun queryString(vararg params: Pair<String, String?>): String {
    val present = params.filter { it.second != null }
    if (present.isEmpty()) return ""
    return present.joinToString("&", prefix = "?") { (name, value) -> "${encodeQueryComponent(name)}=${encodeQueryComponent(value!!)}" }
}

/** Percent-encodes everything outside the RFC 3986 unreserved set, as UTF-8. */
fun encodeQueryComponent(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        val c = b.toChar()
        if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
            append(c)
        } else {
            append('%')
            append(HEX[b shr 4])
            append(HEX[b and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
