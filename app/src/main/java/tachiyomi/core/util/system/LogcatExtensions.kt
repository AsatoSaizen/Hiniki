/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomi.core.util.system

import logcat.LogPriority
import logcat.asLog
import logcat.logcat

inline fun Any.logcat(
    priority: LogPriority = LogPriority.DEBUG,
    throwable: Throwable? = null,
    message: () -> String = { "" },
) = logcat(priority = priority) {
    var msg = message()
    if (throwable != null) {
        if (msg.isNotBlank()) msg += "\n"
        msg += throwable.asLog()
    }
    msg
}
