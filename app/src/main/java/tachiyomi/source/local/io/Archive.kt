/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomi.source.local.io

import java.io.File

object ArchiveAnime {

    private val SUPPORTED_ARCHIVE_TYPES = listOf("mp4", "mkv")

    fun isSupported(file: File): Boolean = with(file) {
        return extension.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }
}

object ArchiveManga {

    private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "cbz", "rar", "cbr", "epub")

    fun isSupported(file: File): Boolean = with(file) {
        return extension.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }
}
