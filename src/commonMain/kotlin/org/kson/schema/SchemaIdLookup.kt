package org.kson.schema

import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.walker.KsonValueWalker
import org.kson.walker.navigateWithJsonPointer

/**
 * Manages the mapping of `$id` values to their corresponding schema nodes for `$ref` resolution.
 *
 * @param schemaRootValue the [KsonValue] root of the schema to build this [SchemaIdLookup] from
 */
class SchemaIdLookup(val schemaRootValue: KsonValue) {

    private val idMap: Map<String, KsonValue>

    init {
        /**
         * Collect all `$id` entries from the given schema tree.
         * This pre-processes the entire schema to build a map of fully-qualified IDs.
         */
        idMap = mutableMapOf()

        // preload know meta-schemas
        idMap[KsonDraft7MetaSchema.ID] = KsonDraft7MetaSchema.schemaValue

        if (schemaRootValue is KsonObject) {
            val rootBaseUri = rootBaseUri()

            // Store the root schema at its baseUri
            idMap[rootBaseUri] = schemaRootValue

            // Walk the schema tree to collect all IDs with fully-qualified URIs
            walkSchemaForIds(schemaRootValue, idMap, rootBaseUri)
        }
    }

    private fun rootBaseUri(): String =
        (schemaRootValue as? KsonObject)?.propertyLookup["\$id"]
            ?.let { (it as? KsonString)?.value }
            ?: ""

    /**
     * Resolves a `$ref` reference string to the corresponding schema value.
     *
     * @param ref The reference string (e.g., "#foo", "#/definitions/address", "bar")
     * @param currentBaseUri The current base URI context for resolving relative references
     * @return The resolved [KsonValue] representing the referenced schema, or null if not found
     */
    fun resolveRef(ref: String, currentBaseUri: String): ResolvedRef? {
        val resolvedRefUri = resolveUri(ref, currentBaseUri)

        // try a direct lookup of our resolved ref URI
        idMap[resolvedRefUri.toString()]?.let {
            return ResolvedRef(it, currentBaseUri)
        }

        // otherwise, see if we can interpret the fragment
        return if (resolvedRefUri.fragment.startsWith("#/")) {
            val decodedPointer = JsonPointer(decodeUriEncoding(resolvedRefUri.fragment.substring(1)))
            if (resolvedRefUri.origin.isNotBlank()) {
                idMap[resolvedRefUri.toString().substringBefore("#")]?.let { resolveJsonPointer(decodedPointer, it, resolvedRefUri.toString()) }
            } else {
                idMap[currentBaseUri]?.let { resolveJsonPointer(decodedPointer, it, currentBaseUri) }
            }
        } else {
            idMap[resolvedRefUri.toString().substringBefore("#") + resolvedRefUri.fragment.removePrefix("#")]
                ?.let { ResolvedRef(it, currentBaseUri) }
        }
    }


    /**
     * Navigate schema by document path tokens.
     *
     * This function translates document paths to schema paths by inserting schema-specific wrappers:
     * - For object properties: navigates through "properties" wrapper
     * - For array indices: navigates to "items" schema (all array elements share the same schema)
     * - Falls back to "additionalProperties" or "patternProperties" when specific property not found
     * - Resolves `$ref` references to their target schemas
     * - Handles combinators (allOf, anyOf, oneOf) and conditionals (if/then/else), flattening
     *   them at every level so callers receive fully decomposed branches
     *
     * Base URI tracking is handled internally to ensure correct `$ref` resolution.
     *
     * Returns a list because a single document path can match multiple schema locations:
     * - Property defined in multiple combinator branches
     * - Multiple patternProperties matching
     * - Both `then` and `else` branches active when the `if` can't be evaluated
     *
     * Example:
     * ```kotlin
     * // Document path: ["users", "0", "name"]
     * // Schema navigation: properties/users → items → properties/name
     * val idLookup = SchemaIdLookup(schemaRoot)
     * val schemaRefs = idLookup.navigateByDocumentPath(listOf("users", "0", "name"))
     * ```
     *
     * Narrowing treats the document as authoritative for committed context, but the value the
     * caret is authoring must never disqualify the branches it selects among.  [placeholderLocation]
     * is the span of that in-flux value: validation errors falling within it are ignored during
     * narrowing at every level.  Completion passes the caret's half-typed value span; hover and
     * go-to-definition pass null (the leaf value is committed, so it narrows too).  Null is the
     * default so direct-navigation callers narrow by the real document values.
     *
     * @param documentPointer Pointer through the document (from [org.kson.walker.navigateToLocationWithPointer])
     * @param documentValue The document being navigated against (drives branch narrowing)
     * @param placeholderLocation Span of the value being authored at the caret, excluded from narrowing
     * @return List of [ResolvedRef] containing all sub-schemas at that location (empty if not found)
     */
    fun navigateByDocumentPointer(
        documentPointer: JsonPointer,
        documentValue: KsonValue? = null,
        placeholderLocation: Location? = null,
    ): List<ResolvedRef> {
        return SchemaNavigator(this).navigate(documentPointer, documentValue, placeholderLocation)
    }

