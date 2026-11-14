/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.screenrecord

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.android.systemui.screencapture.record.ui.compose.labelRes
import com.android.systemui.screenrecord.shared.model.ScreenRecordVideoCodec

/** Shows each of [codecs] in a spinner by its label. */
internal class ScreenRecordVideoCodecAdapter(
    context: Context,
    private val codecs: List<ScreenRecordVideoCodec>,
) : ArrayAdapter<ScreenRecordVideoCodec>(context, android.R.layout.simple_spinner_item, codecs) {

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        super.getView(position, convertView, parent).showingLabelOf(codecs[position])

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        super.getDropDownView(position, convertView, parent).showingLabelOf(codecs[position])

    private fun View.showingLabelOf(codec: ScreenRecordVideoCodec): View = apply {
        (this as TextView).setText(codec.labelRes)
    }
}
