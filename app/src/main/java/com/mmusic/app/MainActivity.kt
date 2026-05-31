package com.mmusic.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.os.LocaleListCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mmusic.app.data.AppLanguage
import com.mmusic.app.ui.MMusicApp
import com.mmusic.app.ui.MMusicViewModel
import com.mmusic.app.ui.theme.MMusicTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("m_music_prefs", MODE_PRIVATE)
        val resolvedLanguage = AppLanguage.resolve(savedName = null, fallbackLocale = Locale.getDefault())
        prefs.edit().remove("language").apply()
        val languageTag = resolvedLanguage.tag
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        enableEdgeToEdge()
        setContent {
            val viewModel: MMusicViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                viewModel.updateMediaPermission(granted)
            }
            val manageStorageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                viewModel.updateMediaPermission(hasFullStorageAccess())
            }

            LaunchedEffect(Unit) {
                viewModel.updateMediaPermission(hasFullStorageAccess())
            }

            LaunchedEffect(state.requestMediaPermission, state.showWelcomeDialog) {
                if (!state.showWelcomeDialog) {
                    val granted = hasFullStorageAccess()
                    if (granted) {
                        viewModel.updateMediaPermission(true)
                    } else if (state.requestMediaPermission || !state.hasMediaPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            manageStorageLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        } else {
                            permissionLauncher.launch(permission)
                        }
                    }
                }
            }

            MMusicTheme(style = state.uiStyle, darkModeLevel = state.darkModeLevel) {
                MMusicApp(
                    state = state,
                    onTabSelected = viewModel::selectTab,
                    onCategorySelected = viewModel::selectCategory,
                    onStyleSelected = viewModel::selectStyle,
                    onSourceFilterSelected = viewModel::selectSourceFilter,
                    onSearchQueryChanged = viewModel::updateLibrarySearchQuery,
                    onTrackSelected = viewModel::togglePlayback,
                    onOpenDrilldown = viewModel::openDrilldown,
                    onClearDrilldown = viewModel::clearDrilldown,
                    onOpenFullscreenPlayer = viewModel::openFullscreenPlayer,
                    onCloseFullscreenPlayer = viewModel::closeFullscreenPlayer,
                    onToggleCurrentPlayback = viewModel::toggleCurrentPlayback,
                    onSeekTo = viewModel::seekTo,
                    onPlaybackModeSelected = viewModel::selectPlaybackMode,
                    onPlayPrevious = viewModel::playPreviousTrack,
                    onPlayNext = viewModel::playNextTrack,
                    onStopPlayback = viewModel::stopPlayback,
                    onDismissServerInfo = viewModel::dismissServerInfoDialog,
                    onDismissInfoDialog = viewModel::dismissInfoDialog,
                    onRefreshSources = viewModel::refreshDetectedSources,
                    onToggleSourceEnabled = viewModel::toggleSourceEnabled,
                    onFolderEnabledChanged = viewModel::toggleFolderManagement,
                    onSourceFolderChanged = viewModel::updateSourceFolder,
                    onSourceFolderToggled = viewModel::toggleSourceFolderSelection,
                    onSourceFormatToggled = viewModel::toggleSourceFormatSelection,
                    onSourceDurationFilterSelected = viewModel::selectSourceDurationFilter,
                    onDarkModeLevelSelected = viewModel::selectDarkModeLevel,
                    onPlayerStyleSelected = viewModel::selectPlayerStyle,
                    onBottomBarSizeSelected = viewModel::selectBottomBarSize,
                    onBottomBarFloatingChanged = viewModel::setBottomBarFloating,
                    onBottomBarGlassChanged = viewModel::setBottomBarGlass,
                    onBottomBarCompactChanged = viewModel::setBottomBarCompact,
                    onBottomBarLabelsChanged = viewModel::setBottomBarShowLabels,
                    onBottomBarGlowChanged = viewModel::setBottomBarActiveGlow,
                    onShowServerTabChanged = viewModel::setShowServerTab,
                    onShowRadioTabChanged = viewModel::setShowRadioTab,
                    onAudioOutputModeSelected = viewModel::selectAudioOutputMode,
                    onEqualizerPresetSelected = viewModel::selectEqualizerPreset,
                    onBassChanged = viewModel::updateEqualizerBass,
                    onMidChanged = viewModel::updateEqualizerMid,
                    onTrebleChanged = viewModel::updateEqualizerTreble,
                    onBassBoostChanged = viewModel::updateBassBoost,
                    onSurroundChanged = viewModel::updateSurround,
                    onLoudnessChanged = viewModel::updateLoudness,
                    onShowPlaybackProgressChanged = viewModel::setShowPlaybackProgress,
                    onServerEndpointChanged = viewModel::updateServerEndpoint,
                    onServerTypeChanged = viewModel::updateServerType,
                    onServerUserChanged = viewModel::updateServerUser,
                    onServerPasswordChanged = viewModel::updateServerPassword,
                    onServerDownloadStorageChanged = viewModel::updateServerDownloadStorage,
                    onServerSortOrderChanged = viewModel::updateServerSortOrder,
                    onDeleteDownloadedFiles = viewModel::deleteDownloadedServerTracks,
                    onServerWifiOnlyChanged = viewModel::setServerWifiOnlyPlayback,
                    onServerDataSaverChanged = viewModel::setServerDataSaverEnabled,
                    onServerOriginalQualityChanged = viewModel::setServerOriginalQualityPlayback,
                    onServerBitrateChanged = viewModel::setServerBitrateKbps,
                    onConnectToServer = viewModel::connectToServer,
                    onDownloadServerTrack = viewModel::downloadServerTrack,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onUpdateMetadata = viewModel::updateTrackMetadata,
                    onCheckForUpdates = { viewModel.checkForUpdates(force = true) },
                    onOpenRadioCountryPicker = viewModel::openRadioCountryPicker,
                    onRadioSearchQueryChanged = viewModel::updateRadioSearchQuery,
                    onShowRadioMetadataChanged = viewModel::setShowRadioMetadata,
                    onRadioCountrySelected = viewModel::updateRadioCountry,
                    onDismissRadioCountryPicker = viewModel::dismissRadioCountryPicker,
                    onDismissReleaseNotesDialog = viewModel::dismissReleaseNotesDialog,
                    onShowReleaseNotes = viewModel::showReleaseNotesDialog,
                    onDismissUpdateDialog = viewModel::dismissUpdateDialog,
                    onInstallUpdate = viewModel::installLatestUpdateFromGithub,
                    onAcceptWelcome = viewModel::acceptWelcomeDialog
                )
            }
        }
    }

    private fun hasFullStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}
