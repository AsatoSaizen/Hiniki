/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomi.domain.source.manga.service

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.manga.model.StubMangaSource

interface MangaSourceManager {

    val catalogueSources: Flow<List<CatalogueSource>>

    fun get(sourceKey: Long): MangaSource?

    fun getOrStub(sourceKey: Long): MangaSource

    fun getOnlineSources(): List<HttpSource>

    fun getCatalogueSources(): List<CatalogueSource>

    fun getStubSources(): List<StubMangaSource>
}
