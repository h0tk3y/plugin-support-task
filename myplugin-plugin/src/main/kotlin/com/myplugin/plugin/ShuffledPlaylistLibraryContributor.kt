package com.myplugin.plugin

import com.h0tk3y.player.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class ShuffledPlaylistLibraryContributor(override val musicAppInstance: MusicApp) : MusicLibraryContributorPlugin {

    init {
        val beepTracks = (1..4).map {
            Track(
                mapOf(
                    TrackMetadataKeys.ARTIST to "beep${it}Artist",
                    TrackMetadataKeys.NAME to "beep-$it"
                ),
                File("sounds/beep-$it.mp3")
            )
        }

        val sampleTracks = (1..4).map {
            Track(
                mapOf(
                    TrackMetadataKeys.ARTIST to "sample${it}Artist",
                    TrackMetadataKeys.NAME to "sample-$it"
                ),
                File("sounds/sample-$it.mp3")
            )
        }
        playlists += Playlist("beeps", beepTracks)
        playlists += Playlist("samples", sampleTracks)
    }

    var numOfInits = 0

    companion object {
        val playlists: MutableList<Playlist> = mutableListOf()
        var order: MutableList<Int> = mutableListOf()
    }

    override val preferredOrder = 0

    override fun contribute(current: MusicLibrary): MusicLibrary {
        if (numOfInits == 0) {
            order = mutableListOf()
            val numOfPlayLists = playlists.size
            var i = 0
            while (i < numOfPlayLists) {
                order.add(i)
                i++
            }
            order.shuffle()
        }


        val shuffledPlaylist = mutableListOf<Playlist>()
        for (index in order) {
            shuffledPlaylist.add(playlists[index])
        }
        numOfInits++
        numOfInits %= 3

        current.playlists += shuffledPlaylist
        return current
    }

    override fun init(persistedState: InputStream?) {
        if (persistedState != null) {
            val text = persistedState.reader().readText().lines()
            numOfInits = text[0].toIntOrNull() ?: 0
            order = text[1].split(" ").mapNotNull { it.toIntOrNull() }.toMutableList() ?: mutableListOf()
        }
    }

    override fun persist(stateStream: OutputStream) {
        stateStream.write(buildString {
            appendln(numOfInits)
            order.forEach {
                append("$it ")
            }
        }.toByteArray())
    }
}