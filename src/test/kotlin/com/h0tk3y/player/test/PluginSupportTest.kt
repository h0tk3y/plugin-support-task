package com.h0tk3y.player.test

import com.h0tk3y.player.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.*

private val thirdPartyPluginClasses: List<File> =
    System.getProperty("third-party-plugin-classes").split(File.pathSeparator).map { File(it) }

private const val usageStatsPluginName = "com.h0tk3y.third.party.plugin.UsageStatsPlugin"
private const val pluginWithAppPropertyName = "com.h0tk3y.third.party.plugin.PluginWithAppProperty"

internal class PluginSupportTest {
    private val defaultPlugins = listOf(
        MusicPluginPath(listOf(StaticPlaylistsLibraryContributor::class.java.canonicalName), listOf()),
        MusicPluginPath(listOf(usageStatsPluginName), thirdPartyPluginClasses),
        MusicPluginPath(listOf(pluginWithAppPropertyName), thirdPartyPluginClasses)
    )

    private fun withApp(
        wipePersistedData: Boolean = false,
        pluginPath: List<MusicPluginPath> = defaultPlugins,
        doTest: TestableMusicApp.() -> Unit
    ) {
        val app = TestableMusicApp(pluginPath)
        if (wipePersistedData) {
            app.wipePersistedPluginData()
        }
        app.use {
            it.init()
            it.doTest()
        }
    }

    @Test
    fun testPluginLoadedByChildClassloader() {
        withApp(
            pluginPath = listOf(
                MusicPluginPath(listOf(pluginWithAppPropertyName), thirdPartyPluginClasses)
            )
        ) {
            val loader = this.getPlugins(MusicPlugin::class.java).single().javaClass.classLoader
            assertEquals(MusicPlugin::class.java.classLoader, loader.parent)
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
    fun testDontLoadSamePluginTwice() {
        withApp(
            pluginPath = listOf(
                MusicPluginPath(listOf(pluginWithAppPropertyName), thirdPartyPluginClasses),
                MusicPluginPath(listOf(pluginWithAppPropertyName), thirdPartyPluginClasses)
            )
        ) {
            val plugins = this.getPlugins(MusicPlugin::class.java)
            assertEquals(1, plugins.size)
            assertEquals(pluginWithAppPropertyName, plugins.single().javaClass.canonicalName)
        }
    }

    @Test
    fun testPluginIsolation() {
        val pluginClasses = listOf(
            pluginWithAppPropertyName,
            "com.h0tk3y.third.party.plugin.ExtendPluginWithAppProperty"
        )
        withApp(
            pluginPath = pluginClasses.map { MusicPluginPath(listOf(it), thirdPartyPluginClasses) }
        ) {
            val (c1, c2) = pluginClasses.map { findSinglePlugin(it)!!.javaClass }
            assertFalse { c1.isAssignableFrom(c2) }
            assertFalse { c2.isAssignableFrom(c1) }
        }
    }

    @Test
    fun testPluginCouplingFromSameEntry() {
        val pluginClasses = listOf(
            pluginWithAppPropertyName,
            "com.h0tk3y.third.party.plugin.ExtendPluginWithAppProperty"
        )
        withApp(
            pluginPath = listOf(MusicPluginPath(pluginClasses, thirdPartyPluginClasses))
        ) {
            val (c1, c2) = pluginClasses.map { findSinglePlugin(it)!!.javaClass }
            assertTrue { c1.isAssignableFrom(c2) }
            assertFalse { c2.isAssignableFrom(c1) }
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
        withApp(true) {
            assertNull(findSinglePlugin("some.missing.plugin"))
        }
    }

    @Test
    fun testGetAllPlugins() {
        withApp(true) {
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
        val pathForMalformedPlugin = MusicPluginPath(listOf(MalformedPlugin::class.java.canonicalName), emptyList())
        val exception = assertFailsWith<IllegalPluginException> {
            withApp(pluginPath = defaultPlugins + pathForMalformedPlugin) { }
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
            withApp(pluginPath = listOf(MusicPluginPath(listOf(usageStatsPluginName), emptyList()))) {}
        }
        assertEquals(usageStatsPluginName, exception.pluginClassName)
    }

    @Test
    fun testLoadEmptyStateOnFirstStart() {
        withApp(
            true,
            defaultPlugins + MusicPluginPath(listOf(PersistanceCheckerPlugin::class.java.canonicalName), emptyList())
        ) {
            val pluginInstance =
                findSinglePlugin(PersistanceCheckerPlugin::class.java.canonicalName) as PersistanceCheckerPlugin
            assertNull(pluginInstance.initBytes)
        }
    }

    @Test
    fun testPluginCloseRoutine() {
        val pathForAppCloseTrapPlugin =
            MusicPluginPath(listOf(AppCloseTrapPlugin::class.java.canonicalName), emptyList())

        lateinit var pluginInstance: AppCloseTrapPlugin
        withApp(pluginPath = defaultPlugins + pathForAppCloseTrapPlugin) {
            pluginInstance = findSinglePlugin(AppCloseTrapPlugin::class.java.canonicalName) as AppCloseTrapPlugin
        }
        assertTrue(pluginInstance.closed)
    }


    @Test
    fun testSameBytesInStreams() {
        lateinit var pluginInstance: PersistanceCheckerPlugin

        val plugins =
            defaultPlugins + MusicPluginPath(listOf(PersistanceCheckerPlugin::class.java.canonicalName), emptyList())

        val expectedBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        withApp(pluginPath = plugins) {
            pluginInstance =
                findSinglePlugin(PersistanceCheckerPlugin::class.java.canonicalName) as PersistanceCheckerPlugin
            pluginInstance.persistBytes = expectedBytes
        }
        withApp(pluginPath = plugins) {
            pluginInstance =
                findSinglePlugin(PersistanceCheckerPlugin::class.java.canonicalName) as PersistanceCheckerPlugin
            assertTrue(expectedBytes.contentEquals(pluginInstance.initBytes))
        }
    }

    @Test
    fun testContributorsOrdering() {
        withApp(
            pluginPath = defaultPlugins +
                    MusicPluginPath(listOf(AddPlaylistTestContributor1::class.java.canonicalName), emptyList()) +
                    MusicPluginPath(listOf(AddPlaylistTestContributor2::class.java.canonicalName), emptyList())
        ) {
            val pl1 =
                findSinglePlugin(AddPlaylistTestContributor1::class.java.canonicalName) as AddPlaylistTestContributor
            val pl2 =
                findSinglePlugin(AddPlaylistTestContributor2::class.java.canonicalName) as AddPlaylistTestContributor

            assertEquals(0, pl1.playlistsBefore.size)
            assertEquals(1, pl2.playlistsBefore.size)

            assertTrue(musicLibrary.playlists.any { it.name == AddPlaylistTestContributor1::class.java.canonicalName })
            assertTrue(musicLibrary.playlists.any { it.name == AddPlaylistTestContributor2::class.java.canonicalName })
        }
    }
}

class PersistanceCheckerPlugin(override val musicAppInstance: MusicApp) : MusicPlugin {
    var initBytes: ByteArray? = null
    var persistBytes: ByteArray? = null

    override fun init(persistedState: InputStream?) {
        initBytes = persistedState?.readBytes()
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
        current.playlists.add(Playlist(name, mutableListOf()))
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
