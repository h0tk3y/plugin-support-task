plugins {
    kotlin("jvm")
    id("application")
}

group = "org.example"
version = "1.0"

kotlin.target { compilations.all { kotlinOptions.jvmTarget = "11" } }

val pluginsRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation("javazoom:jlayer:1.0.1")
    implementation(kotlin("reflect"))

    implementation("com.googlecode.soundlibs:tritonus-share:0.3.7-2")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5-1")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3-1")

    testImplementation(kotlin("test-junit"))
    testCompileOnly(project(":third-party-plugin"))

    pluginsRuntime(project(":third-party-plugin")) { isTransitive = false}
}

val packedPluginsRuntimeDirectory = buildDir.resolve("pluginsRuntime")
val packPluginsRuntime by tasks.registering(Sync::class) {
    from(pluginsRuntime)
    into(packedPluginsRuntimeDirectory)
}

// Workaround: ensure that the IDE forces the JAR to be built upon classes launch time:
tasks.named("classes") {
    finalizedBy(packPluginsRuntime)
}

val pluginsDirectoryInDistribution = "plugins"
application {
    distributions {
        main {
            this.
            contents {
                from(projectDir.resolve("sounds")) {
                    include("*.mp3")
                    into("sounds")
                }
                from(files(packedPluginsRuntimeDirectory).builtBy(packPluginsRuntime)) {
                    into(pluginsDirectoryInDistribution)
                }
            }
        }
    }
    this.applicationName = "plugin-support-hw"
    this.executableDir = ""
    mainClass.set("com.h0tk3y.player.MainKt")
}

tasks.withType<CreateStartScripts>().named("startScripts") {
    defaultJvmOpts = listOf("-DpluginsDirectory=$pluginsDirectoryInDistribution")
}

tasks.withType(Test::class).named("test") {
    doFirst {
        systemProperty(
            "third-party-plugin-classes",
            project(":third-party-plugin").kotlin.target.compilations["main"].output.classesDirs.asPath
        )
    }
}