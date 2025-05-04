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
data class TorrentDetails(
    val Title: String,
    val Name: String,
    val Names: List<String>,
    val Categories: String,
    val Size: String,
    val CreateDate: String,
    val Tracker: String,
    val Link: String,
    val Year: Int,
    val Peer: Int,
    val Seed: Int,
    val Magnet: String,
    val Hash: String,
    val IMDBID: String,
    val VideoQuality: Int,
    val AudioQuality: Int,
)
