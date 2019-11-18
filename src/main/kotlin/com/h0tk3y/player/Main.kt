package com.h0tk3y.player

import java.io.File

fun main(args: Array<String>) {
    val defaultsPlugins =
        listOf(
            ConsolePlaybackReporterPlugin::class,
            ConsoleControlsPlugin::class,
            BasicConsoleControlsPlugin::class,
            ListPluginsInConsolePlugin::class,
            StaticPlaylistsLibraryContributor::class
        ).map { MusicPluginPath(listOf(it.java.canonicalName), emptyList()) }

    val externalPlugins = listOf(
        MusicPluginPath(
            listOf("com.h0tk3y.third.party.plugin.UsageStatsPlugin"),
            listOf(File(pluginsPath).resolve("third-party-plugin.jar"))
        )
    )

    val pluginPaths =
        args.map { parsePluginPath(it) ?: error("could not parse argument $it") } +
                externalPlugins +
                defaultsPlugins

    MusicApp(pluginPaths).init()
}

val pluginsPath = System.getProperty("pluginsDirectory")?.toString() ?: "build/pluginsRuntime"

private val pluginClassChar = '+'
private val classpathChar = ';'
private val pluginPathRegex =
    "[$pluginClassChar$classpathChar]([^$pluginClassChar$classpathChar])*".toRegex()

fun parsePluginPath(string: String): MusicPluginPath? {
    val matchStrings = pluginPathRegex.findAll(string).groupBy { it.value.first() }
    val pluginClasses = matchStrings[pluginClassChar].orEmpty().map { it.value.removePrefix("$pluginClassChar") }
    val pluginClasspath = matchStrings[classpathChar].orEmpty().map { it.value.removePrefix("$classpathChar") }
    if (pluginClasses.isEmpty() || pluginClasspath.isEmpty())
        return null
    return MusicPluginPath(pluginClasses, pluginClasspath.map { File(it) })
}