    /**
     * Resolves a `$ref` in a schema value if present.
     *
     * Public to support downstream `$ref` resolution within schema branches, e.g.,
     * when checking property constraints inside oneOf/anyOf branches.
     *
     * @param value The schema value that might contain a `$ref`
     * @param currentBaseUri The current base URI for resolving the reference
     * @return A [ResolvedRef] with the resolved value and base URI
     */
    fun resolveRefIfPresent(value: KsonValue, currentBaseUri: String): ResolvedRef {
        if (value is KsonObject) {
            val refValue = value.propertyLookup["\$ref"] as? KsonString
            if (refValue != null) {
                return resolveRef(refValue.value, currentBaseUri) ?: ResolvedRef(value, currentBaseUri)
            }
        }

        return ResolvedRef(value, currentBaseUri)
    }

    /**
     * Navigates a [JsonPointer] through a schema, returning all sub-schemas at the
     * target location fully flattened (combinators exploded, conditionals narrowed by
     * strict isValid against the document).
     *
     * The navigator is built on two primitives:
     * - [stepInto]: structural per-token descent through a single schema, no combinator
     *   awareness.
     * - [flatten]: doc-aware decomposition of a schema's top-level branches.
     *
     * Every level applies `flatten` before stepping, including the root and the target,
     * so no post-navigation expansion pass is needed.
     *
     * Mirrors the shape of `TreeNavigator` in the walker package (see
     * [org.kson.walker.navigateWithJsonPointer]) — one entry point, small internal
     * helpers — so the pattern is recognizable.
     */
    private class SchemaNavigator(private val idLookup: SchemaIdLookup) {

        fun navigate(
            documentPointer: JsonPointer,
            documentValue: KsonValue?,
            placeholderLocation: Location?
        ): List<ResolvedRef> {
            val rootBaseUri = idLookup.rootBaseUri()
            val rootRef = idLookup.resolveRefIfPresent(idLookup.schemaRootValue, rootBaseUri)

            val tokens = documentPointer.tokens
            var current = flatten(rootRef, documentValue, placeholderLocation)
            var currentDocValue = documentValue

            for (token in tokens) {
                val stepped = current.flatMap { stepInto(it, token) }
                currentDocValue = currentDocValue?.let { docVal ->
                    KsonValueWalker.navigateWithJsonPointer(docVal, JsonPointer.fromTokens(listOf(token)))
                }
                current = stepped.flatMap { flatten(it, currentDocValue, placeholderLocation) }
                if (current.isEmpty()) break
            }

            return current
        }

