package com.mmusic.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.mmusic.app.MainActivity
import com.mmusic.app.R
import com.mmusic.app.data.AudioOutputMode

class PlaybackService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private var currentTrack: ServiceTrack? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            val track = currentTrack
            if (player != null && track != null) {
                val durationMs = player.duration.takeIf { it > 0 }?.toLong() ?: 0L
                val positionMs = player.currentPosition.toLong()
                PlaybackStateStore.update(
                    PlaybackSnapshot(
                        currentTrackId = track.id,
                        isPlaying = player.isPlaying,
                        isLoading = false,
                        positionMs = positionMs,
                        durationMs = durationMs
                    )
                )
                updatePlaybackState(
                    if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    positionMs
                )
                progressHandler.postDelayed(this, 500L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "m-music-playback").apply {
            isActive = true
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        togglePlayback()
                    }

                    override fun onPause() {
                        togglePlayback()
                    }

                    override fun onStop() {
                        stopPlayback()
                    }

                    override fun onSeekTo(pos: Long) {
                        seekTo(pos)
                    }

                    override fun onSkipToPrevious() {
                        playPrevious()
                    }

                    override fun onSkipToNext() {
                        playNext()
                    }
                }
            )
        }
        updatePlaybackState(PlaybackStateCompat.STATE_NONE, 0L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val track = ServiceTrack(
                    id = intent.getStringExtra(EXTRA_TRACK_ID).orEmpty(),
                    title = intent.getStringExtra(EXTRA_TRACK_TITLE).orEmpty(),
                    artist = intent.getStringExtra(EXTRA_TRACK_ARTIST).orEmpty(),
                    album = intent.getStringExtra(EXTRA_TRACK_ALBUM).orEmpty(),
                    artworkUri = intent.getStringExtra(EXTRA_TRACK_ARTWORK),
                    source = intent.getStringExtra(EXTRA_TRACK_SOURCE).orEmpty(),
                    folder = intent.getStringExtra(EXTRA_TRACK_FOLDER).orEmpty(),
                    url = intent.getStringExtra(EXTRA_TRACK_URL).orEmpty(),
                    isLocalFile = intent.getBooleanExtra(EXTRA_TRACK_LOCAL, false)
                )
                playTrack(track)
            }
            ACTION_TOGGLE -> togglePlayback()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_NEXT -> playNext()
            ACTION_SEEK -> seekTo(intent.getLongExtra(EXTRA_SEEK_POSITION, 0L))
            ACTION_REFRESH_AUDIO -> refreshAudioEffects()
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun playTrack(track: ServiceTrack) {
        currentTrack = track
        stopProgressUpdates()
        releaseAudioEffects()
        mediaPlayer?.release()
        mediaPlayer = null
        val trackUrl = track.url.trim()
        if (trackUrl.isBlank()) {
            handlePlaybackFailure(track)
            return
        }
        val player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (track.isLocalFile) {
                    setDataSource(this@PlaybackService, Uri.parse(trackUrl))
                } else {
                    setDataSource(trackUrl)
                }
                setOnPreparedListener {
                    it.start()
                    val durationMs = it.duration.takeIf { value -> value > 0 }?.toLong() ?: 0L
                    PlaybackStateStore.update(
                        PlaybackSnapshot(
                            currentTrackId = track.id,
                            isPlaying = true,
                            isLoading = false,
                            positionMs = 0L,
                            durationMs = durationMs
                        )
                    )
                    updateMetadata(track, durationMs)
                    setupEqualizer(it.audioSessionId)
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, 0L)
                    startForeground(NOTIFICATION_ID, buildNotification(track, true, false))
                    startProgressUpdates()
                }
                setOnCompletionListener {
                    stopProgressUpdates()
                    val durationMs = it.duration.takeIf { value -> value > 0 }?.toLong() ?: 0L
                    PlaybackStateStore.update(
                        PlaybackSnapshot(
                            currentTrackId = track.id,
                            isPlaying = false,
                            isLoading = false,
                            positionMs = durationMs,
                            durationMs = durationMs
                        )
                    )
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, durationMs)
                    startForeground(NOTIFICATION_ID, buildNotification(track, false, false))
                }
                setOnErrorListener { _, _, _ ->
                    handlePlaybackFailure(track)
                    true
                }
                prepareAsync()
            }
        }.getOrElse {
            handlePlaybackFailure(track)
            return
        }
        mediaPlayer = player
        PlaybackStateStore.update(
            PlaybackSnapshot(currentTrackId = track.id, isPlaying = false, isLoading = true, positionMs = 0L, durationMs = 0L)
        )
        updateMetadata(track, 0L)
        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0L)
        startForeground(NOTIFICATION_ID, buildNotification(track, false, true))
    }

    private fun handlePlaybackFailure(track: ServiceTrack) {
        stopProgressUpdates()
        releaseAudioEffects()
        mediaPlayer?.release()
        mediaPlayer = null
        PlaybackStateStore.update(
            PlaybackSnapshot(
                currentTrackId = track.id,
                isPlaying = false,
                isLoading = false,
                positionMs = 0L,
                durationMs = 0L,
                errorMessage = getString(R.string.playback_failed_message)
            )
        )
        updateMetadata(track, 0L)
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L)
        startForeground(NOTIFICATION_ID, buildNotification(track, false, false))
    }

    private fun togglePlayback() {
        val player = mediaPlayer ?: return
        val track = currentTrack ?: return
        val durationMs = player.duration.takeIf { it > 0 }?.toLong() ?: 0L
        val positionMs = player.currentPosition.toLong()
        if (player.isPlaying) {
            player.pause()
            stopProgressUpdates()
            PlaybackStateStore.update(
                PlaybackSnapshot(
                    currentTrackId = track.id,
                    isPlaying = false,
                    isLoading = false,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            )
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, positionMs)
            startForeground(NOTIFICATION_ID, buildNotification(track, false, false))
        } else {
            player.start()
            PlaybackStateStore.update(
                PlaybackSnapshot(
                    currentTrackId = track.id,
                    isPlaying = true,
                    isLoading = false,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            )
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, positionMs)
            startForeground(NOTIFICATION_ID, buildNotification(track, true, false))
            startProgressUpdates()
        }
    }

    private fun stopPlayback() {
        stopProgressUpdates()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentTrack = null
        releaseAudioEffects()
        PlaybackStateStore.update(PlaybackSnapshot())
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0L)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val track = currentTrack ?: return
        val durationMs = player.duration.takeIf { it > 0 }?.toLong() ?: 0L
        val safePosition = positionMs.coerceIn(0L, durationMs)
        player.seekTo(safePosition.toInt())
        PlaybackStateStore.update(
            PlaybackSnapshot(
                currentTrackId = track.id,
                isPlaying = player.isPlaying,
                isLoading = false,
                positionMs = safePosition,
                durationMs = durationMs
            )
        )
        updatePlaybackState(
            if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
            safePosition
        )
    }

    private fun playPrevious() {
        val queue = PlaybackStateStore.queue.value
        val currentId = currentTrack?.id ?: return
        if (queue.isEmpty()) return
        val currentIndex = queue.indexOfFirst { it.id == currentId }
        val target = queue.getOrNull(
            when {
                currentIndex <= 0 -> 0
                else -> currentIndex - 1
            }
        ) ?: return
        if (target.id == currentId && mediaPlayer != null) {
            seekTo(0L)
            return
        }
        playTrack(target.toServiceTrack())
    }

    private fun playNext() {
        val queue = PlaybackStateStore.queue.value
        val currentId = currentTrack?.id ?: return
        if (queue.isEmpty()) return
        val currentIndex = queue.indexOfFirst { it.id == currentId }
        val target = queue.getOrNull(
            when {
                currentIndex == -1 -> 0
                currentIndex >= queue.lastIndex -> queue.lastIndex
                else -> currentIndex + 1
            }
        ) ?: return
        if (target.id == currentId) return
        playTrack(target.toServiceTrack())
    }

    private fun buildNotification(track: ServiceTrack, isPlaying: Boolean, isLoading: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val previousIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PlaybackService::class.java).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(if (isLoading) null else loadArtworkBitmap(track.artworkUri))
            .setContentTitle(track.title)
            .setContentText(
                when {
                    isLoading -> getString(R.string.player_loading)
                    isPlaying -> "${track.artist} | ${track.album}"
                    else -> getString(R.string.player_paused)
                }
            )
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.player_previous_action), previousIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) getString(R.string.player_pause_action) else getString(R.string.player_resume_action),
                toggleIntent
            )
            .addAction(android.R.drawable.ic_media_next, getString(R.string.player_next_action), nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.player_stop_action), stopIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun updateMetadata(track: ServiceTrack, durationMs: Long) {
        val artworkBitmap = if (durationMs <= 0L && track.artworkUri?.startsWith("http", ignoreCase = true) == true) {
            null
        } else {
            loadArtworkBitmap(track.artworkUri)
        }
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                .apply {
                    artworkBitmap?.let {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
                    }
                }
                .build()
        )
    }

    private fun updatePlaybackState(state: Int, positionMs: Long) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, positionMs, 1f)
                .build()
        )
    }

    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressTicker)
        progressHandler.post(progressTicker)
    }

    private fun stopProgressUpdates() {
        progressHandler.removeCallbacks(progressTicker)
    }

    private fun loadArtworkBitmap(artworkUri: String?): Bitmap? {
        if (artworkUri.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(artworkUri)
            when (uri.scheme?.lowercase()) {
                "content", "file" -> contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                "http", "https" -> java.net.URL(artworkUri).openStream().use(BitmapFactory::decodeStream)
                else -> null
            }
        }.getOrNull()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "m-music playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopProgressUpdates()
        releaseAudioEffects()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
        super.onDestroy()
    }

    private fun refreshAudioEffects() {
        mediaPlayer?.audioSessionId?.takeIf { it != 0 }?.let(::setupEqualizer)
    }

    private fun setupEqualizer(audioSessionId: Int) {
        releaseAudioEffects()
        val profile = readProfileForCurrentOutput()
        runCatching { Equalizer(0, audioSessionId) }.getOrNull()?.also { equalizer ->
            val levelRange = equalizer.bandLevelRange
            val minLevel = levelRange.getOrNull(0)?.toInt() ?: -1500
            val maxLevel = levelRange.getOrNull(1)?.toInt() ?: 1500
            for (band in 0 until equalizer.numberOfBands) {
                val centerFreqHz = equalizer.getCenterFreq(band.toShort()) / 1000
                val controlValue = when {
                    centerFreqHz <= 250 -> profile.bass
                    centerFreqHz <= 4000 -> profile.mid
                    else -> profile.treble
                }
                val bandLevel = (controlValue * maxOf(kotlin.math.abs(minLevel), kotlin.math.abs(maxLevel)))
                    .toInt()
                    .coerceIn(minLevel, maxLevel)
                runCatching { equalizer.setBandLevel(band.toShort(), bandLevel.toShort()) }
            }
            equalizer.enabled = profile.bass != 0f || profile.mid != 0f || profile.treble != 0f
            this.equalizer = equalizer
        }

        bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()?.also { effect ->
            effect.setStrength((profile.bassBoost * 1000f).toInt().coerceIn(0, 1000).toShort())
            effect.enabled = profile.bassBoost > 0.01f
        }

        virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()?.also { effect ->
            effect.setStrength((profile.surround * 1000f).toInt().coerceIn(0, 1000).toShort())
            effect.enabled = profile.surround > 0.01f
        }

        loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()?.also { effect ->
            effect.setTargetGain((profile.loudness * 1800f).toInt())
            effect.enabled = profile.loudness > 0.01f
        }
    }

    private fun releaseAudioEffects() {
        runCatching { equalizer?.release() }
        equalizer = null
        runCatching { bassBoost?.release() }
        bassBoost = null
        runCatching { virtualizer?.release() }
        virtualizer = null
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
    }

    private data class OutputProfile(
        val bass: Float,
        val mid: Float,
        val treble: Float,
        val bassBoost: Float,
        val surround: Float,
        val loudness: Float
    )

    private fun readProfileForCurrentOutput(): OutputProfile {
        val prefs = getSharedPreferences("m_music_prefs", MODE_PRIVATE)
        val selectedMode = prefs.getString("selected_audio_output_mode", null)
        val mode = runCatching {
            selectedMode?.let(AudioOutputMode::valueOf)
        }.getOrNull() ?: detectCurrentOutputMode()
        return OutputProfile(
            bass = prefs.getFloat("eq_${mode.name}_bass", 0f),
            mid = prefs.getFloat("eq_${mode.name}_mid", 0f),
            treble = prefs.getFloat("eq_${mode.name}_treble", 0f),
            bassBoost = prefs.getFloat("eq_${mode.name}_bass_boost", 0f),
            surround = prefs.getFloat("eq_${mode.name}_surround", 0f),
            loudness = prefs.getFloat("eq_${mode.name}_loudness", 0f)
        )
    }

    private fun detectCurrentOutputMode(): AudioOutputMode {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return AudioOutputMode.Speaker
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            if (outputs.any { it.isBluetoothOutput() }) return AudioOutputMode.Bluetooth
            if (outputs.any { it.isWiredOutput() }) return AudioOutputMode.WiredHeadphones
        }
        return AudioOutputMode.Speaker
    }

    private fun AudioDeviceInfo.isBluetoothOutput(): Boolean = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
        (Build.VERSION.SDK_INT >= 33 && type == AudioDeviceInfo.TYPE_BLE_SPEAKER)

    private fun AudioDeviceInfo.isWiredOutput(): Boolean = type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
        type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        type == AudioDeviceInfo.TYPE_USB_HEADSET ||
        type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
        type == AudioDeviceInfo.TYPE_LINE_DIGITAL

    data class ServiceTrack(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val artworkUri: String? = null,
        val source: String,
        val folder: String,
        val url: String,
        val isLocalFile: Boolean
    )

    private fun PlaybackQueueTrack.toServiceTrack(): ServiceTrack = ServiceTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artworkUri = artworkUri,
        source = source,
        folder = folder,
        url = url,
        isLocalFile = isLocalFile
    )

    companion object {
        private const val CHANNEL_ID = "m_music_playback"
        private const val NOTIFICATION_ID = 99
        private const val ACTION_PLAY = "com.mmusic.app.action.PLAY"
        private const val ACTION_TOGGLE = "com.mmusic.app.action.TOGGLE"
        private const val ACTION_PREVIOUS = "com.mmusic.app.action.PREVIOUS"
        private const val ACTION_NEXT = "com.mmusic.app.action.NEXT"
        private const val ACTION_REFRESH_AUDIO = "com.mmusic.app.action.REFRESH_AUDIO"
        private const val ACTION_SEEK = "com.mmusic.app.action.SEEK"
        private const val ACTION_STOP = "com.mmusic.app.action.STOP"
        private const val EXTRA_TRACK_ID = "track_id"
        private const val EXTRA_TRACK_TITLE = "track_title"
        private const val EXTRA_TRACK_ARTIST = "track_artist"
        private const val EXTRA_TRACK_ALBUM = "track_album"
        private const val EXTRA_TRACK_ARTWORK = "track_artwork"
        private const val EXTRA_TRACK_SOURCE = "track_source"
        private const val EXTRA_TRACK_FOLDER = "track_folder"
        private const val EXTRA_TRACK_URL = "track_url"
        private const val EXTRA_TRACK_LOCAL = "track_local"
        private const val EXTRA_SEEK_POSITION = "seek_position"

        fun play(context: Context, track: ServiceTrack) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_TRACK_ID, track.id)
                putExtra(EXTRA_TRACK_TITLE, track.title)
                putExtra(EXTRA_TRACK_ARTIST, track.artist)
                putExtra(EXTRA_TRACK_ALBUM, track.album)
                putExtra(EXTRA_TRACK_ARTWORK, track.artworkUri)
                putExtra(EXTRA_TRACK_SOURCE, track.source)
                putExtra(EXTRA_TRACK_FOLDER, track.folder)
                putExtra(EXTRA_TRACK_URL, track.url)
                putExtra(EXTRA_TRACK_LOCAL, track.isLocalFile)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun toggle(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply { action = ACTION_TOGGLE }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun seekTo(context: Context, positionMs: Long) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_SEEK
                putExtra(EXTRA_SEEK_POSITION, positionMs)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun refreshAudioEffects(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_REFRESH_AUDIO
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
