package com.h0tk3y.third.party.plugin

import com.h0tk3y.player.MusicPlugin
import java.io.InputStream
import java.io.OutputStream

class UsageStatsPlugin : MusicPlugin() {
    private var runCount: Int = 0

    override fun init(persistedState: InputStream?) {
        if (persistedState != null) {
            val text = persistedState.bufferedReader().readText()
            runCount = (text.toIntOrNull() ?: 0) + 1
        }
    }

    override fun persist(stateStream: OutputStream) {
        stateStream.write(runCount.toString().toByteArray())
    }
}
