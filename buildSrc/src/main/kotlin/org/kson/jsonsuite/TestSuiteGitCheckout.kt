package org.kson.jsonsuite

import org.kson.CleanGitCheckout
import java.nio.file.Path

const val JSON_TEST_SUITE_CLONE_NAME = "JSONTestSuite"
const val SCHEMA_TEST_SUITE_CLONE_NAME = "JSON-Schema-Test-Suite"

class JsonSuiteGitCheckout(jsonTestSuiteSHA: String, destinationDir: Path)
    : CleanGitCheckout("https://github.com/nst/JSONTestSuite.git", jsonTestSuiteSHA, destinationDir, JSON_TEST_SUITE_CLONE_NAME, dirtyMessage)
class SchemaSuiteGitCheckout(schemaTestSuiteSHA: String, destinationDir: Path)
    : CleanGitCheckout("https://github.com/json-schema-org/JSON-Schema-Test-Suite.git", schemaTestSuiteSHA, destinationDir, SCHEMA_TEST_SUITE_CLONE_NAME, dirtyMessage)

/**
 * The rationale for why these [CleanGitCheckout]s must be clean
 */
private const val dirtyMessage = "This needs to be clean since we generate files from this repo.\n"
