package com.h0tk3y.player.test

import com.h0tk3y.player.*
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.*

private val thirdPartyPluginClasses: List<File> =
    System.getProperty("third-party-plugin-classes").split(File.pathSeparator).map { File(it) }

private val usageStatsPluginName = "com.h0tk3y.third.party.plugin.UsageStatsPlugin"
private val pluginWithAppPropertyName = "com.h0tk3y.third.party.plugin.PluginWithAppProperty"
private val randomizerPluginName = "com.h0tk3y.third.party.plugin.ContributorRandomizerPlugin"

class RandomizerPluginTest {

    private val defaultEnabledPlugins = setOf(
        StaticPlaylistsLibraryContributor::class.java.canonicalName,
        usageStatsPluginName,
        pluginWithAppPropertyName,
        randomizerPluginName
    )

    private fun withApp(
        doTest: TestableMusicApp.() -> Unit
    ) {
        val app = TestableMusicApp(thirdPartyPluginClasses, defaultEnabledPlugins)
        app.use {
            it.init()
            it.doTest()
        }
    }

    val beeps = (0 until 4).map {
        Track(mutableMapOf()) { FileInputStream("sounds/beep-$it.mp3") }
    }

    val samples = (0 until 4).map {
        Track(mutableMapOf()) { FileInputStream("sounds/sample-$it.mp3") }
    }

    @Test
    fun testThirdPartyPlugin() {
        withApp {
            val beepsPlaylist = Playlist("beeps", beeps)
            val samplesPlaylist = Playlist("samples", samples)
            musicLibrary.playlists.add(beepsPlaylist)
            musicLibrary.playlists.add(samplesPlaylist)
            player.playbackState = PlaybackState.Playing(
                PlaylistPosition(beepsPlaylist, 0),
                isResumed = false
            )
            assertEquals(
                musicLibrary.playlists.map { it.name }.toSet(),
                setOf("beeps", "samples", "beeps(shuffled)", "samples(shuffled)")
            )
            assertEquals(
                beeps.size,
                musicLibrary.playlists.find {
                    it.name == "beeps(shuffled)"
                }?.tracks?.size
            )

            assertEquals(
                samples.size,
                musicLibrary.playlists.find {
                    it.name == "samples(shuffled)"
                }?.tracks?.size
            )
        }

    }
}