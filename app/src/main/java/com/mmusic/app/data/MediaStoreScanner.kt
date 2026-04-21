package com.mmusic.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.MediaStore.Audio.AudioColumns
import java.io.File

object MediaStoreScanner {
    private const val INTERNAL_ROOT = "/storage/emulated/0"
    private const val SDCARD_ALIAS = "/sdcard"
    private const val SELF_PRIMARY_ALIAS = "/storage/self/primary"
    private const val MNT_SDCARD_ALIAS = "/mnt/sdcard"
    private val audioExtensions = setOf("mp3", "flac", "wav", "wave", "m4a", "ogg", "aac", "opus", "wma", "alac", "aiff", "ape", "amr", "3gp", "mid", "midi", "mp2", "mka", "webm", "ac3", "dsf", "dff", "tta", "mp1", "mpga")
    val supportedAudioExtensions: List<String> = audioExtensions.toList().sorted()
    private val internalStorageMarkers = listOf(
        INTERNAL_ROOT,
        SDCARD_ALIAS,
        SELF_PRIMARY_ALIAS,
        "/emulated/0"
    )

    fun scanAudio(context: Context, sources: List<SourceConfig>): List<MusicTrack> {
        val enabledSources = sources.filter { it.enabled }
        val mediaStoreTracks = scanFromMediaStore(context)
        val fileSystemTracks = scanFromFileSystem(context)
        val mergedTracks = (mediaStoreTracks + fileSystemTracks)
            .distinctBy { it.contentUri.ifBlank { "${it.title}_${it.artist}_${it.folder}" } }
        return filterBySources(mergedTracks, enabledSources)
    }

