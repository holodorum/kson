package org.kson.schema.validators

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * *Elimination*: a branch that pins a property to a value set the document contradicts is
 * provably dead and drops out of the reported errors, unless another pin selects it.  Presence only
 * ever narrows among the survivors, so these cases also pin down that composition: a single
 * contradicted pin narrows the dump, presence can never resurrect a branch elimination has already
 * ruled out, a value every branch of a union rules out is reported on its own (while a lone branch
 * keeps its own errors), and branches eliminated on different properties still fall back to the
 * full dump.
 */
class OneOfEliminationTest : JsonSchemaTest {
    /**
     * A union whose branches share `kind` — pinned to a `const` by some, repeated across others — with
     * each branch requiring its own `params` property.  `A` repeats, so a value can match several
     * branches' pins, while a mismatch eliminates every branch pinning `kind` away from it.
     */
    private val duplicateConstUnion = """
        {
          "oneOf": [
            {
              "properties": {
                "kind": { "const": "A" },
                "params": { "type": "object", "required": ["p1"] }
              },
              "required": ["kind", "params"]
            },
            {
              "properties": {
                "kind": { "const": "A" },
                "params": { "type": "object", "required": ["p2"] }
              },
              "required": ["kind", "params"]
            },
            {
              "properties": {
                "kind": { "const": "B" },
                "params": { "type": "object", "required": ["p3"] }
              },
              "required": ["kind", "params"]
            }
          ]
        }
    """.trimIndent()

