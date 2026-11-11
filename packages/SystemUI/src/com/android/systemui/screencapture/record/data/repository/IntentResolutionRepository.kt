/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.screencapture.record.data.repository

import android.content.Intent
import android.content.pm.PackageManager
import com.android.systemui.dagger.qualifiers.Background
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

interface IntentResolutionRepository {
    /** Whether the user [userId] has an activity that [intent] resolves to. */
    suspend fun hasActivityFor(intent: Intent, userId: Int): Boolean
}

class IntentResolutionRepositoryImpl
@Inject
constructor(
    private val packageManager: PackageManager,
    @Background private val backgroundContext: CoroutineContext,
) : IntentResolutionRepository {
    override suspend fun hasActivityFor(intent: Intent, userId: Int): Boolean =
        withContext(backgroundContext) {
            packageManager.resolveActivityAsUser(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY,
                userId,
            ) != null
        }
}
