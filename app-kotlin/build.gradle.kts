plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":core-kotlin"))
    implementation(project(":algorithms-scala"))
    // ADD THIS so Kotlin can see scala.Tuple2 (used by SlotFinder's return type)
    implementation("org.scala-lang:scala-library:2.13.14")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jdatepicker:jdatepicker:1.3.4") // ✅ add this line

    testImplementation(kotlin("test"))
}

application { mainClass.set("app.MainKt") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
tasks.test { useJUnitPlatform() }


kotlin {
    jvmToolchain(23)
}

