package com.mmusic.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.hardware.camera2.CameraManager
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mmusic.app.R
import com.mmusic.app.data.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MMusicApp(
    state: MMusicUiState,
    onTabSelected: (AppTab) -> Unit,
    onCategorySelected: (LibraryCategory) -> Unit,
    onStyleSelected: (UiStyle) -> Unit,
    onSourceFilterSelected: (MusicSourceType?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onTrackSelected: (MusicTrack) -> Unit,
    onOpenDrilldown: (DrilldownType, String) -> Unit,
    onClearDrilldown: () -> Unit,
    onOpenFullscreenPlayer: () -> Unit,
    onCloseFullscreenPlayer: () -> Unit,
    onToggleCurrentPlayback: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlaybackModeSelected: (PlaybackMode) -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onStopPlayback: () -> Unit,
    onDismissServerInfo: () -> Unit,
    onDismissInfoDialog: () -> Unit,
    onRefreshSources: () -> Unit,
    onToggleSourceEnabled: (MusicSourceType) -> Unit,
    onFolderEnabledChanged: (MusicSourceType) -> Unit,
    onSourceFolderChanged: (MusicSourceType, String) -> Unit,
    onSourceFolderToggled: (MusicSourceType, String) -> Unit,
    onSourceFormatToggled: (MusicSourceType, String) -> Unit,
    onSourceDurationFilterSelected: (MusicSourceType, DurationFilter) -> Unit,
    onDarkModeLevelSelected: (DarkModeLevel) -> Unit,
    onPlayerStyleSelected: (PlayerStyle) -> Unit,
    onBottomBarSizeSelected: (BottomBarSize) -> Unit,
    onBottomBarFloatingChanged: (Boolean) -> Unit,
    onBottomBarGlassChanged: (Boolean) -> Unit,
    onBottomBarCompactChanged: (Boolean) -> Unit,
    onBottomBarLabelsChanged: (Boolean) -> Unit,
    onBottomBarGlowChanged: (Boolean) -> Unit,
    onShowServerTabChanged: (Boolean) -> Unit,
    onShowRadioTabChanged: (Boolean) -> Unit,
    onAudioOutputModeSelected: (AudioOutputMode) -> Unit,
    onEqualizerPresetSelected: (AudioOutputMode, EqualizerPreset) -> Unit,
    onBassChanged: (AudioOutputMode, Float) -> Unit,
    onMidChanged: (AudioOutputMode, Float) -> Unit,
    onTrebleChanged: (AudioOutputMode, Float) -> Unit,
    onBassBoostChanged: (AudioOutputMode, Float) -> Unit,
    onSurroundChanged: (AudioOutputMode, Float) -> Unit,
    onLoudnessChanged: (AudioOutputMode, Float) -> Unit,
    onShowPlaybackProgressChanged: (Boolean) -> Unit,
    onServerEndpointChanged: (String) -> Unit,
    onServerTypeChanged: (ServerType) -> Unit,
    onServerUserChanged: (String) -> Unit,
    onServerPasswordChanged: (String) -> Unit,
    onServerDownloadStorageChanged: (DownloadStorage) -> Unit,
    onServerSortOrderChanged: (ServerSortOrder) -> Unit,
    onDeleteDownloadedFiles: () -> Unit,
    onServerWifiOnlyChanged: (Boolean) -> Unit,
    onServerDataSaverChanged: (Boolean) -> Unit,
    onServerOriginalQualityChanged: (Boolean) -> Unit,
    onServerBitrateChanged: (Int) -> Unit,
    onConnectToServer: () -> Unit,
    onDownloadServerTrack: (MusicTrack) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onUpdateMetadata: (MusicTrack, String, String, String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenRadioCountryPicker: () -> Unit,
    onRadioSearchQueryChanged: (String) -> Unit,
    onShowRadioMetadataChanged: (Boolean) -> Unit,
    onRadioCountrySelected: (String, String) -> Unit,
    onDismissRadioCountryPicker: () -> Unit,
    onDismissReleaseNotesDialog: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onAcceptWelcome: () -> Unit
) {
    val currentTrack = state.tracks.firstOrNull { it.id == state.currentTrackId }
        ?: state.radioStations.firstOrNull { it.id == state.currentTrackId }
    Scaffold(
        topBar = {
            if (!state.isPlayerFullscreen) {
                TopAppBar(title = {
                    Column {
                        Text("M-Music", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.player_subtitle), style = MaterialTheme.typography.labelMedium)
                    }
                })
            }
        },
        bottomBar = {
            if (!state.isPlayerFullscreen) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    if (currentTrack != null) {
                        MiniPlayerBar(
                            track = currentTrack,
                            isPlaying = state.isPlaying,
                            isLoading = state.isLoadingPlayback,
                            playbackPositionMs = state.playbackPositionMs,
                            onOpenFullscreenPlayer = onOpenFullscreenPlayer,
                            onTogglePlayback = onToggleCurrentPlayback
                        )
                    }
                    ModernBottomBar(
                        selectedTab = state.selectedTab,
                        showServerTab = state.showServerTab,
                        showRadioTab = state.showRadioTab,
                        size = state.bottomBarSize,
                        floating = state.bottomBarFloating,
                        glass = state.bottomBarGlass,
                        compact = state.bottomBarCompact,
                        showLabels = state.bottomBarShowLabels,
                        glow = state.bottomBarActiveGlow,
                        onTabSelected = onTabSelected
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)))
            ).padding(padding)
        ) {
            if (state.selectedTab == AppTab.Player) {
                PlayerTab(state, currentTrack, onCategorySelected, onSourceFilterSelected, onSearchQueryChanged, onTrackSelected, onOpenDrilldown, onClearDrilldown, onToggleCurrentPlayback, onPlaybackModeSelected, onPlayPrevious, onPlayNext, onOpenFullscreenPlayer, onStopPlayback, onSeekTo)
            } else if (state.selectedTab == AppTab.Radio) {
                RadioTab(state, onTrackSelected, onOpenRadioCountryPicker, onRadioSearchQueryChanged)
            } else if (state.selectedTab == AppTab.Server) {
                ServerTab(state, onDismissServerInfo, onTrackSelected, onDownloadServerTrack)
            } else {
                SettingsTab(state, onStyleSelected, onDarkModeLevelSelected, onPlayerStyleSelected, onBottomBarSizeSelected, onBottomBarFloatingChanged, onBottomBarGlassChanged, onBottomBarCompactChanged, onBottomBarLabelsChanged, onBottomBarGlowChanged, onShowServerTabChanged, onShowRadioTabChanged, onAudioOutputModeSelected, onEqualizerPresetSelected, onBassChanged, onMidChanged, onTrebleChanged, onBassBoostChanged, onSurroundChanged, onLoudnessChanged, onShowPlaybackProgressChanged, onRefreshSources, onToggleSourceEnabled, onFolderEnabledChanged, onSourceFolderChanged, onSourceFolderToggled, onSourceFormatToggled, onSourceDurationFilterSelected, onServerEndpointChanged, onServerTypeChanged, onServerUserChanged, onServerPasswordChanged, onServerDownloadStorageChanged, onServerSortOrderChanged, onDeleteDownloadedFiles, onServerWifiOnlyChanged, onServerDataSaverChanged, onServerOriginalQualityChanged, onServerBitrateChanged, onConnectToServer, onCheckForUpdates, onShowReleaseNotes, onOpenRadioCountryPicker, onShowRadioMetadataChanged)
            }
            if (state.isPlayerFullscreen && currentTrack != null) FullscreenPlayer(state, currentTrack, onCloseFullscreenPlayer, onToggleCurrentPlayback, onSeekTo, onPlaybackModeSelected, onPlayPrevious, onPlayNext, onStopPlayback, onDownloadServerTrack, onToggleFavorite, onUpdateMetadata)
            if (state.showWelcomeDialog) WelcomeDialog(onAcceptWelcome)
            if (state.isScanning) ScanDialog()
            state.infoDialogMessage?.let { message ->
                InfoDialog(message = message, onDismiss = onDismissInfoDialog)
            }
            if (state.showRadioCountryPicker) {
                RadioCountryDialog(onSelect = onRadioCountrySelected, onDismiss = onDismissRadioCountryPicker)
            }
            if (state.showReleaseNotesDialog) {
                ReleaseNotesDialog(state.releaseNotes, onDismissReleaseNotesDialog)
            }
            if (state.showUpdateDialog) {
                UpdateAvailableDialog(
                    version = state.updateAvailableVersion.orEmpty(),
                    notes = state.updateReleaseNotes,
                    url = state.updateUrl,
                    onDismiss = onDismissUpdateDialog
                )
            }
            PlaybackModeFeedback(state.playbackMode)
        }
    }
}

