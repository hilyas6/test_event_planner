plugins {
    scala
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core-kotlin"))
    implementation("org.scala-lang:scala-library:2.13.14")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.apply {
        additionalParameters = listOf("-deprecation", "-release", "21")
    }
}
