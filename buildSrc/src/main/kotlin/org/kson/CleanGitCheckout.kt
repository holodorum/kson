package org.kson

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import java.io.File
import java.nio.file.Path

class NoRepoException(msg: String) : RuntimeException(msg)
class DirtyRepoException(msg: String) : RuntimeException(msg)
class ShaNotResolvableException(msg: String) : RuntimeException(msg)
class NetworkOperationFailedException(msg: String, cause: Throwable) : RuntimeException(msg, cause)

/** Number of attempts allowed for network operations (clone / fetch) before giving up. */
private const val NETWORK_ATTEMPTS = 3

/** Backoff between network retry attempts. */
private const val NETWORK_RETRY_BACKOFF_MS = 500L

/**
 * Formats a human-readable report of the dirty entries in a JGit [Status].
 *
 * @param untracked optionally provide a (possibly filtered) set of untracked file paths to include
 */
internal fun formatGitStatusReport(status: Status, untracked: Set<String> = emptySet()): String = buildString {
    if (status.added.isNotEmpty()) appendLine("  Added: ${status.added}")
    if (status.changed.isNotEmpty()) appendLine("  Modified (staged): ${status.changed}")
    if (status.removed.isNotEmpty()) appendLine("  Removed: ${status.removed}")
    if (status.modified.isNotEmpty()) appendLine("  Modified (unstaged): ${status.modified}")
    if (status.missing.isNotEmpty()) appendLine("  Missing: ${status.missing}")
    if (untracked.isNotEmpty()) appendLine("  Untracked: $untracked")
    if (status.conflicting.isNotEmpty()) appendLine("  Conflicting: ${status.conflicting}")
}

/**
 * Ensures there is a clean git checkout of [repoUri] in [cloneParentDir] at SHA [checkoutSHA] when
 * [prepare] is called.  Construction is side-effect free beyond computing [checkoutDir];
 * callers must invoke [prepare] explicitly to perform any network or filesystem work.
 *
 * @param repoUri the URI of the repo to clone.  May be any git URI that [org.eclipse.jgit.transport.URIish.URIish(java.lang.String)]
 *                 can parse, including `https://` URIs and local file paths
 * @param checkoutSHA the SHA of the desired clean checkout of the repo found at [repoUri]
 * @param cloneParentDir the directory to place our cloned [repoUri] into
 * @param cloneName the name of the directory in [cloneParentDir] to clone [repoUri] into
 * @param dirtyMessage optionally provide a short sentence explaining why this directory must be clean.  Will be added
 *                     to the [DirtyRepoException] message thrown on a dirty repo
 */