@Composable
private fun PlaybackModeFeedback(playbackMode: PlaybackMode) {
    var visible by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(playbackMode) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        visible = true
        delay(1800)
        visible = false
    }

    if (visible) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 28.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(playbackModeIcon(playbackMode), null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(playbackMode.titleRes),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTab(
    state: MMusicUiState,
    currentTrack: MusicTrack?,
    onCategorySelected: (LibraryCategory) -> Unit,
    onSourceFilterSelected: (MusicSourceType?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onTrackSelected: (MusicTrack) -> Unit,
    onOpenDrilldown: (DrilldownType, String) -> Unit,
    onClearDrilldown: () -> Unit,
    onToggleCurrentPlayback: () -> Unit,
    onPlaybackModeSelected: (PlaybackMode) -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onOpenFullscreenPlayer: () -> Unit,
    onStopPlayback: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val localSourceTypes = setOf(MusicSourceType.Internal, MusicSourceType.SdCard, MusicSourceType.UsbOtg)
    val libraryTracks = state.tracks.filter { it.sourceType in (localSourceTypes + MusicSourceType.Server) }
    val enabledTypes = state.sources.filter { it.enabled && it.type in localSourceTypes }.map { it.type }.toSet()
    val allFolders = libraryTracks
        .filter { it.sourceType in localSourceTypes }
        .map { it.folder }
        .distinct()
        .sorted()
    val filtered = libraryTracks
        .filter { it.sourceType == MusicSourceType.Server || it.sourceType in localSourceTypes }
        .filter { state.selectedCategory == LibraryCategory.Favorites || enabledTypes.isEmpty() || it.sourceType in enabledTypes }
        .filter { state.selectedCategory != LibraryCategory.Favorites || it.id in state.favoriteTrackIds }
        .filter { state.selectedCategory == LibraryCategory.Favorites || state.selectedSourceFilter == null || it.sourceType == state.selectedSourceFilter }
        .filter { q ->
            val s = state.librarySearchQuery.trim()
            s.isBlank() || listOf(q.title, q.artist, q.album, q.folder).any { it.contains(s, true) }
        }.filter { track ->
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

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedTextField(value = state.librarySearchQuery, onValueChange = onSearchQueryChanged, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search_library)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }) }
        if (state.libraryDrilldown != null) item {
            AssistChip(onClick = onClearDrilldown, label = { Text("${stringResource(R.string.filtered_by)}: ${state.libraryDrilldown.value}") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) })
        }
        item { ChipRow { LibraryCategory.entries.forEach { FilterChip(selected = state.selectedCategory == it, onClick = { onCategorySelected(it) }, label = { Text(stringResource(it.titleRes)) }) } } }
        item { ChipRow { FilterChip(selected = state.selectedSourceFilter == null, onClick = { onSourceFilterSelected(null) }, label = { Text(stringResource(R.string.all_sources)) }); state.sources.filter { it.enabled && it.type in localSourceTypes }.forEach { src -> FilterChip(selected = state.selectedSourceFilter == src.type, onClick = { onSourceFilterSelected(src.type) }, label = { Text(stringResource(src.type.titleRes)) }) } } }
        if (state.libraryDrilldown?.type == DrilldownType.Folder && allFolders.isNotEmpty()) item {
            ChipRow {
                allFolders.forEach { folder ->
                    FilterChip(
                        selected = state.libraryDrilldown.value == folder,
                        onClick = { onOpenDrilldown(DrilldownType.Folder, folder) },
                        label = { Text(folder) }
                    )
                }
            }
        }
        if (!state.hasMediaPermission) item { InfoCard(Icons.Rounded.LibraryMusic, stringResource(R.string.media_permission_needed), stringResource(R.string.media_permission_needed)) }
        if (state.hasMediaPermission && filtered.isEmpty()) item { InfoCard(Icons.Rounded.LibraryMusic, stringResource(R.string.no_music_found), stringResource(R.string.no_music_found_hint)) }
        when (state.selectedCategory) {
            LibraryCategory.AllMusic -> items(filtered) { TrackRow(it, state.currentTrackId == it.id, state.currentTrackId == it.id && state.isPlaying, state.currentTrackId == it.id && state.isLoadingPlayback) { onTrackSelected(it) } }
            LibraryCategory.Favorites -> items(filtered) { TrackRow(it, state.currentTrackId == it.id, state.currentTrackId == it.id && state.isPlaying, state.currentTrackId == it.id && state.isLoadingPlayback) { onTrackSelected(it) } }
            LibraryCategory.Artists -> items(filtered.groupBy { it.artist }.toList()) { (artist, tracks) -> GroupRow(artist, "${tracks.size} | ${tracks.joinToString { it.title }}", Icons.Rounded.Person) { onOpenDrilldown(DrilldownType.Artist, artist) } }
            LibraryCategory.Albums -> items(filtered.groupBy { it.album }.toList()) { (album, tracks) -> GroupRow(album, "${tracks.first().artist} | ${tracks.size}", Icons.Rounded.Album) { onOpenDrilldown(DrilldownType.Album, album) } }
            LibraryCategory.Folders -> items(filtered.groupBy { it.folder }.toList()) { (folder, tracks) -> GroupRow(folder, "${stringResource(tracks.first().sourceType.titleRes)} | ${tracks.size}", Icons.Rounded.Folder) { onOpenDrilldown(DrilldownType.Folder, folder) } }
            LibraryCategory.Sources -> items(state.sources.filter { it.enabled && it.type in localSourceTypes }.map { it.type to filtered.filter { t -> t.sourceType == it.type } }) { (type, tracks) -> GroupRow(stringResource(type.titleRes), "${tracks.size}", iconForSource(type)) { onOpenDrilldown(DrilldownType.Source, type.name) } }
        }
    }
}

