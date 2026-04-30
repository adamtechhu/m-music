package com.mmusic.app.data

import androidx.annotation.StringRes
import com.mmusic.app.R
import java.util.Locale

enum class AppTab(@StringRes val titleRes: Int) {
    Player(R.string.player_tab),
    Radio(R.string.radio_tab),
    Server(R.string.source_server),
    Settings(R.string.settings)
}

enum class LibraryCategory(@StringRes val titleRes: Int) {
    AllMusic(R.string.all_music),
    Favorites(R.string.favorites),
    Artists(R.string.artists),
    Albums(R.string.albums),
    Folders(R.string.folders),
    Sources(R.string.sources)
}

enum class MusicSourceType(@StringRes val titleRes: Int, @StringRes val statusRes: Int) {
    Internal(R.string.source_internal, R.string.status_ready),
    SdCard(R.string.source_sd, R.string.status_waiting_media),
    UsbOtg(R.string.source_usb, R.string.status_waiting_media),
    Radio(R.string.radio_tab, R.string.status_remote),
    Server(R.string.source_server, R.string.status_remote)
}

enum class UiStyle(@StringRes val titleRes: Int) {
    Base(R.string.theme_base),
    NeonGrid(R.string.theme_neon_grid),
    MidnightWave(R.string.theme_midnight_wave),
    CarbonPulse(R.string.theme_carbon_pulse),
    AuroraFlow(R.string.theme_aurora_flow),
    ObsidianInk(R.string.theme_obsidian_ink),
    SolarDrift(R.string.theme_solar_drift),
    CrimsonNoir(R.string.theme_crimson_noir),
    OceanGlass(R.string.theme_ocean_glass),
    ForestEcho(R.string.theme_forest_echo),
    SunsetFlux(R.string.theme_sunset_flux)
}

enum class DarkModeLevel(@StringRes val titleRes: Int) {
    Standard(R.string.dark_mode_standard),
    Extra(R.string.dark_mode_extra)
}

enum class PlaybackMode(@StringRes val titleRes: Int) {
    Normal(R.string.playback_mode_normal),
    RepeatOne(R.string.playback_mode_repeat),
    Shuffle(R.string.playback_mode_shuffle)
}

enum class PlayerStyle(@StringRes val titleRes: Int) {
    Base(R.string.player_theme_base),
    NeonPulse(R.string.player_theme_neon_pulse),
    RubyDrive(R.string.player_theme_ruby_drive),
    AquaMotion(R.string.player_theme_aqua_motion),
    LimeBeat(R.string.player_theme_lime_beat),
    GoldNight(R.string.player_theme_gold_night)
}

enum class BottomBarSize(@StringRes val titleRes: Int) {
    Small(R.string.bottom_bar_size_small),
    Medium(R.string.bottom_bar_size_medium),
    Large(R.string.bottom_bar_size_large)
}

enum class AudioOutputMode(@StringRes val titleRes: Int) {
    Speaker(R.string.audio_mode_speaker),
    WiredHeadphones(R.string.audio_mode_wired),
    Bluetooth(R.string.audio_mode_bluetooth)
}

enum class EqualizerPreset(@StringRes val titleRes: Int) {
    Flat(R.string.equalizer_preset_flat),
    BassBoost(R.string.equalizer_preset_bass_boost),
    Vocal(R.string.equalizer_preset_vocal),
    Bright(R.string.equalizer_preset_bright),
    Party(R.string.equalizer_preset_party),
    Warm(R.string.equalizer_preset_warm)
}

data class EqualizerProfile(
    val outputMode: AudioOutputMode,
    val preset: EqualizerPreset = EqualizerPreset.Flat,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val bassBoost: Float = 0f,
    val surround: Float = 0f,
    val loudness: Float = 0f
)

enum class AppLanguage(@StringRes val titleRes: Int, val tag: String) {
    English(R.string.language_english, "en"),
    Hungarian(R.string.language_hungarian, "hu"),
    German(R.string.language_german, "de"),
    Spanish(R.string.language_spanish, "es");

    companion object {
        fun fromTag(tag: String?): AppLanguage? {
            if (tag.isNullOrBlank()) return null
            val normalized = tag.substringBefore('-').lowercase(Locale.ROOT)
            return entries.firstOrNull { it.tag.equals(normalized, ignoreCase = true) }
        }

        fun resolve(savedName: String?, fallbackLocale: Locale = Locale.getDefault()): AppLanguage {
            return runCatching { savedName?.let(::valueOf) }.getOrNull()
                ?: fromTag(fallbackLocale.toLanguageTag())
                ?: English
        }
    }
}

enum class ServerType(@StringRes val titleRes: Int) {
    Generic(R.string.server_type_generic),
    Navidrome(R.string.server_type_navidrome),
    Jellyfin(R.string.server_type_jellyfin),
    Plex(R.string.server_type_plex)
}

enum class DownloadStorage(@StringRes val titleRes: Int) {
    Internal(R.string.download_storage_internal),
    SdCard(R.string.download_storage_sd)
}

