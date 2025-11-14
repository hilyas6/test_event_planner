plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":core-kotlin"))
    implementation(project(":algorithms-scala"))
    implementation("org.scala-lang:scala-library:2.13.14")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
}

application { mainClass.set("app.MainKt") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
tasks.test { useJUnitPlatform() }


kotlin {
    jvmToolchain(23)
}

