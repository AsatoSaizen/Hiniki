/*
 * Copyright © 2015 Javier Tomás
 * Copyright © 2024 The Aniyomi Open Source Project
 * Copyright © 2024 AbandonedCart
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.kanade.tachiyomi.source.model

data class FilterList(val list: List<Filter<*>>) : List<Filter<*>> by list {

    constructor(vararg fs: Filter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())

    override fun equals(other: Any?): Boolean {
        return false
    }

    override fun hashCode(): Int {
        return list.hashCode()
    }
}
