package com.h0tk3y.player

import java.io.File
import java.io.InputStream
import java.net.URLClassLoader
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

open class MusicApp(
    private val pluginPaths: List<MusicPluginPath>
) : AutoCloseable {

    fun init() {
        /**
         * TODO: Инициализировать плагины с помощью функции [MusicPlugin.init],
         *       предоставив им байтовые потоки их состояния (для тех плагинов, для которых они сохранены,
         *       для остальных – null).
         *  Обратите внимание на cлучаи, когда необходимо выбрасывать исключения
         *       [IllegalPluginException] и [PluginClassNotFoundException].
         **/

        musicLibrary // access to initialize

        player.init()
    }

    fun wipePersistedPluginData() {
        TODO("Очистить сохраненные данные плагинов")
    }

    /** TODO, следуя контракту [MusicPlugin] и [MusicPluginPath], загрузить плагины, перечисленные в [pluginPaths].
     *  Эта функция не должна вызывать [MusicPlugin.init], это нужно сделать в [init].
     */
    private val plugins: List<MusicPlugin> by lazy<List<MusicPlugin>> {
        TODO()
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? =
        TODO("Если есть единственный плагин класса [pluginClassName], вернуть его. " +
                "Если есть несколько плагинов (этого типа и его подтипов), " +
                "но один из них имеет в точности такой класс, вернуть его." + "" +
                "Иначе вернуть null")

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

        /** TODO: Сохранить состояние плагинов с помощью [MusicPlugin.persist]. */
    }
}