package com.h0tk3y.third.party.plugin

import com.h0tk3y.player.*
import java.io.InputStream
import java.io.OutputStream

class UsageStatsPlugin(override val musicAppInstance: MusicApp) :
    MusicLibraryContributorPlugin,
    PlaybackListenerPlugin {

    var runCount: Int = 0
        private set

    override val preferredOrder: Int
        get() = Int.MAX_VALUE

    companion object {
        const val ARTIST_PLAYED_COUNT_KEY = "artist-played-count"
    }

    override fun contribute(current: MusicLibrary): MusicLibrary = current.apply {
        current.playlists.forEach { playlist ->
            playlist.tracks.forEach { track ->
                updateMetadata(track)
            }
        }
    }

    private fun updateMetadata(track: Track) {
        val artist = track.metadata[TrackMetadataKeys.ARTIST] ?: return
        val count = artistListenedToCount[artist] ?: return
        track.metadata[ARTIST_PLAYED_COUNT_KEY] = count.toString()
    }

    override fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState) {
        if (oldPlaybackState.playlistPosition?.currentTrack != newPlaybackState.playlistPosition?.currentTrack) {
            val newTrack = newPlaybackState.playlistPosition?.currentTrack
            if (newTrack != null) {
                val artist = newTrack.metadata[TrackMetadataKeys.ARTIST]
                if (artist != null) {
                    artistListenedToCount.merge(artist, 1, Int::plus)
                    contribute(musicAppInstance.musicLibrary) // TODO: optimize
                }
            }
        }
    }

    private var artistListenedToCount: MutableMap<String, Int> = mutableMapOf()

    override fun init(persistedState: InputStream?) {
        if (persistedState != null) {
            val text = persistedState.reader().readText().lines()
            runCount = text[0].toIntOrNull() ?: 0
            for (l in text.drop(1)) {
                if (l.isEmpty()) continue
                val count = l.takeLastWhile { it.isDigit() }
                artistListenedToCount[l.removeSuffix(" -> $count")] = count.toInt()
            }
        }
        ++runCount
    }

    override fun persist(stateStream: OutputStream) {
        stateStream.write(buildString {
            appendln(runCount)
            artistListenedToCount.forEach { artist, count ->
                appendln("$artist -> $count")
            }
        }.toByteArray())
    }
}