        /**
         * Structural step by one pointer token.  Looks at properties / patternProperties /
         * additionalProperties (for names) or items / additionalItems (for integer indices).
         * No combinator / conditional logic — [flatten] handles branching.
         *
         * Applies `$id` on [ref] to the base URI before property lookup, and resolves `$ref`
         * on the stepped-into schema.
         *
         * Branch context inheritance: if [ref]'s resolutionType is a branch marker
         * (ONE_OF / ANY_OF / ALL_OF / IF_THEN / IF_ELSE), stepped results keep that marker
         * so downstream completion merging still treats them as conditional.  Otherwise the
         * stepped result's resolutionType reflects how the step resolved the token
         * (DIRECT_PROPERTY / PATTERN_PROPERTY / ADDITIONAL_PROPERTY / ARRAY_ITEMS).
         */
        private fun stepInto(ref: ResolvedRef, token: String): List<ResolvedRef> {
            val schemaObj = ref.resolvedValue as? KsonObject ?: return emptyList()

            // Apply $id on the current node to the base URI before any lookup from this node.
            var updatedBaseUri = ref.resolvedValueBaseUri
            schemaObj.propertyLookup[$$"$id"]?.let { idValue ->
                if (idValue is KsonString) {
                    updatedBaseUri = resolveUri(idValue.value, updatedBaseUri).toString()
                }
            }

            val stepped = mutableListOf<Pair<KsonValue, SchemaResolutionType>>()
            val isArrayIndex = token.toIntOrNull() != null

            if (isArrayIndex) {
                schemaObj.propertyLookup["items"]?.let {
                    stepped.add(it to SchemaResolutionType.ARRAY_ITEMS)
                }
                schemaObj.propertyLookup["additionalItems"]?.let {
                    stepped.add(it to SchemaResolutionType.ARRAY_ITEMS)
                }
            } else {
                val properties = schemaObj.propertyLookup["properties"] as? KsonObject
                properties?.propertyMap?.get(token)?.let {
                    stepped.add(it.propValue to SchemaResolutionType.DIRECT_PROPERTY)
                }

                val patternProperties = schemaObj.propertyLookup["patternProperties"] as? KsonObject
                patternProperties?.propertyMap?.forEach { (pattern, property) ->
                    try {
                        if (Regex(pattern).containsMatchIn(token)) {
                            stepped.add(property.propValue to SchemaResolutionType.PATTERN_PROPERTY)
                        }
                    } catch (_: Throwable) {
                        // Invalid regex pattern, skip it
                        // Use Throwable to catch JavaScript SyntaxError and other platform-specific errors
                    }
                }

                if (stepped.isEmpty()) {
                    schemaObj.propertyLookup["additionalProperties"]?.let {
                        stepped.add(it to SchemaResolutionType.ADDITIONAL_PROPERTY)
                    }
                }
            }

            val inheritedType = ref.resolutionType.takeIf { it.isBranchMarker }
            return stepped.map { (value, stepType) ->
                val resolved = idLookup.resolveRefIfPresent(value, updatedBaseUri)
                ResolvedRef(
                    resolved.resolvedValue,
                    resolved.resolvedValueBaseUri,
                    inheritedType ?: stepType
                )
            }
        }

