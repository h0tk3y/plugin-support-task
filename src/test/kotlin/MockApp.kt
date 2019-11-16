import com.h0tk3y.player.*
import org.junit.Before
import org.junit.Test
import java.io.File

class MockApp(
    pluginClasspath: List<File>,
    enabledPluginIds: Set<String>
) : MusicApp(
    pluginClasspath,
    enabledPluginIds
) {
    override val player: MockPlayer by lazy { MockPlayer(playbackListeners) }
}

class AppTests {
    lateinit var app: MockApp

    @Before
    fun init() {
        app = MockApp(emptyList(), emptySet()).apply { init() }
    }

    @Test
    fun testNone() {
        val track1 = Track(mapOf(), { TODO() })
        val track2 = Track(mapOf(), { TODO() })
        val playlist = Playlist("my", listOf(track1, track2))
        app.player.playbackState = PlaybackState.Playing(PlaylistPosition(playlist, 0), isResumed = false)
        app.player.finishedTrack()
        assert(app.player.playbackState.let { it is PlaybackState.Playing && it.playlistPosition.position == 1 })
        app.player.finishedTrack()
        assert(app.player.playbackState == PlaybackState.Stopped)
    }
}