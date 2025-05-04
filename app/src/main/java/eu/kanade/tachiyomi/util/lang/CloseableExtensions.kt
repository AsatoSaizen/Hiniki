/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.util.lang

import java.io.Closeable

/**
 * Executes the given block function on this resources and then closes it down correctly whether an exception is
 * thrown or not.
 *
 * @param block a function to process with given Closeable resources.
 * @return the result of block function invoked on this resource.
 */
inline fun <T : Closeable?> Array<T>.use(block: () -> Unit) {
    var blockException: Throwable? = null
    try {
        return block()
    } catch (e: Throwable) {
        blockException = e
        throw e
    } finally {
        when (blockException) {
            null -> forEach { it?.close() }
            else -> forEach {
                try {
                    it?.close()
                } catch (closeException: Throwable) {
                    blockException.addSuppressed(closeException)
                }
            }
        }
    }
}
