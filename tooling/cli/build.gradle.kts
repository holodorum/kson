plugins {
    application
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    implementation(project(":kson-lib"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

application {
    mainClass.set("org.kson.tooling.cli.CommandLineInterfaceKt")
}

// GraalVM configuration - now using JDK toolchain from jdk.properties
val graalvmDir = file("${rootProject.projectDir}/gradle/jdk")

/**
 * Locates the GraalVM JDK installation directory within the gradle/jdk folder.
 * The JDK is automatically downloaded by the Gradle wrapper based on jdk.properties.
 * Handles platform-specific directory structures (e.g., macOS's Contents/Home subdirectory).
 * @return The GraalVM home directory
 * @throws GradleException if GraalVM JDK is not found
 */
fun getGraalVMHome(): File {
    val jdkDir = graalvmDir.listFiles()?.find {
        it.isDirectory && it.name.contains("graalvm")
    } ?: throw GradleException("GraalVM JDK not found in $graalvmDir. Run './gradlew' to download it.")

    val os = System.getProperty("os.name").lowercase()
    return if ((os.contains("mac") || os.contains("darwin")) && file("$jdkDir/Contents/Home").exists()) {
        file("$jdkDir/Contents/Home")
    } else {
        jdkDir
    }
}

/**
 * Determines the file extension for the native-image executable based on the operating system.
 * @return A file extension string: ".cmd" for Windows, empty string for macOS/Linux
 */
fun getNativeImageExtension(): String {
    val os = System.getProperty("os.name").lowercase()
    return if (os.contains("win")) ".cmd" else ""
}

// Custom task to build native image using GraalVM JDK
val buildNativeImage by tasks.registering(PixiExecTask::class) {
    group = "build"
    description = "Builds native executable using GraalVM from JDK toolchain"

    dependsOn(tasks.jar)

    val outputDir = layout.buildDirectory.dir("native/nativeCompile").get().asFile
    val outputFile = file("$outputDir/kson")

    // Configure the command at configuration time using providers
    command.set(provider {
        val graalHome = getGraalVMHome()

        val nativeImageExe = file("${graalHome}/bin/native-image${getNativeImageExtension()}")
        if (!nativeImageExe.exists()) {
            throw GradleException("native-image not found at $nativeImageExe. Ensure GraalVM JDK is properly installed.")
        }

        val classpath = configurations.runtimeClasspath.get().asPath
        val jarFile = tasks.jar.get().archiveFile.get().asFile

        listOf(
            nativeImageExe.absolutePath,
            "-cp", "${jarFile.absolutePath}${File.pathSeparator}$classpath",
            "-H:+ReportExceptionStackTraces",
            "--no-fallback",
            "-o", outputFile.absolutePath,
            "org.kson.tooling.cli.CommandLineInterfaceKt"
        )
    })

    doFirst {
        // Create output directory
        outputDir.mkdirs()
        
        println("Building native image with GraalVM using pixi")
        println("Creating executable: $outputFile")
    }
    
    doLast {
        if (outputFile.exists()) {
            println("\n✅ Native image built successfully!")
            println("   Executable: $outputFile")
            println("   Size: ${outputFile.length() / 1024 / 1024} MB")
            println("\nRun it with: $outputFile --help")
        }
    }
}

tasks{
    check {
        dependsOn(buildNativeImage)
    }
}
