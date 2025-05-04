/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.extension

enum class InstallStep {
    Idle, Pending, Downloading, Installing, Installed, Error;

    fun isCompleted(): Boolean {
        return this == Installed || this == Error || this == Idle
    }
}
