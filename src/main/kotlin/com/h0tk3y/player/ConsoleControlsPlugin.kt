package com.h0tk3y.player

import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

class BasicConsoleControlsPlugin(override val musicAppInstance: MusicApp) : ConsoleHandlerPlugin {
    override val helpSectionName: String
        get() = "Basic controls"

    override fun printHelp() {
        println(
            """
                ?: print current status
                n: next track
                s: stop
                l: list playlists and tracks
                la: list playlists and tracks with full metadata
                g playlist_name n: play the playlist from position n
                p: pause/play
                'exit': quit the application
        """.trimIndent()
        )
    }

    override val preferredOrder: Int
        get() = 0

    override fun contribute(current: List<String>?): List<String>? {
        if (current == null)
            return null

        val handledValue = when (current[0]) {
            "?" -> {
                printPlaybackState(musicAppInstance.player.playbackState)
                true
            }
            "exit" -> {
                musicAppInstance.close()
                true
            }
            "n" -> {
                if (!musicAppInstance.nextOrStop()) println("Can't go to next track from current state.")
                true
            }
            "s" -> {
                musicAppInstance.player.playbackState = PlaybackState.Stopped
                true
            }
            "l", "la" -> {
                musicAppInstance.musicLibrary.playlists.forEach { playlist ->
                    println("* ${playlist.name}:")
                    playlist.tracks.forEachIndexed { index, track ->
                        println(
                            "  - $index - " +
                                    if (current[0] == "la") track.fullDataString else track.simpleStringRepresentation
                        )
                    }
                }
                true
            }
            "g" -> run {
                val playlistName = current.getOrNull(1) ?: return@run false
                val position = current.getOrNull(2)?.toIntOrNull() ?: return@run false
                val playlist = musicAppInstance.musicLibrary.playlists.find { it.name == playlistName }
                    ?: let { println("playlist $playlistName not found"); return@run false }
                if (position !in playlist.tracks.indices) {
                    println("position $position out of bounds for playlist $playlistName")
                    return@run true
                }
                musicAppInstance.startPlayback(playlist, position)
                true
            }
            "p" -> run {
                when (val state = musicAppInstance.player.playbackState) {
                    is PlaybackState.Paused -> musicAppInstance.player.playbackState =
                        PlaybackState.Playing(state.playlistPosition, isResumedFromPause = true)
                    is PlaybackState.Playing -> musicAppInstance.player.playbackState =
                        PlaybackState.Paused(state.playlistPosition)
                    else -> println("Can't pause or play from this state")
                }
                true
            }
            else -> false
        }
        return if (handledValue) null else current
    }

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit
}

class ListPluginsInConsolePlugin(override val musicAppInstance: MusicApp) : ConsoleHandlerPlugin {
    override val helpSectionName: String
        get() = "Plugins"

    override fun printHelp() {
        println("   plugins: List all loaded plugins")
    }

    override fun contribute(current: List<String>?): List<String>? {
        return if (current == listOf("plugins")) {
            musicAppInstance.getPlugins(MusicPlugin::class.java).forEach {
                val classLoadersString =
                    generateSequence(it.javaClass.classLoader) { it.parent }.toList().reversed()
                        .joinToString(" -> ") { it.name ?: it.toString() }
                println(
                    it.pluginId + " loaded by " + classLoadersString
                )
            }
            null
        } else current
    }

    override val preferredOrder: Int
        get() = 123

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit
}

class ConsoleControlsPlugin(override val musicAppInstance: MusicApp) : MusicPlugin {
    private lateinit var consoleThread: Thread

    private fun printPrompt() = print("> ")

    override fun init(persistedState: InputStream?) {
        val consoleHandlerPlugins =
            musicAppInstance.getPlugins(ConsoleHandlerPlugin::class.java).sortedBy { it.preferredOrder }

        consoleThread = thread(isDaemon = true) {
            while (!musicAppInstance.isClosed && !Thread.interrupted()) {
                printPrompt()
                val parts = try {
                    readLine()?.split("\\s+".toRegex()) ?: break
                } catch (_: InterruptedException) {
                    break
                }
                val finalValue = consoleHandlerPlugins.fold(parts as List<String>?) { acc, it ->
                    if (acc != null) it.contribute(acc) else acc
                }
                if (finalValue != null) {
                    println("Usage:\n")
                    consoleHandlerPlugins.forEach {
                        println("=== ${it.helpSectionName}")
                        it.printHelp()
                        println()
                    }
                }
            }
        }
    }

    override fun persist(stateStream: OutputStream) {
        if (musicAppInstance.isClosed)
            consoleThread.interrupt()
    }
}