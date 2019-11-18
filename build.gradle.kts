plugins {
    kotlin("jvm").version("1.3.50")
    id("application")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("javazoom:jlayer:1.0.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("com.googlecode.soundlibs:tritonus-share:0.3.7-2")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5-1")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3-1")

    runtimeClasspath(project(":third-party-plugin"))

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testCompileOnly(project(":third-party-plugin"))
}

// Workaround: ensure that the IDE forces the JAR to be built upon classes launch time:
tasks["classes"].finalizedBy(":third-party-plugin:classes")


application {
    distributions {
        main {
            contents {
                from(projectDir.resolve("sounds")) {
                    include("beep-*.mp3")
                    include("sample-*.mp3")
                }.into("sounds")
            }
        }
    }
    mainClassName = "com.h0tk3y.player.MainKt"
}

tasks.withType(Test::class).getByName("test") {
    doFirst {
        systemProperty(
            "third-party-plugin-classes",
            project(":third-party-plugin").kotlin.target.compilations["main"].output.classesDirs.asPath
        )
    }
}