@Composable
private fun SettingsTab(
    state: MMusicUiState,
    onStyleSelected: (UiStyle) -> Unit,
    onDarkModeLevelSelected: (DarkModeLevel) -> Unit,
    onPlayerStyleSelected: (PlayerStyle) -> Unit,
    onBottomBarSizeSelected: (BottomBarSize) -> Unit,
    onBottomBarFloatingChanged: (Boolean) -> Unit,
    onBottomBarGlassChanged: (Boolean) -> Unit,
    onBottomBarCompactChanged: (Boolean) -> Unit,
    onBottomBarLabelsChanged: (Boolean) -> Unit,
    onBottomBarGlowChanged: (Boolean) -> Unit,
    onShowServerTabChanged: (Boolean) -> Unit,
    onShowRadioTabChanged: (Boolean) -> Unit,
    onAudioOutputModeSelected: (AudioOutputMode) -> Unit,
    onEqualizerPresetSelected: (AudioOutputMode, EqualizerPreset) -> Unit,
    onBassChanged: (AudioOutputMode, Float) -> Unit,
    onMidChanged: (AudioOutputMode, Float) -> Unit,
    onTrebleChanged: (AudioOutputMode, Float) -> Unit,
    onBassBoostChanged: (AudioOutputMode, Float) -> Unit,
    onSurroundChanged: (AudioOutputMode, Float) -> Unit,
    onLoudnessChanged: (AudioOutputMode, Float) -> Unit,
    onShowPlaybackProgressChanged: (Boolean) -> Unit,
    onRefreshSources: () -> Unit,
    onToggleSourceEnabled: (MusicSourceType) -> Unit,
    onFolderEnabledChanged: (MusicSourceType) -> Unit,
    onSourceFolderChanged: (MusicSourceType, String) -> Unit,
    onSourceFolderToggled: (MusicSourceType, String) -> Unit,
    onSourceFormatToggled: (MusicSourceType, String) -> Unit,
    onSourceDurationFilterSelected: (MusicSourceType, DurationFilter) -> Unit,
    onServerEndpointChanged: (String) -> Unit,
    onServerTypeChanged: (ServerType) -> Unit,
    onServerUserChanged: (String) -> Unit,
    onServerPasswordChanged: (String) -> Unit,
    onServerDownloadStorageChanged: (DownloadStorage) -> Unit,
    onServerSortOrderChanged: (ServerSortOrder) -> Unit,
    onDeleteDownloadedFiles: () -> Unit,
    onServerWifiOnlyChanged: (Boolean) -> Unit,
    onServerDataSaverChanged: (Boolean) -> Unit,
    onServerOriginalQualityChanged: (Boolean) -> Unit,
    onServerBitrateChanged: (Int) -> Unit,
    onConnectToServer: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onOpenRadioCountryPicker: () -> Unit,
    onShowRadioMetadataChanged: (Boolean) -> Unit
) {
    var selectedSection by remember { mutableStateOf("appearance") }
    val extraDarkEnabled = state.uiStyle == UiStyle.Base
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SettingsEntry(
                title = stringResource(R.string.settings_appearance),
                icon = Icons.Rounded.Palette,
                selected = selectedSection == "appearance",
                onClick = { selectedSection = "appearance" }
            )
        }
        item {
            SettingsSection(visible = selectedSection == "appearance") {
                Block {
                Text(stringResource(R.string.ui_style), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ChipRow { UiStyle.entries.forEach { FilterChip(selected = state.uiStyle == it, onClick = { onStyleSelected(it) }, label = { Text(stringResource(it.titleRes)) }) } }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.dark_mode_title), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.alpha(if (extraDarkEnabled) 1f else 0.45f)) {
                    ChipRow {
                        DarkModeLevel.entries.forEach {
                            FilterChip(
                                selected = state.darkModeLevel == it,
                                onClick = { onDarkModeLevelSelected(it) },
                                enabled = extraDarkEnabled,
                                label = { Text(stringResource(it.titleRes)) }
                            )
                        }
                    }
                }
                if (!extraDarkEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.dark_mode_base_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.player_style_title), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ChipRow { PlayerStyle.entries.forEach { FilterChip(selected = state.playerStyle == it, onClick = { onPlayerStyleSelected(it) }, label = { Text(stringResource(it.titleRes)) }) } }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.bottom_bar_style_title), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ChipRow {
                    BottomBarSize.entries.forEach { size ->
                        FilterChip(
                            selected = state.bottomBarSize == size,
                            onClick = { onBottomBarSizeSelected(size) },
                            label = { Text(stringResource(size.titleRes)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingRow(stringResource(R.string.bottom_bar_floating), state.bottomBarFloating, onBottomBarFloatingChanged)
                Spacer(Modifier.height(8.dp))
                SettingRow(stringResource(R.string.bottom_bar_glass), state.bottomBarGlass, onBottomBarGlassChanged)
                Spacer(Modifier.height(8.dp))
                SettingRow(stringResource(R.string.bottom_bar_compact), state.bottomBarCompact, onBottomBarCompactChanged)
                Spacer(Modifier.height(8.dp))
                SettingRow(stringResource(R.string.bottom_bar_show_labels), state.bottomBarShowLabels, onBottomBarLabelsChanged)
                Spacer(Modifier.height(8.dp))
                SettingRow(stringResource(R.string.bottom_bar_active_glow), state.bottomBarActiveGlow, onBottomBarGlowChanged)
                Spacer(Modifier.height(8.dp))
                SettingRow(stringResource(R.string.show_server_tab), state.showServerTab, onShowServerTabChanged)
                Spacer(Modifier.height(8.dp))
                SettingRow(stringResource(R.string.show_radio_tab), state.showRadioTab, onShowRadioTabChanged)
                Spacer(Modifier.height(10.dp))
                SettingRow(stringResource(R.string.show_playback_progress), state.showPlaybackProgress, onShowPlaybackProgressChanged)
            }
        }
        }

        item {
            SettingsEntry(
                title = stringResource(R.string.settings_sound),
                icon = Icons.Rounded.GraphicEq,
                selected = selectedSection == "sound",
                onClick = { selectedSection = "sound" }
            )
        }
        item {
            SettingsSection(visible = selectedSection == "sound") {
                val profile = state.equalizerProfiles.firstOrNull { it.outputMode == state.selectedAudioOutputMode }
                    ?: EqualizerProfile(state.selectedAudioOutputMode)
                Block {
                Text(stringResource(R.string.sound_settings_title), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ChipRow {
                    AudioOutputMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.selectedAudioOutputMode == mode,
                            onClick = { onAudioOutputModeSelected(mode) },
                            label = { Text(stringResource(mode.titleRes)) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.sound_profile_hint, stringResource(state.selectedAudioOutputMode.titleRes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.equalizer_preset_title), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ChipRow {
                    EqualizerPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = profile.preset == preset,
                            onClick = { onEqualizerPresetSelected(state.selectedAudioOutputMode, preset) },
                            label = { Text(stringResource(preset.titleRes)) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                EqualizerSlider(
                    title = stringResource(R.string.equalizer_bass),
                    value = profile.bass,
                    onValueChange = { onBassChanged(state.selectedAudioOutputMode, it) }
                )
                Spacer(Modifier.height(10.dp))
                EqualizerSlider(
                    title = stringResource(R.string.equalizer_mid),
                    value = profile.mid,
                    onValueChange = { onMidChanged(state.selectedAudioOutputMode, it) }
                )
                Spacer(Modifier.height(10.dp))
                EqualizerSlider(
                    title = stringResource(R.string.equalizer_treble),
                    value = profile.treble,
                    onValueChange = { onTrebleChanged(state.selectedAudioOutputMode, it) }
                )
                Spacer(Modifier.height(10.dp))
                EqualizerSlider(
                    title = stringResource(R.string.equalizer_bass_boost),
                    value = profile.bassBoost,
                    valueRange = 0f..1f,
                    onValueChange = { onBassBoostChanged(state.selectedAudioOutputMode, it) }
                )
                Spacer(Modifier.height(10.dp))
                EqualizerSlider(
                    title = stringResource(R.string.equalizer_surround),
                    value = profile.surround,
                    valueRange = 0f..1f,
                    onValueChange = { onSurroundChanged(state.selectedAudioOutputMode, it) }
                )
                Spacer(Modifier.height(10.dp))
                EqualizerSlider(
                    title = stringResource(R.string.equalizer_loudness),
                    value = profile.loudness,
                    valueRange = 0f..1f,
                    onValueChange = { onLoudnessChanged(state.selectedAudioOutputMode, it) }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.equalizer_live_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }

        item {
            SettingsEntry(
                title = stringResource(R.string.settings_storage),
                icon = Icons.Rounded.Storage,
                selected = selectedSection == "storage",
                onClick = { selectedSection = "storage" }
            )
        }
        if (selectedSection == "storage") {
            item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = onRefreshSources) { Icon(Icons.Rounded.Refresh, null) } } }
            items(state.sources.filter { it.type != MusicSourceType.Server }) { source ->
                Block {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(iconForSource(source.type), null); Column { Text(stringResource(source.type.titleRes), fontWeight = FontWeight.SemiBold); Text(sourceSubtitle(source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        Switch(checked = source.enabled, onCheckedChange = { onToggleSourceEnabled(source.type) })
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingRow(stringResource(R.string.folder_management), source.folderManagementEnabled) { onFolderEnabledChanged(source.type) }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.storage_format_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipRow {
                        MediaStoreScanner.supportedAudioExtensions.forEach { format ->
                            FilterChip(
                                selected = source.selectedFormats.isEmpty() || format in source.selectedFormats,
                                onClick = { onSourceFormatToggled(source.type, format) },
                                label = { Text(format.uppercase()) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.storage_format_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.storage_duration_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipRow {
                        DurationFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = source.durationFilter == filter,
                                onClick = { onSourceDurationFilterSelected(source.type, filter) },
                                label = { Text(stringResource(filter.titleRes)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.storage_duration_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (source.folderManagementEnabled) {
                        Spacer(Modifier.height(8.dp))
                        if (source.availableFolders.isEmpty()) {
                            Text(
                                stringResource(R.string.storage_folder_loading_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                stringResource(R.string.storage_folder_list_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            val selectedFolders = source.selectedFolder
                                .split('\n')
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .toSet()
                            source.availableFolders.forEach { folder ->
                                SettingRow(
                                    label = folder,
                                    checked = folder in selectedFolders,
                                    onCheckedChange = { onSourceFolderToggled(source.type, folder) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.storage_folder_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = source.selectedFolder,
                            onValueChange = { onSourceFolderChanged(source.type, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.storage_folder_label)) },
                            enabled = source.enabled,
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (source.connected) stringResource(R.string.connected) else stringResource(R.string.source_not_detected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SettingsEntry(
                title = stringResource(R.string.settings_server),
                icon = Icons.Rounded.Router,
                selected = selectedSection == "server",
                onClick = { selectedSection = "server" }
            )
        }
        item { SettingsSection(visible = selectedSection == "server") { Block {
            val bitrateLocked = state.serverConfig.originalQualityPlayback
            val sdAvailable = state.sources.any { it.type == MusicSourceType.SdCard && it.connected }
            Text(stringResource(R.string.server_settings_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ChipRow { ServerType.entries.forEach { FilterChip(selected = state.serverConfig.type == it, onClick = { onServerTypeChanged(it) }, label = { Text(stringResource(it.titleRes)) }) } }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = state.serverConfig.endpoint, onValueChange = onServerEndpointChanged, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_hint)) }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = state.serverConfig.userName, onValueChange = onServerUserChanged, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_user)) }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = state.serverConfig.password, onValueChange = onServerPasswordChanged, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_password)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.download_storage_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ChipRow {
                DownloadStorage.entries.forEach { storage ->
                    FilterChip(
                        selected = state.serverConfig.downloadStorage == storage,
                        onClick = { onServerDownloadStorageChanged(storage) },
                        enabled = storage != DownloadStorage.SdCard || sdAvailable,
                        label = { Text(stringResource(storage.titleRes)) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.download_storage_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!sdAvailable) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.download_storage_sd_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.server_sort_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ChipRow {
                ServerSortOrder.entries.forEach { sortOrder ->
                    FilterChip(
                        selected = state.serverConfig.sortOrder == sortOrder,
                        onClick = { onServerSortOrderChanged(sortOrder) },
                        label = { Text(stringResource(sortOrder.titleRes)) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDeleteDownloadedFiles) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_downloads))
            }
            Spacer(Modifier.height(8.dp))
            SettingRow(stringResource(R.string.server_wifi_only_playback), state.serverConfig.wifiOnlyPlayback, onServerWifiOnlyChanged)
            Spacer(Modifier.height(8.dp))
            SettingRow(stringResource(R.string.server_data_saver), state.serverConfig.dataSaverEnabled, onServerDataSaverChanged)
            Spacer(Modifier.height(8.dp))
            SettingRow(stringResource(R.string.server_original_quality), state.serverConfig.originalQualityPlayback, onServerOriginalQualityChanged)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.server_bitrate_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.alpha(if (bitrateLocked) 0.45f else 1f)) {
                ChipRow {
                    com.mmusic.app.data.serverBitrateOptions.forEach { bitrate ->
                        FilterChip(
                            selected = state.serverConfig.bitrateKbps == bitrate,
                            onClick = { onServerBitrateChanged(bitrate) },
                            enabled = !bitrateLocked,
                            label = { Text("$bitrate kbps") }
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (bitrateLocked) R.string.server_bitrate_disabled_hint else R.string.server_bitrate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onConnectToServer, enabled = !state.serverConfig.isConnecting) { Text(if (state.serverConfig.isConnecting) stringResource(R.string.server_connecting) else stringResource(R.string.server_connect_action)) }
            Spacer(Modifier.height(6.dp))
            Text(if (state.serverConfig.statusMessage.isBlank()) stringResource(R.string.server_support_hint) else state.serverConfig.statusMessage, color = if (state.serverConfig.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        } } }

        item {
            SettingsEntry(
                title = stringResource(R.string.settings_radio),
                icon = Icons.Rounded.Radio,
                selected = selectedSection == "radio",
                onClick = { selectedSection = "radio" }
            )
        }
        item { SettingsSection(visible = selectedSection == "radio") { Block {
            Text(stringResource(R.string.radio_settings_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenRadioCountryPicker) {
                Icon(Icons.Rounded.Public, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.radioCountryName.isBlank()) {
                        stringResource(R.string.radio_country_select)
                    } else {
                        stringResource(R.string.radio_country_current, state.radioCountryName)
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            SettingRow(stringResource(R.string.radio_metadata_toggle), state.showRadioMetadata, onShowRadioMetadataChanged)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.radio_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } } }

        item {
            SettingsEntry(
                title = stringResource(R.string.settings_about),
                icon = Icons.Rounded.Info,
                selected = selectedSection == "about",
                onClick = { selectedSection = "about" }
            )
        }
        item { SettingsSection(visible = selectedSection == "about") { Block {
            val context = LocalContext.current
            Text(
                stringResource(R.string.current_language_note, stringResource(state.language.titleRes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            AboutLinkCard(
                icon = Icons.Rounded.VolunteerActivism,
                title = stringResource(R.string.support_title),
                description = stringResource(R.string.support_paypal_text),
                actionLabel = stringResource(R.string.support_paypal_action),
                accent = MaterialTheme.colorScheme.primaryContainer,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/adamtechhu")))
                }
            )
            Spacer(Modifier.height(10.dp))
            AboutLinkCard(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.github_button),
                description = "github.com/adamtechhu/m-music",
                actionLabel = stringResource(R.string.open_link),
                accent = MaterialTheme.colorScheme.secondaryContainer,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/adamtechhu/m-music")))
                }
            )
            Spacer(Modifier.height(10.dp))
            AboutLinkCard(
                icon = Icons.Rounded.Mail,
                title = stringResource(R.string.contact_title),
                description = "adamtechhu@proton.me",
                actionLabel = stringResource(R.string.send_email),
                accent = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:adamtechhu@proton.me"))
                    )
                }
            )
            Spacer(Modifier.height(10.dp))
            AboutLinkCard(
                icon = Icons.Rounded.SmartDisplay,
                title = stringResource(R.string.youtube_title),
                description = "@Adam_techhu",
                actionLabel = stringResource(R.string.open_link),
                accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@Adam_techhu")))
                }
            )
            Spacer(Modifier.height(12.dp))
            AboutLinkCard(
                icon = Icons.Rounded.SystemUpdate,
                title = stringResource(R.string.check_updates_title),
                description = if (state.updateCheckStatus.isBlank()) stringResource(R.string.check_updates_description) else state.updateCheckStatus,
                actionLabel = stringResource(R.string.check_updates_action),
                accent = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onCheckForUpdates
            )
            Spacer(Modifier.height(10.dp))
            AboutLinkCard(
                icon = Icons.Rounded.OpenInNew,
                title = stringResource(R.string.release_notes_title),
                description = stringResource(R.string.release_notes_description),
                actionLabel = stringResource(R.string.open_release_notes),
                accent = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = onShowReleaseNotes
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.version_label), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
    }
}

@Composable
private fun WelcomeDialog(onAcceptWelcome: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.onboarding_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.onboarding_body))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_storage_note),
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onAcceptWelcome) {
                Text(stringResource(R.string.onboarding_ok))
            }
        }
    )
}
@Composable private fun ScanDialog() { AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.scan_title), fontWeight = FontWeight.Bold) }, text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(28.dp)); Text(stringResource(R.string.scan_body)) } }, confirmButton = {}) }
@Composable private fun InfoDialog(message: String, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("M-Music", fontWeight = FontWeight.Bold) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onboarding_ok)) } }) }
@Composable private fun Block(content: @Composable ColumnScope.() -> Unit) { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)), modifier = Modifier.animateContentSize()) { Column(Modifier.fillMaxWidth().padding(12.dp), content = content) } }
@Composable private fun Section(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp)) }
@Composable private fun ChipRow(content: @Composable RowScope.() -> Unit) { Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content) }
@Composable
private fun SettingsSection(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(200))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
@Composable
private fun SettingsEntry(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        animationSpec = tween(220),
        label = "settings_entry_color"
    )
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor
        )
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(10.dp))
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Icon(if (selected) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
    }
}

@Composable
private fun ModernBottomBar(
    selectedTab: AppTab,
    showServerTab: Boolean,
    showRadioTab: Boolean,
    size: BottomBarSize,
    floating: Boolean,
    glass: Boolean,
    compact: Boolean,
    showLabels: Boolean,
    glow: Boolean,
    onTabSelected: (AppTab) -> Unit
) {
    val visibleTabs = AppTab.entries.filter { tab ->
        when (tab) {
            AppTab.Player, AppTab.Settings -> true
            AppTab.Server -> showServerTab
            AppTab.Radio -> showRadioTab
        }
    }
    val sizeScale = when (size) {
        BottomBarSize.Small -> 0.88f
        BottomBarSize.Medium -> 1f
        BottomBarSize.Large -> 1.14f
    }
    val shape = RoundedCornerShape(if (floating) 28.dp else 0.dp)
    val containerColor = if (glass) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (glass) 0.22f else 0.08f)
    val horizontalPadding = 0.dp
    val verticalPadding = if (floating) 6.dp else 0.dp
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        shape = shape,
        color = containerColor,
        tonalElevation = if (floating) 10.dp else 4.dp,
        shadowElevation = if (floating) 16.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ((if (compact) 6 else 10) * sizeScale).dp, vertical = ((if (compact) 6 else 10) * sizeScale).dp),
            horizontalArrangement = Arrangement.spacedBy(((if (compact) 6 else 10) * sizeScale).dp)
        ) {
            visibleTabs.forEach { tab ->
                val selected = tab == selectedTab
                val accent = MaterialTheme.colorScheme.primary
                val pillColor = if (selected) accent.copy(alpha = if (glass) 0.22f else 0.18f) else Color.Transparent
                val contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected(tab) },
                    shape = RoundedCornerShape(if (compact) 20.dp else 24.dp),
                    color = pillColor,
                    tonalElevation = if (selected && glow) 8.dp else 0.dp,
                    shadowElevation = if (selected && glow) 10.dp else 0.dp,
                    border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = (6 * sizeScale).dp, vertical = ((if (compact) 6 else 8) * sizeScale).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (showLabels) 6.dp else 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(((if (compact) 30 else 36) * sizeScale).dp)
                                .background(
                                    if (selected) accent.copy(alpha = if (glow) 0.18f else 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.Player -> Icons.Rounded.PlayArrow
                                    AppTab.Radio -> Icons.Rounded.Radio
                                    AppTab.Server -> Icons.Rounded.Router
                                    AppTab.Settings -> Icons.Rounded.Settings
                                },
                                contentDescription = null,
                                tint = contentColor
                            )
                        }
                        if (showLabels) {
                            Text(
                                text = stringResource(tab.titleRes),
                                color = contentColor,
                                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    track: MusicTrack,
    isPlaying: Boolean,
    isLoading: Boolean,
    playbackPositionMs: Long,
    onOpenFullscreenPlayer: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFullscreenPlayer),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (track.artworkUri != null) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(track.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text("${track.artist} | ${track.album}", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isLoading) stringResource(R.string.player_loading_short) else formatTime(playbackPositionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledIconButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier.size(46.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                }
            }
        }
    }
}

@Composable
private fun ServerTab(state: MMusicUiState, onDismiss: () -> Unit, onTrackSelected: (MusicTrack) -> Unit, onDownloadTrack: (MusicTrack) -> Unit) {
    val serverTracks = state.tracks.filter { it.sourceType == MusicSourceType.Server && !it.isLocalFile }.let { tracks ->
        when (state.serverConfig.sortOrder) {
            ServerSortOrder.Alphabetical -> tracks.sortedBy { it.title.lowercase() }
            ServerSortOrder.RecentAdded -> tracks
        }
    }
    val downloadedTracks = state.tracks.filter { it.sourceType == MusicSourceType.Server && it.isLocalFile }.let { tracks ->
        when (state.serverConfig.sortOrder) {
            ServerSortOrder.Alphabetical -> tracks.sortedBy { it.title.lowercase() }
            ServerSortOrder.RecentAdded -> tracks
        }
    }
    val hasConfiguredServer = state.serverConfig.endpoint.isNotBlank() && !state.serverConfig.endpoint.contains("media.example.com", ignoreCase = true)
    var showFolders by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var serverSearchQuery by remember { mutableStateOf("") }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    val baseTracks = if (showDownloads) downloadedTracks else serverTracks
    val visibleTracks = baseTracks
        .filter { selectedFolder == null || it.folder == selectedFolder }
        .filter { selectedArtist == null || it.artist == selectedArtist }
        .filter { selectedAlbum == null || it.album == selectedAlbum }
        .filter { track ->
            val query = serverSearchQuery.trim()
            query.isBlank() || listOf(track.title, track.artist, track.album, track.folder).any {
                it.contains(query, ignoreCase = true)
            }
        }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!state.serverConfig.connected && hasConfiguredServer) {
            item {
                InfoCard(
                    Icons.Rounded.CloudOff,
                    stringResource(R.string.offline_downloads_title),
                    stringResource(R.string.offline_downloads_message)
                )
            }
        }
        if (state.showServerInfoDialog && !hasConfiguredServer) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.source_server), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
                        }
                        Text(stringResource(R.string.server_tab_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (serverTracks.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = serverSearchQuery,
                    onValueChange = { serverSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.server_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (serverSearchQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    serverSearchQuery = ""
                                    showFolders = false
                                    showDownloads = false
                                    selectedFolder = null
                                    selectedArtist = null
                                    selectedAlbum = null
                                }
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    },
                    singleLine = true
                )
            }
            item {
                ChipRow {
                    FilterChip(
                        selected = selectedArtist == null,
                        onClick = { selectedArtist = null },
                        label = { Text(stringResource(R.string.server_artist_all)) }
                    )
                    serverTracks.map { it.artist }.distinct().sorted().forEach { artist ->
                        FilterChip(
                            selected = selectedArtist == artist,
                            onClick = { selectedArtist = if (selectedArtist == artist) null else artist },
                            label = { Text(artist) }
                        )
                    }
                }
            }
            item {
                ChipRow {
                    FilterChip(
                        selected = selectedAlbum == null,
                        onClick = { selectedAlbum = null },
                        label = { Text(stringResource(R.string.server_album_all)) }
                    )
                    serverTracks.map { it.album }.distinct().sorted().forEach { album ->
                        FilterChip(
                            selected = selectedAlbum == album,
                            onClick = { selectedAlbum = if (selectedAlbum == album) null else album },
                            label = { Text(album) }
                        )
                    }
                }
            }
        }
        if (serverTracks.isNotEmpty() || downloadedTracks.isNotEmpty()) {
            item {
                ChipRow {
                    FilterChip(
                        selected = !showFolders && !showDownloads,
                        onClick = {
                            showFolders = false
                            showDownloads = false
                            selectedFolder = null
                        },
                        label = { Text(stringResource(R.string.all_music)) }
                    )
                    FilterChip(
                        selected = showFolders && !showDownloads,
                        onClick = {
                            showFolders = true
                            showDownloads = false
                            selectedFolder = null
                        },
                        label = { Text(stringResource(R.string.folders)) }
                    )
                    FilterChip(
                        selected = showDownloads,
                        onClick = {
                            showDownloads = true
                            showFolders = false
                            selectedFolder = null
                        },
                        label = { Text(stringResource(R.string.downloads_tab)) }
                    )
                }
            }
        }
        if (selectedFolder != null) {
            item {
                AssistChip(
                    onClick = { selectedFolder = null },
                    label = { Text("${stringResource(R.string.filtered_by)}: $selectedFolder") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
                )
            }
        }
        if (selectedArtist != null) {
            item {
                AssistChip(
                    onClick = { selectedArtist = null },
                    label = { Text("${stringResource(R.string.server_artist_label)}: $selectedArtist") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
                )
            }
        }
        if (selectedAlbum != null) {
            item {
                AssistChip(
                    onClick = { selectedAlbum = null },
                    label = { Text("${stringResource(R.string.server_album_label)}: $selectedAlbum") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
                )
            }
        }
        if (serverTracks.isEmpty() && downloadedTracks.isEmpty()) {
            item {
                InfoCard(
                    if (state.serverConfig.connected) Icons.Rounded.Router else Icons.Rounded.CloudOff,
                    if (state.serverConfig.connected) stringResource(R.string.source_server) else stringResource(R.string.offline_downloads_title),
                    if (state.serverConfig.connected) stringResource(R.string.server_library_empty) else stringResource(R.string.offline_downloads_message)
                )
            }
        } else if (showDownloads && visibleTracks.isEmpty()) {
            item {
                InfoCard(
                    Icons.Rounded.DownloadDone,
                    stringResource(R.string.downloads_tab),
                    stringResource(R.string.no_downloads_yet)
                )
            }
        } else if (showFolders && selectedFolder == null) {
            items(visibleTracks.groupBy { it.folder }.toList()) { (folder, tracks) ->
                GroupRow(
                    title = folder,
                    subtitle = "${tracks.size}",
                    icon = Icons.Rounded.Folder,
                    onClick = { selectedFolder = folder }
                )
            }
        } else {
            items(visibleTracks) { track ->
                TrackRow(
                    track = track,
                    isCurrent = state.currentTrackId == track.id,
                    isPlaying = state.currentTrackId == track.id && state.isPlaying,
                    isLoading = state.currentTrackId == track.id && state.isLoadingPlayback,
                    trailingContent = {
                        IconButton(onClick = { onDownloadTrack(track) }) {
                            Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.download_track))
                        }
                    },
                    onClick = { onTrackSelected(track) }
                )
            }
        }
    }
}

