package com.mmusic.app.data

import android.content.Context
import android.os.Environment
import android.os.Build
import android.os.storage.StorageManager
import java.io.File

object StorageDetector {
    fun detectSources(context: Context, existing: List<SourceConfig>, serverConnected: Boolean): List<SourceConfig> {
        val internalDir = "/storage/emulated/0"
        val removableCandidates = detectRemovableCandidates(context)

        val sdPath = removableCandidates.firstOrNull { (_, description) ->
            description.contains("sd") || description.contains("card")
        }?.first ?: removableCandidates.firstOrNull { (path, _) ->
            !path.contains("usb", ignoreCase = true) && !path.contains("otg", ignoreCase = true)
        }?.first.orEmpty()

        val usbPath = removableCandidates.firstOrNull { (_, description) ->
            description.contains("usb") || description.contains("otg")
        }?.first ?: removableCandidates.firstOrNull { (path, _) ->
            path.contains("usb", ignoreCase = true) || path.contains("otg", ignoreCase = true)
        }?.first.orEmpty()

        fun folderManaged(type: MusicSourceType): Boolean =
            existing.firstOrNull { it.type == type }?.folderManagementEnabled ?: false
        fun enabled(type: MusicSourceType): Boolean =
            existing.firstOrNull { it.type == type }?.enabled ?: when (type) {
                MusicSourceType.Internal -> true
                MusicSourceType.Server -> serverConnected
                else -> false
            }
        fun selectedFolder(type: MusicSourceType, fallback: String): String =
            existing.firstOrNull { it.type == type }?.selectedFolder?.ifBlank { fallback } ?: fallback
        fun availableFolders(type: MusicSourceType): List<String> =
            existing.firstOrNull { it.type == type }?.availableFolders ?: emptyList()
        fun selectedFormats(type: MusicSourceType): List<String> =
            existing.firstOrNull { it.type == type }?.selectedFormats ?: emptyList()
        fun durationFilter(type: MusicSourceType): DurationFilter =
            existing.firstOrNull { it.type == type }?.durationFilter ?: DurationFilter.All

        return listOf(
            SourceConfig(
                type = MusicSourceType.Internal,
                connected = true,
                enabled = enabled(MusicSourceType.Internal),
                folderManagementEnabled = folderManaged(MusicSourceType.Internal),
                selectedFolder = selectedFolder(MusicSourceType.Internal, internalDir),
                availableFolders = availableFolders(MusicSourceType.Internal),
                selectedFormats = selectedFormats(MusicSourceType.Internal),
                durationFilter = durationFilter(MusicSourceType.Internal)
            ),
            SourceConfig(
                type = MusicSourceType.SdCard,
                connected = sdPath.isNotBlank(),
                enabled = enabled(MusicSourceType.SdCard),
                folderManagementEnabled = folderManaged(MusicSourceType.SdCard),
                selectedFolder = selectedFolder(MusicSourceType.SdCard, sdPath),
                availableFolders = availableFolders(MusicSourceType.SdCard),
                selectedFormats = selectedFormats(MusicSourceType.SdCard),
                durationFilter = durationFilter(MusicSourceType.SdCard)
            ),
            SourceConfig(
                type = MusicSourceType.UsbOtg,
                connected = usbPath.isNotBlank(),
                enabled = enabled(MusicSourceType.UsbOtg),
                folderManagementEnabled = folderManaged(MusicSourceType.UsbOtg),
                selectedFolder = selectedFolder(MusicSourceType.UsbOtg, usbPath),
                availableFolders = availableFolders(MusicSourceType.UsbOtg),
                selectedFormats = selectedFormats(MusicSourceType.UsbOtg),
                durationFilter = durationFilter(MusicSourceType.UsbOtg)
            ),
            SourceConfig(
                type = MusicSourceType.Server,
                connected = serverConnected,
                enabled = enabled(MusicSourceType.Server),
                folderManagementEnabled = folderManaged(MusicSourceType.Server),
                selectedFolder = selectedFolder(MusicSourceType.Server, "/remote/library"),
                availableFolders = availableFolders(MusicSourceType.Server),
                selectedFormats = selectedFormats(MusicSourceType.Server),
                durationFilter = durationFilter(MusicSourceType.Server),
                autoDetected = false
            )
        )
    }

    private fun detectRemovableCandidates(context: Context): List<Pair<String, String>> {
        val externalDirs = context.getExternalFilesDirs(null).filterNotNull()
        val removablePaths = externalDirs
            .filter { Environment.isExternalStorageRemovable(it) }
            .map { it.absolutePath.substringBefore("/Android/") }

        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val removableVolumes = storageManager.storageVolumes.filter { it.isRemovable }

        val volumeCandidates = removablePaths.mapIndexed { index, path ->
            path to removableVolumes.getOrNull(index)?.getDescription(context).orEmpty().lowercase()
        }

        val storageRootCandidates = File("/storage")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .map { it.absolutePath }
            .filterNot { path ->
                path.equals("/storage/emulated", ignoreCase = true) ||
                    path.equals("/storage/self", ignoreCase = true)
            }
            .filterNot { path ->
                path.startsWith("/storage/emulated/0", ignoreCase = true)
            }
            .map { path ->
                val lower = path.lowercase()
                val description = when {
                    lower.contains("usb") || lower.contains("otg") -> "usb otg"
                    lower.contains("sd") || lower.contains("card") || lower.matches(Regex(".*/[0-9a-f]{4}-[0-9a-f]{4}$")) -> "sd card"
                    else -> "removable"
                }
                path to description
            }

        val volumeDirectoryCandidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            removableVolumes.mapNotNull { volume ->
                volume.directory?.absolutePath?.let { it to volume.getDescription(context).orEmpty().lowercase() }
            }
        } else {
            emptyList()
        }

        return (volumeCandidates + volumeDirectoryCandidates + storageRootCandidates)
            .map { (path, description) -> path.trimEnd('/') to description }
            .distinctBy { it.first.lowercase() }
    }
}
