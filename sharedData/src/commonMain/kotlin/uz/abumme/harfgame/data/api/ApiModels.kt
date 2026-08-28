package uz.abumme.harfgame.data.api

import kotlinx.serialization.Serializable

@Serializable
sealed interface ApiResult<out T> {
    @Serializable
    data class Success<T>(val data: T) : ApiResult<T>

    @Serializable
    data class Error(val code: String, val message: String) : ApiResult<Nothing>
}

@Serializable
data class ApiErrorResponse(
    val error: String,
    val message: String? = null,
)