@Composable
private fun RadioTab(
    state: MMusicUiState,
    onTrackSelected: (MusicTrack) -> Unit,
    onOpenCountryPicker: () -> Unit,
    onRadioSearchQueryChanged: (String) -> Unit
) {
    val visibleStations = state.radioStations.filter { track ->
        val query = state.radioSearchQuery.trim()
        query.isBlank() || listOf(track.title, track.artist, track.album).any { value ->
            value.contains(query, ignoreCase = true)
        }
    }
    val currentRadioTrack = state.radioStations.firstOrNull { it.id == state.currentTrackId }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedButton(onClick = onOpenCountryPicker) {
                Icon(Icons.Rounded.Public, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.radioCountryName.isBlank()) {
                        stringResource(R.string.radio_country_select)
                    } else {
                        stringResource(R.string.radio_country_current, state.radioCountryName)
                    }
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.radioSearchQuery,
                onValueChange = onRadioSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.radio_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true
            )
        }
        if (state.showRadioMetadata && currentRadioTrack != null) {
            item {
                InfoCard(
                    Icons.Rounded.Equalizer,
                    stringResource(R.string.radio_now_playing_title),
                    state.radioLiveDetails.ifBlank { currentRadioTrack.album.ifBlank { stringResource(R.string.radio_metadata_unavailable) } }
                )
            }
        }
        if (state.isLoadingRadio) {
            item {
                InfoCard(Icons.Rounded.Radio, stringResource(R.string.radio_loading_title), stringResource(R.string.radio_loading_message))
            }
        } else if (state.radioStations.isEmpty()) {
            item {
                InfoCard(Icons.Rounded.Radio, stringResource(R.string.radio_tab), stringResource(R.string.radio_empty_message))
            }
        } else if (visibleStations.isEmpty()) {
            item {
                InfoCard(Icons.Rounded.SearchOff, stringResource(R.string.radio_search), stringResource(R.string.radio_search_empty))
            }
        } else {
            items(visibleStations) { track ->
                RadioStationRow(
                    track = track,
                    showMetadata = state.showRadioMetadata,
                    isCurrent = state.currentTrackId == track.id,
                    isPlaying = state.currentTrackId == track.id && state.isPlaying,
                    isLoading = state.currentTrackId == track.id && state.isLoadingPlayback,
                    onClick = { onTrackSelected(track) }
                )
            }
        }
    }
}

