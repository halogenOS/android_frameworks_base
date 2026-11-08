/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit.shared.model

/** A battery level, or a span of battery levels, in whole percent. */
@JvmInline
value class Percent(val value: Int) : Comparable<Percent> {
    init {
        require(value in 0..100) { "$value is not a percentage" }
    }

    operator fun plus(other: Percent) = Percent(value + other.value)

    operator fun minus(other: Percent) = Percent(value - other.value)

    override fun compareTo(other: Percent) = value.compareTo(other.value)

    companion object {
        val NONE = Percent(0)
        val FULL = Percent(100)
    }
}
