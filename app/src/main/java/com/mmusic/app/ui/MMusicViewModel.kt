package com.mmusic.app.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mmusic.app.data.AppLanguage
import com.mmusic.app.data.AppTab
import com.mmusic.app.data.AudioOutputMode
import com.mmusic.app.data.BottomBarSize
import com.mmusic.app.data.DarkModeLevel
import com.mmusic.app.data.DownloadStorage
import com.mmusic.app.data.DrilldownType
import com.mmusic.app.data.DurationFilter
import com.mmusic.app.data.EqualizerPreset
import com.mmusic.app.data.EqualizerProfile
import com.mmusic.app.data.LibraryCategory
import com.mmusic.app.data.LibraryDrilldown
import com.mmusic.app.data.MMusicUiState
import com.mmusic.app.data.MediaStoreScanner
import com.mmusic.app.data.MusicSourceType
import com.mmusic.app.data.MusicTrack
import com.mmusic.app.data.PlaybackMode
import com.mmusic.app.data.PlayerStyle
import com.mmusic.app.data.ServerConfig
import com.mmusic.app.data.ServerSortOrder
import com.mmusic.app.data.ServerType
import com.mmusic.app.data.StorageDetector
import com.mmusic.app.data.UiStyle
import com.mmusic.app.playback.PlaybackService
import com.mmusic.app.playback.PlaybackQueueTrack
import com.mmusic.app.playback.PlaybackStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.Locale
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

class MMusicViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val DOWNLOADED_TRACK_PREFIX = "downloaded::"
    }

    private val app = application
    private val prefs = application.getSharedPreferences("m_music_prefs", Application.MODE_PRIVATE)
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var shouldShowInitialScanDialog = !prefs.getBoolean("initial_scan_done", false)
    private var previousPlaybackState = PlaybackStateStore.state.value
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            enforceWifiOnlyPolicy()
        }

        override fun onLost(network: Network) {
            enforceWifiOnlyPolicy()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            enforceWifiOnlyPolicy()
        }
    }

    private val _uiState = MutableStateFlow(
        MMusicUiState()
    )
    val uiState: StateFlow<MMusicUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(showWelcomeDialog = !prefs.getBoolean("welcome_done", false)) }
        _uiState.update {
            it.copy(
                uiStyle = UiStyle.valueOf(prefs.getString("ui_style", UiStyle.Base.name) ?: UiStyle.Base.name),
                darkModeLevel = DarkModeLevel.valueOf(prefs.getString("dark_level", DarkModeLevel.Standard.name) ?: DarkModeLevel.Standard.name),
                playerStyle = PlayerStyle.valueOf(prefs.getString("player_style", PlayerStyle.Base.name) ?: PlayerStyle.Base.name),
                bottomBarSize = runCatching {
                    BottomBarSize.valueOf(
                        prefs.getString("bottom_bar_size", BottomBarSize.Medium.name) ?: BottomBarSize.Medium.name
                    )
                }.getOrDefault(BottomBarSize.Medium),
                bottomBarFloating = prefs.getBoolean("bottom_bar_floating", true),
                bottomBarGlass = prefs.getBoolean("bottom_bar_glass", true),
                bottomBarCompact = prefs.getBoolean("bottom_bar_compact", false),
                bottomBarShowLabels = prefs.getBoolean("bottom_bar_show_labels", true),
                bottomBarActiveGlow = prefs.getBoolean("bottom_bar_active_glow", true),
                selectedAudioOutputMode = runCatching {
                    AudioOutputMode.valueOf(
                        prefs.getString("selected_audio_output_mode", AudioOutputMode.Speaker.name) ?: AudioOutputMode.Speaker.name
                    )
                }.getOrDefault(AudioOutputMode.Speaker),
                equalizerProfiles = restoreEqualizerProfiles(),
                showPlaybackProgress = prefs.getBoolean("show_playback_progress", true),
                playbackMode = PlaybackMode.valueOf(prefs.getString("playback_mode", PlaybackMode.Normal.name) ?: PlaybackMode.Normal.name),
                language = AppLanguage.resolve(
                    savedName = null,
                    fallbackLocale = Locale.getDefault()
                ),
                tracks = restoreDownloadedServerTracks(),
                serverConfig = ServerConfig(
                    type = ServerType.valueOf(prefs.getString("server_type", ServerType.Generic.name) ?: ServerType.Generic.name),
                    endpoint = prefs.getString("server_endpoint", "https://media.example.com/library").orEmpty(),
                    userName = prefs.getString("server_user", "listener").orEmpty(),
                    password = prefs.getString("server_password", "secret").orEmpty(),
                    downloadStorage = runCatching {
                        DownloadStorage.valueOf(
                            prefs.getString("server_download_storage", DownloadStorage.Internal.name) ?: DownloadStorage.Internal.name
                        )
                    }.getOrDefault(DownloadStorage.Internal),
                    sortOrder = runCatching {
                        ServerSortOrder.valueOf(
                            prefs.getString("server_sort_order", ServerSortOrder.Alphabetical.name) ?: ServerSortOrder.Alphabetical.name
                        )
                    }.getOrDefault(ServerSortOrder.Alphabetical),
                    wifiOnlyPlayback = prefs.getBoolean("server_wifi_only", false),
                    dataSaverEnabled = prefs.getBoolean("server_data_saver", false),
                    originalQualityPlayback = prefs.getBoolean("server_original_quality", true),
                    bitrateKbps = prefs.getInt("server_bitrate_kbps", 320).takeIf { it in com.mmusic.app.data.serverBitrateOptions } ?: 320,
                    connected = false,
                    isConnecting = false,
                    statusMessage = prefs.getString("server_status", "").orEmpty()
                )
            )
        }
        _uiState.update { it.copy(sources = restoreSourceSettings()) }
        refreshDetectedSources()
        val savedServer = _uiState.value.serverConfig
        if (savedServer.endpoint.isNotBlank() && !savedServer.endpoint.contains("media.example.com")) {
            connectToServer()
        }
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        viewModelScope.launch {
            PlaybackStateStore.state.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        currentTrackId = snapshot.currentTrackId,
                        isPlaying = snapshot.isPlaying,
                        isLoadingPlayback = snapshot.isLoading,
                        playbackPositionMs = snapshot.positionMs,
                        playbackDurationMs = snapshot.durationMs,
                        playbackError = snapshot.errorMessage
                    )
                }
                handlePlaybackCompletion(previousPlaybackState, snapshot)
                previousPlaybackState = snapshot
            }
        }
    }

    fun updateMediaPermission(granted: Boolean) {
        _uiState.update { it.copy(hasMediaPermission = granted, requestMediaPermission = false) }
        if (granted) {
            loadLocalTracks()
        } else {
            _uiState.update { it.copy(tracks = emptyList()) }
        }
    }

    fun acceptWelcomeDialog() {
        prefs.edit().putBoolean("welcome_done", true).apply()
        _uiState.update { it.copy(showWelcomeDialog = false, requestMediaPermission = true) }
    }

    fun dismissWelcomeDialog() {
        acceptWelcomeDialog()
    }

    fun dismissInfoDialog() {
        _uiState.update { it.copy(infoDialogMessage = null) }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                showServerInfoDialog = if (tab == AppTab.Server) true else it.showServerInfoDialog
            )
        }
    }

    fun selectCategory(category: LibraryCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun selectStyle(style: UiStyle) {
        prefs.edit().putString("ui_style", style.name).apply()
        _uiState.update {
            it.copy(
                uiStyle = style,
                darkModeLevel = if (style == UiStyle.Base) it.darkModeLevel else DarkModeLevel.Standard
            )
        }
        if (style != UiStyle.Base) {
            prefs.edit().putString("dark_level", DarkModeLevel.Standard.name).apply()
        }
    }

    fun selectDarkModeLevel(level: DarkModeLevel) {
        prefs.edit().putString("dark_level", level.name).apply()
        _uiState.update { it.copy(darkModeLevel = level) }
    }

    fun selectPlayerStyle(style: PlayerStyle) {
        prefs.edit().putString("player_style", style.name).apply()
        _uiState.update { it.copy(playerStyle = style) }
    }

    fun selectBottomBarSize(size: BottomBarSize) {
        prefs.edit().putString("bottom_bar_size", size.name).apply()
        _uiState.update { it.copy(bottomBarSize = size) }
    }

    fun setBottomBarFloating(enabled: Boolean) {
        prefs.edit().putBoolean("bottom_bar_floating", enabled).apply()
        _uiState.update { it.copy(bottomBarFloating = enabled) }
    }

    fun setBottomBarGlass(enabled: Boolean) {
        prefs.edit().putBoolean("bottom_bar_glass", enabled).apply()
        _uiState.update { it.copy(bottomBarGlass = enabled) }
    }

    fun setBottomBarCompact(enabled: Boolean) {
        prefs.edit().putBoolean("bottom_bar_compact", enabled).apply()
        _uiState.update { it.copy(bottomBarCompact = enabled) }
    }

    fun setBottomBarShowLabels(enabled: Boolean) {
        prefs.edit().putBoolean("bottom_bar_show_labels", enabled).apply()
        _uiState.update { it.copy(bottomBarShowLabels = enabled) }
    }

    fun setBottomBarActiveGlow(enabled: Boolean) {
        prefs.edit().putBoolean("bottom_bar_active_glow", enabled).apply()
        _uiState.update { it.copy(bottomBarActiveGlow = enabled) }
    }

    fun selectAudioOutputMode(mode: AudioOutputMode) {
        prefs.edit().putString("selected_audio_output_mode", mode.name).apply()
        _uiState.update { it.copy(selectedAudioOutputMode = mode) }
        PlaybackService.refreshAudioEffects(app)
    }

    fun updateEqualizerBass(mode: AudioOutputMode, value: Float) {
        updateEqualizerProfile(mode) { it.copy(bass = value.coerceIn(-1f, 1f), preset = EqualizerPreset.Flat) }
    }

    fun updateEqualizerMid(mode: AudioOutputMode, value: Float) {
        updateEqualizerProfile(mode) { it.copy(mid = value.coerceIn(-1f, 1f), preset = EqualizerPreset.Flat) }
    }

    fun updateEqualizerTreble(mode: AudioOutputMode, value: Float) {
        updateEqualizerProfile(mode) { it.copy(treble = value.coerceIn(-1f, 1f), preset = EqualizerPreset.Flat) }
    }

    fun selectEqualizerPreset(mode: AudioOutputMode, preset: EqualizerPreset) {
        val updated = when (preset) {
            EqualizerPreset.Flat -> EqualizerProfile(mode, preset, 0f, 0f, 0f, 0f, 0f, 0f)
            EqualizerPreset.BassBoost -> EqualizerProfile(mode, preset, 0.72f, -0.08f, 0.14f, 0.78f, 0.12f, 0.08f)
            EqualizerPreset.Vocal -> EqualizerProfile(mode, preset, -0.12f, 0.54f, 0.2f, 0.04f, 0.08f, 0.18f)
            EqualizerPreset.Bright -> EqualizerProfile(mode, preset, -0.08f, 0.18f, 0.68f, 0f, 0.14f, 0.12f)
            EqualizerPreset.Party -> EqualizerProfile(mode, preset, 0.56f, 0.18f, 0.44f, 0.56f, 0.26f, 0.2f)
            EqualizerPreset.Warm -> EqualizerProfile(mode, preset, 0.24f, 0.16f, -0.18f, 0.12f, 0.06f, 0.1f)
        }
        updateEqualizerProfile(mode) { updated }
    }

    fun updateBassBoost(mode: AudioOutputMode, value: Float) {
        updateEqualizerProfile(mode) { it.copy(bassBoost = value.coerceIn(0f, 1f), preset = EqualizerPreset.Flat) }
    }

    fun updateSurround(mode: AudioOutputMode, value: Float) {
        updateEqualizerProfile(mode) { it.copy(surround = value.coerceIn(0f, 1f), preset = EqualizerPreset.Flat) }
    }

    fun updateLoudness(mode: AudioOutputMode, value: Float) {
        updateEqualizerProfile(mode) { it.copy(loudness = value.coerceIn(0f, 1f), preset = EqualizerPreset.Flat) }
    }

    fun setShowPlaybackProgress(enabled: Boolean) {
        prefs.edit().putBoolean("show_playback_progress", enabled).apply()
        _uiState.update { it.copy(showPlaybackProgress = enabled) }
    }

    fun selectPlaybackMode(mode: PlaybackMode) {
        prefs.edit().putString("playback_mode", mode.name).apply()
        _uiState.update { it.copy(playbackMode = mode) }
    }

    fun selectSourceFilter(sourceType: MusicSourceType?) {
        _uiState.update { it.copy(selectedSourceFilter = sourceType) }
    }

    fun updateLibrarySearchQuery(value: String) {
        _uiState.update { it.copy(librarySearchQuery = value) }
    }

    fun openDrilldown(type: DrilldownType, value: String) {
        _uiState.update {
            it.copy(selectedCategory = LibraryCategory.AllMusic, libraryDrilldown = LibraryDrilldown(type, value))
        }
    }

    fun clearDrilldown() {
        _uiState.update { it.copy(libraryDrilldown = null) }
    }

    fun openFullscreenPlayer() {
        _uiState.update { it.copy(isPlayerFullscreen = true) }
    }

    fun closeFullscreenPlayer() {
        _uiState.update { it.copy(isPlayerFullscreen = false) }
    }

    fun togglePlayback(track: MusicTrack) {
        val current = _uiState.value.currentTrackId
        if (current == track.id) {
            if (isWifiOnlyBlocked(track)) {
                handleWifiOnlyBlocked()
                return
            }
            PlaybackService.toggle(app)
        } else {
            startTrack(track)
        }
        _uiState.update { it.copy(isPlayerFullscreen = true) }
    }

    fun toggleCurrentPlayback() {
        val currentTrack = _uiState.value.tracks.firstOrNull { it.id == _uiState.value.currentTrackId }
        if (currentTrack != null && isWifiOnlyBlocked(currentTrack)) {
            handleWifiOnlyBlocked()
            return
        }
        PlaybackService.toggle(app)
    }

    fun seekTo(positionMs: Long) {
        PlaybackService.seekTo(app, positionMs)
    }

    fun playPreviousTrack() {
        playAdjacentTrack(step = -1)
    }

    fun playNextTrack() {
        playAdjacentTrack(step = 1)
    }

    fun dismissServerInfoDialog() {
        _uiState.update { it.copy(showServerInfoDialog = false, selectedTab = AppTab.Player) }
    }

    fun stopPlayback() {
        PlaybackService.stop(app)
        _uiState.update { it.copy(isPlayerFullscreen = false) }
    }

    fun refreshDetectedSources() {
        _uiState.update { state ->
            state.copy(
                sources = StorageDetector.detectSources(app, state.sources, state.serverConfig.connected)
            )
        }
        if (_uiState.value.hasMediaPermission) {
            loadLocalTracks()
        }
    }

    fun toggleSourceEnabled(sourceType: MusicSourceType) {
        _uiState.update { state ->
            state.copy(
                sources = state.sources.map { source ->
                    if (source.type == sourceType) source.copy(enabled = !source.enabled) else source
                }
            )
        }
        persistSourceSettings(sourceType)
        if (_uiState.value.hasMediaPermission) {
            loadLocalTracks()
        }
    }

    fun updateSourceFolder(sourceType: MusicSourceType, value: String) {
        _uiState.update { state ->
            state.copy(
                sources = state.sources.map { source ->
                    if (source.type == sourceType) source.copy(selectedFolder = value) else source
                }
            )
        }
        persistSourceSettings(sourceType)
        if (_uiState.value.hasMediaPermission) {
            loadLocalTracks()
        }
    }

    fun toggleSourceFolderSelection(sourceType: MusicSourceType, folder: String) {
        _uiState.update { state ->
            state.copy(
                sources = state.sources.map { source ->
                    if (source.type != sourceType) {
                        source
                    } else {
                        val selected = parseSelectedFolders(source).toMutableSet()
                        if (selected.isEmpty()) {
                            selected += source.availableFolders
                        }
                        if (!selected.add(folder)) {
                            selected.remove(folder)
                        }
                        source.copy(selectedFolder = selected.joinToString("\n"))
                    }
                }
            )
        }
        persistSourceSettings(sourceType)
        if (_uiState.value.hasMediaPermission) {
            loadLocalTracks()
        }
    }

    fun toggleSourceFormatSelection(sourceType: MusicSourceType, format: String) {
        _uiState.update { state ->
            state.copy(
                sources = state.sources.map { source ->
                    if (source.type != sourceType) {
                        source
                    } else {
                        val selected = source.selectedFormats.toMutableSet()
                        if (!selected.add(format.lowercase())) {
                            selected.remove(format.lowercase())
                        }
                        source.copy(selectedFormats = selected.toList().sorted())
                    }
                }
            )
        }
        persistSourceSettings(sourceType)
        if (_uiState.value.hasMediaPermission) {
            loadLocalTracks()
        }
    }

    fun selectSourceDurationFilter(sourceType: MusicSourceType, filter: DurationFilter) {
        _uiState.update { state ->
            state.copy(
                sources = state.sources.map { source ->
                    if (source.type == sourceType) source.copy(durationFilter = filter) else source
                }
            )
        }
        persistSourceSettings(sourceType)
        if (_uiState.value.hasMediaPermission) {
            loadLocalTracks()
        }
    }

    fun toggleFolderManagement(sourceType: MusicSourceType) {
        _uiState.update { state ->
            val updated = state.sources.map { source ->
                if (source.type == sourceType) source.copy(folderManagementEnabled = !source.folderManagementEnabled) else source
            }
            state.copy(
                sources = StorageDetector.detectSources(app, updated, state.serverConfig.connected).map { detected ->
                    updated.firstOrNull { it.type == detected.type }?.let { detected.copy(folderManagementEnabled = it.folderManagementEnabled) }
                        ?: detected
                }
            )
        }
        persistSourceSettings(sourceType)
        if (_uiState.value.hasMediaPermission) {
            loadLocalTracks()
        }
    }

    fun updateServerEndpoint(value: String) {
        prefs.edit().putString("server_endpoint", value).apply()
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(endpoint = value)) }
    }

    fun updateServerType(type: ServerType) {
        prefs.edit().putString("server_type", type.name).apply()
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(type = type)) }
    }

    fun updateServerUser(value: String) {
        prefs.edit().putString("server_user", value).apply()
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(userName = value)) }
    }

    fun updateServerPassword(value: String) {
        prefs.edit().putString("server_password", value).apply()
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(password = value)) }
    }

    fun updateServerDownloadStorage(storage: DownloadStorage) {
        prefs.edit().putString("server_download_storage", storage.name).apply()
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(downloadStorage = storage)) }
    }

    fun updateServerSortOrder(sortOrder: ServerSortOrder) {
        prefs.edit().putString("server_sort_order", sortOrder.name).apply()
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(sortOrder = sortOrder)) }
        reloadServerTracksIfConnected()
    }

    fun deleteDownloadedServerTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            val downloaded = restoreDownloadedServerTracks()
            downloaded.forEach { track ->
                runCatching {
                    val file = runCatching { File(java.net.URI(track.contentUri)) }.getOrNull()
                        ?: File(Uri.parse(track.contentUri).path.orEmpty())
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
            prefs.edit().remove("downloaded_server_tracks").apply()
            val message = app.getString(com.mmusic.app.R.string.downloads_deleted_message)
            _uiState.update { state ->
                state.copy(
                    tracks = state.tracks.filterNot { it.sourceType == MusicSourceType.Server && it.isLocalFile },
                    serverConfig = state.serverConfig.copy(statusMessage = message),
                    infoDialogMessage = message
                )
            }
        }
    }

    fun setServerWifiOnlyPlayback(enabled: Boolean) {
        prefs.edit().putBoolean("server_wifi_only", enabled).apply()
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(wifiOnlyPlayback = enabled)) }
    }

    fun setServerDataSaverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("server_data_saver", enabled).apply()
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(dataSaverEnabled = enabled)) }
        reloadServerTracksIfConnected()
    }

    fun setServerOriginalQualityPlayback(enabled: Boolean) {
        prefs.edit().putBoolean("server_original_quality", enabled).apply()
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(originalQualityPlayback = enabled)) }
        reloadServerTracksIfConnected()
    }

    fun setServerBitrateKbps(bitrate: Int) {
        if (bitrate !in com.mmusic.app.data.serverBitrateOptions) return
        prefs.edit().putInt("server_bitrate_kbps", bitrate).apply()
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(bitrateKbps = bitrate)) }
        reloadServerTracksIfConnected()
    }

    fun connectToServer() {
        val config = _uiState.value.serverConfig
        _uiState.update { it.copy(serverConfig = it.serverConfig.copy(isConnecting = true, statusMessage = "")) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                detectAndConnectServer(config)
            }
            _uiState.update { state ->
                val connection = result.getOrDefault(ServerConnectionResult(status = "failed", type = state.serverConfig.type))
                val connected = connection.status == "connected"
                val message = when (connection.status) {
                    "connected" -> app.getString(com.mmusic.app.R.string.connected)
                    "auth" -> app.getString(com.mmusic.app.R.string.server_auth_failed)
                    else -> app.getString(com.mmusic.app.R.string.server_connection_failed)
                }
                state.copy(
                    serverConfig = state.serverConfig.copy(
                        type = connection.type,
                        connected = connected,
                        isConnecting = false,
                        statusMessage = message
                    ),
                    sources = StorageDetector.detectSources(app, state.sources, connected).map { source ->
                        if (source.type == MusicSourceType.Server && connected) source.copy(enabled = true) else source
                    }
                )
            }
            val finalResult = result.getOrDefault(ServerConnectionResult(status = "failed", type = config.type))
            if (finalResult.status == "connected") {
                prefs.edit().putString("server_type", finalResult.type.name).apply()
                loadServerTracks()
            }
            prefs.edit().putString("server_status", _uiState.value.serverConfig.statusMessage).apply()
        }
    }

    fun downloadServerTrack(track: MusicTrack) {
        if (track.sourceType != MusicSourceType.Server) {
            _uiState.update { it.copy(infoDialogMessage = app.getString(com.mmusic.app.R.string.download_local_unavailable)) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val targetFile = createDownloadTargetFile(track)
                URL(track.streamUrl).openStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val downloadedTrack = track.copy(
                    id = downloadedTrackId(track.id),
                    contentUri = targetFile.toURI().toString(),
                    streamUrl = targetFile.toURI().toString(),
                    isLocalFile = true,
                    folder = "/offline/${track.artist}"
                )
                persistDownloadedServerTrack(downloadedTrack)
                downloadedTrack
            }
            val message = result.fold(
                onSuccess = { downloadedTrack ->
                    _uiState.update { state ->
                        val localTracks = state.tracks.filter { it.sourceType != MusicSourceType.Server }
                        val serverTracks = (restoreDownloadedServerTracks() + state.tracks.filter { it.sourceType == MusicSourceType.Server && !it.isLocalFile })
                            .distinctBy { it.id }
                        state.copy(tracks = localTracks + serverTracks)
                    }
                    app.getString(com.mmusic.app.R.string.download_success_message, downloadedTrack.folder)
                },
                onFailure = {
                    app.getString(com.mmusic.app.R.string.download_failed_message)
                }
            )
            _uiState.update {
                it.copy(
                    serverConfig = it.serverConfig.copy(statusMessage = message),
                    infoDialogMessage = message
                )
            }
        }
    }

    private fun loadLocalTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanning = true) }
            val localTracks = runCatching { MediaStoreScanner.scanAudio(app, _uiState.value.sources) }.getOrDefault(emptyList())
            _uiState.update { state ->
                val remoteTracks = state.tracks.filter { it.sourceType == MusicSourceType.Server }
                state.copy(
                    tracks = (localTracks + remoteTracks).distinctBy { it.id },
                    sources = mergeDiscoveredFolders(state.sources, localTracks),
                    isScanning = false
                )
            }
            if (shouldShowInitialScanDialog) {
                prefs.edit().putBoolean("initial_scan_done", true).apply()
                shouldShowInitialScanDialog = false
            }
        }
    }

    private fun loadServerTracks() {
        val config = _uiState.value.serverConfig
        viewModelScope.launch(Dispatchers.IO) {
            val remoteTracks = sortServerTracks(fetchServerTracks(config), config.sortOrder)
            val downloadedTracks = sortServerTracks(restoreDownloadedServerTracks(), config.sortOrder)
            _uiState.update { state ->
                val localTracks = state.tracks.filter { it.sourceType != MusicSourceType.Server }
                state.copy(
                    tracks = (localTracks + downloadedTracks + remoteTracks).distinctBy { it.id },
                    serverConfig = state.serverConfig.copy(
                        statusMessage = if (remoteTracks.isNotEmpty()) {
                            "${app.getString(com.mmusic.app.R.string.connected)} • ${remoteTracks.size}"
                        } else if (state.serverConfig.connected) {
                            app.getString(com.mmusic.app.R.string.server_library_empty)
                        } else {
                            state.serverConfig.statusMessage
                        }
                    )
                )
            }
        }
    }

    private fun fetchServerTracks(config: ServerConfig): List<MusicTrack> {
        return when (config.type) {
            ServerType.Navidrome -> fetchNavidromeTracks(config)
            ServerType.Generic -> fetchNavidromeTracks(config)
            ServerType.Jellyfin -> emptyList()
            ServerType.Plex -> emptyList()
        }
    }

    private fun mergeDiscoveredFolders(sources: List<com.mmusic.app.data.SourceConfig>, tracks: List<MusicTrack>): List<com.mmusic.app.data.SourceConfig> {
        return sources.map { source ->
            if (source.type == MusicSourceType.Server) return@map source
            val discoveredFolders = tracks
                .filter { it.sourceType == source.type }
                .map { it.folder }
                .distinct()
                .sorted()
            if (discoveredFolders.isEmpty()) {
                source.copy(availableFolders = emptyList())
            } else {
                val currentSelection = parseSelectedFolders(source).filter { it in discoveredFolders }
                val nextSelection = if (source.folderManagementEnabled && currentSelection.isEmpty()) {
                    discoveredFolders
                } else {
                    currentSelection
                }
                source.copy(
                    availableFolders = discoveredFolders,
                    selectedFolder = nextSelection.joinToString("\n")
                )
            }
        }
    }

    private fun parseSelectedFolders(source: com.mmusic.app.data.SourceConfig): List<String> {
        return source.selectedFolder
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun persistSourceSettings(sourceType: MusicSourceType) {
        val source = _uiState.value.sources.firstOrNull { it.type == sourceType } ?: return
        prefs.edit()
            .putBoolean("source_${sourceType.name}_enabled", source.enabled)
            .putBoolean("source_${sourceType.name}_folder_management", source.folderManagementEnabled)
            .putString("source_${sourceType.name}_folders", source.selectedFolder)
            .putStringSet("source_${sourceType.name}_formats", source.selectedFormats.toSet())
            .putString("source_${sourceType.name}_duration", source.durationFilter.name)
            .apply()
    }

    private fun restoreSourceSettings(): List<com.mmusic.app.data.SourceConfig> {
        return MusicSourceType.entries.map { type ->
            com.mmusic.app.data.SourceConfig(
                type = type,
                connected = type == MusicSourceType.Internal,
                enabled = prefs.getBoolean("source_${type.name}_enabled", type == MusicSourceType.Internal),
                folderManagementEnabled = prefs.getBoolean("source_${type.name}_folder_management", false),
                selectedFolder = prefs.getString("source_${type.name}_folders", "").orEmpty(),
                selectedFormats = prefs.getStringSet("source_${type.name}_formats", emptySet()).orEmpty().toList().sorted(),
                durationFilter = runCatching {
                    DurationFilter.valueOf(
                        prefs.getString("source_${type.name}_duration", DurationFilter.All.name) ?: DurationFilter.All.name
                    )
                }.getOrDefault(DurationFilter.All),
                autoDetected = type != MusicSourceType.Server
            )
        }
    }

    private fun restoreEqualizerProfiles(): List<EqualizerProfile> {
        return AudioOutputMode.entries.map { mode ->
            EqualizerProfile(
                outputMode = mode,
                preset = runCatching {
                    EqualizerPreset.valueOf(
                        prefs.getString("eq_${mode.name}_preset", EqualizerPreset.Flat.name) ?: EqualizerPreset.Flat.name
                    )
                }.getOrDefault(EqualizerPreset.Flat),
                bass = prefs.getFloat("eq_${mode.name}_bass", 0f),
                mid = prefs.getFloat("eq_${mode.name}_mid", 0f),
                treble = prefs.getFloat("eq_${mode.name}_treble", 0f),
                bassBoost = prefs.getFloat("eq_${mode.name}_bass_boost", 0f),
                surround = prefs.getFloat("eq_${mode.name}_surround", 0f),
                loudness = prefs.getFloat("eq_${mode.name}_loudness", 0f)
            )
        }
    }

    private fun updateEqualizerProfile(mode: AudioOutputMode, transform: (EqualizerProfile) -> EqualizerProfile) {
        _uiState.update { state ->
            state.copy(
                equalizerProfiles = state.equalizerProfiles.map { profile ->
                    if (profile.outputMode == mode) transform(profile) else profile
                }
            )
        }
        persistEqualizerProfile(mode)
        PlaybackService.refreshAudioEffects(app)
    }

    private fun persistEqualizerProfile(mode: AudioOutputMode) {
        val profile = _uiState.value.equalizerProfiles.firstOrNull { it.outputMode == mode } ?: return
        prefs.edit()
            .putString("eq_${mode.name}_preset", profile.preset.name)
            .putFloat("eq_${mode.name}_bass", profile.bass)
            .putFloat("eq_${mode.name}_mid", profile.mid)
            .putFloat("eq_${mode.name}_treble", profile.treble)
            .putFloat("eq_${mode.name}_bass_boost", profile.bassBoost)
            .putFloat("eq_${mode.name}_surround", profile.surround)
            .putFloat("eq_${mode.name}_loudness", profile.loudness)
            .apply()
    }

    private fun reloadServerTracksIfConnected() {
        if (_uiState.value.serverConfig.connected) {
            loadServerTracks()
        }
    }

    private fun playAdjacentTrack(step: Int) {
        val queue = currentPlaybackQueue()
        updatePlaybackQueue(queue)
        if (queue.isEmpty()) return
        if (step > 0 && _uiState.value.playbackMode == PlaybackMode.Shuffle) {
            playRandomTrack(queue)
            return
        }
        val currentIndex = queue.indexOfFirst { it.id == _uiState.value.currentTrackId }
        val targetIndex = when {
            currentIndex == -1 && step > 0 -> 0
            currentIndex == -1 -> queue.lastIndex
            else -> wrapQueueIndex(currentIndex + step, queue.lastIndex)
        }
        val targetTrack = queue.getOrNull(targetIndex) ?: return
        if (targetTrack.id == _uiState.value.currentTrackId) return
        startTrack(targetTrack)
        _uiState.update { it.copy(isPlayerFullscreen = true) }
    }

    private fun filteredPlayableTracks(): List<MusicTrack> {
        val state = _uiState.value
        val enabledTypes = state.sources.filter { it.enabled }.map { it.type }.toSet()
        val query = state.librarySearchQuery.trim()
        return state.tracks
            .filter { enabledTypes.isEmpty() || it.sourceType in enabledTypes }
            .filter { state.selectedSourceFilter == null || it.sourceType == state.selectedSourceFilter }
            .filter { track ->
                query.isBlank() || listOf(track.title, track.artist, track.album, track.folder).any { value ->
                    value.contains(query, ignoreCase = true)
                }
            }
            .filter { track ->
                when (val drill = state.libraryDrilldown) {
                    null -> true
                    else -> when (drill.type) {
                        DrilldownType.Artist -> track.artist == drill.value
                        DrilldownType.Album -> track.album == drill.value
                        DrilldownType.Folder -> track.folder == drill.value
                        DrilldownType.Source -> track.sourceType.name == drill.value
                    }
                }
            }
    }

    private fun handlePlaybackCompletion(previous: com.mmusic.app.playback.PlaybackSnapshot, current: com.mmusic.app.playback.PlaybackSnapshot) {
        val completedNaturally = previous.isPlaying &&
            !current.isPlaying &&
            !current.isLoading &&
            current.currentTrackId != null &&
            current.durationMs > 0L &&
            current.positionMs >= current.durationMs

        if (!completedNaturally) return

        when (_uiState.value.playbackMode) {
            PlaybackMode.Normal -> playAdjacentTrack(step = 1)
            PlaybackMode.RepeatOne -> _uiState.value.tracks.firstOrNull { it.id == current.currentTrackId }?.let(::startTrack)
            PlaybackMode.Shuffle -> playRandomTrack(currentPlaybackQueue())
        }
    }

    private fun playRandomTrack(queue: List<MusicTrack>) {
        if (queue.isEmpty()) return
        updatePlaybackQueue(queue)
        val currentId = _uiState.value.currentTrackId
        val candidates = queue.filter { it.id != currentId }.ifEmpty { queue }
        val targetTrack = candidates[Random.nextInt(candidates.size)]
        startTrack(targetTrack)
        _uiState.update { it.copy(isPlayerFullscreen = true) }
    }

    private fun startTrack(track: MusicTrack) {
        updatePlaybackQueue(queueForTrack(track))
        if (isWifiOnlyBlocked(track)) {
            handleWifiOnlyBlocked()
            return
        }
        if (track.sourceType == MusicSourceType.Server && !track.isLocalFile && isOnMobileConnection()) {
            _uiState.update { it.copy(infoDialogMessage = app.getString(com.mmusic.app.R.string.server_mobile_data_warning)) }
        }
        PlaybackService.play(
            app,
            PlaybackService.ServiceTrack(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUri = track.artworkUri,
                source = track.sourceType.name,
                folder = track.folder,
                url = if (track.isLocalFile) track.contentUri else track.streamUrl,
                isLocalFile = track.isLocalFile
            )
        )
    }

    private fun isWifiOnlyBlocked(track: MusicTrack): Boolean {
        return track.sourceType == MusicSourceType.Server &&
            _uiState.value.serverConfig.wifiOnlyPlayback &&
            !isOnWifiConnection()
    }

    private fun handleWifiOnlyBlocked() {
        val message = app.getString(com.mmusic.app.R.string.server_wifi_only_blocked)
        _uiState.update {
            it.copy(
                serverConfig = it.serverConfig.copy(statusMessage = message),
                infoDialogMessage = message
            )
        }
    }

    private fun enforceWifiOnlyPolicy() {
        val currentTrack = _uiState.value.tracks.firstOrNull { it.id == _uiState.value.currentTrackId } ?: return
        if (!isWifiOnlyBlocked(currentTrack)) return
        PlaybackService.stop(app)
        val message = app.getString(com.mmusic.app.R.string.server_wifi_only_blocked)
        _uiState.update {
            it.copy(
                isPlayerFullscreen = false,
                serverConfig = it.serverConfig.copy(statusMessage = message),
                infoDialogMessage = message
            )
        }
    }

    private fun createDownloadTargetFile(track: MusicTrack): File {
        val preferred = _uiState.value.serverConfig.downloadStorage
        val baseDir = resolveDownloadBaseDir(preferred)
        val downloadsDir = File(baseDir, "downloads/server-cache").apply { mkdirs() }
        val extension = track.streamUrl.substringBefore('?').substringAfterLast('.', "audio").ifBlank { "audio" }
        val hashedName = sha256(track.id).take(24)
        return File(downloadsDir, "$hashedName.$extension")
    }

    private fun persistDownloadedServerTrack(track: MusicTrack) {
        val normalizedTrack = track.copy(id = downloadedTrackId(track.id), isLocalFile = true)
        val items = restoreDownloadedServerTracks().filter { it.id != normalizedTrack.id } + normalizedTrack
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("album", item.album)
                    put("artworkUri", item.artworkUri)
                    put("folder", item.folder)
                    put("duration", item.duration)
                    put("durationMs", item.durationMs)
                    put("streamUrl", item.streamUrl)
                    put("contentUri", item.contentUri)
                    put("isLocalFile", item.isLocalFile)
                }
            )
        }
        prefs.edit().putString("downloaded_server_tracks", array.toString()).apply()
    }

    private fun restoreDownloadedServerTracks(): List<MusicTrack> {
        val raw = prefs.getString("downloaded_server_tracks", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val contentUri = item.optString("contentUri")
                    val file = runCatching { File(java.net.URI(contentUri)) }.getOrNull()
                        ?: File(Uri.parse(contentUri).path.orEmpty())
                    if (!file.exists()) continue
                    val storedId = item.optString("id")
                    add(
                        MusicTrack(
                            id = downloadedTrackId(storedId),
                            title = item.optString("title"),
                            artist = item.optString("artist"),
                            album = item.optString("album"),
                            artworkUri = item.optString("artworkUri").ifBlank { null },
                            folder = item.optString("folder").ifBlank { "/offline" },
                            sourceType = MusicSourceType.Server,
                            duration = item.optString("duration"),
                            durationMs = item.optLong("durationMs"),
                            streamUrl = contentUri,
                            contentUri = contentUri,
                            isLocalFile = item.optBoolean("isLocalFile", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun resolveDownloadBaseDir(storage: DownloadStorage): File {
        val candidates = app.getExternalFilesDirs(null).filterNotNull()
        val preferred = when (storage) {
            DownloadStorage.Internal -> candidates.firstOrNull()
            DownloadStorage.SdCard -> candidates.firstOrNull { dir ->
                val path = dir.absolutePath.lowercase(Locale.ROOT)
                "sd" in path || "ext" in path || "card" in path
            }
        }
        return preferred ?: app.getExternalFilesDir(null) ?: app.filesDir
    }

    private fun updatePlaybackQueue(queue: List<MusicTrack>) {
        PlaybackStateStore.updateQueue(
            queue.map { track ->
                PlaybackQueueTrack(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUri = track.artworkUri,
                    source = track.sourceType.name,
                    folder = track.folder,
                    url = if (track.isLocalFile) track.contentUri else track.streamUrl,
                    isLocalFile = track.isLocalFile
                )
            }
        )
    }

    private fun queueForTrack(track: MusicTrack): List<MusicTrack> {
        return when {
            track.sourceType == MusicSourceType.Server && track.isLocalFile ->
                sortServerTracks(
                    _uiState.value.tracks.filter { it.sourceType == MusicSourceType.Server && it.isLocalFile },
                    _uiState.value.serverConfig.sortOrder
                )
            track.sourceType == MusicSourceType.Server ->
                sortServerTracks(
                    _uiState.value.tracks.filter { it.sourceType == MusicSourceType.Server && !it.isLocalFile },
                    _uiState.value.serverConfig.sortOrder
                )
            else -> filteredPlayableTracks()
        }
    }

    private fun currentPlaybackQueue(): List<MusicTrack> {
        val currentTrack = _uiState.value.tracks.firstOrNull { it.id == _uiState.value.currentTrackId }
        return currentTrack?.let(::queueForTrack) ?: filteredPlayableTracks()
    }

    private fun sortServerTracks(tracks: List<MusicTrack>, sortOrder: ServerSortOrder): List<MusicTrack> {
        return when (sortOrder) {
            ServerSortOrder.Alphabetical -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            ServerSortOrder.RecentAdded -> tracks
        }
    }

    private fun wrapQueueIndex(index: Int, lastIndex: Int): Int {
        val size = lastIndex + 1
        if (size <= 0) return 0
        return ((index % size) + size) % size
    }

    private fun downloadedTrackId(rawId: String): String {
        return if (rawId.startsWith(DOWNLOADED_TRACK_PREFIX)) rawId else "$DOWNLOADED_TRACK_PREFIX$rawId"
    }

    private fun connectToGeneric(endpoint: String, user: String, password: String): String {
        val candidates = normalizeEndpoints(endpoint)
        var lastStatus = "failed"
        candidates.forEach { candidate ->
            val connection = openConnection(candidate, user, password)
            val code = connection.responseCode
            connection.disconnect()
            lastStatus = codeToStatus(code)
            if (lastStatus != "failed") return lastStatus
        }
        return lastStatus
    }

    private fun detectAndConnectServer(config: ServerConfig): ServerConnectionResult {
        return when (config.type) {
            ServerType.Navidrome -> ServerConnectionResult(
                status = connectToNavidrome(config.endpoint, config.userName, config.password),
                type = ServerType.Navidrome
            )
            ServerType.Jellyfin -> ServerConnectionResult(
                status = connectToJellyfin(config.endpoint),
                type = ServerType.Jellyfin
            )
            ServerType.Plex -> ServerConnectionResult(
                status = connectToPlex(config.endpoint),
                type = ServerType.Plex
            )
            ServerType.Generic -> {
                val navidromeStatus = connectToNavidrome(config.endpoint, config.userName, config.password)
                if (navidromeStatus == "connected") {
                    return ServerConnectionResult(navidromeStatus, ServerType.Navidrome)
                }
                val jellyfinStatus = connectToJellyfin(config.endpoint)
                if (jellyfinStatus == "connected") {
                    return ServerConnectionResult(jellyfinStatus, ServerType.Jellyfin)
                }
                val plexStatus = connectToPlex(config.endpoint)
                if (plexStatus == "connected") {
                    return ServerConnectionResult(plexStatus, ServerType.Plex)
                }
                val genericStatus = connectToGeneric(config.endpoint, config.userName, config.password)
                ServerConnectionResult(
                    status = listOf(navidromeStatus, jellyfinStatus, plexStatus, genericStatus)
                        .firstOrNull { it != "failed" } ?: "failed",
                    type = when {
                        navidromeStatus == "auth" -> ServerType.Navidrome
                        jellyfinStatus == "auth" -> ServerType.Jellyfin
                        plexStatus == "auth" -> ServerType.Plex
                        else -> ServerType.Generic
                    }
                )
            }
        }
    }

    private fun fetchNavidromeTracks(config: ServerConfig): List<MusicTrack> {
        if (config.endpoint.isBlank() || config.userName.isBlank() || config.password.isBlank()) return emptyList()
        val salt = "mmusic"
        val token = md5("${config.password}$salt")
        val bases = normalizeEndpoints(config.endpoint).map(::normalizeNavidromeBase)
        bases.forEach { base ->
            fetchNavidromeTracksFromEndpoint(
                apiBase = base,
                config = config,
                token = token,
                salt = salt,
            ).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return emptyList()
    }

    private fun parseNavidromeSongs(body: String, apiBase: String, config: ServerConfig): List<MusicTrack> {
        val json = JSONObject(body)
        val root = json.optJSONObject("subsonic-response") ?: return emptyList()
        val songs = extractSongObjects(root)
        if (songs.isEmpty()) return emptyList()
        return buildList {
            songs.forEach { song ->
                val id = song.optString("id")
                if (id.isBlank()) return@forEach
                val artist = song.optString("artist").ifBlank { "Unknown artist" }
                val album = song.optString("album").ifBlank { "Unknown album" }
                val title = song.optString("title").ifBlank { "Unknown title" }
                val parent = song.optString("parent").ifBlank { "server" }
                val path = song.optString("path")
                val folder = when {
                    path.isNotBlank() -> "/server/${path.substringBeforeLast('/', missingDelimiterValue = path).trimStart('/')}"
                    else -> "/server/$parent"
                }
                val durationMs = song.optLong("duration", 0L) * 1000L
                val coverArtId = song.optString("coverArt").ifBlank { id }
                val authQuery = navidromePasswordAuthQuery(config)
                val streamQuality = navidromeStreamQualityQuery(config)
                val streamUrl = "$apiBase/stream.view?id=$id&$streamQuality&$authQuery"
                val artworkUrl = "$apiBase/getCoverArt.view?id=$coverArtId&$authQuery"
                add(
                    MusicTrack(
                        id = "server_$id",
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUri = artworkUrl,
                        folder = folder,
                        sourceType = MusicSourceType.Server,
                        duration = formatDuration(durationMs),
                        durationMs = durationMs,
                        streamUrl = streamUrl,
                        contentUri = streamUrl,
                        isLocalFile = false
                    )
                )
            }
        }
    }

    private fun fetchNavidromeTracksFromEndpoint(
        apiBase: String,
        config: ServerConfig,
        token: String,
        salt: String
    ): List<MusicTrack> {
        val passwordAuth = navidromePasswordAuthQuery(config)
        val tokenAuth = navidromeTokenAuthQuery(config, token, salt)
        val directRequests = listOf(
            "$apiBase/getRandomSongs.view?size=500&$passwordAuth",
            "$apiBase/getRandomSongs.view?size=500&$tokenAuth",
            "$apiBase/getStarred2.view?$passwordAuth",
            "$apiBase/getStarred2.view?$tokenAuth"
        )
        directRequests.forEach { endpoint ->
            fetchNavidromeResponse(endpoint)?.let { body ->
                parseNavidromeSongs(body, apiBase, config).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }

        val albumListRequests = listOf(
            "$apiBase/getAlbumList2.view?type=alphabeticalByName&size=200&offset=0&$passwordAuth",
            "$apiBase/getAlbumList2.view?type=alphabeticalByName&size=200&offset=0&$tokenAuth",
            "$apiBase/getAlbumList2.view?type=newest&size=200&offset=0&$passwordAuth",
            "$apiBase/getAlbumList2.view?type=newest&size=200&offset=0&$tokenAuth",
            "$apiBase/getAlbumList2.view?type=recent&size=200&offset=0&$passwordAuth",
            "$apiBase/getAlbumList2.view?type=recent&size=200&offset=0&$tokenAuth"
        )
        albumListRequests.forEach { endpoint ->
            fetchNavidromeResponse(endpoint)?.let { body ->
                val albumIds = parseNavidromeAlbumIds(body)
                if (albumIds.isEmpty()) return@let
                val allTracks = albumIds.flatMap { albumId ->
                    val albumRequests = listOf(
                        "$apiBase/getAlbum.view?id=$albumId&$passwordAuth",
                        "$apiBase/getAlbum.view?id=$albumId&$tokenAuth"
                    )
                    albumRequests.firstNotNullOfOrNull { fetchNavidromeResponse(it) }?.let { albumBody ->
                        parseNavidromeSongs(albumBody, apiBase, config)
                    }.orEmpty()
                }.distinctBy { it.id }
                if (allTracks.isNotEmpty()) return allTracks
            }
        }

        val indexRequests = listOf(
            "$apiBase/getIndexes.view?$passwordAuth",
            "$apiBase/getIndexes.view?$tokenAuth"
        )
        indexRequests.forEach { endpoint ->
            fetchNavidromeResponse(endpoint)?.let { body ->
                val artistIds = parseNavidromeArtistIds(body)
                if (artistIds.isEmpty()) return@let
                val allTracks = artistIds.flatMap { artistId ->
                    val artistRequests = listOf(
                        "$apiBase/getArtist.view?id=$artistId&$passwordAuth",
                        "$apiBase/getArtist.view?id=$artistId&$tokenAuth"
                    )
                    val artistBody = artistRequests.firstNotNullOfOrNull { fetchNavidromeResponse(it) } ?: return@flatMap emptyList()
                    parseNavidromeArtistAlbumIds(artistBody).flatMap { albumId ->
                        val albumRequests = listOf(
                            "$apiBase/getAlbum.view?id=$albumId&$passwordAuth",
                            "$apiBase/getAlbum.view?id=$albumId&$tokenAuth"
                        )
                        albumRequests.firstNotNullOfOrNull { fetchNavidromeResponse(it) }?.let { albumBody ->
                            parseNavidromeSongs(albumBody, apiBase, config)
                        }.orEmpty()
                    }
                }.distinctBy { it.id }
                if (allTracks.isNotEmpty()) return allTracks
            }
        }

        return emptyList()
    }

    private fun parseNavidromeAlbumIds(body: String): List<String> {
        val json = JSONObject(body)
        val root = json.optJSONObject("subsonic-response") ?: return emptyList()
        val albumList = root.optJSONObject("albumList2") ?: return emptyList()
        return jsonObjectsFrom(albumList, "album").mapNotNull { album ->
            album.optString("id").takeIf { it.isNotBlank() }
        }
    }

    private fun parseNavidromeArtistIds(body: String): List<String> {
        val json = JSONObject(body)
        val root = json.optJSONObject("subsonic-response") ?: return emptyList()
        val indexes = root.optJSONObject("indexes") ?: return emptyList()
        return (jsonObjectsFrom(indexes, "index")
            .flatMap { indexGroup -> jsonObjectsFrom(indexGroup, "artist") }
            .plus(jsonObjectsFrom(indexes, "child"))
            .mapNotNull { artist -> artist.optString("id").takeIf { it.isNotBlank() } }
            .distinct())
    }

    private fun parseNavidromeArtistAlbumIds(body: String): List<String> {
        val json = JSONObject(body)
        val root = json.optJSONObject("subsonic-response") ?: return emptyList()
        val artist = root.optJSONObject("artist") ?: return emptyList()
        return jsonObjectsFrom(artist, "album").mapNotNull { album ->
            album.optString("id").takeIf { it.isNotBlank() }
        }
    }

    private fun fetchNavidromeResponse(endpoint: String): String? {
        return runCatching {
            val connection = openConnection(endpoint)
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
            connection.disconnect()
            body
        }.getOrNull()
    }

    private fun connectToNavidrome(endpoint: String, user: String, password: String): String {
        if (user.isBlank() || password.isBlank()) return "auth"
        val encodedUser = URLEncoder.encode(user, StandardCharsets.UTF_8.name())
        val encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8.name())
        val salt = "mmusic"
        val token = md5("$password$salt")
        normalizeEndpoints(endpoint).forEach { base ->
            val apiBase = normalizeNavidromeBase(base)
            val candidates = listOf(
                "$apiBase/ping.view?u=$encodedUser&p=$encodedPassword&v=1.16.1&c=mmusic&f=json",
                "$apiBase/ping.view?u=$encodedUser&t=$token&s=$salt&v=1.16.1&c=mmusic&f=json"
            )
            candidates.forEach { candidate ->
                val connection = openConnection(candidate)
                val code = connection.responseCode
                val body = runCatching {
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                connection.disconnect()
                if (code in 200..299 && (body.contains("\"status\":\"ok\"", ignoreCase = true) || body.contains("status=\"ok\"", ignoreCase = true))) {
                    return "connected"
                }
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED || body.contains("wrong", ignoreCase = true) || body.contains("auth", ignoreCase = true)) {
                    return "auth"
                }
            }
        }
        return "failed"
    }

    private fun connectToJellyfin(endpoint: String): String {
        normalizeEndpoints(endpoint).forEach { base ->
            val connection = openConnection("${base.trimEnd('/')}/System/Info/Public")
            val code = connection.responseCode
            connection.disconnect()
            val status = codeToStatus(code)
            if (status != "failed") return status
        }
        return "failed"
    }

    private fun connectToPlex(endpoint: String): String {
        normalizeEndpoints(endpoint).forEach { base ->
            val connection = openConnection("${base.trimEnd('/')}/identity")
            val code = connection.responseCode
            connection.disconnect()
            val status = codeToStatus(code)
            if (status != "failed") return status
        }
        return "failed"
    }

    private fun normalizeEndpoints(endpoint: String): List<String> {
        val rawBase = endpoint.trim().trimEnd('/')
        if (rawBase.isBlank()) return emptyList()
        return if (rawBase.startsWith("http://") || rawBase.startsWith("https://")) {
            listOf(rawBase)
        } else {
            listOf("http://$rawBase", "https://$rawBase")
        }
    }

    private fun normalizeNavidromeBase(endpoint: String): String {
        val trimmed = endpoint.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/rest", ignoreCase = true) -> trimmed
            trimmed.contains("/rest/", ignoreCase = true) -> trimmed.substringBefore("/rest/", missingDelimiterValue = trimmed) + "/rest"
            else -> "$trimmed/rest"
        }
    }

    private fun navidromePasswordAuthQuery(config: ServerConfig): String {
        val user = URLEncoder.encode(config.userName, StandardCharsets.UTF_8.name())
        val password = URLEncoder.encode(config.password, StandardCharsets.UTF_8.name())
        return "u=$user&p=$password&v=1.16.1&c=mmusic&f=json"
    }

    private fun navidromeTokenAuthQuery(config: ServerConfig, token: String, salt: String): String {
        val user = URLEncoder.encode(config.userName, StandardCharsets.UTF_8.name())
        return "u=$user&t=$token&s=$salt&v=1.16.1&c=mmusic&f=json"
    }

    private fun navidromeStreamQualityQuery(config: ServerConfig): String {
        if (config.originalQualityPlayback) return ""
        val bitrate = effectiveServerBitrate(config)
        return if (bitrate > 0) "maxBitRate=$bitrate" else ""
    }

    private fun effectiveServerBitrate(config: ServerConfig): Int {
        val configured = config.bitrateKbps.takeIf { it in com.mmusic.app.data.serverBitrateOptions } ?: 320
        return if (config.dataSaverEnabled && !isOnWifiConnection()) {
            configured.coerceAtMost(96)
        } else {
            configured
        }
    }

    private fun isOnWifiConnection(): Boolean {
        return runCatching {
            val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }.getOrDefault(false)
    }

    private fun isOnMobileConnection(): Boolean {
        return runCatching {
            val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }.getOrDefault(false)
    }

    override fun onCleared() {
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    private fun extractSongObjects(root: JSONObject): List<JSONObject> {
        val containers = listOf("randomSongs", "searchResult3", "album", "starred2")
        containers.forEach { key ->
            root.optJSONObject(key)?.let { container ->
                val songs = jsonObjectsFrom(container, "song")
                if (songs.isNotEmpty()) return songs
            }
        }
        return emptyList()
    }

    private fun jsonObjectsFrom(parent: JSONObject, key: String): List<JSONObject> {
        val value = parent.opt(key) ?: return emptyList()
        return when (value) {
            is JSONObject -> listOf(value)
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    value.optJSONObject(index)?.let(::add)
                }
            }
            else -> emptyList()
        }
    }

    private fun openConnection(endpoint: String, user: String = "", password: String = ""): HttpURLConnection {
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            requestMethod = "GET"
            instanceFollowRedirects = true
            if (user.isNotBlank() || password.isNotBlank()) {
                val auth = Base64.getEncoder().encodeToString("$user:$password".toByteArray())
                setRequestProperty("Authorization", "Basic $auth")
            }
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
    }

    private fun codeToStatus(code: Int): String = when (code) {
        in 200..299 -> "connected"
        HttpURLConnection.HTTP_UNAUTHORIZED,
        HttpURLConnection.HTTP_FORBIDDEN -> "auth"
        else -> "failed"
    }

    private fun md5(value: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private data class ServerConnectionResult(
        val status: String,
        val type: ServerType
    )

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }
}