@Composable
private fun RadioStationRow(
    track: MusicTrack,
    showMetadata: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(
                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (track.artworkUri != null) {
                AsyncImage(model = track.artworkUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.Radio, null)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(track.title, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
            Text(track.artist, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showMetadata) {
                Text(
                    text = track.album.ifBlank { stringResource(R.string.radio_metadata_unavailable) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(track.duration, style = MaterialTheme.typography.labelMedium)
            Text(
                if (isLoading) stringResource(R.string.player_loading_short) else stringResource(R.string.radio_live_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
}

@Composable
private fun RadioCountryDialog(
    onSelect: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val countries = listOf(
        "HU" to "Magyarország",
        "DE" to "Deutschland",
        "ES" to "España",
        "GB" to "United Kingdom",
        "US" to "United States",
        "FR" to "France",
        "IT" to "Italia",
        "AT" to "Österreich"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.radio_country_dialog_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(countries) { (code, name) ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(code, name) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Text(name, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                    }
                }
            }
        }
    )
}

@Composable
private fun ReleaseNotesDialog(notes: AppReleaseNotes, onDismiss: () -> Unit) {
    var page by remember(notes.version) { mutableStateOf(0) }
    val pages = notes.pages.ifEmpty { listOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.release_notes_header, notes.version)) },
        text = { Text(pages[page]) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (page > 0) {
                    TextButton(onClick = { page -= 1 }) { Text(stringResource(R.string.previous_page)) }
                }
                if (page < pages.lastIndex) {
                    TextButton(onClick = { page += 1 }) { Text(stringResource(R.string.next_page)) }
                } else {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.onboarding_ok)) }
                }
            }
        }
    )
}