        /**
         * Flatten a schema's top-level branches, narrowing each against [docVal] — the
         * document value at this level (the object/array/scalar that contains these
         * combinators).  Handles:
         *   - oneOf / anyOf: a branch is emitted only when it is compatible with [docVal]
         *     (soft validation — see [isCompatibleWithDocument]).  Because narrowing happens
         *     where the combinator lives, sibling discriminators (e.g. a `const` on a property
         *     other than the one being completed) are visible and contradicted branches are
         *     dropped right here, with full ancestor context.  When [docVal] is null, all
         *     branches are emitted (nothing to narrow against).
         *   - allOf: unconditional expansion, every branch emitted (all must hold).
         *   - if / then / else: see [evaluateIf].  A matching `if` emits `then`; a
         *     contradicted `if` emits `else`; an undecidable `if` (no document, unparseable
         *     condition, or failing only because a required discriminator isn't typed yet)
         *     emits both branches.
         *
         * Recurses into each branch so nested combinators/conditionals are fully flattened,
         * each narrowed against the same [docVal].
         *
         * The parent [ref] is preserved at the head of the result so its title, description,
         * and constraints remain available.
         */
        private fun flatten(
            ref: ResolvedRef,
            docVal: KsonValue?,
            excludeLocation: Location?
        ): List<ResolvedRef> {
            val schemaObj = ref.resolvedValue as? KsonObject ?: return listOf(ref)

            val results = mutableListOf<ResolvedRef>()
            var addedBranches = false

            fun addBranch(branch: KsonValue, resolutionType: SchemaResolutionType) {
                val resolved = idLookup.resolveRefIfPresent(branch, ref.resolvedValueBaseUri)
                val branchRef = ResolvedRef(
                    resolved.resolvedValue,
                    resolved.resolvedValueBaseUri,
                    resolutionType
                )
                results.addAll(flatten(branchRef, docVal, excludeLocation))
                addedBranches = true
            }

            // oneOf/anyOf alternatives are narrowed against the document at this level: a
            // branch is dropped when it contradicts the document (e.g. a discriminating
            // sibling property).  This is the single, doc-aware narrowing point — no
            // post-navigation sibling/leaf filtering pass is needed.
            fun addNarrowedBranch(branch: KsonValue, resolutionType: SchemaResolutionType) {
                val resolved = idLookup.resolveRefIfPresent(branch, ref.resolvedValueBaseUri)
                if (docVal != null && !isCompatibleWithDocument(resolved, docVal, excludeLocation)) {
                    // narrowing still happened even if every branch was dropped
                    addedBranches = true
                    return
                }
                val branchRef = ResolvedRef(
                    resolved.resolvedValue,
                    resolved.resolvedValueBaseUri,
                    resolutionType
                )
                results.addAll(flatten(branchRef, docVal, excludeLocation))
                addedBranches = true
            }

            (schemaObj.propertyLookup["oneOf"] as? KsonList)?.elements?.forEach { branch ->
                addNarrowedBranch(branch, SchemaResolutionType.ONE_OF)
            }

            (schemaObj.propertyLookup["anyOf"] as? KsonList)?.elements?.forEach { branch ->
                addNarrowedBranch(branch, SchemaResolutionType.ANY_OF)
            }

            (schemaObj.propertyLookup["allOf"] as? KsonList)?.elements?.forEach { branch ->
                addBranch(branch, SchemaResolutionType.ALL_OF)
            }

            val ifCondition = schemaObj.propertyLookup["if"]
            if (ifCondition != null) {
                when (evaluateIf(ifCondition, ref.resolvedValueBaseUri, docVal)) {
                    IfState.MATCH -> schemaObj.propertyLookup["then"]?.let {
                        addBranch(it, SchemaResolutionType.IF_THEN)
                    }
                    IfState.NO_MATCH -> schemaObj.propertyLookup["else"]?.let {
                        addBranch(it, SchemaResolutionType.IF_ELSE)
                    }
                    IfState.UNDETERMINED -> {
                        schemaObj.propertyLookup["then"]?.let {
                            addBranch(it, SchemaResolutionType.IF_THEN)
                        }
                        schemaObj.propertyLookup["else"]?.let {
                            addBranch(it, SchemaResolutionType.IF_ELSE)
                        }
                    }
                }
            }

            if (addedBranches) {
                results.add(0, ref)
            } else {
                results.add(ref)
            }

            return results
        }

        /**
         * Soft-validates [ref]'s schema against [docVal]: a branch is "compatible" unless it
         * produces a validation error that is NOT merely a missing-required-property /
         * missing-dependency error.  Missing required properties are expected mid-completion
         * (the document is being typed), so they never disqualify a branch; type/const/enum/
         * pattern violations on properties that ARE present do.  Fail-open if the branch
         * schema can't be parsed.
         *
         * [excludeLocation], when set, is the span of the caret leaf — the half-typed
         * placeholder being completed (an object property's value or an array item).  Errors
         * located within that span are ignored, so a branch is judged only by its constraints
         * on the committed parts of the document, never by the incomplete value at the caret.
         */
        private fun isCompatibleWithDocument(
            ref: ResolvedRef,
            docVal: KsonValue,
            excludeLocation: Location?
        ): Boolean {
            val schema = SchemaParser.parseSchemaElement(
                ref.resolvedValue, MessageSink(), ref.resolvedValueBaseUri, idLookup
            ) ?: return true
            val sink = MessageSink()
            schema.validate(docVal, sink)
            return sink.loggedMessages().none { logged ->
                if (logged.message.type in IGNORABLE_VALIDATION_ERRORS) return@none false
                // keep the error unless it falls within the caret leaf being completed
                val within = excludeLocation != null &&
                    excludeLocation.startOffset <= logged.location.startOffset &&
                    logged.location.endOffset <= excludeLocation.endOffset
                !within
            }
        }

