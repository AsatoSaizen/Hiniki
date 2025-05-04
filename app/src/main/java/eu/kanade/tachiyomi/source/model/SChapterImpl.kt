/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.source.model

class SChapterImpl : SChapter {

    override lateinit var url: String

    override lateinit var name: String

    override var date_upload: Long = 0

    override var chapter_number: Float = -1f

    override var scanlator: String? = null
}
