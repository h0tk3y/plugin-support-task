package com.h0tk3y.player

import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface MusicPlugin {
    val pluginId: String
        get() = javaClass.canonicalName

    /** Called upon application start to initialize the plugin. The [persistedState] is the byte stream written by
     * [persist], if present. */
    fun init(persistedState: InputStream?)

    /** Called on a plugin instance to instruct it to persist all of its state. The plugin is allowed to use the
     * [stateStream] for storing the state, but should not close the [stateStream].
     *
     * May be called multiple times during application execution.
     *
     * If [MusicApp.isClosed] is true on the [musicAppInstance], the plugin should also yield all of its resources
     * and gracefully teardown.*/
    fun persist(stateStream: OutputStream)

    /** A reference to the application instance.
     *
     * A plugin may override this property as the single parameter of the primary constructor or a mutable property
     * (then the class must contain a no-argument constructor).
     *
     * In both cases, the application that instantiates the plugin must provide the value for the property.
     * If this property cannot be initialized in either way, the application must throw an [IllegalPluginException]
     * */
    val musicAppInstance: MusicApp
}

open class IllegalPluginException(val pluginClass: Class<*>) : Exception(
    "Illegal plugin class $pluginClass."
)

class PluginClassNotFoundException(val pluginClassName: String) : ClassNotFoundException(
    "Plugin class $pluginClassName not found."
)

interface PipelineContributorPlugin<T> : MusicPlugin {
    /** Plugins with lower [preferredOrder] should contribute to the pipeline earlier, that is, their results may
     * be altered by the plugins with higher preferred order. Plugins with equal values are not guaranteed to be
     * ordered in any particular way. */
    val preferredOrder: Int

    /** The implementation handles the [current] value on the pipeline and returns either the same value or a
     * different value that further pipeline contributors will receive. */
    fun contribute(current: T): T
}

/** A plugin that contributes to the music library upon application initialization. */
interface MusicLibraryContributorPlugin : PipelineContributorPlugin<MusicLibrary>

/** A plugin that handles the console input. Used by [ConsoleControlsPlugin] */
interface ConsoleHandlerPlugin : PipelineContributorPlugin<List<String>?> {
    val helpSectionName: String
    fun printHelp()
    /** Handle the console input by user separated by whitespaces. Returns null if the input is recognized by this
     * plugin, so that other plugins should not receive this input. */
    override fun contribute(current: List<String>?): List<String>?
}

interface PlaybackListenerPlugin : MusicPlugin {
    /** Observe the change of the playback state form [oldPlaybackState] to [newPlaybackState]. If the returned value
     * is not null, then the playback state changes to [newPlaybackState] without notifying this listener. */
    fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState): PlaybackState?
}

/** An item describing a plugin or a set of related plugins (that should be loaded together).
 * Each [MusicPluginPath] should get loaded by a separate class loader that loads the classes from classpath entries in
 * [pluginClasses] (for example, behaving as a [java.net.URLClassLoader].
 *
 * If a plugin class is mentioned in [pluginClasses] of multiple [MusicPluginPath]s, the application loads only a
 * single instance of the plugin. */
data class MusicPluginPath(
    /** Fully qualified names (FQN) of the plugin classes that the application should use. */
    val pluginClasses: List<String>,
    /** List of files that the application should use as the classpath elements (JARs or class directories). */
    val pluginClasspath: List<File>
)

