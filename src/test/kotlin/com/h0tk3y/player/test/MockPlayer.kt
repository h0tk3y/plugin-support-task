package com.h0tk3y.player.test

import com.h0tk3y.player.MusicPlayer
import com.h0tk3y.player.PlaybackListenerPlugin
import com.h0tk3y.player.PlaybackState
import com.h0tk3y.player.handlePlaybackStateWithListeners

internal class MockPlayer(
    override val playbackListeners: List<PlaybackListenerPlugin>
) : MusicPlayer {
    override var playbackState: PlaybackState = PlaybackState.Stopped
        set(value) {
            field = handlePlaybackStateWithListeners(field, value, playbackListeners)
        }

    internal fun finishedTrack() {
        val playlistPosition = checkNotNull(playbackState.playlistPosition) { "Unexpected stopped state" }
        val playNext = playlistPosition.let { it.position + 1 in it.playlist.tracks.indices }
        playbackState = if (playNext) {
            PlaybackState.Playing(
                playlistPosition.copy(position = playlistPosition.position + 1),
                isResumedFromPause = false
            )
        } else {
            PlaybackState.Stopped
        }
    }

    override fun init() = Unit

    override fun close() = Unit
}