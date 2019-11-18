plugins {
    kotlin("jvm")
}

repositories {
    jcenter()
    maven("https://kotlin.bintray.com/kotlin-dev1")
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(rootProject)
}