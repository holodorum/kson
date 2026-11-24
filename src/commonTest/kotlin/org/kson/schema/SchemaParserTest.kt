package org.kson.schema

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.LoggedMessage
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageSeverity
import org.kson.parser.messages.MessageType
import org.kson.uri.UriReference
import kotlin.test.*
import org.kson.KsonCore

class SchemaParserTest : JsonSchemaTest {

    /**
     * Assertion helper for testing that [source] is partially parsed by the schema parser
     * (produces non-null jsonSchema) but has validation errors listed in [expectedMessageTypes]
     */
    private fun assertSchemaHasValidationErrors(
        source: String,
        expectedMessageTypes: List<MessageType>
    ) {
        val result = KsonCore.parseSchema(source)
        assertEquals(
            expectedMessageTypes,
            result.messages.map { it.message.type },
            "Should have the expected validation errors."
        )
        assertTrue(result.messages.isNotEmpty(), "Should have error messages for validation errors")
    }

    /**
     * Assertion helper for testing that [source] produces a [JsonObjectSchema] with no errors
     */
    private fun assertValidObjectSchema(source: String): JsonObjectSchema {
        val schema = assertValidSchema(source)
        assertTrue(schema is JsonObjectSchema, "Should produce a JsonObjectSchema")
        return schema
    }

    @Test
    fun testParseBooleanSchema() {
        val trueSchema = assertValidSchema("true")
        assertTrue(trueSchema is JsonBooleanSchema)
        assertTrue(trueSchema.valid)

        val falseSchema = assertValidSchema("false")
        assertTrue(falseSchema is JsonBooleanSchema)
        assertFalse(falseSchema.valid)
    }

    @Test
    fun testParseEmptySchema() {
        assertSchemaHasValidationErrors("", listOf(MessageType.SCHEMA_EMPTY_SCHEMA))
    }

    @Test
    fun testParseMinimalObjectSchema() {
        assertValidObjectSchema("{}")
    }

    @Test
    fun testParseSchemaWithTitle() {
        val schema = assertValidObjectSchema("title: 'Test Schema'")
        assertEquals("Test Schema", schema.title)
    }

    @Test
    fun testParseSchemaWithDescription() {
        val schema = assertValidObjectSchema("description: 'A test schema'")
        assertEquals("A test schema", schema.description)
    }

    @Test
    fun testParseSchemaWithEmbedBlockDescription() {
        val schema = assertValidObjectSchema(
            """
            description: %markdown
              A **rich** description of this schema.
              %%
            type: object
            """.trimIndent()
        )
        assertEquals("A **rich** description of this schema.", schema.description)
    }

    @Test
    fun testParseSchemaWithMultilineEmbedBlockDescription() {
        val schema = assertValidObjectSchema(
            """
            |description: %markdown
            |  A **person** object representing a user in the system.
            |  
            |  Must include at least a `name` and `email`.
            |  %%
            |type: object
            """.trimMargin()
        )
        assertEquals(
            "A **person** object representing a user in the system.\n\nMust include at least a `name` and `email`.",
            schema.description
        )
    }

    @Test
    fun testParseSchemaWithTaglessEmbedBlockDescription() {
        val schema = assertValidObjectSchema(
            """
            description: %
              A plain embed block description.
              %%
            type: object
            """.trimIndent()
        )
        assertEquals("A plain embed block description.", schema.description)
    }

    @Test
    fun testParseSchemaWithInvalidDescriptionType() {
        assertSchemaHasValidationErrors(
            "description: 123",
            listOf(MessageType.SCHEMA_STRING_OR_EMBED_BLOCK_REQUIRED)
        )
    }

    @Test
    fun testParseSchemaWithComment() {
        val schema = assertValidObjectSchema($$"""'$comment': 'A comment on this schema'""")
        assertEquals("A comment on this schema", schema.comment)
    }

    @Test
    fun testParseSchemaWithEmbedBlockComment() {
        val schema = assertValidObjectSchema(
            $$"""
            |'$comment': %markdown
            |  A **rich** comment on this schema.
            |  %%
            |type: object
            """.trimMargin()
        )
        assertEquals("A **rich** comment on this schema.", schema.comment)
    }

    @Test
    fun testParseSchemaWithInvalidCommentType() {
        assertSchemaHasValidationErrors(
            $$"""'$comment': 123""",
            listOf(MessageType.SCHEMA_STRING_OR_EMBED_BLOCK_REQUIRED)
        )
    }