enum class ServerSortOrder(@StringRes val titleRes: Int) {
    Alphabetical(R.string.server_sort_alphabetical),
    RecentAdded(R.string.server_sort_recent)
}

enum class DurationFilter(@StringRes val titleRes: Int) {
    All(R.string.duration_filter_all),
    Short(R.string.duration_filter_short),
    Medium(R.string.duration_filter_medium),
    Long(R.string.duration_filter_long)
}

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String? = null,
    val folder: String,
    val sourceType: MusicSourceType,
    val duration: String,
    val durationMs: Long,
    val streamUrl: String,
    val contentUri: String,
    val isLocalFile: Boolean
)

data class SourceConfig(
    val type: MusicSourceType,
    val connected: Boolean,
    val enabled: Boolean,
    val folderManagementEnabled: Boolean,
    val selectedFolder: String,
    val availableFolders: List<String> = emptyList(),
    val selectedFormats: List<String> = emptyList(),
    val durationFilter: DurationFilter = DurationFilter.All,
    val autoDetected: Boolean = true
)

data class ServerConfig(
    val type: ServerType = ServerType.Generic,
    val endpoint: String,
    val userName: String,
    val password: String,
    val downloadStorage: DownloadStorage = DownloadStorage.Internal,
    val sortOrder: ServerSortOrder = ServerSortOrder.Alphabetical,
    val wifiOnlyPlayback: Boolean = false,
    val dataSaverEnabled: Boolean = false,
    val originalQualityPlayback: Boolean = true,
    val bitrateKbps: Int = 320,
    val connected: Boolean = false,
    val isConnecting: Boolean = false,
    val statusMessage: String = ""
)

data class MetadataOverride(
    val title: String,
    val artist: String,
    val album: String
)

data class AppReleaseNotes(
    val version: String,
    val pages: List<String>
)

val serverBitrateOptions = listOf(96, 128, 160, 192, 256, 320)

enum class DrilldownType {
    Artist,
    Album,
    Folder,
    Source
}

data class LibraryDrilldown(
    val type: DrilldownType,
    val value: String
)

data class MMusicUiState(
    val selectedTab: AppTab = AppTab.Player,
    val selectedCategory: LibraryCategory = LibraryCategory.AllMusic,
    val selectedSourceFilter: MusicSourceType? = null,
    val librarySearchQuery: String = "",
    val uiStyle: UiStyle = UiStyle.Base,
    val darkModeLevel: DarkModeLevel = DarkModeLevel.Standard,
    val playerStyle: PlayerStyle = PlayerStyle.Base,
    val bottomBarSize: BottomBarSize = BottomBarSize.Medium,
    val bottomBarFloating: Boolean = true,
    val bottomBarGlass: Boolean = true,
    val bottomBarCompact: Boolean = false,
    val bottomBarShowLabels: Boolean = true,
    val bottomBarActiveGlow: Boolean = true,
    val showServerTab: Boolean = true,
    val showRadioTab: Boolean = true,
    val selectedAudioOutputMode: AudioOutputMode = AudioOutputMode.Speaker,
    val equalizerProfiles: List<EqualizerProfile> = AudioOutputMode.entries.map { EqualizerProfile(it) },
    val showPlaybackProgress: Boolean = true,
    val playbackMode: PlaybackMode = PlaybackMode.Normal,
    val language: AppLanguage = AppLanguage.English,
    val tracks: List<MusicTrack> = emptyList(),
    val radioStations: List<MusicTrack> = emptyList(),
    val sources: List<SourceConfig> = emptyList(),
    val favoriteTrackIds: Set<String> = emptySet(),
    val metadataOverrides: Map<String, MetadataOverride> = emptyMap(),
    val libraryDrilldown: LibraryDrilldown? = null,
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val isLoadingPlayback: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val isPlayerFullscreen: Boolean = false,
    val showServerInfoDialog: Boolean = true,
    val infoDialogMessage: String? = null,
    val playbackError: String? = null,
    val hasMediaPermission: Boolean = false,
    val requestMediaPermission: Boolean = false,
    val showWelcomeDialog: Boolean = false,
    val isScanning: Boolean = false,
    val isLoadingRadio: Boolean = false,
    val radioCountryCode: String = "",
    val radioCountryName: String = "",
    val radioSearchQuery: String = "",
    val showRadioMetadata: Boolean = true,
    val radioLiveDetails: String = "",
    val showRadioCountryPicker: Boolean = false,
    val updateAvailableVersion: String? = null,
    val updateUrl: String? = null,
    val updateReleaseNotes: String = "",
    val updateCheckStatus: String = "",
    val showUpdateDialog: Boolean = false,
    val releaseNotes: AppReleaseNotes = AppReleaseNotes(version = "1.1.2", pages = emptyList()),
    val showReleaseNotesDialog: Boolean = false,
    val serverConfig: ServerConfig = ServerConfig(
        endpoint = "https://media.example.com/library",
        userName = "listener",
        password = "secret"
    )
)
