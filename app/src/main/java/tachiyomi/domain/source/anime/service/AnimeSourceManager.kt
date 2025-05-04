/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomi.domain.source.anime.service

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.anime.model.StubAnimeSource

interface AnimeSourceManager {

    val catalogueSources: Flow<List<AnimeCatalogueSource>>

    fun get(sourceKey: Long): AnimeSource?

    fun getOrStub(sourceKey: Long): AnimeSource

    fun getOnlineSources(): List<AnimeHttpSource>

    fun getCatalogueSources(): List<AnimeCatalogueSource>

    fun getStubSources(): List<StubAnimeSource>
}