    @Test
    fun testParseSchemaWithTypeString() {
        assertValidObjectSchema("type: string")
    }

    @Test
    fun testParseSchemaWithTypeArray() {
        assertValidObjectSchema("""
            type:
              - string
              - number
        """.trimIndent())
    }

    @Test
    fun testParseSchemaWithInvalidTypeArrayEntry() {
        assertSchemaHasValidationErrors(
            """
                type:
                  - string
                  - 123
            """.trimIndent(),
            listOf(MessageType.SCHEMA_TYPE_ARRAY_ENTRY_ERROR)
        )
    }

    @Test
    fun testParseSchemaWithNumericValidators() {
        assertValidObjectSchema("""
            minimum: 0
            maximum: 100
            multipleOf: 5
            exclusiveMinimum: -1
            exclusiveMaximum: 101
        """.trimIndent())
    }

    @Test
    fun testParseSchemaWithInvalidMinimum() {
        assertSchemaHasValidationErrors(
            "minimum: 'not a number'",
            listOf(MessageType.SCHEMA_NUMBER_REQUIRED)
        )
    }

    @Test
    fun testParseSchemaWithStringValidators() {
        assertValidObjectSchema("""
            minLength: 1
            maxLength: 10
            pattern: '^[a-z]+$'
        """.trimIndent())
    }

    @Test
    fun testParseSchemaWithArrayValidators() {
        assertValidObjectSchema("""
            minItems: 1
            maxItems: 5
            uniqueItems: true
            items:
              type: string
        """.trimIndent())
    }

