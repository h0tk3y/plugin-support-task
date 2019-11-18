pluginManagement {
    fun RepositoryHandler.setup() {
        mavenCentral()
        if (this == pluginManagement.repositories) {
            gradlePluginPortal()
        }
    }
    repositories.setup()
    gradle.allprojects { repositories.setup() }

    plugins {
        kotlin("jvm").version("1.4.31")
    }
}

rootProject.name = "plugin-support-hw"
include("third-party-plugin")