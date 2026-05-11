package org.kson.jsonsuite

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.kson.CleanGitCheckout
import org.kson.DirtyRepoException
import org.kson.NetworkOperationFailedException
import org.kson.NoRepoException
import org.kson.ShaNotResolvableException
import java.io.File
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile
import kotlin.test.*

/**
 * Unzip the git directory test fixture we prepared for these tests into a temp dir
 */
private val gitTestFixturePath = run {
    val gitTestFixtureURI =
        ({}.javaClass.getResource("/GitTestFixture.zip")?.file)
            ?: throw RuntimeException("Expected to find this test resource!")

    val tmpDir = createTempDirectory("GitTestFixtureUnzipped").toString()

    ZipFile(gitTestFixtureURI).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            zip.getInputStream(entry).use { input ->
                val outputFile = File(tmpDir, entry.name)
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
    File(tmpDir, "GitTestFixture").absolutePath
}

class CleanGitCheckoutTest {

    @Test
    fun testConstructionPerformsNoIO() {
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val cloneName = "GitFixture"
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        val checkout = CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testDestinationDir,
            cloneName
        )

        assertFalse(
            checkout.checkoutDir.exists(),
            "Construction should not create the checkout directory; that's `prepare()`'s job"
        )
        assertEquals(
            testDestinationDir.resolve(cloneName).toFile(),
            checkout.checkoutDir,
            "checkoutDir should be computed at construction time"
        )
    }

    @Test
    fun testEnsureCheckoutOnNonExistentDir() {
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        val cleanGitCheckout = CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testDestinationDir,
            "GitFixture"
        )
        cleanGitCheckout.prepare()

        val repository = Git.open(cleanGitCheckout.checkoutDir).repository
        val actualCheckoutSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertEquals(desiredCheckoutSHA, actualCheckoutSHA, "should have made new dir and checked out the desired SHA")
    }

    @Test
    fun testEnsureCheckoutIsIdempotent() {
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        val cleanGitCheckout = CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testDestinationDir,
            "GitFixture"
        )
        cleanGitCheckout.prepare()
        // second call should be a no-op against an already-correct checkout
        cleanGitCheckout.prepare()

        val repository = Git.open(cleanGitCheckout.checkoutDir).repository
        val actualCheckoutSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name
        assertEquals(desiredCheckoutSHA, actualCheckoutSHA)
    }

    @Test
    fun testEnsureCleanGitCheckoutOnEmptyDir() {
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val cloneName = "GitFixture"

        // create an empty dir where the repository would be cloned...
        testDestinationDir.resolve(cloneName).toFile().mkdir()

        // and try to check out the repository...
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"
        assertFailsWith<NoRepoException>("should error on non-git dir") {
            CleanGitCheckout(
                gitTestFixturePath,
                desiredCheckoutSHA,
                testDestinationDir,
                "GitFixture"
            ).prepare()
        }
    }

    @Test
    fun testEnsureCleanGitCheckoutOnCleanDir() {
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        val cleanGitCheckout = CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testDestinationDir,
            "GitFixture"
        )
        cleanGitCheckout.prepare()

        // reach into the checkout we just created and point it another SHA
        val repository = Git.open(cleanGitCheckout.checkoutDir).repository
        val git = Git(repository)
        git.checkout().setName("296892b3392df3adeb7fb14e6b74140a311a1695").call()
        val currentRepoSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertNotEquals(
            currentRepoSHA,
            desiredCheckoutSHA,
            "should not currently be pointed to our desired SHA, " +
                    "because this test is trying verify we're able to check out our desired SHA from a " +
                    "clean git repo pointed to another SHA"
        )

        /**
         * create a new [CleanGitCheckout] in the same directory demanding our `desiredCheckoutSHA`
         */
        CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testDestinationDir,
            "GitFixture"
        ).prepare()

        // this incantation gets us the currently checked out SHA
        val actualCheckoutSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertEquals(
            desiredCheckoutSHA,
            actualCheckoutSHA,
            "should have ensured our desired SHA is checked out")
    }

    @Test
    fun testEnsureCleanGitCheckoutOnDirtyDir() {
        val testCheckoutDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        val cleanGitCheckout = CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testCheckoutDir,
            "GitFixture"
        )
        cleanGitCheckout.prepare()

        val dirtyFileName = "dirty.txt"
        // reach into the checkout we just ensured and dirty it up
        Paths.get(cleanGitCheckout.checkoutDir.toString(), dirtyFileName).toFile().createNewFile()

        val dirtyMessage = "this message should be included in our exception"
        val exception = assertFailsWith<DirtyRepoException>("should error on a dirty git dir") {
            CleanGitCheckout(
                gitTestFixturePath,
                desiredCheckoutSHA,
                testCheckoutDir,
                "GitFixture",
                dirtyMessage
            ).prepare()
        }

        assertTrue(
            exception.message!!.contains(cleanGitCheckout.checkoutDir.absolutePath),
            "Exception message should contain the absolute path of the dirty directory"
        )

        assertTrue(
            exception.message!!.contains(dirtyFileName),
            "Exception message should name any file dirtying up the directory"
        )

        assertTrue(
            exception.message!!.contains(dirtyMessage),
            "Exception message should contain the given additional message"
        )
    }

    @Test
    fun testEnsureCleanGitCheckoutIgnoresDSStore() {
        val testCheckoutDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        val cleanGitCheckout = CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testCheckoutDir,
            "GitFixture"
        )
        cleanGitCheckout.prepare()

        // Create a .DS_Store file in the checkout directory
        Paths.get(cleanGitCheckout.checkoutDir.toString(), ".DS_Store").toFile().createNewFile()

        // This should NOT throw an exception, since .DS_Store should be ignored
        CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testCheckoutDir,
            "GitFixture"
        ).prepare()

        // Verify we're still at the correct SHA
        val repository = Git.open(cleanGitCheckout.checkoutDir).repository
        val actualCheckoutSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertEquals(
            desiredCheckoutSHA,
            actualCheckoutSHA,
            "Should maintain desired behavior even with .DS_Store present"
        )
    }

    @Test
    fun testFetchOnMissingShaSucceeds() {
        val sourceRepo = createTempGitRepoWithCommit("initial.txt", "initial content")
        val initialSHA = headSHA(sourceRepo)
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())

        // first checkout at the initial SHA, populating the local clone
        CleanGitCheckout(
            sourceRepo.toURI().toString(),
            initialSHA,
            testDestinationDir,
            "GitFixture"
        ).prepare()

        // now advance the source repo with a new commit; the existing checkout has no idea this exists
        val newSHA = addCommit(sourceRepo, "added.txt", "added content")
        assertNotEquals(initialSHA, newSHA)

        // request the new SHA -- prepare must fetch and resolve it
        CleanGitCheckout(
            sourceRepo.toURI().toString(),
            newSHA,
            testDestinationDir,
            "GitFixture"
        ).prepare()

        val actualSHA = headSHA(testDestinationDir.resolve("GitFixture").toFile())
        assertEquals(newSHA, actualSHA, "should have fetched and checked out the newly-added SHA")
    }

    @Test
    fun testFetchUnresolvableShaThrowsTypedException() {
        val sourceRepo = createTempGitRepoWithCommit("initial.txt", "initial content")
        val initialSHA = headSHA(sourceRepo)
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())

        CleanGitCheckout(
            sourceRepo.toURI().toString(),
            initialSHA,
            testDestinationDir,
            "GitFixture"
        ).prepare()

        // a plausibly-shaped but non-existent SHA -- fetch will run but resolve nothing new
        val bogusSHA = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        val checkoutDir = testDestinationDir.resolve("GitFixture").toFile()
        val exception = assertFailsWith<ShaNotResolvableException> {
            CleanGitCheckout(
                sourceRepo.toURI().toString(),
                bogusSHA,
                testDestinationDir,
                "GitFixture"
            ).prepare()
        }

        assertTrue(exception.message!!.contains(bogusSHA), "Message should include the unresolvable SHA")
        assertTrue(exception.message!!.contains(sourceRepo.toURI().toString()), "Message should include the repo URI")
        assertTrue(exception.message!!.contains(checkoutDir.absolutePath), "Message should include the checkout dir")
        assertTrue(
            exception.message!!.contains("re-cloned on the next build"),
            "Message should suggest deleting the checkout dir so it is re-cloned"
        )
    }

    @Test
    fun testTransientCloneFailureIsRetried() {
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        // fail the first clone attempt, then succeed; verifies the retry kicks in
        val checkout = FlakyCloneCheckout(
            failuresBeforeSuccess = 1,
            repoUri = gitTestFixturePath,
            checkoutSHA = desiredCheckoutSHA,
            cloneParentDir = testDestinationDir,
            cloneName = "GitFixture"
        )
        checkout.prepare()

        assertEquals(2, checkout.cloneAttempts, "should have retried clone once after the first failure")
        val actualSHA = headSHA(checkout.checkoutDir)
        assertEquals(desiredCheckoutSHA, actualSHA, "retry should have eventually checked out the desired SHA")
    }

    @Test
    fun testPersistentCloneFailureThrowsTypedException() {
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        // fail every attempt -- should give up after 3 and throw the typed exception
        val checkout = FlakyCloneCheckout(
            failuresBeforeSuccess = Int.MAX_VALUE,
            repoUri = gitTestFixturePath,
            checkoutSHA = desiredCheckoutSHA,
            cloneParentDir = testDestinationDir,
            cloneName = "GitFixture"
        )

        val exception = assertFailsWith<NetworkOperationFailedException> { checkout.prepare() }
        assertEquals(3, checkout.cloneAttempts, "should have tried exactly 3 times before giving up")
        assertTrue(
            exception.message!!.contains(gitTestFixturePath),
            "Message should include the repo URI"
        )
        assertNotNull(exception.cause, "Should preserve the underlying TransportException as the cause")
    }

    @Test
    fun testTransientFetchFailureIsRetried() {
        val sourceRepo = createTempGitRepoWithCommit("initial.txt", "initial content")
        val initialSHA = headSHA(sourceRepo)
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())

        // populate the local clone at the initial SHA using a non-flaky checkout
        CleanGitCheckout(
            sourceRepo.toURI().toString(),
            initialSHA,
            testDestinationDir,
            "GitFixture"
        ).prepare()

        // advance the source repo so the new SHA must be fetched
        val newSHA = addCommit(sourceRepo, "added.txt", "added content")
        assertNotEquals(initialSHA, newSHA)

        // fail the first fetch attempt, then succeed; verifies the retry kicks in
        val checkout = FlakyFetchCheckout(
            failuresBeforeSuccess = 1,
            repoUri = sourceRepo.toURI().toString(),
            checkoutSHA = newSHA,
            cloneParentDir = testDestinationDir,
            cloneName = "GitFixture"
        )
        checkout.prepare()

        assertEquals(2, checkout.fetchAttempts, "should have retried fetch once after the first failure")
        val actualSHA = headSHA(checkout.checkoutDir)
        assertEquals(newSHA, actualSHA, "retry should have eventually fetched and checked out the new SHA")
    }

    @Test
    fun testPersistentFetchFailureThrowsTypedException() {
        val sourceRepo = createTempGitRepoWithCommit("initial.txt", "initial content")
        val initialSHA = headSHA(sourceRepo)
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())

        CleanGitCheckout(
            sourceRepo.toURI().toString(),
            initialSHA,
            testDestinationDir,
            "GitFixture"
        ).prepare()

        val newSHA = addCommit(sourceRepo, "added.txt", "added content")
        assertNotEquals(initialSHA, newSHA)

        // fail every fetch attempt -- should give up after 3 and throw the typed exception
        val checkout = FlakyFetchCheckout(
            failuresBeforeSuccess = Int.MAX_VALUE,
            repoUri = sourceRepo.toURI().toString(),
            checkoutSHA = newSHA,
            cloneParentDir = testDestinationDir,
            cloneName = "GitFixture"
        )

        val exception = assertFailsWith<NetworkOperationFailedException> { checkout.prepare() }
        assertEquals(3, checkout.fetchAttempts, "should have tried exactly 3 times before giving up")
        assertTrue(
            exception.message!!.contains(sourceRepo.toURI().toString()),
            "Message should include the repo URI"
        )
        assertNotNull(exception.cause, "Should preserve the underlying TransportException as the cause")
    }

    /**
     * Test double: wraps [CleanGitCheckout] and throws a [TransportException] from `cloneRepository`
     * the first [failuresBeforeSuccess] times it's called, then delegates to the real implementation.
     */
    private class FlakyCloneCheckout(
        private val failuresBeforeSuccess: Int,
        repoUri: String,
        checkoutSHA: String,
        cloneParentDir: Path,
        cloneName: String,
    ) : CleanGitCheckout(repoUri, checkoutSHA, cloneParentDir, cloneName) {
        var cloneAttempts = 0
            private set

        override fun cloneRepository(uri: String, dir: File) {
            cloneAttempts++
            if (cloneAttempts <= failuresBeforeSuccess) {
                throw TransportException("simulated transient clone failure #$cloneAttempts")
            }
            super.cloneRepository(uri, dir)
        }
    }

    /**
     * Test double: wraps [CleanGitCheckout] and throws a [TransportException] from `fetchUpdates`
     * the first [failuresBeforeSuccess] times it's called, then delegates to the real implementation.
     */
    private class FlakyFetchCheckout(
        private val failuresBeforeSuccess: Int,
        repoUri: String,
        checkoutSHA: String,
        cloneParentDir: Path,
        cloneName: String,
    ) : CleanGitCheckout(repoUri, checkoutSHA, cloneParentDir, cloneName) {
        var fetchAttempts = 0
            private set

        override fun fetchUpdates(git: Git) {
            fetchAttempts++
            if (fetchAttempts <= failuresBeforeSuccess) {
                throw TransportException("simulated transient fetch failure #$fetchAttempts")
            }
            super.fetchUpdates(git)
        }
    }

    /**
     * Creates a temp directory, initializes a git repo, and writes [filename] with [contents] as
     * the initial commit.  Returns the repo directory.
     */
    private fun createTempGitRepoWithCommit(filename: String, contents: String): File {
        val repoDir = createTempDirectory("CleanGitCheckoutTestSource").toFile()
        Git.init().setDirectory(repoDir).call().use { git ->
            File(repoDir, filename).writeText(contents)
            git.add().addFilepattern(".").call()
            git.commit().setMessage("initial").setSign(false).call()
        }
        return repoDir
    }

    /**
     * Adds a new file with the given [filename] and [contents] to [repoDir] and commits it,
     * returning the new HEAD SHA.
     */
    private fun addCommit(repoDir: File, filename: String, contents: String): String {
        Git.open(repoDir).use { git ->
            File(repoDir, filename).writeText(contents)
            git.add().addFilepattern(".").call()
            val commit = git.commit().setMessage("add $filename").setSign(false).call()
            return commit.name
        }
    }

    private fun headSHA(repoDir: File): String {
        return Git.open(repoDir).use { git ->
            git.repository.refDatabase.firstExactRef("HEAD").objectId.name
        }
    }
}