    @Test
    fun testParseSchemaWithContainsBounds() {
        assertValidObjectSchema($$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "contains": {"type": "string"},
                "minContains": 2,
                "maxContains": 3
            }
        """)
    }

    @Test
    fun testParseSchemaWithNonIntegerContainsBounds() {
        assertSchemaHasValidationErrors(
            $$"""
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "contains": {"type": "string"},
                    "minContains": "two",
                    "maxContains": true
                }
            """,
            listOf(MessageType.SCHEMA_INTEGER_REQUIRED, MessageType.SCHEMA_INTEGER_REQUIRED)
        )
    }

    @Test
    fun testParseSchemaWithContainsBoundWithoutContains() {
        // without `contains` the bound validates nothing, but a malformed one is still a malformed schema
        assertSchemaHasValidationErrors(
            $$"""
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "minContains": "two"
                }
            """,
            listOf(MessageType.SCHEMA_INTEGER_REQUIRED)
        )
    }

    @Test
    fun testParseSchemaWithContainsBoundsIgnoredByDraft7() {
        // Draft 7 has no `minContains` or `maxContains`: it neither reports the malformed bound nor applies the other
        assertValidObjectSchema($$"""
            {
                "$schema": "http://json-schema.org/draft-07/schema#",
                "contains": {"type": "string"},
                "minContains": "two"
            }
        """)
        assertKsonEnforcesSchema(
            "[a, b]",
            $$"""
                {
                    "$schema": "http://json-schema.org/draft-07/schema#",
                    "contains": {"type": "string"},
                    "maxContains": 1
                }
            """,
            true
        )
    }

    @Test
    fun testParseSchemaWithObjectValidators() {
        assertValidObjectSchema("""
            minProperties: 1
            maxProperties: 10
            required:
              - name
              - age
            properties:
              name:
                type: string
                .
              age:
                type: number
        """.trimIndent())
    }

    @Test
    fun testParseSchemaWithInvalidRequiredArray() {
        assertSchemaHasValidationErrors(
            """
                required:
                  - 123
                  - name
            """.trimIndent(),
            listOf(MessageType.SCHEMA_STRING_ARRAY_ENTRY_ERROR)
        )
    }

    @Test
    fun testParseSchemaWithIfThenElse() {
        assertValidObjectSchema("""
            if:
              type: string
              .
            then:
              minLength: 1
              .
            else:
              type: number
        """.trimIndent())
    }

    @Test
    fun testParseSchemaWithDependencies() {
        assertValidObjectSchema("""
            dependencies:
              name:
                - age
              address:
                type: object
        """.trimIndent())
    }

    @Test
    fun testParseSchemaWithDependentRequired() {
        assertValidObjectSchema($$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "dependentRequired": {
                    "name": ["age"]
                }
            }
        """)
    }

    @Test
    fun testParseSchemaWithNonObjectDependentRequired() {
        assertSchemaHasValidationErrors(
            $$"""
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "dependentRequired": ["age"]
                }
            """,
            listOf(MessageType.SCHEMA_OBJECT_REQUIRED)
        )
    }

    @Test
    fun testParseSchemaWithNonArrayDependentRequired() {
        assertSchemaHasValidationErrors(
            $$"""
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "dependentRequired": {
                        "name": "age"
                    }
                }
            """,
            listOf(MessageType.SCHEMA_ARRAY_REQUIRED)
        )
    }

    @Test
    fun testParseSchemaWithInvalidDependentRequiredEntry() {
        assertSchemaHasValidationErrors(
            $$"""
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "dependentRequired": {
                        "name": ["age", 123]
                    }
                }
            """,
            listOf(MessageType.SCHEMA_STRING_ARRAY_ENTRY_ERROR)
        )
    }

    @Test
    fun testParseSchemaWithDependentRequiredIgnoredByDraft7() {
        // Draft 7 has no `dependentRequired`, so `name` without `age` is still valid
        assertKsonEnforcesSchema(
            "name: x",
            $$"""
                {
                    "$schema": "http://json-schema.org/draft-07/schema#",
                    "dependentRequired": {
                        "name": ["age"]
                    }
                }
            """,
            true
        )
    }

    @Test
    fun testParseSchemaWithDependentSchemas() {
        assertValidObjectSchema($$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "dependentSchemas": {
                    "name": {"required": ["age"]},
                    "nickname": true,
                    "alias": false
                }
            }
        """)
    }

    @Test
    fun testParseSchemaWithNonObjectDependentSchemas() {
        assertSchemaHasValidationErrors(
            $$"""
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "dependentSchemas": ["age"]
                }
            """,
            listOf(MessageType.SCHEMA_OBJECT_REQUIRED)
        )
    }

    @Test
    fun testParseSchemaWithNonSchemaDependentSchema() {
        assertSchemaHasValidationErrors(
            $$"""
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "dependentSchemas": {
                        "name": 42
                    }
                }
            """,
            listOf(MessageType.SCHEMA_OBJECT_OR_BOOLEAN)
        )
    }

    @Test
    fun testParseSchemaWithDependentSchemasIgnoredByDraft7() {
        // Draft 7 has no `dependentSchemas`, so the `false` dependent schema doesn't reject `name`
        assertKsonEnforcesSchema(
            "name: x",
            $$"""
                {
                    "$schema": "http://json-schema.org/draft-07/schema#",
                    "dependentSchemas": {
                        "name": false
                    }
                }
            """,
            true
        )
    }

    @Test
    fun testParseInvalidSchemaType() {
        assertSchemaHasValidationErrors(
            "123",
            listOf(MessageType.SCHEMA_OBJECT_OR_BOOLEAN)
        )
    }

    @Test
    fun testParseSchemaWithIntegerLengths() {
        assertValidObjectSchema("""
            minLength: 1.0
            maxLength: 10.0
            minItems: 2.0
            maxItems: 5.0
        """.trimIndent())
    }

    @Test
    fun testParseSchemaWithInvalidIntegerLengths() {
        assertSchemaHasValidationErrors(
            """
            minLength: 1.5
            maxLength: 'not a number'
            """.trimIndent(),
            listOf(MessageType.SCHEMA_INTEGER_REQUIRED)
        )
    }

    @Test
    fun testRefWithAllowedProperties() {
        // Test that $ref with only title and description does not generate errors
        assertValidObjectSchema(
            $$"""
            definitions:
              someType:
                type: string
                .
              .
            properties:
              myRef:
                '$ref': '#/definitions/someType'
                title: 'Reference with title'
                description: 'This is allowed alongside $ref'
            """.trimIndent()
        )
    }

    @Test
    fun testRefToMalformedDefinitionReportsError() {
        // A $ref pointing to a definition that is not a valid schema (a number,
        // where a schema object or boolean is required) must report an error.
        val schema = $$"""
            definitions:
              broken: 42
              .
            '$ref': '#/definitions/broken'
        """.trimIndent()
        val ksonSource = """
            key: value
        """
        assertKsonSchemaErrors(
            ksonSource,
            schema,
            listOf(MessageType.SCHEMA_OBJECT_OR_BOOLEAN)
        )
    }

    @Test
    fun testMalformedSchemaIsReportedUnderDefinitionsButNotUnderDefs() {
        // Draft 7 has no `$defs`, so values under it are only compiled as schemas when a `$ref` points to them
        assertSchemaHasValidationErrors(
            """
                definitions:
                  broken: 42
            """.trimIndent(),
            listOf(MessageType.SCHEMA_OBJECT_OR_BOOLEAN)
        )
        assertValidObjectSchema($$"""
            '$defs':
              broken: 42
        """.trimIndent())
    }

    @Test
    fun testDefinitionsThatIsNoObjectReportsError() {
        assertSchemaHasValidationErrors("definitions: 42", listOf(MessageType.SCHEMA_OBJECT_REQUIRED))
    }

    @Test
    fun testTypeIsReportedBeforeTheDefinitions() {
        assertSchemaHasValidationErrors(
            """
                type: 3
                definitions:
                  a: 4
            """.trimIndent(),
            listOf(MessageType.SCHEMA_TYPE_TYPE_ERROR, MessageType.SCHEMA_OBJECT_OR_BOOLEAN)
        )
    }

    @Test
    fun testRefWithMalformedPointerReportsError() {
        // `~2` is no JSON Pointer escape: only `~0` and `~1` are
        val result = KsonCore.parseSchema($$"""'$ref': '#/$defs/a~2b'""")
        assertEquals(
            listOf(
                LoggedMessage(
                    Location(Coordinates(0, 9), Coordinates(0, 21), 9, 21),
                    MessageType.JSON_POINTER_INVALID_ESCAPE.create("2")
                )
            ),
            result.messages
        )
        assertEquals(
            "Invalid escape sequence: '~2'. Valid escape sequences are '~0' for '~' and '~1' for '/'",
            result.messages.single().message.toString()
        )
    }

    @Test
    fun testRefToNothingReportsError() {
        val result = KsonCore.parseSchema($$"""'$ref': '#/$defs/missing'""")
        assertEquals(
            listOf(
                LoggedMessage(
                    Location(Coordinates(0, 9), Coordinates(0, 24), 9, 24),
                    MessageType.SCHEMA_REF_RESOLUTION_FAILED.create($$"#/$defs/missing")
                )
            ),
            result.messages
        )
    }

    @Test
    fun testRefToAListItemResolvesOnlyByACanonicalIndex() {
        fun schema(index: String) = $$"""
            items:
              - type: string
              - type: number
                .
            properties:
              a:
                '$ref': '#/items/$$index'
        """.trimIndent()
        assertKsonEnforcesSchema("a: 1", schema("1"), true)
        for (index in listOf("01", "+1", "-0")) {
            val ref = "#/items/$index"
            assertEquals(
                listOf(MessageType.SCHEMA_REF_RESOLUTION_FAILED.create(ref)),
                KsonCore.parseSchema(schema(index)).messages.map { it.message },
                ref
            )
        }
    }

    @Test
    fun testRefToRefWithMalformedPointerReportsError() {
        // `wrapper` is only parsed once validation follows the root `$ref` to it
        val schema = $$"""
            '$ref': '#/$defs/wrapper'
            '$defs':
              wrapper:
                '$ref': '#/$defs/a~2b'
        """.trimIndent()
        assertKsonSchemaErrorAtLocation(
            "key: value",
            schema,
            listOf(MessageType.JSON_POINTER_INVALID_ESCAPE),
            listOf(Location(Coordinates(3, 13), Coordinates(3, 25), 59, 71))
        )
    }

    @Test
    fun testRefCycleReportsError() {
        // the root `$ref` leads to `a`, then to `b`, whose `$ref` leads back to `a`
        val schema = $$"""
            '$ref': '#/definitions/a'
            definitions:
              a: { '$ref': '#/definitions/b' }
              b: { '$ref': '#/definitions/a' }
        """.trimIndent()
        assertKsonSchemaErrorAtLocation(
            "key: value",
            schema,
            listOf(MessageType.SCHEMA_REF_CYCLE),
            listOf(Location(Coordinates(3, 16), Coordinates(3, 31), 90, 105))
        )
    }

    @Test
    fun testRefCycleThroughAnAllOfReportsTheRefThatClosesIt() {
        // the root `$ref` leads to `a`, whose `allOf` applies `a` again to the same value
        val schema = $$"""
            '$ref': '#/definitions/a'
            definitions:
              a:
                minimum: 1
                allOf:
                  - '$ref': '#/definitions/a'
        """.trimIndent()
        assertKsonSchemaErrorAtLocation(
            "0",
            schema,
            listOf(MessageType.SCHEMA_VALUE_TOO_SMALL, MessageType.SCHEMA_REF_CYCLE),
            listOf(
                Location(Coordinates(0, 0), Coordinates(0, 1), 0, 1),
                Location(Coordinates(5, 17), Coordinates(5, 32), 87, 102)
            )
        )
    }

    @Test
    fun testRefCycleThroughAnySubschemaOfTheSameValueReportsError() {
        val cycles = listOf(
            $$"allOf: [{ '$ref': '#' }]",
            $$"anyOf: [{ '$ref': '#' }]",
            $$"oneOf: [{ '$ref': '#' }]",
            $$"if: true, then: { '$ref': '#' }",
            $$"if: false, else: { '$ref': '#' }"
        )
        for (cycle in cycles) {
            assertKsonSchemaErrors("key: value", "{ $cycle }", listOf(MessageType.SCHEMA_REF_CYCLE), cycle)
        }

        val dependencyError = assertKsonSchemaErrors(
            "key: value",
            $$"{ dependencies: { key: { '$ref': '#' } } }",
            listOf(MessageType.SCHEMA_DEPENDENCIES_SCHEMA_ERROR)
        ).single()
        val cycle = MessageType.SCHEMA_REF_CYCLE.create("#").toString()
        assertEquals(MessageType.SCHEMA_DEPENDENCIES_SCHEMA_ERROR.create("key", cycle), dependencyError.message)
    }

    @Test
    fun testRefCycleFailsOnlyTheSubschemaOfANotOrIf() {
        // `not` and `if` keep their subschema's errors to themselves, so the cycle only fails that subschema
        assertKsonEnforcesSchema("key: value", $$"{ not: { '$ref': '#' } }", true)
        assertKsonEnforcesSchema("key: value", $$"{ if: { '$ref': '#' }, else: false }", false)
    }

    @Test
    fun testPointerRefUnderAPlainNameIdResolvesInItsDocument() {
        // a plain-name `$id` names a fragment of the document, so `#/definitions/target` still addresses the
        // document's root
        assertValidObjectSchema(
            $$"""
            properties:
              field:
                '$ref': '#anchored'
                .
              .
            definitions:
              anchored:
                '$id': '#anchored'
                properties:
                  x:
                    '$ref': '#/definitions/target'
                    .
                  .
                .
              target:
                type: number
            """.trimIndent()
        )
    }

    @Test
    fun testRefWithinASchemaWithARelativeIdResolvesInThatSchema() {
        // `a` is read under `other`'s `$id`, so its `#/definitions/b` addresses `other`
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            properties:
              field:
                '$ref': 'sub/other.json#/definitions/a'
                .
              .
            definitions:
              other:
                '$id': 'sub/other.json'
                definitions:
                  a:
                    '$ref': '#/definitions/b'
                    .
                  b:
                    type: string
        """.trimIndent()
        assertKsonEnforcesSchema("field: ok", schema, true)
        assertKsonSchemaErrors("field: 1", schema, listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH))
    }

    @Test
    fun testRefWithinASchemaFoundByAPlainNameIdResolvesInItsDocument() {
        // `anchored` sits in other.json, so its `#/definitions/t` addresses other.json's `t` rather than root.json's
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            properties:
              x:
                '$ref': 'other.json#anchored'
                .
              .
            definitions:
              t: {}
              other:
                '$id': 'other.json'
                definitions:
                  t:
                    type: string
                    .
                  anchored:
                    '$id': '#anchored'
                    allOf:
                      - '$ref': '#/definitions/t'
        """.trimIndent()
        assertKsonEnforcesSchema("x: ok", schema, true)
        assertKsonSchemaErrors("x: 1", schema, listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH))
    }

    @Test
    fun testSchemaFoundByARelativeIdIsReadUnderTheBaseUriItSitsUnder() {
        // `other` sits under root.json, so its `$id` resolves against root.json, not against deep/z.json, which refers
        // to it
        val schema = $$"""
            '$id': 'http://example.com/a/root.json'
            properties:
              x:
                '$ref': 'deep/z.json'
                .
              .
            definitions:
              z:
                '$id': 'deep/z.json'
                allOf:
                  - '$ref': '../sub/other.json'
                    =
                .
              other:
                '$id': 'sub/other.json'
                definitions:
                  t:
                    type: string
                    .
                  .
                allOf:
                  - '$ref': '#/definitions/t'
        """.trimIndent()
        assertKsonEnforcesSchema("x: ok", schema, true)
        assertKsonSchemaErrors("x: 1", schema, listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH))
    }

    @Test
    fun testRefUnderARelativeRootIdResolves() {
        // `x`'s ref and `other`'s `$id` both resolve against the root's `$id`, to sub/other.json
        val schema = $$"""
            '$id': 'sub/root.json'
            properties:
              x:
                '$ref': 'other.json'
                .
              .
            definitions:
              other:
                '$id': 'other.json'
                type: string
        """.trimIndent()
        assertKsonEnforcesSchema("x: ok", schema, true)
        assertKsonSchemaErrors("x: 1", schema, listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH))
    }

    @Test
    fun testRootRefBesideTheRootIdResolvesInTheRootDocument() {
        // `other.json` resolves against the retrieval URI, not the root's `$id`
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            '$ref': '#/definitions/a'
            definitions:
              a:
                '$ref': 'other.json'
        """.trimIndent()
        val externalSchemaSources = mapOf("other.json" to "type: string")
        assertKsonEnforcesSchema("ok", schema, true, externalSchemaSources = externalSchemaSources)
        assertKsonSchemaErrors(
            "1",
            schema,
            listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH),
            externalSchemaSources = externalSchemaSources
        )
    }

    @Test
    fun testRootRefBesideARelativeRootIdNamesTheSchemaUnderTheRetrievalUri() {
        val schema = $$"""
            '$id': 'sub/root.json'
            '$ref': '#/definitions/target'
            definitions:
              target:
                type: string
        """.trimIndent()
        assertKsonEnforcesSchema("ok", schema, true)
        assertKsonSchemaErrors("1", schema, listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH))
    }

    @Test
    fun testPointerRefUnderARootIdWithAnEmptyFragmentResolves() {
        // `http://example.com/root.json#` identifies the same schema as `http://example.com/root.json`
        val schema = $$"""
            '$id': 'http://example.com/root.json#'
            properties:
              x:
                '$ref': '#/definitions/a'
                .
              .
            definitions:
              a:
                type: string
        """.trimIndent()
        assertKsonSchemaErrors("x: 1", schema, listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH))
    }

    @Test
    fun testNonStringIdReportsError() {
        assertSchemaHasValidationErrors($$"""'$id': 42""", listOf(MessageType.SCHEMA_STRING_REQUIRED))
    }

    @Test
    fun testUnrecognizedMetaSchemaUriIsWarnedOfAtItsValue() {
        val result = KsonCore.parseSchema($$"""'$schema': 'https://json-schema.org/draft/2019-09/schema'""")

        val warning = result.messages.single()
        assertEquals(Location(Coordinates(0, 12), Coordinates(0, 56), 12, 56), warning.location)
        assertEquals(MessageType.SCHEMA_UNRECOGNIZED_META_SCHEMA, warning.message.type)
        assertEquals(MessageSeverity.WARNING, warning.message.type.severity)
        assertEquals(
            $$"""Unrecognized "$schema" "https://json-schema.org/draft/2019-09/schema": this schema is read as """ +
                """Draft 7.  Recognized: Draft 7 ("http://json-schema.org/draft-07/schema"), """ +
                """Draft 2020-12 ("https://json-schema.org/draft/2020-12/schema")""",
            warning.message.toString()
        )
        assertNotNull(result.jsonSchema)
    }

    @Test
    fun testSchemaWithAnUnrecognizedMetaSchemaUriIsReadAsDraft7() {
        // Draft 7's `$ref` hides the `maximum` beside it, where Draft 2019-09's would apply it
        val schema = $$"""
            '$schema': 'https://json-schema.org/draft/2019-09/schema'
            '$ref': '#/definitions/number'
            maximum: 1
            definitions:
              number:
                type: number
        """.trimIndent()
        assertKsonEnforcesSchema("7", schema, true)
        assertKsonEnforcesSchema("seven", schema, false)
    }

    @Test
    fun testRecognizedOrAbsentMetaSchemaUriIsNotWarnedOf() {
        val sources = listOf(
            $$"'$schema': 'http://json-schema.org/draft-07/schema#'",
            $$"'$schema': 'http://json-schema.org/draft-07/schema'",
            $$"'$schema': 'https://json-schema.org/draft/2020-12/schema'",
            $$"'$schema': 'https://json-schema.org/draft/2020-12/schema#'",
            "type: object"
        )
        for (source in sources) {
            assertEquals(emptyList(), KsonCore.parseSchema(source).messages, source)
        }
    }

    @Test
    fun testNonStringMetaSchemaUriIsNotWarnedOf() {
        // unlike a non-string `$id`, not an error: that would make a usable schema unusable, and `$schema` only warns
        assertEquals(emptyList(), KsonCore.parseSchema($$"'$schema': 7").messages)
    }

    @Test
    fun testReferencedDocumentWithAnUnrecognizedMetaSchemaUriIsNotWarnedOf() {
        // a message's location names no document, so a warning inside other.json would read as a place in the root
        val root = SchemaDocument(
            assertNotNull(KsonCore.parseToAst($$"'$ref': 'other.json'").ksonValue),
            UriReference.parse("https://example.test/root.json")
        )
        val other = SchemaDocument(
            assertNotNull(KsonCore.parseToAst($$"'$schema': 'https://json-schema.org/draft/2019-09/schema'").ksonValue),
            UriReference.parse("https://example.test/other.json")
        )
        val messageSink = MessageSink()

        assertNotNull(SchemaParser.parseSchemaRoot(IndexedSchemaDocuments(root, listOf(other)), messageSink))
        assertEquals(emptyList(), messageSink.loggedMessages())
    }

    @Test
    fun testInvalidRegexInPatternReportsError() {
        assertSchemaHasValidationErrors(
            "pattern: '*'",
            listOf(MessageType.SCHEMA_INVALID_REGEX)
        )
    }

    @Test
    fun testInvalidRegexInPatternPropertiesReportsError() {
        assertSchemaHasValidationErrors(
            """
                patternProperties:
                  '*':
                    type: string
            """.trimIndent(),
            listOf(MessageType.SCHEMA_INVALID_REGEX)
        )
    }

    @Test
    fun testMalformedNotSubSchemaDoesNotSilentlyAccept() {
        // A "not" with a malformed sub-schema must not silently become permissive.
        // Schema parse errors are already reported; the validator must not be created
        // with a null schema that causes it to skip validation entirely.
        assertSchemaHasValidationErrors(
            "not: 42",
            listOf(MessageType.SCHEMA_OBJECT_OR_BOOLEAN)
        )
    }

    @Test
    fun testMalformedIfSubSchemaDoesNotSilentlyAccept() {
        assertSchemaHasValidationErrors(
            """
                if: 42
                then:
                  type: string
            """.trimIndent(),
            listOf(MessageType.SCHEMA_OBJECT_OR_BOOLEAN)
        )
    }

    @Test
    fun testErrorsInThenAndElseAreReportedUnderAnIfThatDoesNotCompile() {
        assertSchemaHasValidationErrors(
            $$"""
                if:
                  '$ref': '#/missing'
                  .
                then:
                  type: 7
                  .
                else:
                  minimum: low
            """.trimIndent(),
            listOf(
                MessageType.SCHEMA_REF_RESOLUTION_FAILED,
                MessageType.SCHEMA_TYPE_TYPE_ERROR,
                MessageType.SCHEMA_NUMBER_REQUIRED
            )
        )
    }

    @Test
    fun testMalformedPropertyNamesSubSchemaDoesNotSilentlyAccept() {
        assertSchemaHasValidationErrors(
            "propertyNames: 42",
            listOf(MessageType.SCHEMA_OBJECT_OR_BOOLEAN)
        )
    }

    @Test
    fun testRefOnlyWithNoOtherProperties() {
        // Test that $ref alone works without errors
        assertValidObjectSchema(
            $$"""
            definitions:
              simpleType:
                type: string
                .
              .
            properties:
              myProperty:
                '$ref': '#/definitions/simpleType'
            """.trimIndent()
        )
    }
}
