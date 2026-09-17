package uz.abumme.harfgame.admin.components

/** Server paging of a table: the zero-based [page], its [size], and the [total] the server reported. */
data class PagingState(val page: Int = 0, val size: Int = 50, val total: Long = 0) {
    init {
        require(size > 0) { "page size must be positive" }
    }

    val pageCount: Int get() = maxOf(1, ((total + size - 1) / size).toInt())
    val hasPrevious: Boolean get() = page > 0
    val hasNext: Boolean get() = page + 1 < pageCount

    /** 1-based number of the first item on this page, 0 when empty. */
    val firstItem: Long get() = if (total == 0L) 0 else page.toLong() * size + 1
    val lastItem: Long get() = minOf(total, (page.toLong() + 1) * size)

    fun next(): PagingState = goTo(page + 1)
    fun previous(): PagingState = goTo(page - 1)
    fun goTo(target: Int): PagingState = copy(page = target.coerceIn(0, pageCount - 1))

    /** The server's answer for this page; a page beyond the new total moves back to the last one. */
    fun withTotal(newTotal: Long): PagingState = copy(total = newTotal).let { it.goTo(it.page) }

    /** Any filter change starts again from the first page. */
    fun resetForFilterChange(): PagingState = copy(page = 0)
}