    /**
     * A single pinned branch is enough to eliminate: only branch A pins `kind`, and `kind: "B"` is
     * outside its `["A"]`, so branch A is dropped and the union narrows to the lone unpinned branch —
     * surfacing its missing `value` alone rather than dumping both branches.
     */
    @Test
    fun testOneOfSinglePinnedBranchMismatchEliminates() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "extra_a": { "type": "string" }
                      },
                      "required": ["kind", "extra_a"]
                    },
                    {
                      "properties": { "value": { "type": "boolean" } },
                      "required": ["value"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // branch A (pinned `kind: "A"`) is eliminated by `kind: "B"`; only the unpinned branch's `value` remains
        assertContains(errors[0].message.toString(), "value")
        assertFalse(errors[0].message.toString().contains("extra_a"))
    }

    /**
     * Pins needn't be disjoint (`kind`: A, A, B): a mismatch eliminates every branch that pins `kind`
     * away from the document's value, so `kind: "B"` drops both `A` branches, narrowing to the lone `B`
     * branch and surfacing its deeper `p3` requirement as a bare message.
     */
    @Test
    fun testOneOfNonDisjointPinsEliminateContradictedBranches() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
                params: {}
            """.trimIndent(),
            duplicateConstUnion,
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // both `A` branches are eliminated by `kind: "B"`; only the surviving `B` branch's `p3` remains
        assertContains(errors[0].message.toString(), "p3")
        assertFalse(errors[0].message.toString().contains("p1"))
        assertFalse(errors[0].message.toString().contains("p2"))
    }

    /**
     * When the document's value contradicts *every* branch's pin on one property (`kind: "Z"` is outside
     * {A}, {A} and {B}), no branch can match until that value changes, so it is reported on its own: one
     * enum error listing what the branches admit, each value once, rather than a dump of every branch.
     * The pins needn't be disjoint for this — `A` is pinned twice.
     */
    @Test
    fun testOneOfValueNoBranchAdmitsReportsAllowedValues() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                params: {}
            """.trimIndent(),
            duplicateConstUnion,
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            // the enum error hangs off the `kind` value `Z`
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8)
            )
        )

        assertEquals("Value must be one of: \"A\", \"B\"", errors[0].message.toString())
    }

    /**
     * Each value no branch admits is reported on its own: `kind: "Z"` and `mode: "sync"` are both outside
     * every branch's pins, so each gets an enum error listing what the branches allow for it.
     */
    @Test
    fun testOneOfEachValueNoBranchAdmitsIsReported() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                mode: "sync"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": { "kind": { "const": "A" }, "mode": { "const": "read" } },
                      "required": ["kind", "mode"]
                    },
                    {
                      "properties": { "kind": { "const": "B" }, "mode": { "const": "write" } },
                      "required": ["kind", "mode"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED,
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8),
                Location(Coordinates(1, 7), Coordinates(1, 11), 17, 21)
            )
        )

        assertEquals("Value must be one of: \"A\", \"B\"", errors[0].message.toString())
        assertEquals("Value must be one of: \"read\", \"write\"", errors[1].message.toString())
    }

    /**
     * A selected branch outranks a value every branch rejects: `version: 2` is outside both branches'
     * `version: 1`, but `kind: "A"` is admitted by branch A alone, so branch A is reported in full —
     * its `version` const failure and its missing `alpha` — rather than one enum error on `version`
     * that would hide `alpha` until `version` was fixed.
     */
    @Test
    fun testOneOfSelectedBranchOutranksSharedPinRejection() {
        val errors = assertKsonSchemaErrors(
            """
                version: 2
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "version": { "const": 1 },
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["version", "kind"]
                    },
                    {
                      "properties": {
                        "version": { "const": 1 },
                        "kind": { "const": "B" },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["version", "kind"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_VALUE_NOT_EQUAL_TO_CONST,
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[1].message.toString(), "alpha")
    }

    /**
     * A union of one branch offers no choice for a value to block, so a value the branch's pin rejects
     * is reported in the branch's own words, alongside its other errors: `kind: "B"` breaks the lone
     * branch's `const`, and its missing `name` is reported too, rather than one enum error hiding it.
     */
    @Test
    fun testOneOfSingleBranchKeepsItsOwnErrors() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": { "kind": { "const": "A" }, "name": { "type": "string" } },
                      "required": ["kind", "name"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_VALUE_NOT_EQUAL_TO_CONST,
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[1].message.toString(), "name")
    }

    /**
     * Elimination can rule out every branch without any one value being to blame: `kind: "X"` eliminates
     * branch A and `mode: "N"` branch B, yet each value is accepted by the branch that doesn't pin it.
     * With no survivor and no value every branch rejects, nothing narrows, so the full dump is kept.
     */
    @Test
    fun testOneOfBranchesEliminatedOnDifferentPropertiesKeepFullDump() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
                mode: "N"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "title": "BranchA",
                      "properties": { "kind": { "const": "A" }, "need_a": { "type": "string" } },
                      "required": ["need_a"]
                    },
                    {
                      "title": "BranchB",
                      "properties": { "mode": { "const": "M" }, "need_b": { "type": "string" } },
                      "required": ["need_b"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        // both branches are eliminated, but on different properties, so both are dumped
        val dump = errors[1].message.toString()
        assertContains(dump, "BranchA")
        assertContains(dump, "BranchB")
    }

    /**
     * With no branches at all (`oneOf: []`), "every branch rejects this value" holds vacuously for any
     * value; that must not turn into enum errors that list no allowed values.  The plain no-match report
     * stands, its per-branch dump empty.
     */
    @Test
    fun testOneOfWithNoBranchesBlamesNoValue() {
        assertKsonSchemaErrors(
            """
                kind: "A"
            """.trimIndent(),
            """
                { "oneOf": [] }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
    }

    /**
     * A multi-value `enum` pin eliminates just like a `const`: branch A pins `kind` to `["A", "B"]`, and
     * `kind: "C"` is outside that whole set, so branch A is dropped and the union narrows to the unpinned
     * branch — proving elimination tests membership in the full pinned set, not just its first value.
     */
    @Test
    fun testOneOfMultiValueEnumPinEliminates() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "C"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "enum": ["A", "B"] },
                        "foo": { "type": "string" }
                      },
                      "required": ["kind", "foo"]
                    },
                    {
                      "properties": { "baz": { "type": "boolean" } },
                      "required": ["baz"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // `kind: "C"` is outside `["A", "B"]`, eliminating branch A; only the unpinned branch's `baz` remains
        assertContains(errors[0].message.toString(), "baz")
        assertFalse(errors[0].message.toString().contains("foo"))
    }

    /**
     * Presence refines the survivors: elimination drops branch A (`kind: "X"` ∉ `["A"]`), leaving two
     * survivors, and presence then narrows those to the single branch whose distinguishing property the
     * document carries.  With `kind: "X"` alone, `need_b`'s branch is the one both signals agree on, so its
     * missing `need_b` surfaces — neither the eliminated branch A's `need_a` nor the unmatched branch C.
     */
    @Test
    fun testOneOfEliminationRefinesPresenceMatch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "title": "BranchA",
                      "properties": { "kind": { "const": "A" }, "need_a": { "type": "string" } },
                      "required": ["kind", "need_a"]
                    },
                    {
                      "title": "BranchB",
                      "properties": { "kind": { "type": "string" }, "need_b": { "type": "string" } },
                      "required": ["kind", "need_b"]
                    },
                    {
                      "title": "BranchC",
                      "properties": { "other": { "type": "string" }, "need_c": { "type": "string" } },
                      "required": ["other", "need_c"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // survivors {B, C} refined by presence of `kind` to just B; A eliminated, C unmatched
        assertContains(errors[0].message.toString(), "need_b")
        assertFalse(errors[0].message.toString().contains("need_a"))
        assertFalse(errors[0].message.toString().contains("need_c"))
    }

    /**
     * Presence must not resurrect an eliminated branch.  `kind: "X"` eliminates branch A, yet `kind` is
     * also the only property branch A *knows*, so presence alone would match A — the exact branch just
     * proven dead.  Presence is only preferred among the survivors, which drops A from the match and
     * leaves nothing to prefer, so the report keeps the survivors {B, C} and never mentions A's
     * requirements.
     */
    @Test
    fun testOneOfPresenceCannotResurrectEliminatedBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "title": "BranchA",
                      "properties": { "kind": { "const": "A" }, "need_a": { "type": "string" } },
                      "required": ["kind", "need_a"]
                    },
                    {
                      "title": "BranchB",
                      "properties": { "sel_b": { "type": "string" }, "need_b": { "type": "string" } },
                      "required": ["sel_b", "need_b"]
                    },
                    {
                      "title": "BranchC",
                      "properties": { "sel_c": { "type": "string" }, "need_c": { "type": "string" } },
                      "required": ["sel_c", "need_c"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        // branch A is eliminated despite presence matching it, so the dump narrows to the survivors, not A
        val dump = errors[1].message.toString()
        assertContains(dump, "BranchB")
        assertContains(dump, "BranchC")
        assertFalse(dump.contains("BranchA"))
    }

    /**
     * An empty `enum: []` admits no value, so it eliminates any branch once the document carries the
     * pinned property: branch A pins `kind` to `[]`, and `kind: "anything"` is outside it, so branch A is
     * dropped and the union narrows to the unpinned branch — surfacing its missing `bar` alone.
     */
    @Test
    fun testOneOfEmptyEnumPinEliminatesWhenPropertyPresent() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "anything"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "enum": [] },
                        "foo": { "type": "string" }
                      },
                      "required": ["kind", "foo"]
                    },
                    {
                      "properties": { "bar": { "type": "boolean" } },
                      "required": ["bar"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // the empty-pin branch is eliminated by the present `kind`; only the unpinned branch's `bar` remains
        assertContains(errors[0].message.toString(), "bar")
        assertFalse(errors[0].message.toString().contains("foo"))
    }
}