open class CleanGitCheckout(private val repoUri: String,
                            private val checkoutSHA: String,
                            cloneParentDir: Path,
                            cloneName: String,
                            private val dirtyMessage: String? = null
    ) {
    val checkoutDir: File = File(cloneParentDir.toFile(), cloneName)

    /**
     * Ensures the checkout exists, is clean, and points at [checkoutSHA], returning a
     * [PreparedCheckout] witness that callers can hand to code that reads repo contents.
     * Clones the repo if [checkoutDir] is empty; if the SHA cannot be resolved locally (e.g. the
     * SHA constant was bumped after a previous checkout), fetches updates from the remote and
     * retries.  Safe to call repeatedly: idempotent against an already-correct checkout.
     *
     * @throws NoRepoException if [checkoutDir] exists but is not a git repo
     * @throws DirtyRepoException if [checkoutDir] has uncommitted changes or unexpected untracked files
     * @throws ShaNotResolvableException if [checkoutSHA] cannot be resolved even after fetching
     * @throws NetworkOperationFailedException if clone/fetch fails after [NETWORK_ATTEMPTS] attempts
     */
    fun prepare(): PreparedCheckout {
        if (!checkoutDir.exists()) {
            checkoutDir.mkdirs()
            withNetworkRetry("clone $repoUri into $checkoutDir") {
                cloneRepository(repoUri, checkoutDir)
            }
        } else if (!File(checkoutDir, ".git").exists()) {
            throw NoRepoException(
                "ERROR: cannot create a ${CleanGitCheckout::class.simpleName} because `$checkoutDir` " +
                        "does not appear to be a git repo")
        }

        Git.open(checkoutDir).use { git ->
            val status = git.status().call()

            /**
             * We are dirty in the presence of any uncommitted changes or any untracked files other than the ones enumerated
             * in [acceptableUntrackedFiles]
             */
            val isDirty =
                status.uncommittedChanges.isNotEmpty() || status.untracked.minus(acceptableUntrackedFiles).isNotEmpty()

            if(isDirty) {
                val statusReport = formatGitStatusReport(status, status.untracked.minus(acceptableUntrackedFiles))

                val customDirtyMessage = if (dirtyMessage != null) { dirtyMessage + "\n" } else { "" }

                /**
                 * Error if we're not clean other than [acceptableUntrackedFiles], since we cannot create a [CleanGitCheckout],
                 * emphasis on _Clean_.  We also can't automatically blow away any changes since someone may have made
                 * those changes on purpose for reasons we're not guessing, and quietly nuking those changes as a
                 * side-effect of the trying to verify a clean checkout could do them a real disservice
                 */
                throw DirtyRepoException(
                    "ERROR: Dirty git status in `$checkoutDir`.\n$customDirtyMessage" +
                    "Suggested fixes:\n" +
                            "- either clean up the git status in `$checkoutDir`\n" +
                            "- or, delete `$checkoutDir`\n" +
                            "  so it is re-cloned on the next build" +
                            "\n\n# Dirty Git Status in `$checkoutDir`:\n$statusReport")
            }
        }

        checkoutCommit(checkoutDir, checkoutSHA)

        return PreparedCheckout(checkoutDir)
    }

    /**
     * Clone the given [uri] into [dir].
     *
     * @param uri will be passed to [org.eclipse.jgit.api.CloneCommand.setURI] to be parsed as a [org.eclipse.jgit.transport.URIish])
     * @param dir the directory to clone the repo at [uri] into
     */
    protected open fun cloneRepository(uri: String, dir: File) {
        Git.cloneRepository()
            .setURI(uri)
            .setDirectory(dir)
            .call()
    }

    /**
     * Fetches updates from `origin` into the already-open [git].
     */
    protected open fun fetchUpdates(git: Git) {
        git.fetch().call()
    }

    /**
     * Checks out the given [commit] of the repo found in [dir].  If the SHA isn't present in the
     * local object database (e.g. the SHA constant was bumped since the existing clone), fetches
     * from origin and retries the lookup; if still unresolvable, throws [ShaNotResolvableException]
     * with an actionable message.
     */
    private fun checkoutCommit(dir: File, commit: String) {
        Git.open(dir).use { git ->
            if (!hasCommitLocally(git.repository, commit)) {
                // SHA missing locally — likely a bumped pin; fetch and retry.
                withNetworkRetry("fetch updates for $repoUri in $dir") {
                    fetchUpdates(git)
                }
                if (!hasCommitLocally(git.repository, commit)) {
                    throw ShaNotResolvableException(
                        "ERROR: SHA `$commit` from `$repoUri` is still not resolvable after fetching.\n" +
                                "Checkout dir: `$dir`\n" +
                                "Suggested fix: delete `$dir` so it is re-cloned on the next build."
                    )
                }
            }
            git.checkout().setName(commit).call()
        }
    }

    /** Probe whether [commit] is a SHA already present in [repo]'s object database. */
    private fun hasCommitLocally(repo: Repository, commit: String): Boolean {
        val objectId = try {
            ObjectId.fromString(commit)
        } catch (e: IllegalArgumentException) {
            // not a SHA-shaped string; let checkout handle it (e.g. a branch name)
            return true
        }
        repo.newObjectReader().use { reader ->
            return reader.has(objectId)
        }
    }

    /**
     * Runs [block] with up to [NETWORK_ATTEMPTS] attempts, backing off [NETWORK_RETRY_BACKOFF_MS]
     * between tries.  Retries only [TransportException]s (transient network failures); anything
     * else is propagated immediately.  If all attempts fail, throws
     * [NetworkOperationFailedException] with the last underlying error.
     *
     * @param description human-readable operation name used in error messages
     */
    private fun <T> withNetworkRetry(description: String, block: () -> T): T {
        var lastError: TransportException? = null
        repeat(NETWORK_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (e: TransportException) {
                lastError = e
                if (attempt < NETWORK_ATTEMPTS - 1) {
                    Thread.sleep(NETWORK_RETRY_BACKOFF_MS)
                }
            }
        }
        throw NetworkOperationFailedException(
            "ERROR: Failed to $description after $NETWORK_ATTEMPTS attempts. " +
                    "Last error: ${lastError?.message}",
            lastError!!
        )
    }
}

/**
 * We still consider a directory clean if it contains any of these untracked files (we do not control the
 * underlying git repos .gitignore, else we would deal with these there)
 */
private val acceptableUntrackedFiles = setOf(".DS_Store", "Thumbs.db")

/**
 * Witness that the underlying repo has been cloned/fetched as needed, verified clean, and
 * checked out at the requested SHA.  Only constructible via [CleanGitCheckout.prepare].
 *
 * Functions that read repo contents should accept [PreparedCheckout] rather than a bare
 * [File] path -- the type makes it impossible to forget preparation.
 */
class PreparedCheckout internal constructor(val checkoutDir: File)
