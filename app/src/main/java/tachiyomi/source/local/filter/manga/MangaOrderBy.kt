/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomi.source.local.filter.manga

import eu.kanade.tachiyomi.source.model.Filter

sealed class MangaOrderBy(selection: Selection) : Filter.Sort(
    "Order by",
    arrayOf("Title", "Date"),
    selection,
) {
    class Popular : MangaOrderBy(Selection(0, true))
    class Latest : MangaOrderBy(Selection(1, false))
}
