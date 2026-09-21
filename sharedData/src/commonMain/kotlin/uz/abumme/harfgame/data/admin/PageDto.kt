package uz.abumme.harfgame.data.admin

import kotlinx.serialization.Serializable

/** One page of a server-paged list. [page] is zero-based; [total] counts every matching item. */
@Serializable
data class PageDto<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
)
