package com.mmusic.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackSnapshot(
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

data class PlaybackQueueTrack(
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

object PlaybackStateStore {
    private val _state = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()
    private val _queue = MutableStateFlow<List<PlaybackQueueTrack>>(emptyList())
    val queue: StateFlow<List<PlaybackQueueTrack>> = _queue.asStateFlow()

    fun update(snapshot: PlaybackSnapshot) {
        _state.value = snapshot
    }

    fun updateQueue(queue: List<PlaybackQueueTrack>) {
        _queue.value = queue
    }
}
