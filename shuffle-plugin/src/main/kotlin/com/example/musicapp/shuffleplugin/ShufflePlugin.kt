package com.example.musicapp.shuffleplugin

import com.h0tk3y.player.*
import java.io.InputStream
import java.io.OutputStream

class ShufflePlugin(override val musicAppInstance: MusicApp) : MusicLibraryContributorPlugin {
    override val preferredOrder: Int
        get() = 239

    override fun contribute(current: MusicLibrary): MusicLibrary {
        current.playlists += current.playlists.map {
            Playlist("shuffled-${it.name}", it.tracks.shuffled())
        }
        return current
    }

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit
}