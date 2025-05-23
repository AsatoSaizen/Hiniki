/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.Page
import rx.Observable

fun HttpSource.fetchAllImageUrlsFromPageList(pages: List<Page>): Observable<Page> {
    return Observable.from(pages)
        .filter { !it.imageUrl.isNullOrEmpty() }
        .mergeWith(fetchRemainingImageUrlsFromPageList(pages))
}

private fun HttpSource.fetchRemainingImageUrlsFromPageList(pages: List<Page>): Observable<Page> {
    return Observable.from(pages)
        .filter { it.imageUrl.isNullOrEmpty() }
        .concatMap { getImageUrl(it) }
}

private fun HttpSource.getImageUrl(page: Page): Observable<Page> {
    page.status = Page.State.LOAD_PAGE
    return fetchImageUrl(page)
        .doOnError { page.status = Page.State.ERROR }
        .onErrorReturn { null }
        .doOnNext { page.imageUrl = it }
        .map { page }
}
