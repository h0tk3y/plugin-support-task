package com.h0tk3y.player

import java.io.File

fun main(args: Array<String>) {
    val pluginClasspathParentDirs = args.map { File(it) }
    val pluginFiles = pluginClasspathParentDirs.flatMap { it.listFiles()?.toList().orEmpty() }

    MusicApp(
        pluginFiles,
        setOf(
            ConsolePlaybackReporterPlugin::class.java.canonicalName,
            ConsoleControlsPlugin::class.java.canonicalName,
            "com.myplugin.plugin.ShuffledPlaylistLibraryContributor",
            "com.h0tk3y.third.party.plugin.UsageStatsPlugin"
        )
    ).init()
}