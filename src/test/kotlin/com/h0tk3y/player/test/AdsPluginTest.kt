package com.h0tk3y.player.test

import com.h0tk3y.player.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.lang.IllegalArgumentException
import kotlin.reflect.full.memberProperties

private val thirdPartyPluginClasses: List<File> =
    System.getProperty("third-party-plugin-classes").split(File.pathSeparator).map { File(it) }

class AdsTests {
    val track1 = Track(mutableMapOf()) { FileInputStream(File("sounds/beep-1.mp3")) }
    val track2 = Track(mutableMapOf()) { FileInputStream(File("sounds/beep-2.mp3")) }

    private val defaultEnabledPlugins = setOf(
        StaticPlaylistsLibraryContributor::class.java.canonicalName,
        "com.h0tk3y.third.party.plugin.AdsPlugin"
    )

    private fun withApp(clear: Boolean = true, doTest: (TestableMusicApp) -> Unit) {
        val app = TestableMusicApp(thirdPartyPluginClasses, defaultEnabledPlugins)
        if (clear) {
            app.wipePersistedPluginData()
        }
        app.use {
            it.init()
            doTest(it)
        }
    }

    private fun getAds(app: MusicApp) : List<String> {
        val p = app.findSinglePlugin("com.h0tk3y.third.party.plugin.AdsPlugin") ?: throw IllegalArgumentException()
        val pr = p::class.memberProperties.find { it.name == "ads" } ?: throw IllegalArgumentException()
        val res = pr.getter.call(p) ?: throw IllegalArgumentException()
        return (res as List<*>).map { it as String }
    }

    @Test
    fun testAdsQueue() {
        withApp { app ->
            val playlist = Playlist("my", listOf(track1, track2, track1, track2, track1, track2))
            app.player.playbackState = PlaybackState.Playing(PlaylistPosition(playlist, 0), isResumed = false)
            assert(getAds(app).isEmpty())
            app.player.finishedTrack()
            app.player.finishedTrack()
            assert(getAds(app).size == 1)
            val firstAdd = getAds(app).first()
            app.player.finishedTrack()
            app.player.finishedTrack()
            assert(getAds(app).size == 2)
            assert(getAds(app).first() == firstAdd)
            val lastAdd = getAds(app).last()
            app.player.finishedTrack()
            app.player.finishedTrack()
            assert(getAds(app).size == 2)
            assert(getAds(app).first() == lastAdd)
        }
    }

    @Test
    fun testOneTrackPlaylist() {
        withApp { app ->
            val playlist = Playlist("my", listOf(track1, track1, track1, track1))
            app.player.playbackState = PlaybackState.Playing(PlaylistPosition(playlist, 0), isResumed = false)
            app.wipePersistedPluginData()
            assert(getAds(app).isEmpty())
            app.player.finishedTrack()
            app.player.finishedTrack()
            app.player.finishedTrack()
            app.player.finishedTrack()
            assert(getAds(app).size == 2)
        }
    }

    @Test
    fun testPersistStates() {
        var ads = mutableListOf<String>()
        withApp { app ->
            val playlist = Playlist("my", listOf(track1, track2, track1, track2))
            app.player.playbackState = PlaybackState.Playing(PlaylistPosition(playlist, 0), isResumed = false)
            assert(getAds(app).isEmpty())
            playlist.tracks.forEach {
                app.player.finishedTrack()
            }
            ads = getAds(app).toMutableList()
            app.close()
        }
        withApp(false) { app ->
            val newAds = getAds(app).toMutableList()
            assert(newAds == ads)
        }
    }
}