@Composable
private fun UpdateAvailableDialog(
    version: String,
    notes: String,
    url: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available, version)) },
        text = { Text(notes.ifBlank { stringResource(R.string.check_updates_description) }) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!url.isNullOrBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    onDismiss()
                }
            ) { Text(stringResource(R.string.open_link)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
private fun MetadataEditDialog(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_metadata_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.metadata_title_label)) })
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text(stringResource(R.string.metadata_artist_label)) })
                OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text(stringResource(R.string.metadata_album_label)) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, artist, album) }) {
                Text(stringResource(R.string.save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
private fun FullscreenPlayer(
    state: MMusicUiState,
    track: MusicTrack,
    onClose: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlaybackModeSelected: (PlaybackMode) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onDownloadTrack: (MusicTrack) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onUpdateMetadata: (MusicTrack, String, String, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dragOffset = remember { Animatable(0f) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showServerShareDialog by remember { mutableStateOf(false) }
    var showDiscoDialog by remember { mutableStateOf(false) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var discoEnabled by remember { mutableStateOf(false) }
    var pendingDiscoEnable by remember { mutableStateOf(false) }
    val latestIsPlaying by rememberUpdatedState(state.isPlaying)
    val latestPositionMs by rememberUpdatedState(state.playbackPositionMs)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingDiscoEnable) {
            discoEnabled = true
        }
        pendingDiscoEnable = false
    }
    val cameraManager = remember {
        context.getSystemService(CameraManager::class.java)
    }
    val torchCameraId = remember(cameraManager) {
        runCatching {
            cameraManager?.cameraIdList?.firstOrNull { cameraId ->
                cameraManager.getCameraCharacteristics(cameraId)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    fun animateToMiniPlayer() {
        coroutineScope.launch {
            dragOffset.animateTo(
                targetValue = screenHeightPx * 0.24f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
            onClose()
            dragOffset.snapTo(0f)
        }
    }

    BackHandler(enabled = true) {
        animateToMiniPlayer()
    }

    LaunchedEffect(discoEnabled, track.id, torchCameraId) {
        if (!discoEnabled || torchCameraId == null || cameraManager == null) {
            runCatching { torchCameraId?.let { cameraManager?.setTorchMode(it, false) } }
            return@LaunchedEffect
        }
        val beatIntervalMs = 468L
        var anchorRealtime = SystemClock.elapsedRealtime()
        var anchorPosition = latestPositionMs
        var lastTorchState: Boolean? = null

        while (isActive && discoEnabled) {
            val now = SystemClock.elapsedRealtime()
            if (kotlin.math.abs(latestPositionMs - anchorPosition) > 700L || !latestIsPlaying) {
                anchorRealtime = now
                anchorPosition = latestPositionMs
            }
            val effectivePosition = if (latestIsPlaying) {
                anchorPosition + (now - anchorRealtime)
            } else {
                latestPositionMs
            }
            val beatPhase = ((effectivePosition % beatIntervalMs) + beatIntervalMs) % beatIntervalMs
            val pulseOn = latestIsPlaying && (
                beatPhase < 90L ||
                    (beatPhase in 180L..245L)
                )

            if (lastTorchState != pulseOn) {
                runCatching { cameraManager.setTorchMode(torchCameraId, pulseOn) }
                lastTorchState = pulseOn
            }
            delay(45L)
        }
        runCatching { cameraManager.setTorchMode(torchCameraId, false) }
    }

    DisposableEffect(discoEnabled, torchCameraId) {
        onDispose {
            runCatching { torchCameraId?.let { cameraManager?.setTorchMode(it, false) } }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffset.value
                alpha = (1f - (dragOffset.value / (screenHeightPx * 0.9f))).coerceIn(0.82f, 1f)
            },
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)
    ) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f), MaterialTheme.colorScheme.background))).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { showTopMenu = true },
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_actions))
                }
                DropdownMenu(
                    expanded = showTopMenu,
                    onDismissRequest = { showTopMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (track.id in state.favoriteTrackIds) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite)) },
                        leadingIcon = {
                            Icon(
                                if (track.id in state.favoriteTrackIds) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showTopMenu = false
                            onToggleFavorite(track)
                        }
                    )
                    if (track.isLocalFile && track.sourceType != MusicSourceType.Server) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_metadata_title)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                showTopMenu = false
                                showMetadataDialog = true
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_track)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            showTopMenu = false
                            if (track.sourceType == MusicSourceType.Server) {
                                showServerShareDialog = true
                            } else {
                                val sourceUri = Uri.parse(track.contentUri)
                                val shareUri = when (sourceUri.scheme?.lowercase()) {
                                    "content" -> sourceUri
                                    "file" -> FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        File(requireNotNull(sourceUri.path))
                                    )
                                    else -> null
                                }
                                shareUri?.let { uri ->
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = context.contentResolver.getType(uri) ?: "audio/*"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, track.title)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                clipData = android.content.ClipData.newUri(
                                                    context.contentResolver,
                                                    track.title,
                                                    uri
                                                )
                                            },
                                            context.getString(R.string.share_track)
                                        )
                                    )
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download_track)) },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        onClick = {
                            showTopMenu = false
                            onDownloadTrack(track)
                        }
                    )
                }
                IconButton(
                    onClick = {
                        if (discoEnabled) {
                            discoEnabled = false
                            pendingDiscoEnable = false
                        } else {
                            showDiscoDialog = true
                        }
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Rounded.FlashlightOn,
                        contentDescription = stringResource(R.string.disco_party_beta_title),
                        tint = if (discoEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(58.dp)
                        .height(7.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), RoundedCornerShape(999.dp))
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    coroutineScope.launch {
                                        val nextValue = (dragOffset.value + dragAmount).coerceAtLeast(0f)
                                        dragOffset.snapTo(nextValue)
                                    }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (dragOffset.value > screenHeightPx * 0.08f) {
                                            animateToMiniPlayer()
                                        } else {
                                            dragOffset.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        dragOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                        )
                                    }
                                }
                            )
                        }
                        .clickable(onClick = ::animateToMiniPlayer)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                val isRadioTrack = track.sourceType == MusicSourceType.Radio
                val radioProgramInfo = if (isRadioTrack) {
                    track.album.ifBlank { stringResource(R.string.radio_metadata_unavailable) }
                } else {
                    ""
                }
                val radioSongInfo = if (isRadioTrack) {
                    state.radioLiveDetails
                        .takeIf { it.isNotBlank() && it != track.album && it != stringResource(R.string.radio_metadata_unavailable) }
                        .orEmpty()
                } else {
                    ""
                }
                ArtworkBox(track.artworkUri, Modifier.fillMaxWidth().height(320.dp))
                Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (isRadioTrack) {
                    Text(
                        text = radioProgramInfo,
                        color = Color(0xFF3DA5FF),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (radioSongInfo.isNotBlank()) {
                        Text(
                            text = radioSongInfo,
                            color = Color(0xFFFF5A5A),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(track.folder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("${track.artist} | ${track.album}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stringResource(track.sourceType.titleRes)} | ${track.folder}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.showPlaybackProgress) {
                    PlaybackProgress(state.playbackPositionMs, state.playbackDurationMs, state.playerStyle, onSeekTo)
                }
            }
            PlayerControls(
                style = state.playerStyle,
                isPlaying = state.isPlaying,
                playbackMode = state.playbackMode,
                onPrevious = onPrevious,
                onTogglePlayback = onTogglePlayback,
                onPlaybackModeToggle = { onPlaybackModeSelected(nextPlaybackMode(state.playbackMode)) },
                onNext = onNext,
                onStop = onStop
            )
        }
    }
    if (showServerShareDialog) {
        AlertDialog(
            onDismissRequest = { showServerShareDialog = false },
            confirmButton = {
                TextButton(onClick = { showServerShareDialog = false }) {
                    Text(stringResource(R.string.onboarding_ok))
                }
            },
            title = { Text(stringResource(R.string.server_share_blocked_title)) },
            text = { Text(stringResource(R.string.server_share_blocked_message)) }
        )
    }
    if (showMetadataDialog) {
        MetadataEditDialog(
            track = track,
            onDismiss = { showMetadataDialog = false },
            onSave = { title, artist, album ->
                showMetadataDialog = false
                onUpdateMetadata(track, title, artist, album)
            }
        )
    }
    if (showDiscoDialog) {
        AlertDialog(
            onDismissRequest = { showDiscoDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            discoEnabled = true
                        } else {
                            pendingDiscoEnable = true
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        showDiscoDialog = false
                    }
                ) {
                    Text(stringResource(R.string.onboarding_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscoDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.disco_party_beta_title)) },
            text = { Text(stringResource(R.string.disco_party_beta_message)) }
        )
    }
}

