package com.h0tk3y.player

import java.io.*
import java.net.URLClassLoader
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.*

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
) : AutoCloseable {

    private fun fileByPlugin(p: MusicPlugin) : File {
        val f = File("pluginsData/" + p.pluginId + "Metadata.txt")
        f.createNewFile()
        return f
    }

    fun init() {
        plugins.forEach {
            FileInputStream(fileByPlugin(it)).use { input ->
                it.init(input)
            }
        }
        /**
         * Инициализировать плагины с помощью функции [MusicPlugin.init],
         * предоставив им байтовые потоки их состояния (для тех плагинов, для которых они сохранены).
         * Обратите внимание на cлучаи, когда необходимо выбрасывать исключения
         * [IllegalPluginException] и [PluginClassNotFoundException].
         **/

        musicLibrary // access to initialize
        player.init()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        plugins.forEach {
            FileOutputStream(fileByPlugin(it)).use { output ->
                it.persist(output)
            }
        }
        /** Сохранить состояние плагинов с помощью [MusicPlugin.persist]. */
    }

    fun wipePersistedPluginData() {
        plugins.forEach {fileByPlugin(it).delete() }
        /** Удалить сохранённое состояние плагинов. */
    }

    private val pluginClassLoader: ClassLoader = URLClassLoader(
        pluginClasspath.map { it.toURI().toURL() }.toTypedArray()
    )

    private val plugins: List<MusicPlugin> by lazy {
        enabledPluginClasses.map {plugin ->
            val c = try {
                pluginClassLoader.loadClass(plugin)
            } catch (e: ClassNotFoundException) {
                throw PluginClassNotFoundException(plugin)
            }
            val primaryCon = c.kotlin.primaryConstructor
            if (primaryCon != null && primaryCon.parameters.size == 1 &&
                primaryCon.parameters[0].type.classifier == MusicApp::class) {
                primaryCon.call(this) as MusicPlugin
            } else  {
                val con = c.kotlin.constructors.singleOrNull {
                    it.parameters.isEmpty()
                } ?: throw IllegalPluginException(c)

                val instance = con.call() as MusicPlugin

                val app = c.kotlin.memberProperties.find {
                    it is KMutableProperty<*> && it.name == "musicAppInstance"
                            && it.returnType.classifier == MusicApp::class
                } ?: throw IllegalPluginException(c)
                (app as KMutableProperty<*>).setter.call(instance, this)

                instance
            }
        }
        /**
         * используя [pluginClassLoader] и следуя контракту [MusicPlugin],
         * загрузить плагины, перечисленные в [enabledPluginClasses].
         * Эта функция не должна вызывать [MusicPlugin.init]
         */
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? =
        plugins.singleOrNull {
            it::class.qualifiedName == pluginClassName
        }
    /** Если есть единственный плагин, принадлежащий fтипу по имени pluginClassName, вернуть его, иначе null.*/

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
}