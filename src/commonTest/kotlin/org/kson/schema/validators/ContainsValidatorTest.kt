package org.kson.schema.validators

import org.kson.parser.messages.MessageType.SCHEMA_CONTAINS_TOO_FEW
import org.kson.parser.messages.MessageType.SCHEMA_CONTAINS_TOO_MANY
import org.kson.parser.messages.MessageType.SCHEMA_CONTAINS_VALIDATION_FAILED
import org.kson.schema.JsonSchemaTest
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import kotlin.test.Test

class ContainsValidatorTest : JsonSchemaTest {
    private val partial = SourceContext(mode = ValidationMode.PARTIAL)

    private fun stringsSchema(bounds: String) = $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "contains": { "type": "string" },
          $$bounds
        }
    """

    @Test
    fun defaultMinimumKeepsTheContainsMessage() {
        assertKsonSchemaErrors("[1, 2]", stringsSchema(""""minContains": 1"""), listOf(SCHEMA_CONTAINS_VALIDATION_FAILED))
    }

    @Test
    fun tooManyMatchesReportsTheMaximum() {
        assertKsonSchemaErrors("[a, b, 1]", stringsSchema(""""maxContains": 1"""), listOf(SCHEMA_CONTAINS_TOO_MANY))
    }

    @Test
    fun partialValidationSkipsTooFewMatches() {
        assertKsonSchemaErrors("[a, 1]", stringsSchema(""""minContains": 2"""), listOf(SCHEMA_CONTAINS_TOO_FEW))
        assertKsonEnforcesSchema("[a, 1]", stringsSchema(""""minContains": 2"""), true, sourceContext = partial)
    }

    @Test
    fun partialValidationReportsTooManyMatches() {
        assertKsonSchemaErrors(
            "[a, b]",
            stringsSchema(""""maxContains": 1"""),
            listOf(SCHEMA_CONTAINS_TOO_MANY),
            sourceContext = partial
        )
    }
}
