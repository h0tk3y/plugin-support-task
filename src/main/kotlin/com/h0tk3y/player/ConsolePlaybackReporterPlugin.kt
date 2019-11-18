package com.h0tk3y.player

import java.io.InputStream
import java.io.OutputStream

class ConsolePlaybackReporterPlugin(override val musicAppInstance: MusicApp) : PlaybackListenerPlugin {
    override fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState) {
        when (newPlaybackState) {
            is PlaybackState.Playing -> {
                println("Playing (playlist: ${newPlaybackState.playlistPosition.playlist.name}): " +
                    newPlaybackState.playlistPosition.currentTrack.simpleStringRepresentation)
            }
            is PlaybackState.Paused -> {
                println("Paused")
            }
            PlaybackState.Stopped -> println("Stopped")
        }
    }

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit
}