package com.mmusic.app.data

object MusicRepository {
    fun demoTracks(): List<MusicTrack> = listOf(
        MusicTrack(
            id = "track_internal_1",
            title = "Night Drive",
            artist = "Aron Vale",
            album = "Skyline",
            folder = "/Music/Synth",
            sourceType = MusicSourceType.Internal,
            duration = "04:12",
            durationMs = 252000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            contentUri = "",
            isLocalFile = false
        ),
        MusicTrack(
            id = "track_internal_2",
            title = "Glass Harbour",
            artist = "Luma",
            album = "Blue Current",
            folder = "/Music/Chill",
            sourceType = MusicSourceType.Internal,
            duration = "03:48",
            durationMs = 228000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            contentUri = "",
            isLocalFile = false
        ),
        MusicTrack(
            id = "track_sd_1",
            title = "Red Signal",
            artist = "Nova Circuit",
            album = "Afterlight",
            folder = "/SDCard/Electro",
            sourceType = MusicSourceType.SdCard,
            duration = "05:03",
            durationMs = 303000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            contentUri = "",
            isLocalFile = false
        ),
        MusicTrack(
            id = "track_usb_1",
            title = "Monsoon Echo",
            artist = "Mira Lane",
            album = "Rain Maps",
            folder = "/USBOTG/Live",
            sourceType = MusicSourceType.UsbOtg,
            duration = "04:35",
            durationMs = 275000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            contentUri = "",
            isLocalFile = false
        ),
        MusicTrack(
            id = "track_server_1",
            title = "Halo Lines",
            artist = "Aron Vale",
            album = "Skyline",
            folder = "/Server/Favorites",
            sourceType = MusicSourceType.Server,
            duration = "03:57",
            durationMs = 237000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            contentUri = "",
            isLocalFile = false
        ),
        MusicTrack(
            id = "track_server_2",
            title = "Dust And Neon",
            artist = "Velin",
            album = "Late Transit",
            folder = "/Server/New",
            sourceType = MusicSourceType.Server,
            duration = "04:28",
            durationMs = 268000L,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            contentUri = "",
            isLocalFile = false
        )
    )

    fun demoSources(): List<SourceConfig> = listOf(
        SourceConfig(
            type = MusicSourceType.Internal,
            connected = true,
            enabled = true,
            folderManagementEnabled = true,
            selectedFolder = "/storage/emulated/0/Music"
        ),
        SourceConfig(
            type = MusicSourceType.SdCard,
            connected = false,
            enabled = false,
            folderManagementEnabled = true,
            selectedFolder = ""
        ),
        SourceConfig(
            type = MusicSourceType.UsbOtg,
            connected = false,
            enabled = false,
            folderManagementEnabled = false,
            selectedFolder = ""
        ),
        SourceConfig(
            type = MusicSourceType.Server,
            connected = false,
            enabled = false,
            folderManagementEnabled = true,
            selectedFolder = "/remote/library"
        )
    )
}
