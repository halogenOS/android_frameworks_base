/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate

/**
 * Whether the thread sends the samples of the audible track to the output with all of their
 * information, also when it does not copy them unchanged. Null on paths without a mixer, and where
 * no single track is audible or a stage after the mixer processes its output.
 */
val OutputThread.isLossless: Boolean?
    get() {
        val mixer = path as? OutputPath.Mixer ?: return null
        val blockers =
            when (val verdict = bitPerfect) {
                BitPerfectVerdict.BitPerfect -> return true
                is BitPerfectVerdict.NotBitPerfect -> verdict.blockers
            }
        return when {
            blockers.any { it.changesSamples } -> false
            BitPerfectBlocker.MixerStage in blockers -> null
            else -> sources.singleOrNull { it.audible }?.let { !it.losesInformationIn(mixer) }
        }
    }

/**
 * The mixer converts the samples of the track into a sum or output format that does not represent
 * each of them, e.g. 32-bit integer into float or float into an integer output, or sums its
 * channels into fewer.
 */
private fun AudioSource.losesInformationIn(mixer: OutputPath.Mixer): Boolean {
    val sample = format.encoding as? SampleEncoding.Pcm
    val conversions =
        listOfNotNull(mixer.accumulation.encoding, mixer.output.encoding)
            .filterIsInstance<SampleEncoding.Pcm>()
    val channels = format.channelCount
    val outputChannels = mixer.output.channelCount
    return (sample != null && conversions.any { !it.representsEverySampleOf(sample) }) ||
        (channels != null && outputChannels != null && channels > outputChannels)
}
