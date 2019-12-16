package com.h0tk3y.third.party.plugin

import com.h0tk3y.player.*
import java.io.InputStream
import java.io.OutputStream

class ContributorRandomizerPlugin(override val musicAppInstance: MusicApp) : MusicLibraryContributorPlugin {
    override val preferredOrder = 1

    override fun contribute(current: MusicLibrary): MusicLibrary {
        with(current.playlists) {
            addAll(
                map {
                    (Playlist("${it.name}(shuffled)", it.tracks.shuffled()))
                }
            )
        }
        return current
    }

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit
}

