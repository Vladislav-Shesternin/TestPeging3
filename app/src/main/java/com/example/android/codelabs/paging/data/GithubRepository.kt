package com.example.android.codelabs.paging.data

import com.example.android.codelabs.paging.api.GithubService
import com.example.android.codelabs.paging.api.IN_QUALIFIER
import com.example.android.codelabs.paging.model.Repo
import com.example.android.codelabs.paging.model.RepoSearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import retrofit2.HttpException
import java.io.IOException

class GithubRepository(private val service: GithubService) {

    private val inMemoryCache = mutableListOf<Repo>()
    private val searchResults = MutableSharedFlow<RepoSearchResult>(replay = 1)
    private var lastRequestedPage = GITHUB_STARTING_PAGE_INDEX
    private var isRequestInProgress = false

    suspend fun getSearchResultStream(query: String): Flow<RepoSearchResult> {
        lastRequestedPage = 1
        inMemoryCache.clear()
        requestAndSaveData(query)

        return searchResults
    }

    suspend fun requestMore(query: String) {
        if (isRequestInProgress) return
        val successful = requestAndSaveData(query)
        if (successful) {
            lastRequestedPage++
        }
    }

    suspend fun retry(query: String) {
        if (isRequestInProgress) return
        requestAndSaveData(query)
    }

    private suspend fun requestAndSaveData(query: String): Boolean {
        isRequestInProgress = true
        var successful = false

        val apiQuery = query + IN_QUALIFIER
        try {

            val response = service.searchRepos(apiQuery, lastRequestedPage, NETWORK_PAGE_SIZE)
            val repos = response.items

            inMemoryCache.addAll(repos)

            val reposByName = reposByName(query)
            searchResults.emit(RepoSearchResult.Success(reposByName))

            successful = true

        } catch (exception: IOException) {
            searchResults.emit(RepoSearchResult.Error(exception))
        } catch (exception: HttpException) {
            searchResults.emit(RepoSearchResult.Error(exception))
        }
        isRequestInProgress = false
        return successful
    }

    private fun reposByName(query: String): List<Repo> {
        return inMemoryCache.filter {
            it.name.contains(query, true) ||
                    (it.description != null && it.description.contains(query, true))
        }.sortedWith(compareByDescending<Repo> { it.stars }.thenBy { it.name })
    }
}
