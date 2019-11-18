package com.h0tk3y.player

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URLClassLoader
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

interface MusicPlugin {
    val pluginId: String
        get() = javaClass.canonicalName

    /** Called upon application start to initialize the plugin. The [persistedState] is the byte stream written by
     * [persist], if present. */
    fun init(persistedState: InputStream?)

    /** Called on a plugin instance to instruct it to persist all of its state. The plugin is allowed to use the
     * [stateStream] for storing the state, but should not close the [stateStream].
     *
     * May be called multiple times during application execution.
     *
     * If [MusicApp.isClosed] is true on the [musicAppInstance], the plugin should also yield all of its resources
     * and gracefully teardown.*/
    fun persist(stateStream: OutputStream)

    /** A reference to the application instance.
     *
     * A plugin may override this property as the single parameter of the primary constructor or a mutable property
     * (then the class must contain a no-argument constructor).
     *
     * In both cases, the application that instantiates the plugin must provide the value for the property.
     * If this property cannot be initialized in either way, the application must throw an [IllegalPluginException]
     * */
    val musicAppInstance: MusicApp
}

class IllegalPluginException(val pluginClass: Class<*>) : Exception(
    "Illegal plugin class $pluginClass."
)

class PluginClassNotFoundException(val pluginClassName: String) : ClassNotFoundException(
    "Plugin class $pluginClassName not found."
)

interface PipelineContributorPlugin<T> : MusicPlugin {
    /** Plugins with lower preferred order should contribute to the pipeline earlier, that is, their results may
     * be altered by the plugins with higher preferred order. */
    val preferredOrder: Int

    fun contribute(current: T): T
}

interface MusicLibraryContributorPlugin : PipelineContributorPlugin<MusicLibrary>

interface PlaybackListenerPlugin : MusicPlugin {
    fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState)
}

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
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

        musicLibrary // access to initialize

        player.init()
    }

    fun wipePersistedPluginData() {
        File("musicApp/pluginState").deleteRecursively()
    }

    private val pluginClassLoader: ClassLoader =
        URLClassLoader(pluginClasspath.map { it.toURI().toURL() }.toTypedArray())

    private val plugins: List<MusicPlugin> by lazy {
        enabledPluginClasses.map { pluginClassName ->
            val pluginClass = try {
                Class.forName(pluginClassName, true, pluginClassLoader)
            } catch (_: ClassNotFoundException) {
                throw PluginClassNotFoundException(pluginClassName)
            }

            val kclass = pluginClass.kotlin

            val primaryConstructor = kclass.primaryConstructor
            if (primaryConstructor?.parameters?.singleOrNull()?.type?.classifier == MusicApp::class)
                return@map primaryConstructor.call(this@MusicApp) as MusicPlugin

            val noArgConstructor = kclass.constructors.find { it.parameters.isEmpty() }
                ?: throw IllegalPluginException(pluginClass)

            val appInstanceProperty =
                kclass.memberProperties.single { it.name == MusicPlugin::musicAppInstance.name }

            if (appInstanceProperty is KMutableProperty<*>) {
                return@map noArgConstructor.call().also { result ->
                    appInstanceProperty.setter.call(result, this@MusicApp)
                } as MusicPlugin
            } else throw IllegalPluginException(pluginClass)
        }
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? {
        val pluginClass = try {
            pluginClassLoader.loadClass(pluginClassName)
        } catch (e: ClassNotFoundException) {
            return null
        }

        if (!MusicPlugin::class.java.isAssignableFrom(pluginClass)) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return getPlugins(pluginClass as Class<out MusicPlugin>).singleOrNull()
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
            ), isResumed = false)
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