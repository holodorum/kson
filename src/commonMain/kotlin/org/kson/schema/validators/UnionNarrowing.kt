package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.Message
import org.kson.schema.JsonObjectSchema
import org.kson.schema.JsonSchema
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

/**
 * Reports a document that matched no branch of a `oneOf` / `anyOf`, narrowed to the branches that
 * matter.  A *pin* is a property a branch fixes by `const` or `enum`; a branch *knows* a property it
 * declares or requires.  Both are read through `$ref` and `allOf` by [JsonObjectSchema.pinnedProperties]
 * and [JsonObjectSchema.knownProperties].
 *
 * For an object document, in order:
 *  1. a value only one branch's pin admits selects that branch, whatever its other pins say;
 *  2. a value every branch pins and none admits is wrong whichever branch is meant, so it is reported
 *     as the enum error the pinned values would give — given two or more branches, as a lone branch's
 *     own errors say more;
 *  3. otherwise the branches no pin rules out, preferring those that know a property the document
 *     carries and not every branch knows; failing that, every branch.
 *
 * A non-object document, or [ValidationMode.PARTIAL] mode (where a half-typed document shouldn't get
 * the enum error), reports every branch.
 */
internal fun reportUnionMatchFailure(
    branches: List<JsonSchema>,
    ksonValue: KsonValue,
    messageSink: MessageSink,
    matchAttemptMessageSinks: List<LabelledMessageSink>,
    noMatchMessage: Message,
    sourceContext: SourceContext
) {
    fun report(reported: List<Int>) =
        reportNoSubSchemaMatchErrors(ksonValue, messageSink, reported.map { matchAttemptMessageSinks[it] }, noMatchMessage)

    if (ksonValue !is KsonObject || sourceContext.mode == ValidationMode.PARTIAL) {
        report(branches.indices.toList())
        return
    }
    val document = ksonValue.propertyLookup
    val pins = branches.map { (it as? JsonObjectSchema)?.pinnedProperties() ?: emptyMap() }

    val selected = selectedBranches(pins, document)
    if (selected.isNotEmpty()) {
        report(selected)
        return
    }

    val contradicted = pins.map { branchPins ->
        branchPins.filter { (property, values) -> document[property]?.let { it !in values } ?: false }.keys
    }
    // a lone branch offers no choice to block; an empty union has nothing to reduce
    val noBranchAdmits =
        if (branches.size > 1) contradicted.reduce { shared, next -> shared intersect next } else emptySet()
    if (noBranchAdmits.isNotEmpty()) {
        noBranchAdmits.forEach { property ->
            val allowedValues = pins.flatMapTo(LinkedHashSet()) { it.getValue(property) }
            reportEnumValueNotAllowed(document.getValue(property), messageSink, allowedValues)
        }
        return
    }

    val known = branches.map { (it as? JsonObjectSchema)?.knownProperties() ?: emptySet() }
    val survivors = branches.indices.filter { contradicted[it].isEmpty() }
    val lookedLike = survivors.filter { it in branchesLookedLike(known, document.keys) }
    report(lookedLike.ifEmpty { survivors }.ifEmpty { branches.indices.toList() })
}

/** The branches some value of [document] is admitted by the pin of, and of no other branch. */
private fun selectedBranches(pins: List<Map<String, Set<KsonValue>>>, document: Map<String, KsonValue>): List<Int> =
    pins.indices.filter { i ->
        pins[i].any { (property, values) ->
            val value = document[property]
            value != null && value in values && pins.count { value in it[property].orEmpty() } == 1
        }
    }

/** The branches knowing a present property that not every branch knows; one every branch knows proves nothing. */
private fun branchesLookedLike(known: List<Set<String>>, present: Set<String>): List<Int> {
    val distinguishing = known.flatten().filterTo(mutableSetOf()) { property -> known.count { property in it } < known.size }
    return known.indices.filter { i -> known[i].any { it in distinguishing && it in present } }
}