    private fun scanFromMediaStore(context: Context): List<MusicTrack> {
        val resolver = context.contentResolver
        val tracks = mutableListOf<MusicTrack>()
        val volumeNames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (MediaStore.getExternalVolumeNames(context) + setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY, MediaStore.VOLUME_INTERNAL)).toList()
        } else {
            listOf("external", "internal")
        }

        volumeNames.forEach { volumeName ->
            runCatching {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(volumeName)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val projection = mutableListOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION
                ).apply {
                    add(MediaStore.Audio.Media.RELATIVE_PATH)
                    add(AudioColumns.ALBUM_ID)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        add(MediaStore.Audio.Media.DATA)
                    }
                }.toTypedArray()

                resolver.query(
                    collection,
                    projection,
                    "${MediaStore.Audio.Media.SIZE} > 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
                    null,
                    "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val relativePathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    val albumIdIndex = cursor.getColumnIndex(AudioColumns.ALBUM_ID)
                    val dataIndex = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Audio.Media.DATA) else -1

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val contentUri = Uri.withAppendedPath(collection, id.toString())
                        val title = cursor.getString(titleIndex).orEmpty().ifBlank { "Unknown title" }
                        val artist = cursor.getString(artistIndex).orEmpty().ifBlank { "Unknown artist" }
                        val album = cursor.getString(albumIndex).orEmpty().ifBlank { "Unknown album" }
                        val durationMs = cursor.getLong(durationIndex)
                        val relativePath = if (relativePathIndex >= 0) cursor.getString(relativePathIndex).orEmpty() else ""
                        val albumId = if (albumIdIndex >= 0) cursor.getLong(albumIdIndex) else -1L
                        val dataPath = if (dataIndex >= 0) cursor.getString(dataIndex).orEmpty() else ""
                        val rawFolder = when {
                            dataPath.isNotBlank() -> dataPath.substringBeforeLast("/", dataPath)
                            relativePath.isNotBlank() -> "/$relativePath".trimEnd('/')
                            else -> "/"
                        }
                        val sourceType = classifySource(context, volumeName, rawFolder)
                        val folder = normalizeDisplayFolder(rawFolder, sourceType)

                        tracks += MusicTrack(
                            id = "local_${volumeName}_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            artworkUri = buildAlbumArtUri(albumId),
                            folder = folder,
                            sourceType = sourceType,
                            duration = formatDuration(durationMs),
                            durationMs = durationMs,
                            streamUrl = contentUri.toString(),
                            contentUri = contentUri.toString(),
                            isLocalFile = true
                        )
                    }
                }
            }
        }

        return tracks.distinctBy { it.contentUri }
    }

    private fun scanFromFileSystem(context: Context): List<MusicTrack> {
        val roots = linkedSetOf<File>()
        roots += File(INTERNAL_ROOT)
        detectRemovableRoots(context)
            .map { it.first }
            .map(::normalizeScanRoot)
            .map(::File)
            .forEach { roots += it }

        val tracks = mutableListOf<MusicTrack>()
        roots.filter { it.exists() }.forEach { root ->
            runCatching {
                root.walkTopDown()
                    .onFail { _, _ -> }
                    .filter { file -> file.isFile && file.extension.lowercase() in audioExtensions }
                    .forEach { file ->
                        val metadata = readMetadata(context, file)
                        val sourceType = classifySource(context, "filesystem", file.absolutePath)
                        tracks += MusicTrack(
                            id = "fs_${normalizeAbsolutePath(file.absolutePath).hashCode()}",
                            title = metadata.title.ifBlank { file.nameWithoutExtension },
                            artist = metadata.artist.ifBlank { "Unknown artist" },
                            album = metadata.album.ifBlank { "Unknown album" },
                            artworkUri = metadata.artworkUri,
                            folder = normalizeDisplayFolder(file.parent ?: "/Music", sourceType),
                            sourceType = sourceType,
                            duration = formatDuration(metadata.durationMs),
                            durationMs = metadata.durationMs,
                            streamUrl = Uri.fromFile(file).toString(),
                            contentUri = Uri.fromFile(file).toString(),
                            isLocalFile = true
                        )
                    }
            }
        }

        return tracks.distinctBy { it.contentUri }
    }

    private fun readMetadata(context: Context, file: File): LocalMetadata {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            val artworkUri = retriever.embeddedPicture?.let { bytes ->
                val artworkFile = File(context.cacheDir, "artwork_${file.absolutePath.hashCode()}.jpg")
                artworkFile.writeBytes(bytes)
                Uri.fromFile(artworkFile).toString()
            }
            LocalMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                artworkUri = artworkUri
            )
        }.getOrDefault(LocalMetadata()).also {
            runCatching { retriever.release() }
        }
    }

    private fun classifySource(context: Context, volumeName: String, folder: String): MusicSourceType {
        val name = volumeName.lowercase()
        val path = normalizeAbsolutePath(folder).lowercase()
        val removableRoots = detectRemovableRoots(context)
        removableRoots.firstOrNull { (root, _) -> path.startsWith(root.lowercase()) }?.let { (_, type) ->
            return type
        }
        return when {
            name == "external_primary" || name == "external" || name == "primary" -> MusicSourceType.Internal
            internalStorageMarkers.any { marker -> path.contains(marker) } -> MusicSourceType.Internal
            name.contains("usb") || name.contains("otg") || path.contains("usb") || path.contains("otg") -> MusicSourceType.UsbOtg
            else -> MusicSourceType.SdCard
        }
    }

    private fun detectRemovableRoots(context: Context): List<Pair<String, MusicSourceType>> {
        val externalDirs = context.getExternalFilesDirs(null).filterNotNull()
        val removablePaths = externalDirs
            .filter { Environment.isExternalStorageRemovable(it) }
            .map { normalizeAbsolutePath(it.absolutePath.substringBefore("/Android/")) }
            .distinct()

        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val removableVolumes = storageManager.storageVolumes.filter { it.isRemovable }

        val volumeCandidates = removablePaths.mapIndexed { index, path ->
            val description = removableVolumes.getOrNull(index)?.getDescription(context).orEmpty().lowercase()
            path to description
        }

        val volumeDirectoryCandidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            removableVolumes.mapNotNull { volume ->
                volume.directory?.absolutePath?.let { normalizeAbsolutePath(it) to volume.getDescription(context).orEmpty().lowercase() }
            }
        } else {
            emptyList()
        }

        val storageRootCandidates = File("/storage")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .map { normalizeAbsolutePath(it.absolutePath) }
            .filterNot { path ->
                path.equals("/storage/emulated", ignoreCase = true) ||
                    path.equals("/storage/self", ignoreCase = true) ||
                    path.startsWith(INTERNAL_ROOT, ignoreCase = true)
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

        return (volumeCandidates + volumeDirectoryCandidates + storageRootCandidates)
            .distinctBy { it.first.lowercase() }
            .map { (path, description) ->
            val type = when {
                description.contains("usb") || description.contains("otg") -> MusicSourceType.UsbOtg
                description.contains("sd") || description.contains("card") -> MusicSourceType.SdCard
                path.contains("usb", ignoreCase = true) || path.contains("otg", ignoreCase = true) -> MusicSourceType.UsbOtg
                else -> MusicSourceType.SdCard
            }
            path to type
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun filterBySources(tracks: List<MusicTrack>, sources: List<SourceConfig>): List<MusicTrack> {
        if (sources.isEmpty()) return tracks
        return tracks.filter { track ->
            val source = sources.firstOrNull { it.type == track.sourceType } ?: return@filter false
            if (!source.enabled) return@filter false
            if (!matchesFormat(track, source)) return@filter false
            if (!matchesDuration(track, source.durationFilter)) return@filter false
            if (!source.folderManagementEnabled || source.selectedFolder.isBlank()) return@filter true
            matchesAnyFolder(track, source)
        }
    }

    private fun matchesFormat(track: MusicTrack, source: SourceConfig): Boolean {
        val selectedFormats = source.selectedFormats.map { it.lowercase() }.toSet()
        if (selectedFormats.isEmpty()) return true
        val extension = track.contentUri.substringAfterLast('.', "").substringBefore('?').lowercase()
        return extension in selectedFormats
    }

    private fun matchesDuration(track: MusicTrack, filter: DurationFilter): Boolean {
        return when (filter) {
            DurationFilter.All -> true
            DurationFilter.Short -> track.durationMs in 1L..119_999L
            DurationFilter.Medium -> track.durationMs in 120_000L..600_000L
            DurationFilter.Long -> track.durationMs > 600_000L
        }
    }

    private fun matchesAnyFolder(track: MusicTrack, source: SourceConfig): Boolean {
        val configuredFolders = parseConfiguredFolders(source.selectedFolder, source.type)
        if (configuredFolders.isEmpty()) return true
        val normalizedTrackFolder = normalizeTrackFolder(track.folder, source.type)
        return configuredFolders.any { configured ->
            configured == "/" || normalizedTrackFolder == configured || normalizedTrackFolder.startsWith("$configured/", ignoreCase = true)
        }
    }

    private fun parseConfiguredFolders(rawValue: String, sourceType: MusicSourceType): List<String> {
        return rawValue
            .split('\n', ';', ',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { normalizeConfiguredFolder(it, sourceType) }
            .distinct()
    }

    private fun normalizeTrackFolder(folder: String, sourceType: MusicSourceType): String {
        val normalized = normalizeAbsolutePath(folder)
        return when (sourceType) {
            MusicSourceType.Internal -> normalizeInternalFolder(normalized)
            else -> normalizePath(normalized)
        }
    }

    private fun normalizeConfiguredFolder(folder: String, sourceType: MusicSourceType): String {
        val normalized = normalizeAbsolutePath(folder)
        return when (sourceType) {
            MusicSourceType.Internal -> {
                when {
                    normalized == INTERNAL_ROOT -> "/"
                    normalized.startsWith(INTERNAL_ROOT, ignoreCase = true) -> normalizeInternalFolder(normalized)
                    folder.startsWith("/") -> normalizePath(folder)
                    else -> normalizePath("/$folder")
                }
            }
            else -> normalizePath(normalized)
        }
    }

    private fun normalizeInternalFolder(path: String): String {
        val normalized = normalizePath(path)
            .replace(SDCARD_ALIAS, INTERNAL_ROOT, ignoreCase = true)
            .replace(SELF_PRIMARY_ALIAS, INTERNAL_ROOT, ignoreCase = true)
            .replace(MNT_SDCARD_ALIAS, INTERNAL_ROOT, ignoreCase = true)
        return normalized.removePrefix(INTERNAL_ROOT).ifBlank { "/" }
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trim().trimEnd('/').ifBlank { "/" }
    }

    private fun buildAlbumArtUri(albumId: Long): String? {
        if (albumId <= 0L) return null
        return Uri.parse("content://media/external/audio/albumart/$albumId").toString()
    }

    private fun normalizeDisplayFolder(folder: String, sourceType: MusicSourceType): String {
        val normalized = normalizeAbsolutePath(folder)
        return if (sourceType == MusicSourceType.Internal) {
            normalized.removePrefix(INTERNAL_ROOT).ifBlank { "/" }
        } else {
            normalized
        }
    }

    private fun normalizeAbsolutePath(path: String): String {
        return path
            .replace(SDCARD_ALIAS, INTERNAL_ROOT, ignoreCase = true)
            .replace(SELF_PRIMARY_ALIAS, INTERNAL_ROOT, ignoreCase = true)
            .replace(MNT_SDCARD_ALIAS, INTERNAL_ROOT, ignoreCase = true)
            .replace('\\', '/')
            .trim()
            .trimEnd('/')
            .ifBlank { "/" }
    }

    private fun normalizeScanRoot(path: String): String {
        val normalized = normalizeAbsolutePath(path)
        return if (internalStorageMarkers.any { marker -> normalized.startsWith(marker, ignoreCase = true) }) {
            INTERNAL_ROOT
        } else {
            normalized
        }
    }

    private data class LocalMetadata(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val durationMs: Long = 0L,
        val artworkUri: String? = null
    )
}
