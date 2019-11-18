package com.h0tk3y.player

import java.io.File
import java.io.InputStream

object TrackMetadataKeys {
    const val NAME = "name"
    const val ARTIST = "artist"
    const val ALBUM = "album"

    val basicKeys = setOf(NAME, ARTIST, ALBUM)
}

data class Track(val metadata: MutableMap<String, String>, val byteStreamProvider: () -> InputStream) {
    constructor(metadata: Map<String, String>, file: File) : this(metadata.toMutableMap(), { file.inputStream() })
}

val Track.simpleStringRepresentation
    get() = buildString {
        append(metadata[TrackMetadataKeys.ARTIST] ?: "Unknown Artist")
        append(" – ")
        append(metadata[TrackMetadataKeys.NAME] ?: "Unknown Track")
    }

val Track.fullDataString: String
    get() = simpleStringRepresentation + buildString {
        val additionalMetadata = metadata.filterKeys { it !in TrackMetadataKeys.basicKeys }
        if (additionalMetadata.isNotEmpty()) {
            append(additionalMetadata.entries.joinToString(", ", " (", ")") { (key, value) -> "$key=$value" })
        }
    }

data class Playlist(val name: String, val tracks: MutableList<Track>)

data class MusicLibrary(val playlists: MutableList<Playlist>)

sealed class PlaybackState {
    abstract val playlistPosition: PlaylistPosition?

    data class Playing(override val playlistPosition: PlaylistPosition, val isResumedFromPause: Boolean) : PlaybackState()

    data class Paused(override val playlistPosition: PlaylistPosition) : PlaybackState()

    object Stopped : PlaybackState() {
        override val playlistPosition: Nothing?
            get() = null
    }
}

data class PlaylistPosition(val playlist: Playlist, val position: Int) {
    init {
        require(position in playlist.tracks.indices)
    }

    val currentTrack: Track
        get() = playlist.tracks[position]
}