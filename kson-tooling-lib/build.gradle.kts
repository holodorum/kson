plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish") version "0.30.0"
}

repositories {
    mavenCentral()
}

group = "org.kson"
version = "0.1.2-SNAPSHOT"
kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    js(IR) {
        browser()
        nodejs()
        binaries.library()
        useEsModules()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
                api(project(":kson-lib"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// Create an index.mjs that re-exports from both kson-lib and kson-tooling-lib modules
// This ensures all types (including SchemaResult, Message, etc.) are available from a single import
val createJsIndexFiles by tasks.registering {
    dependsOn("jsBrowserProductionLibraryDistribution")
    dependsOn(":kson-lib:jsBrowserProductionLibraryDistribution")

    val distDir = layout.buildDirectory.dir("dist/js/productionLibrary")
    val ksonLibDistDir = project(":kson-lib").layout.buildDirectory.dir("dist/js/productionLibrary")

    doLast {
        // Copy kson-lib type declarations to this distribution
        val ksonLibDmts = ksonLibDistDir.get().file("kson-kson-lib.d.mts").asFile
        if (ksonLibDmts.exists()) {
            ksonLibDmts.copyTo(distDir.get().file("kson-kson-lib.d.mts").asFile, overwrite = true)
        }

        // Create index.mjs that re-exports everything
        val indexMjs = distDir.get().file("index.mjs").asFile
        indexMjs.writeText(
            """
            // Re-export all symbols from both modules for single-import convenience
            export * from './kson-kson-lib.mjs';
            export * from './kson-kson-tooling-lib.mjs';
        """.trimIndent()
        )

        // Create index.d.mts that re-exports type declarations
        val indexDmts = distDir.get().file("index.d.mts").asFile
        indexDmts.writeText(
            """
            // Re-export all type declarations
            export * from './kson-kson-lib.d.mts';
            export * from './kson-kson-tooling-lib.d.mts';
        """.trimIndent()
        )

        // Update package.json to use index as main entry point
        val packageJson = distDir.get().file("package.json").asFile
        val content = packageJson.readText()
        val updated = content
            .replace("\"main\": \"kson-kson-tooling-lib.mjs\"", "\"main\": \"index.mjs\"")
            .replace("\"types\": \"kson-kson-tooling-lib.d.mts\"", "\"types\": \"index.d.mts\"")
        packageJson.writeText(updated)
    }
}

tasks.named("check") {
    dependsOn("assemble")
}
// Ensure the index files are created when assembling
tasks.named("assemble") {
    dependsOn(createJsIndexFiles)
}


mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    signAllPublications()

    coordinates("org.kson", "kson", version.toString())

    pom {
        name.set("KSON")
        description.set("A 💌 to the humans maintaining computer configurations")
        url.set("https://kson.org")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("dmarcotte")
                name.set("Daniel Marcotte")
                email.set("kson@kson.org")
            }
        }

        scm {
            connection.set("scm:git:https://github.com/kson-org/kson.git")
            developerConnection.set("scm:git:git@github.com:kson-org/kson.git")
            url.set("https://github.com/kson-org/kson")
        }
    }
}
