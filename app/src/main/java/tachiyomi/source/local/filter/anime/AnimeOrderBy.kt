/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomi.source.local.filter.anime

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

sealed class AnimeOrderBy(selection: Selection) : AnimeFilter.Sort(

    "Order by",
    arrayOf("Title", "Date"),
    selection,
) {
    class Popular : AnimeOrderBy(Selection(0, true))
    class Latest : AnimeOrderBy(Selection(1, false))
}
