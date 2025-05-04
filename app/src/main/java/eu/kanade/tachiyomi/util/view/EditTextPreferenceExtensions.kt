/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("PackageDirectoryMismatch")

package androidx.preference

/**
 * Returns package-private [EditTextPreference.getOnBindEditTextListener]
 */
fun EditTextPreference.getOnBindEditTextListener(): EditTextPreference.OnBindEditTextListener? {
    return onBindEditTextListener
}
