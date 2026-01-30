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
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}


/**
 * Task to install npm dependencies in the production library output directory.
 *
 * This task is used when you need to prepare the production library with its dependencies
 * installed.
 *
 * This will:
 * 1. Build the production library (via jsNodeProductionLibraryDistribution)
 * 2. Run 'npm install' in build/dist/js/productionLibrary
 * 3. Install all dependencies specified in the library's package.json
 */
tasks.register<PixiExecTask>("npmInstallProductionLibrary") {
    description = "Install npm dependencies in the production library output"
    dependsOn("jsNodeProductionLibraryDistribution")

    command.set(listOf("npm", "install"))
    workingDirectory.set(layout.buildDirectory.dir("dist/js/productionLibrary"))
    doNotTrackState("npm already tracks its own state")
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
