/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.pipeline.domain.interactor

import android.content.ComponentName
import android.content.pm.ServiceInfo
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.qs.pipeline.data.repository.InstalledTilesComponentRepository
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.pipeline.shared.logging.QSPipelineLogger
import javax.inject.Inject

/**
 * Puts the tile of an installed tile service in place of a saved tile whose service is no longer
 * installed, when the installed service sets [META_DATA_REPLACED_TILE] to that earlier service.
 *
 * Only a tile service of a system app takes over a position, so an app cannot take the place of the
 * tile of another app.
 */
@SysUISingleton
class TileReplacementInteractor
@Inject
constructor(
    private val installedTilesComponentRepository: InstalledTilesComponentRepository,
    private val logger: QSPipelineLogger,
) {

    /**
     * Replaces each tile of [userId] whose service is not installed with the installed tile that
     * sets [META_DATA_REPLACED_TILE] to it. A replaced tile is dropped when [tiles] already contain
     * its replacement.
     *
     * Which tiles are not installed and which tiles replace them both come from one list of
     * installed services, so a tile whose service is in that list is never replaced.
     */
    fun replaceNotInstalledTiles(userId: Int, tiles: List<TileSpec>): List<TileSpec> {
        val services = installedTilesComponentRepository.getInstalledTilesServiceInfos(userId)
        val installedComponents = services.map { it.componentName }
        val replacementsOfNotInstalledTiles =
            services.filter { it.applicationInfo.isSystemApp }.groupBy { it.replacedTile } -
                installedComponents
        return tiles.mapNotNull { tile ->
            val replacements =
                (tile as? TileSpec.CustomTileSpec)
                    ?.let { replacementsOfNotInstalledTiles[it.componentName] }
                    .orEmpty()
                    .map { TileSpec.create(it.componentName) }
            when (replacements.size) {
                0 -> tile
                1 -> {
                    val replacement = replacements.single()
                    val replacementAlreadyAdded = replacement in tiles
                    logger.logTileReplaced(tile, replacement, replacementAlreadyAdded, userId)
                    replacement.takeUnless { replacementAlreadyAdded }
                }
                else -> tile.also { logger.logTileReplacementConflict(it, replacements, userId) }
            }
        }
    }
}

/**
 * Meta-data of a tile service: the flattened [ComponentName] of the earlier tile service that it
 * replaces.
 */
private const val META_DATA_REPLACED_TILE = "android.service.quicksettings.REPLACED_TILE"

private val ServiceInfo.replacedTile: ComponentName?
    get() = metaData?.getString(META_DATA_REPLACED_TILE)?.let(ComponentName::unflattenFromString)
