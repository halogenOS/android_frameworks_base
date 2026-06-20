/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate

import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import custom.media.audio_information.AudioEffectInfo
import custom.media.audio_information.AudioFormatInfo
import custom.media.audio_information.AudioPathInfo
import custom.media.audio_information.AudioPathType
import custom.media.audio_information.AudioSourceInfo
import custom.media.audio_information.BitPerfectReason
import custom.media.audio_information.IAudioInformation

internal sealed interface OutputThreads {
    data class Read(val threads: List<OutputThread>) : OutputThreads

    data class Unavailable(val failure: OutputPathFailure) : OutputThreads
}

/** Reads the output threads from the audio information service of audioserver. */
internal class AudioFlingerInfoProvider {

    fun readOutputThreads(): OutputThreads {
        // Looked up on every read, so a restarted audioserver is found again.
        val service =
            ServiceManager.checkService(IAudioInformation.SERVICE_NAME)
                ?.let(IAudioInformation.Stub::asInterface)
                ?: return unavailable(OutputPathFailure.SERVICE_MISSING, cause = null)
        return try {
            OutputThreads.Read(service.listActiveAudioPaths().mapNotNull { it.toOutputThread() })
        } catch (e: SecurityException) {
            unavailable(OutputPathFailure.PERMISSION_DENIED, e)
        } catch (e: RemoteException) {
            unavailable(OutputPathFailure.QUERY_FAILED, e)
        }
    }

    private fun unavailable(failure: OutputPathFailure, cause: Exception?): OutputThreads {
        Log.w(TAG, "audio paths unavailable: $failure", cause)
        return OutputThreads.Unavailable(failure)
    }

    private fun AudioPathInfo.toOutputThread(): OutputThread? {
        val path =
            toOutputPath()
                ?: run {
                    Log.w(TAG, "path $ioHandle has type $pathType without a matching mixer")
                    return null
                }
        return OutputThread(
            ioHandle = ioHandle,
            path = path,
            isPlaying = playing,
            flags = outputFlags.toList(),
            hardwareFormat = hardwareFormat.toSummary(),
            latencyMillis = latencyMs.takeUnless { path == OutputPath.Mmap },
            sources = sources.map { it.toAudioSource(path) },
            effects = effects.map { it.toSummary() },
            sinkPortIds = sinkPortIds.toList(),
            bitPerfect =
                if (bitPerfect) {
                    BitPerfectVerdict.BitPerfect
                } else {
                    BitPerfectVerdict.NotBitPerfect(
                        bitPerfectReasons.asList().mapNotNull { blockerOf(it, audibleTrackCount) }
                    )
                },
        )
    }

    private fun AudioPathInfo.toOutputPath(): OutputPath? {
        val kind =
            when (pathType) {
                AudioPathType.DIRECT -> return OutputPath.Direct
                AudioPathType.OFFLOAD -> return OutputPath.Offload
                AudioPathType.MMAP -> return OutputPath.Mmap
                AudioPathType.MIXER -> MixerKind.MIXER
                AudioPathType.DUPLICATING -> MixerKind.DUPLICATING
                AudioPathType.SPATIALIZER -> MixerKind.SPATIALIZER
                AudioPathType.BIT_PERFECT -> MixerKind.BIT_PERFECT
                else -> return null
            }
        val stage = mixer ?: return null
        return OutputPath.Mixer(
            kind = kind,
            output = stage.output.toSummary(),
            accumulation = stage.accumulation.toSummary(),
        )
    }

    private fun blockerOf(reason: Int, audibleTracks: Int): BitPerfectBlocker? =
        when (reason) {
            BitPerfectReason.MIXER_STAGE -> BitPerfectBlocker.MixerStage
            BitPerfectReason.MULTIPLE_TRACKS -> BitPerfectBlocker.MultipleTracks(audibleTracks)
            BitPerfectReason.RESAMPLING -> BitPerfectBlocker.Resampling
            else -> null
        }

    private fun AudioSourceInfo.toAudioSource(path: OutputPath): AudioSource {
        val summary = format.toSummary()
        return AudioSource(
            format = summary,
            resampling = resampling,
            trackFlags = outputFlags.toList(),
            keptBits = keptBits(summary, path),
        )
    }

    /** The bits a float sum keeps of an integer sample wider than the float significand. */
    private fun keptBits(source: AudioFormatSummary, path: OutputPath): Int? {
        val sample = source.encoding as? SampleEncoding.Pcm ?: return null
        val sum = (path as? OutputPath.Mixer)?.accumulation?.encoding as? SampleEncoding.Pcm
        return FLOAT_SIGNIFICAND_BITS.takeIf {
            sum?.isFloat == true && !sample.isFloat && sample.bitsPerSample > it
        }
    }

    private fun AudioEffectInfo.toSummary(): AudioEffectSummary =
        AudioEffectSummary(
            name = name.takeIf { it.isNotBlank() },
            typeUuid = typeUuid,
            // A suspended effect does not process audio.
            enabled = enabled && !suspended,
        )

    private fun AudioFormatInfo.toSummary(): AudioFormatSummary =
        AudioFormatSummary(
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            encoding =
                pcm?.let { SampleEncoding.Pcm(it.bitsPerSample, it.isFloat) }
                    ?: encoding.takeIf { it.isNotEmpty() }?.let { SampleEncoding.Other(it) },
        )

    private companion object {
        const val TAG = "AudioFlingerInfo"

        /** IEEE 754 binary32 significand precision, the sample bits a float sum keeps. */
        const val FLOAT_SIGNIFICAND_BITS = 24
    }
}
