plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withType<JavaPlugin>().configureEach {
        the<JavaPluginExtension>().toolchain {
            languageVersion.set(JavaLanguageVersion.of(23))
        }
    }
}
