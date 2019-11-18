package com.h0tk3y.player.test

import com.h0tk3y.player.MusicPlayer
import com.h0tk3y.player.PlaybackListenerPlugin
import com.h0tk3y.player.PlaybackState

class MockPlayer(
    override val playbackListeners: List<PlaybackListenerPlugin>
) : MusicPlayer {
    override var playbackState: PlaybackState = PlaybackState.Stopped
        set(value) {
            if (value is PlaybackState.Playing && value.isResumed) {
                require(field is PlaybackState.Paused && field.playlistPosition == value.playlistPosition) {
                    "isResumed is only allowed when the previous state was com.h0tk3y.player.PlaybackState.Paused at the same track"
                }
            }

            for (listener in playbackListeners) {
                listener.onPlaybackStateChange(field, value)
            }
            field = value
        }

    internal fun finishedTrack() {
        val playlistPosition = checkNotNull(playbackState.playlistPosition) { "Unexpected stopped state" }
        val playNext = playlistPosition.let { it.position + 1 in it.playlist.tracks.indices }
        playbackState = if (playNext) {
            PlaybackState.Playing(
                playlistPosition.copy(position = playlistPosition.position + 1),
                isResumed = false
            )
        } else {
            PlaybackState.Stopped
        }
    }

    override fun init() = Unit

    override fun close() = Unit
}