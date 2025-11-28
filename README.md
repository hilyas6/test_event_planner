# Event Planner Project

## Overview
This repository hosts a multi-language event planner application that combines Kotlin and Scala modules. The project demonstrates scheduling logic, user-interface components, and shared domain models to help organize events in a structured and reproducible manner. The documentation below is written in an academic but accessible style so technical and non-technical readers can follow along.

## Repository Structure
- `app-kotlin/`: The Kotlin-based desktop application that wires together the UI and scheduling logic. The `AppContext` and `MainKt` entry point live here.
- `core-kotlin/`: Shared domain models and serialization utilities that are consumed by both the application and supporting libraries.
- `algorithms-scala/`: Scala helper library that contributes scheduling algorithms (for example, slot finding routines) that the Kotlin app consumes.
- `data/`: Project datasets or sample inputs the application can reference.
- `gradle/`, `gradlew`, and `gradlew.bat`: Gradle wrapper files so you can build and run the project without installing Gradle manually.
- `build.gradle.kts` and `settings.gradle.kts`: Root build configuration that links all subprojects and defines shared settings.

## Requirements
To build and run the application, ensure the following are available:
- **Java Development Kit (JDK) 21 or later**. The Gradle toolchains configure Java 21 or higher for compilation, and the app module targets at least Java 17 compatibility for runtime.
- **Kotlin 2.2.x toolchain** and **Scala 2.13.x standard library**. These are resolved automatically by Gradle; no manual installation is required.
- **Internet access for dependency resolution** on the first build (Gradle will download the required plugins and libraries).
- A Unix-like shell (macOS, Linux, or Windows Subsystem for Linux) or Windows PowerShell for running the Gradle wrapper scripts.

## Setup
1. **Clone the repository** (if you have not already) and open a terminal at the project root.
2. **Ensure Java is on your PATH** by running `java -version`. If Java is missing, install a JDK 21+ distribution (e.g., Temurin or Microsoft Build of OpenJDK).
3. **Trust the Gradle wrapper**. On Unix-like systems, you may need to make it executable once via `chmod +x gradlew`.

## Running the Application
Use the Gradle wrapper from the project root to build and run the Kotlin application module:

```bash
./gradlew :app-kotlin:run
```

Gradle will:
1. Download any missing dependencies (Kotlin stdlib, Scala library, JSON serialization, date picker widgets, etc.).
2. Compile the `core-kotlin`, `algorithms-scala`, and `app-kotlin` modules.
3. Launch the `app.MainKt` entry point.

If you are on Windows, run the equivalent command with the batch script:

```bat
gradlew.bat :app-kotlin:run
```

### Optional: Running Tests
To execute the project test suites for all modules, run:

```bash
./gradlew test
```

Gradle will report test results and produce build outputs under each module’s `build/` directory.

## Data and Configuration
- Place any event datasets or configuration files inside the `data/` directory so they can be referenced by the application.
- Build outputs and generated artifacts are kept inside the respective module `build/` folders to keep the source tree organized.

## Troubleshooting
- If Gradle reports toolchain download issues, verify that your network allows access to `services.gradle.org` and Maven Central.
- When switching Java versions, delete the `.gradle/` directory and rerun the Gradle commands so the toolchain and dependencies refresh.
- For IDE users, import the project as a **Gradle Kotlin/Scala multi-module project** so the IDE honors the shared build scripts.

## Authors
- Hanzla Ilyas — 001060407
- Sebastian Cap — 001289569

## License
This project is released under the MIT License:

```
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
