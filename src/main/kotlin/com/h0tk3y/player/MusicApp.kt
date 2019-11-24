package com.h0tk3y.player

import java.io.File
import java.lang.IllegalArgumentException
import java.net.URLClassLoader
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
) : AutoCloseable {
    fun init() {
        plugins.forEach {
            val data = File(pluginDataDir, it.pluginId)
            if (data.exists()) {
                it.init(data.inputStream())
            } else {
                it.init(null)
            }
        }

        musicLibrary // access to initialize
        player.init()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        plugins.forEach {
            val data = File(pluginDataDir, it.pluginId)
            if (!data.exists()) {
                data.createNewFile()
            }
            it.persist(data.outputStream())
        }
    }

    fun wipePersistedPluginData() {
        pluginDataDir.deleteRecursively()
    }

    private val pluginClassLoader: ClassLoader =
        URLClassLoader(pluginClasspath.map { it.toURI().toURL() }.toTypedArray())

    private val plugins: List<MusicPlugin> by lazy {
        enabledPluginClasses.map {
            val jclass: Class<*>
            try {
                jclass = pluginClassLoader.loadClass(it)
            } catch (e: Exception) {
                throw PluginClassNotFoundException(it)
            }
            val kclass = Reflection.createKotlinClass(jclass)
            try {
                val cons = kclass.primaryConstructor ?: throw Exception()
                val plugin: MusicPlugin
                // primary constructor with only one parameter
                if (cons.parameters.size == 1 && cons.parameters[0].type.jvmErasure == MusicApp::class) {
                    plugin = cons.call(this) as MusicPlugin
                } else { // or zero parameter constructor, but property must be mutable
                    plugin = cons.call() as MusicPlugin
                    jclass.methods.single { it.name == "setMusicAppInstance" }.invoke(plugin, this)
                }
                plugin
            } catch (e: Exception) {
                throw IllegalPluginException(kclass.java)
            }
        }
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? = plugins.find { it::class.java.name == pluginClassName }

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

    val pluginDataLocation = ".musicapp"
    val pluginDataDir: File
        get() {
            val res = File(pluginDataLocation)
            if (!res.exists()) {
                res.mkdirs()
            }
            return res
        }
}