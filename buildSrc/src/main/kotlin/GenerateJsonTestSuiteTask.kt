import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.kson.jsonsuite.JSON_TEST_SUITE_CLONE_NAME
import org.kson.jsonsuite.JsonSuiteGitCheckout
import org.kson.jsonsuite.JsonTestSuiteGenerator
import org.kson.jsonsuite.SCHEMA_TEST_SUITE_CLONE_NAME
import org.kson.jsonsuite.SchemaSuiteGitCheckout
import java.io.File

/**
 * The Git SHAs in [JSONTestSuite](https://github.com/nst/JSONTestSuite) and [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)
 * that we currently test against.
 *
 * These can be updated if/when we want to pull in newer tests from those projects.
 */
const val jsonTestSuiteSHA = "984defc2deaa653cb73cd29f4144a720ec9efe7c"
const val schemaTestSuiteSHA = "9fc880bfb6d8ccd093bc82431f17d13681ffae8e"

private const val generatedTestClassPackage = "org.kson.parser.json.generated"

/**
 * This task exposes [JsonTestSuiteGenerator] to our Gradle build, ensuring the task's
 * [inputs and outputs](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:task_inputs_outputs)
 * are properly defined so that we support incremental builds (and so that, for instance, the task re-runs
 * if/when the test at [getGeneratedClassDirectory] is deleted)
 */
open class GenerateJsonTestSuiteTask : DefaultTask() {
    private val projectRoot = project.projectDir.toPath()
    private val destinationDir = projectRoot.resolve("buildSrc").resolve("support/jsonsuite")
    private val sourceRoot = projectRoot.resolve("src/commonTest/kotlin/")

    // Mirror the checkout dirs without constructing the checkouts themselves (which clones over the network at construction).
    private val jsonSuiteCheckoutDir = destinationDir.resolve(JSON_TEST_SUITE_CLONE_NAME).toFile()
    private val schemaSuiteCheckoutDir = destinationDir.resolve(SCHEMA_TEST_SUITE_CLONE_NAME).toFile()
    private val generatedClassDir = sourceRoot.resolve(generatedTestClassPackage.replace('.', '/')).toFile()

    init {
        // ensure we're out of date when/if the repo of test source files is deleted
        outputs.upToDateWhen {
            jsonSuiteCheckoutDir.exists() && schemaSuiteCheckoutDir.exists()
        }
    }

    @OutputDirectory
    fun getGeneratedClassDirectory(): File {
        return generatedClassDir
    }

    @TaskAction
    fun generate() {
        JsonTestSuiteGenerator(
            JsonSuiteGitCheckout(jsonTestSuiteSHA, destinationDir),
            SchemaSuiteGitCheckout(schemaTestSuiteSHA, destinationDir),
            projectRoot,
            sourceRoot,
            generatedTestClassPackage
        ).generate()
    }

    @Internal
    override fun getDescription(): String? {
        return "Generates the JSON Test files in $generatedClassDir"
    }
}
