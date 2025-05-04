/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomi.domain.source.anime.model

data class AnimeSourceWithCount(
    val source: AnimeSource,
    val count: Long,
) {

    val id: Long
        get() = source.id

    val name: String
        get() = source.name
}
