/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate

/** The audio state of the device at one moment, rebuilt in full on every change. */
data class AudioStateSnapshot(
    val outputPaths: OutputPaths,
    /** One entry per recording in progress. */
    val inputRoutes: List<AudioInputRoute>,
    val outputDevices: List<AudioDevice>,
    val inputDevices: List<AudioDevice>,
    /** Null when the microphone list could not be read. */
    val microphones: List<MicrophoneInfoSummary>?,
)

/** The output threads audioserver reports, or why they could not be read. */
sealed interface OutputPaths {
    /** One route per output thread, in no particular order. */
    data class Read(val routes: List<AudioRoute>) : OutputPaths

    data class Unavailable(val failure: OutputPathFailure) : OutputPaths
}

enum class OutputPathFailure {
    SERVICE_MISSING,
    PERMISSION_DENIED,
    QUERY_FAILED,
}

/** One output thread together with the devices it plays to and the usages routed there. */
data class AudioRoute(
    val thread: OutputThread,
    val sink: Sink,
    /** The usages of the active playbacks routed to the sink devices. */
    val usages: List<AudioUsage>,
)

/** Where an output thread sends its audio. */
sealed interface Sink {
    data class Devices(val devices: List<AudioDevice>) : Sink {
        init {
            require(devices.isNotEmpty())
        }
    }

    /** A duplicating thread, which plays through several other output threads. */
    data object Duplicated : Sink

    /** The thread has no patch to any device. */
    data object NotPatched : Sink

    /** The thread is patched to a device that is not in the device list. */
    data object Unidentified : Sink
}

/** One AudioFlinger output thread. */
data class OutputThread(
    val ioHandle: Int,
    val path: OutputPath,
    val isPlaying: Boolean,
    /** audio_output_flags_t names of the output stream, e.g. "FAST". */
    val flags: List<String>,
    val hardwareFormat: AudioFormatSummary,
    val latencyMillis: Int?,
    /** The active client tracks, in no particular order. */
    val sources: List<AudioSource>,
    /** The output-chain effects, in chain order. */
    val effects: List<AudioEffectSummary>,
    val sinkPortIds: List<Int>,
    val bitPerfect: BitPerfectVerdict,
) {
    val isResampling: Boolean
        get() = sources.any { it.resampling }

    val sourceSampleRatesHz: List<Int>
        get() = sources.mapNotNull { it.format.sampleRateHz }.distinct()
}

/** The AudioFlinger thread type of an output thread. */
sealed interface OutputPath {
    data class Mixer(
        val kind: MixerKind,
        /** The format the mixer writes for the output stream. */
        val output: AudioFormatSummary,
        /** The format the mixer sums the tracks in. */
        val accumulation: AudioFormatSummary,
    ) : OutputPath

    data object Direct : OutputPath

    data object Offload : OutputPath

    data object Mmap : OutputPath
}

enum class MixerKind {
    MIXER,
    DUPLICATING,
    SPATIALIZER,
    BIT_PERFECT,
}

sealed interface BitPerfectVerdict {
    /** AudioFlinger copies the samples of one track to the output unchanged. */
    data object BitPerfect : BitPerfectVerdict

    data class NotBitPerfect(val blockers: List<BitPerfectBlocker>) : BitPerfectVerdict
}

sealed interface BitPerfectBlocker {
    data object MixerStage : BitPerfectBlocker

    data class MultipleTracks(val audibleTracks: Int) : BitPerfectBlocker

    data object Resampling : BitPerfectBlocker
}

/** One active client track of an output thread. */
data class AudioSource(
    val format: AudioFormatSummary,
    /** The track plays at a rate other than the output rate. */
    val resampling: Boolean,
    /** audio_output_flags_t names the track got from AudioFlinger, e.g. "FAST". */
    val trackFlags: List<String>,
    /** The bits of each sample the mixer keeps, when fewer than the sample carries. */
    val keptBits: Int?,
)

data class AudioEffectSummary(
    /** Null when the effect has no name. */
    val name: String?,
    val typeUuid: String,
    val enabled: Boolean,
)

data class AudioInputRoute(
    val source: CaptureSource,
    /** The format the device records in. */
    val captureFormat: AudioFormatSummary?,
    val inputDevice: AudioDevice?,
)

data class AudioDevice(
    /** AudioDeviceInfo.getId(), also the audio_port_handle_t of the device. */
    val id: Int,
    /** The product name, or null when the device reports none. */
    val name: String?,
    /** Null for a device type this model does not know. */
    val kind: AudioDeviceKind?,
    /** Audio of an active playback or recording is routed to this device. */
    val isActive: Boolean,
    val address: String?,
    /** Empty when the device takes any rate. */
    val sampleRates: List<Int>,
    /** Empty when the device takes any channel count. */
    val channelCounts: List<Int>,
    /** Empty when the device takes any encoding. */
    val encodings: List<SampleEncoding>,
    /** The battery of a Bluetooth accessory, 0 to 100. */
    val batteryPercent: Int?,
) {
    val isHostEndpoint: Boolean
        get() = kind?.isHostEndpoint == true
}

data class AudioFormatSummary(
    val sampleRateHz: Int?,
    val channelCount: Int?,
    val encoding: SampleEncoding?,
)

sealed interface SampleEncoding {
    data class Pcm(val bitsPerSample: Int, val isFloat: Boolean) : SampleEncoding

    /** An encoding other than linear PCM, by its platform name, e.g. "AAC_LC". */
    data class Other(val name: String) : SampleEncoding
}

data class MicrophoneInfoSummary(
    val description: String,
    val address: String?,
    val location: MicrophoneLocation?,
    val directionality: MicrophoneDirectionality?,
)

enum class AudioUsage {
    MEDIA,
    CALL,
    ALARM,
    NOTIFICATION,
    RINGTONE,
    GAME,
    ASSISTANT,
    OTHER,
}

enum class CaptureSource {
    MIC,
    VOICE_RECOGNITION,
    VOICE_COMMUNICATION,
    CAMCORDER,
    UNPROCESSED,
    VOICE_UPLINK,
    VOICE_DOWNLINK,
    VOICE_CALL,
    VOICE_PERFORMANCE,
    REMOTE_SUBMIX,
    OTHER,
}

enum class MicrophoneLocation {
    MAIN_BODY,
    MAIN_BODY_MOVABLE,
    PERIPHERAL,
}

enum class MicrophoneDirectionality {
    OMNI,
    BI_DIRECTIONAL,
    CARDIOID,
    HYPER_CARDIOID,
    SUPER_CARDIOID,
}
