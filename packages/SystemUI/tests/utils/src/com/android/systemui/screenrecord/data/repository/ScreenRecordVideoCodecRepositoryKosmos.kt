/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.screenrecord.data.repository

import com.android.systemui.kosmos.Kosmos

val Kosmos.screenRecordVideoCodecRepository by
    Kosmos.Fixture<ScreenRecordVideoCodecRepository> { ScreenRecordVideoCodecRepositoryImpl() }
