/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.extension.manga.model

sealed class MangaLoadResult {
    class Success(val extension: MangaExtension.Installed) : MangaLoadResult()
    class Untrusted(val extension: MangaExtension.Untrusted) : MangaLoadResult()
    data object Error : MangaLoadResult()
}