@Composable
private fun PlayerControls(
    style: PlayerStyle,
    isPlaying: Boolean,
    playbackMode: PlaybackMode? = null,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPlaybackModeToggle: (() -> Unit)? = null,
    onNext: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val colors = playerStyleColors(style)
        if (playbackMode != null && onPlaybackModeToggle != null) {
            FilledTonalIconButton(
                onClick = onPlaybackModeToggle,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colors.secondary,
                    contentColor = colors.onAccent
                )
            ) { Icon(playbackModeIcon(playbackMode), null) }
        }
        FilledTonalIconButton(
            onClick = onPrevious,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.secondary, contentColor = colors.onAccent)
        ) { Icon(Icons.Rounded.SkipPrevious, null) }
        FilledIconButton(
            onClick = onTogglePlayback,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.accent, contentColor = colors.onAccent)
        ) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
        }
        FilledTonalIconButton(
            onClick = onNext,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.secondary, contentColor = colors.onAccent)
        ) { Icon(Icons.Rounded.SkipNext, null) }
        FilledTonalIconButton(
            onClick = onStop,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.stop, contentColor = colors.onAccent)
        ) { Icon(Icons.Rounded.Stop, null) }
    }
}

@Composable
private fun PlaybackProgress(positionMs: Long, durationMs: Long, style: PlayerStyle, onSeekTo: (Long) -> Unit) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = positionMs.coerceIn(0L, safeDuration.takeIf { it > 0L } ?: positionMs.coerceAtLeast(0L))
    var sliderPosition by remember(safePosition, safeDuration) { mutableStateOf(safePosition.toFloat()) }
    val colors = playerStyleColors(style)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            valueRange = 0f..safeDuration.coerceAtLeast(1L).toFloat(),
            onValueChangeFinished = { onSeekTo(sliderPosition.toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.secondary.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(sliderPosition.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(safeDuration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrackRow(track: MusicTrack, isCurrent: Boolean, isPlaying: Boolean, isLoading: Boolean, trailingContent: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            if (track.artworkUri != null) {
                AsyncImage(model = track.artworkUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
            }
        }
        Column(Modifier.weight(1f)) { Text(track.title, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.titleMedium); Text("${track.artist} | ${track.album}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        trailingContent?.invoke()
        Column(horizontalAlignment = Alignment.End) { Text(track.duration, style = MaterialTheme.typography.labelMedium); Text(if (isLoading) stringResource(R.string.player_loading_short) else stringResource(track.sourceType.titleRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
}

@Composable
private fun GroupRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, summary: String) {
    Block { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
}

@Composable
private fun AboutLinkCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label); Switch(checked = checked, onCheckedChange = onCheckedChange) }
}

@Composable
private fun EqualizerSlider(title: String, value: Float, valueRange: ClosedFloatingPointRange<Float> = -1f..1f, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                when {
                    valueRange.start < 0f && value > 0f -> "+${(value * 100).toInt()}"
                    else -> "${(value * 100).toInt()}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

private fun iconForSource(source: MusicSourceType): ImageVector = when (source) {
    MusicSourceType.Internal -> Icons.Rounded.Storage
    MusicSourceType.SdCard -> Icons.Rounded.SdStorage
    MusicSourceType.UsbOtg -> Icons.Rounded.Usb
    MusicSourceType.Server -> Icons.Rounded.Router
    MusicSourceType.Radio -> Icons.Rounded.Radio
}

private fun formatTime(valueMs: Long): String {
    val totalSeconds = (valueMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun playbackModeIcon(mode: PlaybackMode): ImageVector = when (mode) {
    PlaybackMode.Normal -> Icons.Rounded.PlaylistPlay
    PlaybackMode.RepeatOne -> Icons.Rounded.RepeatOne
    PlaybackMode.Shuffle -> Icons.Rounded.Shuffle
}

private fun nextPlaybackMode(mode: PlaybackMode): PlaybackMode = when (mode) {
    PlaybackMode.Normal -> PlaybackMode.RepeatOne
    PlaybackMode.RepeatOne -> PlaybackMode.Shuffle
    PlaybackMode.Shuffle -> PlaybackMode.Normal
}

private data class PlayerStyleColors(
    val accent: Color,
    val secondary: Color,
    val stop: Color,
    val onAccent: Color
)

private fun playerStyleColors(style: PlayerStyle): PlayerStyleColors = when (style) {
    PlayerStyle.Base -> PlayerStyleColors(Color(0xFFB7C4FF), Color(0xFF3D4D79), Color(0xFF8B3A3A), Color(0xFF081125))
    PlayerStyle.NeonPulse -> PlayerStyleColors(Color(0xFF47F0FF), Color(0xFF0E6A76), Color(0xFF0F8D8D), Color(0xFF02181C))
    PlayerStyle.RubyDrive -> PlayerStyleColors(Color(0xFFFF668A), Color(0xFF7E2942), Color(0xFFA62C4A), Color(0xFF24060F))
    PlayerStyle.AquaMotion -> PlayerStyleColors(Color(0xFF67D6FF), Color(0xFF1B5E78), Color(0xFF247C8A), Color(0xFF05202A))
    PlayerStyle.LimeBeat -> PlayerStyleColors(Color(0xFF9AE65F), Color(0xFF466B1E), Color(0xFF6C8E1A), Color(0xFF152407))
    PlayerStyle.GoldNight -> PlayerStyleColors(Color(0xFFFFC857), Color(0xFF7B5720), Color(0xFFA65628), Color(0xFF2B1802))
}


@Composable
private fun ArtworkBox(artworkUri: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Rounded.LibraryMusic, null, modifier = Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun sourceSubtitle(source: SourceConfig): String = when (source.type) {
    MusicSourceType.Internal -> stringResource(R.string.internal_source_summary)
    MusicSourceType.SdCard -> stringResource(R.string.sd_source_summary)
    MusicSourceType.UsbOtg -> stringResource(R.string.usb_source_summary)
    MusicSourceType.Server -> stringResource(R.string.server_source_summary)
    MusicSourceType.Radio -> stringResource(R.string.radio_source_summary)
}
