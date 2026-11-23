// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package com.android.settingslib.thememode.ui

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import com.android.settingslib.thememode.R
import com.android.settingslib.thememode.ThemeMode
import com.android.settingslib.thememode.ThemeModeState
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class ThemeModeButtons(private val group: MaterialButtonToggleGroup) {

    val buttons: Map<ThemeMode, MaterialButton> =
        ThemeMode.entries.associateWith { group.requireViewById(it.buttonId) }

    fun show(state: ThemeModeState, @DrawableRes checkIconId: Int) {
        group.check(state.selected.buttonId)
        buttons.forEach { (mode, button) ->
            button.visibility = if (mode in state.available) View.VISIBLE else View.GONE
            button.setIconResource(if (mode == state.selected) checkIconId else 0)
        }
    }

    fun setEnabled(isEnabled: Boolean) {
        buttons.values.forEach { it.isEnabled = isEnabled }
    }

    fun setOnThemeModeClickListener(onClick: (ThemeMode) -> Unit) {
        buttons.forEach { (mode, button) -> button.setOnClickListener { onClick(mode) } }
    }

    @get:IdRes
    private val ThemeMode.buttonId: Int
        get() =
            when (this) {
                ThemeMode.LIGHT -> R.id.theme_mode_light
                ThemeMode.DARK -> R.id.theme_mode_dark
                ThemeMode.DIM -> R.id.theme_mode_dim
            }
}
