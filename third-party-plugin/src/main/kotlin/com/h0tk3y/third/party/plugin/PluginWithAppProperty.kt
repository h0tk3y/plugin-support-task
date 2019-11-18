package com.h0tk3y.third.party.plugin

import com.h0tk3y.player.MusicApp
import com.h0tk3y.player.MusicPlugin
import java.io.InputStream
import java.io.OutputStream

class PluginWithAppProperty : MusicPlugin {
    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit

    override lateinit var musicAppInstance: MusicApp
}