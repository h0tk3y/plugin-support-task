package com.h0tk3y.player

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URLClassLoader
import java.util.*

abstract class MusicPlugin {
    open val pluginId: String
        get() = javaClass.canonicalName

    /** Called upon application start to initialize the plugin. The [persistedState] is the byte stream written by
     * [persist], if present. */
    abstract fun init(persistedState: InputStream?)

    /** Called on a plugin instance to instruct it to persist any of its state. The plugin is allowed to use the
     * [stateStream] for storing the state, but should not close the [stateStream].
     *
     * May be called multiple times during application execution.
     *
     * If [MusicApp.isClosed] is true on the [musicAppInstance], the plugin should also yield all of its resources
     * and gracefully teardown.*/
    abstract fun persist(stateStream: OutputStream)

    /** A reference to the application instance. Must be initialized by the application immediately after it loads
     * the plugin, before [init] is called. */
    lateinit var musicAppInstance: MusicApp
}

abstract class PipelineContributorPlugin<T> : MusicPlugin() {
    /** Plugins with lower preferred order should contribute to the pipeline earlier, that is, their results may
     * be altered by the plugins with higher preferred order. */
    abstract val preferredOrder: Int

    abstract fun contribute(current: T): T
}

abstract class MusicLibraryContributorPlugin : PipelineContributorPlugin<MusicLibrary>()

abstract class PlaybackListenerPlugin : MusicPlugin() {
    abstract fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState)
}

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginIds: Set<String>
) : AutoCloseable {
    private fun pluginStateFile(pluginId: String) = File("musicApp/pluginState/$pluginId/state.dat")

    fun init() {
        plugins.forEach { plugin ->
            val persistedState = File("musicApp/pluginState/${plugin.pluginId}/state.dat")
                .takeIf { it.isFile }
                ?.inputStream()
                ?.use { it.readBytes().inputStream() }
            plugin.init(persistedState)
        }

        player.init()
    }

    private val plugins: List<MusicPlugin> by lazy {
        val pluginClassLoader = URLClassLoader(pluginClasspath.map { it.toURI().toURL() }.toTypedArray())
        val plugins = ServiceLoader.load(MusicPlugin::class.java, pluginClassLoader)
        val enabledPlugins = plugins.filter { it.pluginId in enabledPluginIds }
        enabledPlugins.forEach { plugin ->
            plugin.musicAppInstance = this
        }
        enabledPlugins
    }

    fun <T : MusicPlugin> getPlugin(pluginId: String, pluginClass: Class<T>): MusicPlugin? =
        getPlugins(pluginClass).single { it.pluginId == pluginId }

    fun <T : MusicPlugin> getPlugins(pluginClass: Class<T>): List<T> =
        plugins.filterIsInstance(pluginClass)

    private val musicLibraryContributors: List<MusicLibraryContributorPlugin>
        get() = getPlugins(MusicLibraryContributorPlugin::class.java)

    protected val playbackListeners: List<PlaybackListenerPlugin>
        get() = getPlugins(PlaybackListenerPlugin::class.java)

    val musicLibrary: MusicLibrary =
        musicLibraryContributors
            .sortedWith(compareBy({ it.preferredOrder }, { it.pluginId }))
            .fold(MusicLibrary(mutableListOf())) { acc, it -> it.contribute(acc) }

    open val player: MusicPlayer by lazy {
        JLayerMusicPlayer(
            playbackListeners
        )
    }

    fun startPlayback(playlist: Playlist, fromPosition: Int) {
        player.playbackState = PlaybackState.Playing(
            PlaylistPosition(
                playlist,
                fromPosition
            ), isResumed = false)
    }

    fun nextOrStop() = player.playbackState.playlistPosition?.let {
        val nextPosition = it.position + 1
        player.playbackState = if (nextPosition in it.playlist.tracks.indices)
            PlaybackState.Playing(
                PlaylistPosition(
                    it.playlist,
                    nextPosition
                ), isResumed = false)
        else
            PlaybackState.Stopped
    }

    @Volatile
    var isClosed = false
        private set

    override fun close() {
        if (isClosed) return
        isClosed = true
        plugins.forEach { plugin ->
            val pluginStateFile = pluginStateFile(plugin.pluginId)
            pluginStateFile.parentFile.mkdirs()
            pluginStateFile.outputStream().use { output ->
                plugin.persist(output)
            }
        }
        player.close()
    }
}

fun main(args: Array<String>) {
    val beepTracks = (1..4).map {
        Track(
            mapOf(
                TrackMetadataKeys.ARTIST to "beep${it}Artist",
                TrackMetadataKeys.NAME to "beep-$it"
            ),
            File("beep-$it.mp3")
        )
    }

    val sampleTracks = (1..4).map {
        Track(
            mapOf(
                TrackMetadataKeys.ARTIST to "sample${it}Artist",
                TrackMetadataKeys.NAME to "sample-$it"
            ),
            File("sample-$it.mp3")
        )
    }

    StaticPlaylistsLibraryContributor.playlists += Playlist("beeps", beepTracks)
    StaticPlaylistsLibraryContributor.playlists += Playlist(
        "samples",
        sampleTracks
    )

    val pluginClasspathParentDirs = args.map { File(it) }
    val pluginFiles = pluginClasspathParentDirs.flatMap { it.listFiles()?.toList().orEmpty() }

    MusicApp(
        pluginFiles,
        setOf(
            ConsolePlaybackReporterPlugin::class.java.canonicalName,
            ConsoleControlsPlugin::class.java.canonicalName,
            StaticPlaylistsLibraryContributor::class.java.canonicalName,
            "com.h0tk3y.third.party.plugin.UsageStatsPlugin"
        )
    ).init()
}