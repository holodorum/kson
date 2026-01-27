package org.kson

import java.io.File

/**
 * Centralized version management for KSON projects.
 * Computes version strings that include git commit SHA for snapshot builds.
 *
 * Usage in build.gradle.kts:
 *   val isRelease = project.findProperty("release") == "true"
 *   version = org.kson.KsonVersion.getVersion(rootProject.projectDir, isRelease)
 *
 * Build commands:
 *   Snapshot: ./gradlew build              -> 0.3.0-abc1234-SNAPSHOT
 *   Release:  ./gradlew build -Prelease=true -> 0.3.0
 */
object KsonVersion {
    /**
     * Base version number without snapshot suffix.
     * Update this when preparing a new release.
     */
    const val BASE_VERSION = "0.3.0"

    /**
     * Returns the short git commit SHA (7 characters).
     * Falls back to "unknown" if git is not available.
     */
    fun getGitSha(projectDir: File): String {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Returns the version string based on release mode.
     *
     * @param projectDir The project directory for git SHA lookup
     * @param isRelease If true, returns release version (e.g., "0.3.0").
     *                  If false, returns snapshot version (e.g., "0.3.0-abc1234-SNAPSHOT").
     */
    fun getVersion(projectDir: File, isRelease: Boolean): String {
        return if (isRelease) {
            BASE_VERSION
        } else {
            "$BASE_VERSION-${getGitSha(projectDir)}-SNAPSHOT"
        }
    }

    /**
     * Returns the full snapshot version including git SHA.
     * Format: {BASE_VERSION}-{gitSha}-SNAPSHOT
     * Example: 0.3.0-abc1234-SNAPSHOT
     */
    fun getSnapshotVersion(projectDir: File): String = getVersion(projectDir, isRelease = false)

    /**
     * Returns the release version (without SNAPSHOT suffix).
     * Format: {BASE_VERSION}
     */
    fun getReleaseVersion(): String = BASE_VERSION
}