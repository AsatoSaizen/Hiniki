/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.PreferenceScreen

interface ConfigurableSource : MangaSource {

    fun setupPreferenceScreen(screen: PreferenceScreen)
}
