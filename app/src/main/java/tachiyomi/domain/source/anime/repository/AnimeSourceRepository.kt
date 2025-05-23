/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomi.domain.source.anime.repository

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.AnimeSourceWithCount

typealias AnimeSourcePagingSourceType = PagingSource<Long, SAnime>

interface AnimeSourceRepository {

    fun getAnimeSources(): Flow<List<AnimeSource>>

    fun getOnlineAnimeSources(): Flow<List<AnimeSource>>

    fun getAnimeSourcesWithFavoriteCount(): Flow<List<Pair<AnimeSource, Long>>>

    fun getSourcesWithNonLibraryAnime(): Flow<List<AnimeSourceWithCount>>

    fun searchAnime(
        sourceId: Long,
        query: String,
        filterList: AnimeFilterList
    ): AnimeSourcePagingSourceType

    fun getPopularAnime(sourceId: Long): AnimeSourcePagingSourceType

    fun getLatestAnime(sourceId: Long): AnimeSourcePagingSourceType
}
