package com.h0tk3y.player

import java.io.File
import java.io.InputStream
import java.io.OutputStream

class StaticPlaylistsLibraryContributor(override val musicAppInstance: MusicApp) : MusicLibraryContributorPlugin {
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

    companion object {
        val playlists: MutableList<Playlist> = mutableListOf()
    }

    override val preferredOrder = 0

    override fun contribute(current: MusicLibrary): MusicLibrary {
        current.playlists += playlists
        return current
    }

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit
}