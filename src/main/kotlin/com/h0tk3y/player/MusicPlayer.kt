package com.h0tk3y.player

import javazoom.jl.player.Player
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

interface MusicPlayer : AutoCloseable {
    val playbackListeners: List<PlaybackListenerPlugin>
    var playbackState: PlaybackState
    fun init()
}

class JLayerMusicPlayer(
    override val playbackListeners: List<PlaybackListenerPlugin>
) : MusicPlayer {
    @Volatile
    override var playbackState: PlaybackState = PlaybackState.Stopped
        set(value) {
            field = handlePlaybackStateWithListeners(field, value, playbackListeners)
            newStateChannel.put(field)
        }

    private val newStateChannel = ArrayBlockingQueue<PlaybackState>(2)

    @Volatile
    private var closing = false

    private fun playLoop() {
        var currentPlayer: Player? = null
        var paused = false
        var waitForNewPlaybackState: PlaybackState? = null

        while (!closing) {
            val newPlaybackState = waitForNewPlaybackState ?: newStateChannel.poll(50L, TimeUnit.MILLISECONDS)
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
                    paused = false
                    if (!newPlaybackState.isResumedFromPause) {
                        currentPlayer?.close()
                        currentPlayer = Player(newPlaybackState.playlistPosition.currentTrack.byteStreamProvider())
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
                        isResumedFromPause = false
                    )
                } else {
                    PlaybackState.Stopped
                }
            }
            if (currentPlayer != null && !paused) {
                currentPlayer.play(5)
            }
        }
    }

    override fun init() {
        thread(name = "player-thread") { playLoop() }
    }

    override fun close() {
        closing = true
        newStateChannel.add(PlaybackState.Stopped)
    }
}

internal fun handlePlaybackStateWithListeners(
    oldPlaybackState: PlaybackState,
    newPlaybackState: PlaybackState,
    listeners: Iterable<PlaybackListenerPlugin>
): PlaybackState {
    var newValue = newPlaybackState
    checkPause(oldPlaybackState, newPlaybackState)

    for (listener in listeners) {
        newValue = listener.onPlaybackStateChange(oldPlaybackState, newValue)?.also { checkPause(oldPlaybackState, it) }
            ?: newValue
    }

    return newValue
}

private fun checkPause(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState) {
    if (newPlaybackState is PlaybackState.Playing && newPlaybackState.isResumedFromPause) {
        require(oldPlaybackState is PlaybackState.Paused && oldPlaybackState.playlistPosition == newPlaybackState.playlistPosition) {
            "isResumedFromPreviousPosition is only allowed when the previous state " +
                    "was com.h0tk3y.player.PlaybackState.Paused at the same track"
        }
    }
}