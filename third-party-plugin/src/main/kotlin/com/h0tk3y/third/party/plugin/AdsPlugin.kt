package com.h0tk3y.third.party.plugin

import com.h0tk3y.player.*
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalArgumentException
import java.nio.channels.Channel
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

class AdsPlugin(override val musicAppInstance: MusicApp) :
    PlaybackListenerPlugin {

    var ads = mutableListOf<String>()

    var runCount: Int = 0
        private set

    companion object {
        const val PERIOD = 2
        const val CAPACITY = 2
        val adTexts = listOf(
            "Легендарный Бока и его внук популярный певец Жока",
            "Не забывайте проветривать....",
            "покупайте деньги!",
            "Говорю мужу: ну и живот ты отрастил! Ответ убил..."
        )
    }

    override fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState) {
        if (oldPlaybackState !is PlaybackState.Paused && newPlaybackState !is PlaybackState.Paused) {
            runCount++
            if (runCount >= PERIOD) {
                runCount = 0
                if (ads.size == CAPACITY) {
                    ads.removeAt(0)
                }
                ads.add(adTexts[(0 until adTexts.size).random()])
                println(ads.last())
            }
        }
    }

    override fun init(persistedState: InputStream?) {
        runCount = 0
        ads.clear()
        persistedState?.reader()?.readLines()?.let {line ->
            if (!line.isEmpty()) {
                runCount = line[0].toInt()
                line.subList(1, line.size).forEach { ads.add(it) }
            }
        }
    }

    override fun persist(stateStream: OutputStream) {
        val str = runCount.toString() + "\n" + ads.joinToString("\n")
        stateStream.write(str.toByteArray())
    }
}
