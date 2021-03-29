package com.h0tk3y.player

import java.io.File
import java.io.InputStream
import java.net.URLClassLoader
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

open class MusicApp(
    pluginPaths: List<MusicPluginPath>
) : AutoCloseable {
    private fun pluginStateFile(pluginId: String) = pluginDataRoot.resolve("$pluginId/state.dat")

    open val pluginDataRoot: File get() = File("musicApp/pluginState")

    protected open fun getPluginDataInputStream(plugin: MusicPlugin): InputStream? =
        pluginStateFile(plugin.pluginId)
            .takeIf { it.isFile }
            ?.inputStream()
            ?.use { it.readBytes().inputStream() }

    fun init() {
        plugins.forEach { plugin ->
            getPluginDataInputStream(plugin).use { persistedState ->
                plugin.init(persistedState)
            }
        }

        musicLibrary // access to initialize

        player.init()
    }

    fun wipePersistedPluginData() {
        File("musicApp/pluginState").deleteRecursively()
    }

    private val pluginPathByClassName: Map<String, MusicPluginPath> = pluginPaths.flatMap { path ->
        path.pluginClasses.map { it to path }
    }.toMap()

    private val pluginClassLoaderByPath: Map<MusicPluginPath, ClassLoader> =
        pluginPaths.associateWith {
            val loaderName = it.pluginClasses.joinToString(", ", "[", "]") { it.substringAfterLast(".") }
            URLClassLoader(loaderName, it.pluginClasspath.map { it.toURI().toURL() }.toTypedArray(), javaClass.classLoader)
        }

    private val plugins: List<MusicPlugin> by lazy {
        pluginClassLoaderByPath.flatMap { (pluginPath, pluginClassLoader) ->
            val pluginClassNames = pluginPath.pluginClasses
            pluginClassNames.map { pluginClassName ->
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
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? {
        val pluginPath = pluginPathByClassName[pluginClassName] ?: return null
        val pluginClassLoader = pluginClassLoaderByPath.getValue(pluginPath)
        val pluginClass = try {
            pluginClassLoader.loadClass(pluginClassName)
        } catch (e: ClassNotFoundException) {
            return null
        }

        if (!MusicPlugin::class.java.isAssignableFrom(pluginClass)) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val matchingPlugins = getPlugins(pluginClass as Class<out MusicPlugin>)

        return matchingPlugins.singleOrNull { it.javaClass.canonicalName == pluginClassName }
            ?: matchingPlugins.singleOrNull()
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
        JLayerMusicPlayer(playbackListeners)
    }

    fun startPlayback(playlist: Playlist, fromPosition: Int) {
        player.playbackState = PlaybackState.Playing(
            PlaylistPosition(
                playlist,
                fromPosition
            ), isResumedFromPause = false
        )
    }

    fun nextOrStop(): Boolean =
        player.playbackState.playlistPosition?.let {
            val nextPosition = it.position + 1
            val newState = if (nextPosition in it.playlist.tracks.indices)
                PlaybackState.Playing(
                    PlaylistPosition(
                        it.playlist,
                        nextPosition
                    ), isResumedFromPause = false
                )
            else
                PlaybackState.Stopped
            player.playbackState = newState
            if (newState is PlaybackState.Playing) true else false
        } ?: false

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