package com.h0tk3y.player.test

import com.h0tk3y.player.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.*

private val thirdPartyPluginClasses: List<File> =
    System.getProperty("third-party-plugin-classes").split(File.pathSeparator).map { File(it) }

private val usageStatsPluginName = "com.h0tk3y.third.party.plugin.UsageStatsPlugin"
private val pluginWithAppPropertyName = "com.h0tk3y.third.party.plugin.PluginWithAppProperty"

class PluginSupportTest {

    private val defaultEnabledPlugins = setOf(
        StaticPlaylistsLibraryContributor::class.java.canonicalName,
        usageStatsPluginName,
        pluginWithAppPropertyName
    )

    private fun withApp(
        wipePersistedData: Boolean = false,
        pluginClasspath: List<File> = thirdPartyPluginClasses,
        enabledPlugins: Set<String> = defaultEnabledPlugins,
        doTest: TestableMusicApp.() -> Unit
    ) {
        val app = TestableMusicApp(pluginClasspath, enabledPlugins)
        if (wipePersistedData) {
            app.wipePersistedPluginData()
        }
        app.use {
            it.init()
            it.doTest()
        }
    }

    @Test
    fun testThirdPartyPlugin() {
        withApp(true) {
            val value = assertNotNull(findSinglePlugin(usageStatsPluginName))
            assertEquals(value.javaClass.classLoader.parent, javaClass.classLoader)
            assertSame(value.musicAppInstance, this@withApp)
        }
        withApp {
            val value = assertNotNull(findSinglePlugin(usageStatsPluginName))
            assertEquals(2, value.javaClass.getMethod("getRunCount")(value))
        }
    }

    @Test
    fun testPlaybackListening() {
        withApp(true) {
            val playlist = musicLibrary.playlists.first()
            check(playlist.tracks.size >= 2)
            startPlayback(playlist, 0)
            player.finishedTrack()
            startPlayback(playlist, 0)
            player.finishedTrack()
            startPlayback(playlist, 0)

            val artist0 = playlist.tracks[0].metadata["artist-played-count"]?.toInt()
            val artist1 = playlist.tracks[1].metadata["artist-played-count"]?.toInt()
            assertEquals(3, artist0)
            assertEquals(2, artist1)
        }
    }

    @Test
    fun testMissingPlugin() {
        withApp {
            assertNull(findSinglePlugin("some.missing.plugin"))
        }
    }

    @Test
    fun testGetAllPlugins() {
        withApp {
            val allPlugins = getPlugins(MusicPlugin::class.java)
            val playbackListeners = getPlugins(PlaybackListenerPlugin::class.java)
            val libraryContributors = getPlugins(MusicLibraryContributorPlugin::class.java)

            assertEquals(3, allPlugins.size)
            assertEquals(1, playbackListeners.size)
            assertEquals(2, libraryContributors.size)

            assertTrue(libraryContributors.contains(playbackListeners.single() as MusicLibraryContributorPlugin))
        }
    }

    @Test
    fun testPropertyInitialization() {
        withApp {
            val plugin = checkNotNull(findSinglePlugin(pluginWithAppPropertyName))
            assertSame(this, plugin.musicAppInstance)
        }
    }

    @Test
    fun testNoSuitableInitializationRoutine() {
        val exception = assertFailsWith<IllegalPluginException> {
            withApp(enabledPlugins = defaultEnabledPlugins + MalformedPlugin::class.java.name) { }
        }
        assertEquals(MalformedPlugin::class.java, exception.pluginClass)
    }

    @Test
    fun testPluginFromThisClassLoader() {
        withApp {
            val plugin = checkNotNull(findSinglePlugin(StaticPlaylistsLibraryContributor::class.java.canonicalName))
            assertSame(plugin.javaClass.classLoader, javaClass.classLoader)
        }
    }

    @Test
    fun testPluginMissingOnClasspath() {
        val exception = assertFailsWith<PluginClassNotFoundException> {
            withApp(pluginClasspath = emptyList(), enabledPlugins = setOf(usageStatsPluginName)) {}
        }
        assertEquals(usageStatsPluginName, exception.pluginClassName)
    }

