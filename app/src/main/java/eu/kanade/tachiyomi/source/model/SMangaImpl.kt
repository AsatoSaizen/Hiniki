/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.source.model

class SMangaImpl : SManga {

    override lateinit var url: String

    override lateinit var title: String

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    override var initialized: Boolean = false
}
