/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 Diegopyl1209
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.data.torrentServer.model

import kotlinx.serialization.Serializable

@Serializable
data class TorrServVersion(
    val version: String,
    val links: Map<String, String>,
)
