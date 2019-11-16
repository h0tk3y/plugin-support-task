package com.h0tk3y.player

import java.io.InputStream
import java.io.OutputStream

class StaticPlaylistsLibraryContributor : MusicLibraryContributorPlugin() {
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