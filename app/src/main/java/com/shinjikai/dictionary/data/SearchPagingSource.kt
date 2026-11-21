package com.shinjikai.dictionary.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.shinjikai.dictionary.ui.ResultMode

data class SearchRequestSpec(
    val mode: ResultMode,
    val query: String = "",
    val categoryId: Int? = null,
    val categoryName: String? = null
)

class SearchPagingSource(
    private val repository: ShinjikaiRepository,
    private val spec: SearchRequestSpec,
    private val onTotalCount: ((Int) -> Unit)? = null
) : PagingSource<Int, SearchItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SearchItem> {
        val pageIndex = params.key ?: 0
        val result = when (spec.mode) {
            ResultMode.Search -> repository.searchWords(term = spec.query, page = pageIndex)
            ResultMode.Category -> {
                val categoryId = spec.categoryId
                    ?: return LoadResult.Error(IllegalStateException("Missing category id"))
                repository.loadCategory(id = categoryId, page = pageIndex).map { it.members }
            }
            ResultMode.None -> Result.success(SearchWordsResponse())
        }

        return result.fold(
            onSuccess = { response ->
                val currentPage = response.page.coerceAtLeast(pageIndex)
                onTotalCount?.invoke(maxOf(response.totalCount, response.items.size))
                LoadResult.Page(
                    data = response.items,
                    prevKey = currentPage.takeIf { it > 0 }?.minus(1),
                    nextKey = (currentPage + 1).takeIf { it < response.pageCount }
                )
            },
            onFailure = { throwable ->
                LoadResult.Error(throwable)
            }
        )
    }

    override fun getRefreshKey(state: PagingState<Int, SearchItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchor) ?: return null
        return anchorPage.prevKey?.plus(1) ?: anchorPage.nextKey?.minus(1)
    }
}