    @Test
    fun testPluginCloseRoutine() {
        lateinit var pluginInstance: AppCloseTrapPlugin
        withApp(enabledPlugins = defaultEnabledPlugins + AppCloseTrapPlugin::class.java.canonicalName) {
            pluginInstance = findSinglePlugin(AppCloseTrapPlugin::class.java.canonicalName) as AppCloseTrapPlugin
        }
        assertTrue(pluginInstance.closed)
    }

    @Test
    fun testSameBytesInStreams() {
        lateinit var pluginInstance: PersistanceCheckerPlugin
        val expectedBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        withApp(enabledPlugins = defaultEnabledPlugins + PersistanceCheckerPlugin::class.java.canonicalName) {
            pluginInstance =
                findSinglePlugin(PersistanceCheckerPlugin::class.java.canonicalName) as PersistanceCheckerPlugin
            pluginInstance.persistBytes = expectedBytes
        }
        withApp(enabledPlugins = defaultEnabledPlugins + PersistanceCheckerPlugin::class.java.canonicalName) {
            pluginInstance =
                findSinglePlugin(PersistanceCheckerPlugin::class.java.canonicalName) as PersistanceCheckerPlugin
            assertTrue(expectedBytes.contentEquals(pluginInstance.initBytes))
        }
        withApp(
            enabledPlugins = defaultEnabledPlugins + PersistanceCheckerPlugin::class.java.canonicalName,
            wipePersistedData = true
        ) {
            pluginInstance =
                findSinglePlugin(PersistanceCheckerPlugin::class.java.canonicalName) as PersistanceCheckerPlugin
            assertTrue(pluginInstance.initBytes.isEmpty())
        }
    }

    @Test
    fun testContributorsOrdering() {
        withApp(enabledPlugins = defaultEnabledPlugins +
                AddPlaylistTestContributor1::class.java.canonicalName +
                AddPlaylistTestContributor2::class.java.canonicalName
        ) {
            val pl1 = findSinglePlugin(AddPlaylistTestContributor1::class.java.canonicalName) as AddPlaylistTestContributor
            val pl2 = findSinglePlugin(AddPlaylistTestContributor2::class.java.canonicalName) as AddPlaylistTestContributor

            assertEquals(0, pl1.playlistsBefore.size)
            assertEquals(1, pl2.playlistsBefore.size)

            assertTrue(musicLibrary.playlists.any { it.name == AddPlaylistTestContributor1::class.java.canonicalName })
            assertTrue(musicLibrary.playlists.any { it.name == AddPlaylistTestContributor2::class.java.canonicalName })
        }
    }
}

class PersistanceCheckerPlugin(override val musicAppInstance: MusicApp) : MusicPlugin {
    lateinit var initBytes: ByteArray
    var persistBytes: ByteArray? = null

    override fun init(persistedState: InputStream?) {
        initBytes = persistedState?.readBytes() ?: byteArrayOf()
    }

    override fun persist(stateStream: OutputStream) {
        persistBytes?.let {
            stateStream.write(it)
        }
    }
}

class AppCloseTrapPlugin(override val musicAppInstance: MusicApp) : MusicPlugin {
    var closed: Boolean = false

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) {
        if (musicAppInstance.isClosed) {
            closed = true
        }
    }
}

class MalformedPlugin : MusicPlugin {
    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit

    override val musicAppInstance: MusicApp
        get() = error("not implemented")
}

abstract class AddPlaylistTestContributor(override val musicAppInstance: MusicApp) : MusicLibraryContributorPlugin {
    lateinit var playlistsBefore: List<Playlist>

    val name: String
        get() = javaClass.canonicalName

    override fun contribute(current: MusicLibrary): MusicLibrary {
        playlistsBefore = current.playlists.toList()
        current.playlists.add(Playlist(name, emptyList()))
        return current
    }

    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit
}

class AddPlaylistTestContributor1(musicAppInstance: MusicApp) : AddPlaylistTestContributor(musicAppInstance) {
    override val preferredOrder: Int
        get() = -100
}

class AddPlaylistTestContributor2(musicAppInstance: MusicApp) : AddPlaylistTestContributor(musicAppInstance) {
    override val preferredOrder: Int
        get() = -50
}
