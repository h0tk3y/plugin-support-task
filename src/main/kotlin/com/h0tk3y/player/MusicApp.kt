package com.h0tk3y.player

import java.io.File
import java.net.URLClassLoader
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
) : AutoCloseable {
    fun init() {
        for (plugin in plugins) {
            val stateFile = File(plugin.pluginId)
            if (stateFile.exists())
                stateFile.inputStream().use {
                    plugin.init(it)
                }
            else
                plugin.init(null)
        }
        musicLibrary // access to initialize
        player.init()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        for (plugin in plugins) {
            val stateFile = File(plugin.pluginId)
            stateFile.createNewFile()
            stateFile.outputStream().use {
                plugin.persist(it)
            }
        }
    }

    fun wipePersistedPluginData() {
        for (plugin in plugins) {
            val stateFile = File(plugin.pluginId)
            if (stateFile.exists()) stateFile.delete()
        }
    }

    private val pluginClassLoader: ClassLoader =
        URLClassLoader(pluginClasspath.map { it.toURI().toURL() }.toTypedArray())

    private val plugins: List<MusicPlugin> by lazy {
        enabledPluginClasses.map { plugin ->
            val rawPlugin = try {
                pluginClassLoader.loadClass(plugin)
            } catch (e: ClassNotFoundException) {
                throw PluginClassNotFoundException(plugin)
            }
            val primaryConstructor = rawPlugin.kotlin.primaryConstructor
            if (primaryConstructor != null && primaryConstructor.parameters.size == 1 &&
                primaryConstructor.parameters[0].type.classifier == MusicApp::class
            ) {
                primaryConstructor.call(this) as MusicPlugin
            } else {
                val constructor = rawPlugin.kotlin.constructors.singleOrNull {
                    it.parameters.isEmpty()
                }

                val newPlugin = constructor?.call() as MusicPlugin

                val app = rawPlugin.kotlin.memberProperties.find {
                    it is KMutableProperty<*> && it.name == "musicAppInstance"
                            && it.returnType.classifier == MusicApp::class
                } ?: throw IllegalPluginException(rawPlugin)

                (app as KMutableProperty<*>).setter.call(newPlugin, this)

                newPlugin
            }
        }
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? {
        for (plugin in plugins) {
            if (plugin.javaClass.canonicalName == pluginClassName) return plugin
        }
        return null
    }

    fun <T : MusicPlugin> getPlugins(pluginClass: Class<T>): List<T> =
        plugins.filterIsInstance(pluginClass)

    private val musicLibraryContributors: List<MusicLibraryContributorPlugin>
        get() = getPlugins(MusicLibraryContributorPlugin::class.java)

    protected val playbackListeners: List<PlaybackListenerPlugin>
        get() = getPlugins(PlaybackListenerPlugin::class.java)

    val musicLibrary: MusicLibrary by lazy {
        musicLibraryContributors
            .sortedWith(compareBy({ it.preferredOrder }, { it.pluginId }))
            .fold(MusicLibrary(mutableListOf())) { acc, it -> it.contribute(acc) }
    }

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
            ), isResumed = false
        )
    }

    fun nextOrStop() = player.playbackState.playlistPosition?.let {
        val nextPosition = it.position + 1
        player.playbackState =
            if (nextPosition in it.playlist.tracks.indices)
                PlaybackState.Playing(
                    PlaylistPosition(
                        it.playlist,
                        nextPosition
                    ), isResumed = false
                )
            else
                PlaybackState.Stopped
    }

    @Volatile
    var isClosed = false
        private set
}