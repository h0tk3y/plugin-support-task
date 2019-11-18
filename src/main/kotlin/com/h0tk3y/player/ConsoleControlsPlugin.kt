package com.h0tk3y.player

import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

class ConsoleControlsPlugin(override val musicAppInstance: MusicApp) : MusicPlugin {
    private lateinit var consoleThread: Thread

    private fun printPrompt() = print("> ")

    private fun printHelp() {
        println("""
            Usage:
                n: next track
                s: stop
                l: list playlists and tracks
                g playlist_name n: play the playlist from position n
                p: pause/play
            Use 'exit' to quit
        """.trimIndent())
    }

    override fun init(persistedState: InputStream?) {
        consoleThread = thread(isDaemon = true) {
            while (!musicAppInstance.isClosed && !Thread.interrupted()) {
                printPrompt()
                val parts = try {
                    readLine()?.split("\\s+".toRegex()) ?: break
                } catch (_: InterruptedException) {
                    break
                }
                when (parts[0]) {
                    "exit" -> {
                        musicAppInstance.close()
                    }
                    "n" -> {
                        musicAppInstance.nextOrStop()
                    }
                    "s" -> {
                        musicAppInstance.player.playbackState = PlaybackState.Stopped
                    }
                    "l" -> {
                        musicAppInstance.musicLibrary.playlists.forEach { playlist ->
                            println("* ${playlist.name}:")
                            playlist.tracks.forEachIndexed { index, track ->
                                println("  - $index - ${track.simpleStringRepresentation}")
                            }
                        }
                    }
                    "g" -> run {
                        val playlistName = parts.getOrNull(1) ?: return@run
                        val position = parts.getOrNull(2)?.toIntOrNull() ?: return@run
                        val playlist = musicAppInstance.musicLibrary.playlists.find { it.name == playlistName }
                            ?: let { println("playlist $playlistName not found"); return@run }
                        if (position !in playlist.tracks.indices) {
                            println("position $position out of bounds for playlist")
                        }
                        musicAppInstance.startPlayback(playlist, position)
                    }
                    "p" -> run {
                        when (val state = musicAppInstance.player.playbackState) {
                            is PlaybackState.Paused -> musicAppInstance.player.playbackState =
                                PlaybackState.Playing(state.playlistPosition, isResumed = true)
                            is PlaybackState.Playing -> musicAppInstance.player.playbackState =
                                PlaybackState.Paused(state.playlistPosition)
                        }
                    }
                    else -> printHelp()
                }
            }
        }
    }

    override fun persist(stateStream: OutputStream) {
        if (musicAppInstance.isClosed)
            consoleThread.interrupt()
    }
}