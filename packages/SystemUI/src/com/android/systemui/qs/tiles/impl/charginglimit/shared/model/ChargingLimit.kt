/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit.shared.model

/** The charge limit the user set. */
sealed interface ChargingLimit {

    /** This limit with the battery level set to [level], with as much of the variation as fits. */
    fun withLevel(level: Percent): On

    /** The battery charges to full. */
    data object Off : ChargingLimit {
        override fun withLevel(level: Percent) = On(level)
    }

    /**
     * Charging stops at [level], and the battery stays between [level] and [level] plus
     * [variation].
     */
    data class On(val level: Percent, val variation: Percent = Percent.NONE) : ChargingLimit {
        init {
            require(variation <= maxVariation) { "$level plus $variation is above a full battery" }
        }

        /** The largest variation that fits between [level] and a full battery. */
        val maxVariation: Percent
            get() = Percent.FULL - level

        /** The highest level the battery reaches. */
        val top: Percent
            get() = level + variation

        /** Whether the battery stays at [level] alone. */
        val isExact: Boolean
            get() = variation == Percent.NONE

        override fun withLevel(level: Percent) = On(level, minOf(variation, Percent.FULL - level))
    }
}