        /**
         * Evaluate an `if` condition against [docVal] into three states.  A condition that
         * fails ONLY because a required discriminator property hasn't been typed yet is
         * [IfState.UNDETERMINED], not [IfState.NO_MATCH]: the document is incomplete, so we
         * can't yet rule out `then`.  This distinguishes "not decidable due to incompleteness"
         * (emit both branches) from "decidably false against present data" (emit `else`).
         */
        private fun evaluateIf(ifCondition: KsonValue, baseUri: String, docVal: KsonValue?): IfState {
            if (docVal == null) return IfState.UNDETERMINED
            val ifSchema = SchemaParser.parseSchemaElement(ifCondition, MessageSink(), baseUri, idLookup)
                ?: return IfState.UNDETERMINED
            val sink = MessageSink()
            ifSchema.validate(docVal, sink)
            val errors = sink.loggedMessages()
            return when {
                errors.isEmpty() -> IfState.MATCH
                errors.all { it.message.type in IGNORABLE_VALIDATION_ERRORS } -> IfState.UNDETERMINED
                else -> IfState.NO_MATCH
            }
        }
    }

    /** Outcome of evaluating an `if` condition during [SchemaNavigator] flattening. */
    private enum class IfState { MATCH, NO_MATCH, UNDETERMINED }

