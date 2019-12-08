package com.h0tk3y.player

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.net.URLClassLoader
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.createType
import kotlin.reflect.full.defaultType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
) : AutoCloseable {
    private fun getFileName(plugin: MusicPlugin) = "${plugin.pluginId}.dat";

    fun init() {

        for (plugin in plugins) {
            val initFile = File(getFileName(plugin))
            if (initFile.exists()) {
                FileInputStream(initFile).use {
                    plugin.init(it)
                }
            } else {
                plugin.init(null)
            }
        }
        /**
         * TODO: Инициализировать плагины с помощью функции [MusicPlugin.init],
         *       предоставив им байтовые потоки их состояния (для тех плагинов, для которых они сохранены).
         *       Обратите внимание на cлучаи, когда необходимо выбрасывать исключения
         *       [IllegalPluginException] и [PluginClassNotFoundException].
         **/

        musicLibrary // access to initialize
        player.init()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        for (plugin in plugins) {
            FileOutputStream(getFileName(plugin)).use {
                plugin.persist(it)
            }
        }
        /** TODO: Сохранить состояние плагинов с помощью [MusicPlugin.persist]. */
    }

    fun wipePersistedPluginData() {
        plugins.forEach {
            File(getFileName(it)).delete()
        }
    }

    private val pluginClassLoader: ClassLoader = URLClassLoader(
        pluginClasspath.map { it.toURI().toURL() }.toTypedArray()
    )

    private val plugins: List<MusicPlugin> by lazy {
        val list = mutableListOf<MusicPlugin>()
        enabledPluginClasses.forEach {
            val plugin = try {
                pluginClassLoader.loadClass(it)
            } catch (_: ClassNotFoundException) {
                throw (PluginClassNotFoundException(it))
            }


            val primConstructor = plugin.kotlin.primaryConstructor
            val pluginInstance = if (
                primConstructor?.parameters?.singleOrNull()?.type == MusicApp::class.defaultType
            ) {
                primConstructor.call(this)
            } else {
                val instance = plugin.kotlin.constructors.singleOrNull { it.parameters.isEmpty() }
                    ?.call()
                val prop = plugin.kotlin.memberProperties.find {
                    (it.name == "musicAppInstance")
                }
                    ?: throw IllegalPluginException(plugin)
                val mutProp = (prop as? KMutableProperty<*>) ?: throw IllegalPluginException(plugin)
                mutProp.setter.call(instance, this)
                instance
            } as? MusicPlugin
            pluginInstance?.let { list.add(it) }
        }
        /**
         * TODO используя [pluginClassLoader] и следуя контракту [MusicPlugin],
         *      загрузить плагины, перечисленные в [enabledPluginClasses].
         *      Эта функция не должна вызывать [MusicPlugin.init]
         */
        list
    }

fun findSinglePlugin(pluginClassName: String): MusicPlugin? =
    plugins.singleOrNull { it.pluginId == pluginClassName }

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