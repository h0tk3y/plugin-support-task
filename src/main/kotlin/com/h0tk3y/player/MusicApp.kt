package com.h0tk3y.player

import java.io.File
import java.net.URLClassLoader
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty0
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
) : AutoCloseable {
    fun init() {

        try {
            plugins.forEach {
                val file = File(it::class.simpleName + ".txt").createNewFile()
                it.init(File(it::class.simpleName + ".txt").inputStream())
            }
        } catch (e: ClassNotFoundException) {
            throw PluginClassNotFoundException(e.message!!)
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

        /** TODO: Сохранить состояние плагинов с помощью [MusicPlugin.persist]. */
        plugins.forEach {
            it.persist(File(it::class.simpleName + ".txt").outputStream())
        }
    }

    fun wipePersistedPluginData() {
        // TODO: Удалить сохранённое состояние плагинов.
        plugins.forEach {
            File(it::class.simpleName + ".txt").writeBytes(byteArrayOf())
        }
    }

    // TODO: Создать ClassLoader
    private val pluginClassLoader: ClassLoader =
        URLClassLoader(pluginClasspath.map { it.toURI().toURL() }.toTypedArray())

    private val plugins: List<MusicPlugin> by lazy {
        /**
         * TODO используя [pluginClassLoader] и следуя контракту [MusicPlugin],
         *      загрузить плагины, перечисленные в [enabledPluginClasses].
         *      Эта функция не должна вызывать [MusicPlugin.init]
         */
        enabledPluginClasses.map {
            val javaClass = pluginClassLoader.loadClass(it)
            val kClass = Reflection.createKotlinClass(javaClass)
            val ctor = kClass.primaryConstructor
            if (ctor == null) throw IllegalPluginException(javaClass)
            if (ctor.parameters.size > 1) throw IllegalPluginException(javaClass)
            if (ctor.parameters.size == 1) {
                if (ctor.parameters[0].type.toString() != MusicApp::class.qualifiedName)
                    throw IllegalPluginException(javaClass)
                ctor.call(this) as? MusicPlugin ?: throw IllegalPluginException(javaClass)
            } else {
                val application = kClass.memberProperties
                    .singleOrNull{it.returnType.toString() == MusicApp::class.qualifiedName} ?:
                        throw IllegalPluginException(javaClass)
                if (application.name != "musicAppInstance")
                    throw IllegalPluginException(javaClass)
                val instance = ctor.call() as MusicPlugin
                if (application.isLateinit) {
                    (application as KMutableProperty1<MusicPlugin, MusicApp>).set(instance, this)
                }
                else throw IllegalPluginException(javaClass)
                instance
            }
        }
    }
    // TODO("Если есть единственный плагин, принадлежащий типу по имени pluginClassName, вернуть его, иначе null.")
    fun findSinglePlugin(pluginClassName: String): MusicPlugin? =
        plugins.singleOrNull{it::class.qualifiedName == pluginClassName}

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