    companion object {
        /**
         * Validation errors that never disqualify a branch during completion narrowing:
         * a missing required property (or dependency) is expected while the document is
         * still being typed.  Any other error (type/const/enum/pattern mismatch on a
         * property that IS present) marks the branch incompatible with the document.
         */
        private val IGNORABLE_VALIDATION_ERRORS = setOf(
            MessageType.SCHEMA_REQUIRED_PROPERTY_MISSING,
            MessageType.SCHEMA_MISSING_REQUIRED_DEPENDENCIES
        )

        /**
         * Recursively walks a schema value to collect all `$id` entries with fully-qualified URIs.
         *
         * @param schemaValue The current schema node to examine
         * @param idMap The map to collect fully-qualified $id entries into
         * @param currentBaseUri The current base URI context for resolving relative URIs
         */
        private fun walkSchemaForIds(
            schemaValue: KsonValue,
            idMap: MutableMap<String, KsonValue>,
            currentBaseUri: String
        ) {
            when (schemaValue) {
                is KsonObject -> {
                    var contextBaseUri = currentBaseUri

                    // Check for $id in this object
                    schemaValue.propertyLookup["\$id"]?.let { idValue ->
                        if (idValue is KsonString) {
                            val idString = idValue.value

                            // Resolve the ID to its fully-qualified form
                            val fullyQualifiedId = resolveUri(idString, currentBaseUri)
                            contextBaseUri = fullyQualifiedId.toString()
                            idMap[contextBaseUri] = schemaValue
                        }
                    }

                    // Recursively walk all property values with the updated context
                    schemaValue.propertyMap.values.forEach { propertyValue ->
                        walkSchemaForIds(propertyValue.propValue, idMap, contextBaseUri)
                    }
                }

                is KsonList -> {
                    // Recursively walk all list elements
                    schemaValue.elements.forEach { element ->
                        walkSchemaForIds(element, idMap, currentBaseUri)
                    }
                }

                else -> {
                    /** no-op, only [KsonObject] and [KsonValue] have children */
                }
            }
        }

        data class RefUriParts (
            val origin: String,
            val path: String,
            val fragment: String) {
            override fun toString(): String {
                return "$origin$path$fragment"
            }
        }

        /**
         * Parse the given string into [RefUriParts].  This is a simplified version of that parsing specified in
         * [RFC 3986 Section 3](https://datatracker.ietf.org/doc/html/rfc3986#section-3) targeted towards our
         * $ref parsing use case.
         * TODO we likely want to consider implementing a more formal parser implementation based on that specification
         */
        internal fun parseUri(uri: String): RefUriParts {
            val origin = if (uri.contains("://")) {
                val scheme = uri.substringBefore("://")
                val authority = uri.substringAfter("://")
                    .substringBefore('/')
                    .substringBefore('#')
                "$scheme://$authority"
            } else if (uri.substringBefore("/").contains(":")){
                uri.substringBefore("#")
            } else {
                ""
            }

            val afterOrigin = uri.substringAfter(origin)
            val path = if (origin.isBlank()) {
                afterOrigin.substringBeforeLast('#')
            } else if (afterOrigin.isNotBlank() && !afterOrigin.startsWith("#")) {
                "/" + afterOrigin.removePrefix("/").substringBefore('#')
            } else {
                ""
            }

            val fragment = if (uri.contains('#')) {
                "#" + uri.substringAfter('#')
            } else {
                ""
            }

            return RefUriParts(origin, path, fragment)
        }

        /**
         * Resolve [uri] in the context of [baseUri].
         *
         * This works analogously to how url updates in a web browsers: if you are "on" [baseUri] and "click" on
         *   and link with href="[uri]", you will be sent to the uri defined by the returned [RefUriParts]
         *
         * NOTE: this implements reference transformation from
         *   [RFC 3986 §5.2.2](https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.2) — including its
         *   scheme/authority inheritance cases and the
         *   [§5.2.3](https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.3) merge-paths sub-operation.
         *   It does not yet perform the
         *   [§5.2.4](https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.4) remove-dot-segments sub-operation;
         *   add that when a case needs it.
         */
        fun resolveUri(uri: String, baseUri: String): RefUriParts {
            val uriParts = parseUri(uri)
            val baseUriParts = parseUri(baseUri)
            val origin = uriParts.origin.ifBlank { baseUriParts.origin }
            val path = when {
                // Absolute-path reference: use as-is.
                uriParts.path.startsWith('/') -> uriParts.path
                // Empty reference path: inherit the base path.
                uriParts.path.isEmpty() -> baseUriParts.path
                // Relative reference path: merge with base path per RFC 3986 §5.2.3.
                // If the base has an authority and an empty path, the merged path
                // starts with "/". Otherwise, replace the last segment of the base
                // path with the reference path; if the base path has no "/", the
                // entire base path is discarded.
                baseUriParts.origin.isNotEmpty() && baseUriParts.path.isEmpty() ->
                    "/" + uriParts.path
                else -> {
                    val lastSlash = baseUriParts.path.lastIndexOf('/')
                    if (lastSlash < 0) {
                        uriParts.path
                    } else {
                        baseUriParts.path.substring(0, lastSlash + 1) + uriParts.path
                    }
                }
            }
            return RefUriParts(origin, path, uriParts.fragment)
        }
    }
}

/**
 * Decodes percent-encoded characters in a URI. According to RFC 6901, percent-encoding must be decoded
 * before JSON Pointer processing.
 *
 * @param encoded The percent-encoded string
 * @return The decoded string
 */
private fun decodeUriEncoding(encoded: String): String {
    if (!encoded.contains('%')) {
        return encoded
    }

    val result = StringBuilder()
    var i = 0
    while (i < encoded.length) {
        val char = encoded[i]
        if (char == '%' && i + 2 < encoded.length) {
            // Try to decode the next two characters as hex
            val hex = encoded.substring(i + 1, i + 3)
            val decoded = try {
                hex.toInt(16).toChar()
            } catch (_: NumberFormatException) {
                // Invalid hex sequence, keep the % and continue
                result.append(char)
                i++
                continue
            }
            result.append(decoded)
            i += 3
        } else {
            result.append(char)
            i++
        }
    }
    return result.toString()
}

