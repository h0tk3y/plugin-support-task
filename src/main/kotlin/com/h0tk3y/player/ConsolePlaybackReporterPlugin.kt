package com.h0tk3y.player

import java.io.InputStream
import java.io.OutputStream

internal fun printPlaybackState(playbackState: PlaybackState) {
    fun playlistPositionString(playlistPosition: PlaylistPosition) =
        "(playlist: ${playlistPosition.playlist.name}): " + playlistPosition.currentTrack.simpleStringRepresentation
    when (playbackState) {
        is PlaybackState.Playing -> {
            println("Playing ${playlistPositionString(playbackState.playlistPosition)}")
        }
        is PlaybackState.Paused -> {
            println("Paused ${playlistPositionString(playbackState.playlistPosition)}")
        }
        PlaybackState.Stopped -> println("Stopped")
    }
}

class ConsolePlaybackReporterPlugin(override val musicAppInstance: MusicApp) : PlaybackListenerPlugin {
    override fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState): Nothing? {
        printPlaybackState(newPlaybackState)
        return null
    }

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit
}