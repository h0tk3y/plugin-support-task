package com.h0tk3y.player

import javazoom.jl.player.Player
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

interface MusicPlayer : AutoCloseable {
    val playbackListeners: List<PlaybackListenerPlugin>
    var playbackState: PlaybackState

    fun init(): Unit
}

class JLayerMusicPlayer(
    override val playbackListeners: List<PlaybackListenerPlugin>
) : MusicPlayer {
    @Volatile
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

            newStateRendezvousChannel.put(value)
        }

    private val newStateRendezvousChannel = ArrayBlockingQueue<PlaybackState>(1)

    @Volatile
    private var closing = false

    private fun playLoop() {
        var currentPlayer: Player? = null
        var paused = false
        var waitForNewPlaybackState: PlaybackState? = null

        while (!closing) {
            val newPlaybackState = waitForNewPlaybackState ?: newStateRendezvousChannel.poll()
            waitForNewPlaybackState = null

            when (newPlaybackState) {
                null -> Unit
                is PlaybackState.Paused -> {
                    paused = true
                }
                PlaybackState.Stopped -> {
                    currentPlayer?.close()
                    currentPlayer = null
                }
                is PlaybackState.Playing -> {
                    if (paused && newPlaybackState.isResumed)
                        paused = false
                    else {
                        currentPlayer?.close()
                        currentPlayer =
                            Player(newPlaybackState.playlistPosition.currentTrack.byteStreamProvider())
                    }
                }
            }

            if (currentPlayer?.isComplete == true) {
                currentPlayer.close()
                currentPlayer = null

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
            if (currentPlayer != null && !paused) {
                currentPlayer.play(5)
            } else {
                waitForNewPlaybackState = newStateRendezvousChannel.poll(100,
                    TimeUnit.MILLISECONDS
                )
            }
        }
    }

    override fun init() {
        thread(name = "player-thread") { playLoop() }
    }

    override fun close() {
        closing = true
    }
}