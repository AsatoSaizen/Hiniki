/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomi.domain.entries.manga.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import tachiyomi.domain.entries.TriStateFilter
import java.io.Serializable

data class Manga(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val lastUpdate: Long,
    val nextUpdate: Long,
    val calculateInterval: Int,
    val dateAdded: Long,
    val viewerFlags: Long,
    val chapterFlags: Long,
    val coverLastModified: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Long,
    val thumbnailUrl: String?,
    val updateStrategy: UpdateStrategy,
    val initialized: Boolean,
) : Serializable {

    val sorting: Long
        get() = chapterFlags and CHAPTER_SORTING_MASK

    val displayMode: Long
        get() = chapterFlags and CHAPTER_DISPLAY_MASK

    val unreadFilterRaw: Long
        get() = chapterFlags and CHAPTER_UNREAD_MASK

    val downloadedFilterRaw: Long
        get() = chapterFlags and CHAPTER_DOWNLOADED_MASK

    val bookmarkedFilterRaw: Long
        get() = chapterFlags and CHAPTER_BOOKMARKED_MASK

    val unreadFilter: TriStateFilter
        get() = when (unreadFilterRaw) {
            CHAPTER_SHOW_UNREAD -> TriStateFilter.ENABLED_IS
            CHAPTER_SHOW_READ -> TriStateFilter.ENABLED_NOT
            else -> TriStateFilter.DISABLED
        }

    val bookmarkedFilter: TriStateFilter
        get() = when (bookmarkedFilterRaw) {
            CHAPTER_SHOW_BOOKMARKED -> TriStateFilter.ENABLED_IS
            CHAPTER_SHOW_NOT_BOOKMARKED -> TriStateFilter.ENABLED_NOT
            else -> TriStateFilter.DISABLED
        }

    fun sortDescending(): Boolean {
        return chapterFlags and CHAPTER_SORT_DIR_MASK == CHAPTER_SORT_DESC
    }

    companion object {
        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000L

        const val CHAPTER_SORT_DESC = 0x00000000L
        const val CHAPTER_SORT_ASC = 0x00000001L
        const val CHAPTER_SORT_DIR_MASK = 0x00000001L

        const val CHAPTER_SHOW_UNREAD = 0x00000002L
        const val CHAPTER_SHOW_READ = 0x00000004L
        const val CHAPTER_UNREAD_MASK = 0x00000006L

        const val CHAPTER_SHOW_DOWNLOADED = 0x00000008L
        const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010L
        const val CHAPTER_DOWNLOADED_MASK = 0x00000018L

        const val CHAPTER_SHOW_BOOKMARKED = 0x00000020L
        const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040L
        const val CHAPTER_BOOKMARKED_MASK = 0x00000060L

        const val CHAPTER_SORTING_SOURCE = 0x00000000L
        const val CHAPTER_SORTING_NUMBER = 0x00000100L
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200L
        const val CHAPTER_SORTING_MASK = 0x00000300L

        const val CHAPTER_DISPLAY_NAME = 0x00000000L
        const val CHAPTER_DISPLAY_NUMBER = 0x00100000L
        const val CHAPTER_DISPLAY_MASK = 0x00100000L

        fun create() = Manga(
            id = -1L,
            url = "",
            title = "",
            source = -1L,
            favorite = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            calculateInterval = 0,
            dateAdded = 0L,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            artist = null,
            author = null,
            description = null,
            genre = null,
            status = 0L,
            thumbnailUrl = null,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = false,
        )
    }
}
