package com.h0tk3y.third.party.plugin

import java.io.InputStream
import java.io.OutputStream
import com.h0tk3y.player.*

class ListenerRandomizerPlugin(override val musicAppInstance: MusicApp) : PlaybackListenerPlugin {
    override fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState) {
        if (newPlaybackState == PlaybackState.Stopped) {
            playlist = null
            return
        }
        if (newPlaybackState is PlaybackState.Paused
            || oldPlaybackState is PlaybackState.Paused
        )
            return
        val newPlaylist = newPlaybackState.playlistPosition?.playlist ?: return
        if (playlist != newPlaylist) {
            playlist = newPlaylist
            size = playlist?.tracks?.size ?: 0
            shuffledPlaylist = (0 until size).shuffled()
            currentPosition = -1
            alreadySwitched = false
        }

        if (alreadySwitched) {
            alreadySwitched = false
            return
        }

        alreadySwitched = true

        currentPosition++
        if (currentPosition == size)
            musicAppInstance.player.playbackState =
                PlaybackState.Stopped
        else {
            musicAppInstance.player.playbackState =
                PlaybackState.Playing(
                    PlaylistPosition(
                        playlist!!, shuffledPlaylist[currentPosition]
                    ), isResumed = false
                )
        }
    }

    private var size = 0
    private var currentPosition = -1
    private var shuffledPlaylist = listOf<Int>()
    private var playlist: Playlist? = null
    private var alreadySwitched = false

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit
}