/**
 * Resolves a JSON Pointer path within a [KsonValue] structure.
 *
 * @param pointer The JSON Pointer string (e.g., "/definitions/address")
 * @param ksonValue The [KsonValue] to traverse
 * @return The [KsonValue] at the pointer location, or null if not found
 */
private fun resolveJsonPointer(pointer: JsonPointer, ksonValue: KsonValue, currentBaseUri: String): ResolvedRef? {
    val resolvedValue = KsonValueWalker.navigateWithJsonPointer(ksonValue, pointer)
    val resolvedBaseUri = updateBaseUriAlongPath(ksonValue, pointer, currentBaseUri)
    return resolvedValue?.let { ResolvedRef(it, resolvedBaseUri) }
}

/**
 * Describes how a schema was reached during navigation, recording the combinator or
 * structural step that produced it.
 */
enum class SchemaResolutionType {
    /** Schema found via direct property lookup in "properties" */
    DIRECT_PROPERTY,
    /** Schema found via pattern matching in "patternProperties" */
    PATTERN_PROPERTY,
    /** Schema from "additionalProperties" fallback */
    ADDITIONAL_PROPERTY,
    /** Schema from "items" or "additionalItems" for array elements */
    ARRAY_ITEMS,
    /** Schema from "allOf" combinator - all branches must be valid */
    ALL_OF,
    /** Schema from "anyOf" combinator - at least one branch must be valid */
    ANY_OF,
    /** Schema from "oneOf" combinator - exactly one branch must be valid */
    ONE_OF,
    /** Schema from "then" branch of an if/then conditional */
    IF_THEN,
    /** Schema from "else" branch of an if/then/else conditional */
    IF_ELSE,
    /** Root schema or schema resolved via $ref */
    ROOT;

    /**
     * True if this resolution type was produced by a branching construct (combinator
     * or conditional).  Stepping into a branch-marked ref preserves the marker so the
     * downstream leaf still gets filtered by the branch's semantics.
     *
     * Exhaustive by design: adding a new enum entry forces a compile error here so
     * the branch-vs-structural classification is an explicit decision, not a default.
     */
    val isBranchMarker: Boolean
        get() = when (this) {
            ONE_OF, ANY_OF, ALL_OF, IF_THEN, IF_ELSE -> true
            DIRECT_PROPERTY, PATTERN_PROPERTY, ADDITIONAL_PROPERTY,
            ARRAY_ITEMS, ROOT -> false
        }
}

/**
 * A schema node resolved during navigation, carrying the context of how it was found.
 *
 * @param resolvedValue The schema value at this location
 * @param resolvedValueBaseUri The base URI for resolving `$ref` within this schema
 * @param resolutionType How this schema was reached (direct property, combinator branch, etc.)
 */
data class ResolvedRef(
    val resolvedValue: KsonValue,
    val resolvedValueBaseUri: String,
    val resolutionType: SchemaResolutionType = SchemaResolutionType.ROOT
)

/**
 * Updates the base URI while following a path of JSON Pointer tokens.
 *
 * @param current The current [KsonValue] node to start from
 * @param pointer The [JsonPointer] to follow
 * @param currentBaseUri The starting base URI
 * @return The updated base URI after following the token path
 */
private fun updateBaseUriAlongPath(current: KsonValue, pointer: JsonPointer, currentBaseUri: String): String {
    var node = current
    var updatedBaseUri = currentBaseUri

    for (token in pointer.tokens) {
        // Update base URI if current node has a $id property
        if (node is KsonObject) {
            node.propertyLookup["\$id"]?.let { idValue ->
                if (idValue is KsonString) {
                    updatedBaseUri = SchemaIdLookup.resolveUri(idValue.value, updatedBaseUri).toString()
                }
            }
        }

        // Navigate to next node
        node = KsonValueWalker.navigateWithJsonPointer(node, JsonPointer.fromTokens(listOf(token))) ?: break
    }

    return updatedBaseUri
}
