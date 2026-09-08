package uz.abumme.harfgame.data.service

import uz.abumme.harfgame.data.api.ApiResult
import uz.abumme.harfgame.data.suggestion.SuggestWordRequest
import uz.abumme.harfgame.data.suggestion.SuggestWordResponse

interface SuggestionService {
    suspend fun suggest(token: String, request: SuggestWordRequest): ApiResult<SuggestWordResponse>
}
