package org.kson.schema

import org.kson.parser.messages.Message
import org.kson.schema.draft.SchemaDraft
import org.kson.schema.draft.hasHidingRef
import org.kson.uri.UriReference
import org.kson.value.navigation.json_pointer.FragmentPointer
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.value.navigation.json_pointer.fragmentPointer
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue

/** A schema document's root [value] and the URI it was retrieved from, which may differ from its `$id` */
data class SchemaDocument(val value: KsonValue, val retrievalUri: UriReference? = null)

/**
 * Indexes the schemas in [root] and [additionalDocuments] by retrieval URI, `$id`, and `$anchor`, to resolve `$ref`s
 * among them.  It fetches no documents: [missingDocumentUris] lists those still needed.
 *
 * Each document is read in the draft its `$schema` names, or else in [root]'s draft.  Inside it, a schema with an
 * `$id` is a resource of its own, read in the draft its `$schema` names, or else in the draft of the resource
 * enclosing it.  A `$schema` anywhere else is ignored.
 *
 * A schema is a document's root, a subschema under one of the subschema keywords of its parent's draft, or a value a
 * `$ref` points to.  The `$id`s and `$schema`s of values that are not schemas change no base URI or draft.
 *
 * A URI several schemas share identifies the document retrieved from it, or else the deepest schema with that `$id` or
 * `$anchor`.  Ties go to the last one, in the order: [root], [additionalDocuments].  The built-in meta-schemas yield
 * their URIs to any schema that claims them.
 *
 * @param root the root schema document, which may have no retrieval URI
 * @param additionalDocuments the documents [root] references, directly or through each other
 * @param drafts the drafts a `$schema` can name, with their meta-schemas indexed alongside the documents.  [root] is
 *   read in the first when its `$schema` names none.
 * @throws IllegalArgumentException if a retrieval URI in [additionalDocuments] is missing, empty or fragment-only
 */
class IndexedSchemaDocuments internal constructor(
    val root: SchemaDocument,
    additionalDocuments: List<SchemaDocument>,
    private val drafts: List<SchemaDraft>
) {
    constructor(root: SchemaDocument, additionalDocuments: List<SchemaDocument> = emptyList()) :
        this(root, additionalDocuments, listOf(SchemaDraft.DRAFT_7, SchemaDraft.DRAFT_2020_12))

    /**
     * The location of a value: the index of its document in [documents], and the JSON Pointer tokens from that
     * document's root to the value
     */
    private data class Place(val document: Int, val path: List<String>) {
        fun below(tokens: List<String>) = Place(document, path + tokens)
    }

    /** The meta-schemas, the root and the additional documents */
    private val documents: List<SchemaDocument>

    /** The root of the last of [root] and [additionalDocuments] retrieved from each URI, by that URI */
    private val documentRootsByUri: Map<UriReference, Place>

    /** The value of each schema found, by its place */
    private val schemas = mutableMapOf<Place, KsonValue>()

    /** The schemas whose `$id` resolves to each URI, by that URI */
    private val schemasById = mutableMapOf<UriReference, MutableSet<Place>>()

    /** The schemas whose `$anchor` resolves to each URI, by that URI */
    private val schemasByAnchor = mutableMapOf<UriReference, MutableSet<Place>>()

    /** Ranks schemas whose `$id`s or `$anchor`s resolve to the same URI: the URI identifies the highest-ranked one */
    private val idOrder =
        compareBy<Place>({ it.path.size }, { it.document }, { schemas.getValue(it).location.startOffset })

    private val rootDraft: SchemaDraft = root.value.declaredDraft() ?: drafts.first()

    /** The base URI [root]'s value is read under: its retrieval URI without a fragment, or else empty */
    val rootBaseUri: UriReference = root.initialBaseUri

    /** The root schema, which is [root]'s value with [rootBaseUri] as its base URI */
    val rootSchema: SchemaNode = SchemaNode(root.value, rootBaseUri, rootDraft, this)

    /** The root schema, with the `$ref`s that hide the keywords beside them followed (see [resolveRefIfPresent]) */
    val resolvedRootSchema: SchemaNode by lazy { resolveRefIfPresent(rootSchema) }

    /** The base URI everything inside [root]'s value is read under */
    val rootSubschemaBaseUri: UriReference = rootSchema.innerBaseUri

    init {
        require(additionalDocuments.all { it.initialBaseUri.toString().isNotEmpty() }) {
            "Each additional schema document needs the URI it was retrieved from"
        }
        val metaSchemas = drafts.flatMap { it.metaSchemas() }
        documents = metaSchemas + root + additionalDocuments
        // only their `$id`s identify the meta-schemas
        documentRootsByUri = documents.indices.drop(metaSchemas.size)
            .associateBy({ documents[it].initialBaseUri }) { Place(it, emptyList()) }
        Indexer().indexDocuments()
    }

    /** The retrieval URIs of referenced documents that were not supplied */
    val missingDocumentUris: List<UriReference>
        get() = schemas.keys.mapNotNull { place ->
            val node = nodeAt(place)
            val ref = keywordsOf(node.value, node.draft)[$$"$ref"] as? KsonString ?: return@mapNotNull null
            val refUri = node.innerBaseUri.resolve(ref.value)
            refUri.withoutFragment().takeIf {
                it.toString().isNotEmpty() && schemaIdentifiedBy(refUri) == null && schemaIdentifiedBy(it) == null
            }
        }.distinct()

    /**
     * Resolves [ref] to the schema with that `$id` or retrieval URI, or else follows its JSON Pointer fragment within
     * the schema the rest of its URI identifies.
     *
     * @param ref The reference string (e.g., "#foo", "#/definitions/address", "other.json#/definitions/address")
     * @param currentBaseUri The current base URI context for resolving relative references
     * @return The schema [ref] names with the base URI it is read under, or why [ref] names none
     */
    fun resolveRef(ref: String, currentBaseUri: UriReference): RefResolution {
        val refUri = currentBaseUri.resolve(ref)
        val target = placeNamedBy(refUri) ?: return when (val fragmentPointer = refUri.fragmentPointer()) {
            is FragmentPointer.Malformed -> RefResolution.MalformedPointer(fragmentPointer.message)
            else -> RefResolution.NotFound
        }
        return RefResolution.Found(nodeAt(target))
    }

    /**
     * Follows the chain of `$ref`s from [schema] that hide the keywords beside them (see [SchemaDraft.refHidesSiblings])
     * and returns the schema at the end of the chain, or [schema] itself if it has no such `$ref`.  If a `$ref` doesn't
     * resolve, or leads back into the chain, the chain ends at the schema containing it, with that `$ref` as the
     * result's [unresolvedRef][SchemaNode.unresolvedRef].
     */
    internal fun resolveRefIfPresent(schema: SchemaNode): SchemaNode {
        val followed = mutableListOf<KsonValue>()
        var current = schema
        while (true) {
            val ref = (current.value as? KsonObject)?.propertyLookup?.get($$"$ref") as? KsonString
            if (ref == null || !current.draft.refHidesSiblings) return current
            followed.add(current.value)
            val target = when (val resolution = resolveRef(ref.value, current.innerBaseUri)) {
                is RefResolution.Found -> resolution.target
                is RefResolution.Failure ->
                    return current.copy(unresolvedRef = UnresolvedRef.MissingTarget(ref, resolution))
            }
            val leadsBack = followed.any { it === target.value }
            if (leadsBack) return current.copy(unresolvedRef = UnresolvedRef.Cycle(ref))
            current = target
        }
    }

    /**
     * Returns the place of the schema [uri] identifies, or null if no document was retrieved from [uri] and no schema's
     * `$id` or `$anchor` resolves to it
     */
    private fun schemaIdentifiedBy(uri: UriReference): Place? {
        val identifyingUri = uri.withoutEmptyFragment()
        return documentRootsByUri[identifyingUri]
            ?: schemasById[identifyingUri]?.maxWithOrNull(idOrder)
            ?: schemasByAnchor[identifyingUri]?.maxWithOrNull(idOrder)
    }

    /**
     * Returns the place of the value [uri] names, or null if it names none.  That value is the schema [uri] identifies,
     * or else the value that [uri]'s JSON Pointer fragment names within the schema the rest of [uri] identifies.
     */
    private fun placeNamedBy(uri: UriReference): Place? {
        schemaIdentifiedBy(uri)?.let { return it }
        val pointer = (uri.fragmentPointer() as? FragmentPointer.Valid)?.pointer ?: return null
        return schemaIdentifiedBy(uri.withoutFragment())?.below(pointer.tokens)?.takeIf { valueAt(it) != null }
    }

    /**
     * Returns the value at [place] as a schema, which takes its base URI from the schemas enclosing it, and its draft
     * from them too unless it is a resource that names its own.
     */
    private fun nodeAt(place: Place): SchemaNode {
        val document = documents[place.document]
        val documentDraft = document.value.declaredDraft() ?: rootDraft
        val documentRoot = SchemaNode(document.value, document.initialBaseUri, documentDraft, this)
        if (place.path.isEmpty()) return documentRoot
        val schemasBelowRoot = enclosingPlaces(place).drop(1).mapNotNull { schemas[it] }
        return (schemasBelowRoot + checkNotNull(valueAt(place))).fold(documentRoot, SchemaNode::enter)
    }

    /** Returns the places of the values enclosing the value at [place], outermost first */
    private fun enclosingPlaces(place: Place): List<Place> =
        place.path.indices.map { Place(place.document, place.path.subList(0, it)) }

    /**
     * Returns the draft [schema] is read in when the resource enclosing it is read in [enclosingDraft]: the one of the
     * [drafts] that its `$schema` names, if [schema] has an `$id`, which makes it a schema resource of its own, and
     * otherwise [enclosingDraft].  A `$ref` beside the `$id` hides neither the `$id` nor the `$schema`, since that
     * draft decides what the `$ref` hides.
     */
    internal fun draftOf(schema: KsonValue, enclosingDraft: SchemaDraft): SchemaDraft =
        schema.takeIf(::hasId)?.declaredDraft() ?: enclosingDraft

    /** The draft among the [drafts] whose meta-schema this schema's `$schema` names, if any */
    private fun KsonValue.declaredDraft(): SchemaDraft? {
        val schemaUri = (this as? KsonObject)?.propertyLookup?.get($$"$schema") as? KsonString ?: return null
        val metaSchemaUri = UriReference.parse(schemaUri.value).withoutEmptyFragment()
        return drafts.find { it.metaSchemaUri == metaSchemaUri }
    }

    /** Returns the value at [place], or null if there is none. */
    private fun valueAt(place: Place): KsonValue? =
        place.path.fold<String, KsonValue?>(documents[place.document].value) { value, token ->
            when (value) {
                is KsonObject -> value.propertyLookup[token]
                is KsonList -> JsonPointer.arrayIndex(token)?.let { value.elements.getOrNull(it) }
                else -> null
            }
        }

    /**
     * Finds the schemas in the [documents].  Reading a schema identifies it by its `$id` and `$anchor`, follows its
     * `$ref` and registers its subschemas.
     *
     * A schema is read again when something found later can change its base URI, its draft or where its `$ref` leads:
     * an enclosing value turning out to be a schema with an `$id`, or its `$ref`'s resource gaining or losing an `$id`.
     *
     * A value stays a schema once found, even if a schema found later redirects the `$ref` that pointed to it, or puts
     * its parent in a draft in which it is not a subschema.
     */
    private inner class Indexer {
        /** The schemas to read, some of them again */
        private val unread = ArrayDeque<Place>()

        /** The URI each schema's `$id` resolved to when the schema was last read */
        private val idUris = mutableMapOf<Place, UriReference>()

        /** The URI each schema's `$anchor` resolved to when the schema was last read */
        private val anchorUris = mutableMapOf<Place, UriReference>()

        /** The schemas whose `$ref` has pointed into each resource, by the resource's URI */
        private val referringSchemas = mutableMapOf<UriReference, MutableSet<Place>>()

        /** For each value that has an `$id` but is not a schema yet, the schemas found inside it so far */
        private val awaitingEnclosingSchema = mutableMapOf<Place, MutableList<Place>>()

        fun indexDocuments() {
            documents.indices.forEach { register(Place(it, emptyList())) }
            while (unread.isNotEmpty()) read(unread.removeFirst())
        }

        /** Makes the value at [place] a schema and queues it for reading, unless it is a schema already */
        private fun register(place: Place) {
            if (place in schemas) return
            schemas[place] = checkNotNull(valueAt(place))
            unread += place
            awaitingEnclosingSchema.remove(place)?.let(unread::addAll)
            for (enclosing in enclosingPlaces(place).filter { it !in schemas }) {
                if (hasId(checkNotNull(valueAt(enclosing)))) {
                    awaitingEnclosingSchema.getOrPut(enclosing, ::mutableListOf) += place
                }
            }
        }

        private fun read(schema: Place) {
            val node = nodeAt(schema)
            val draft = node.draft
            val keywords = keywordsOf(node.value, draft)
            val innerBaseUri = node.innerBaseUri
            identifyById(schema, innerBaseUri.takeIf { keywords[$$"$id"] is KsonString })
            val anchor = (keywords[$$"$anchor"] as? KsonString)?.takeIf { draft.supportsAnchors }
            identifyByAnchor(schema, anchor?.let { innerBaseUri.resolve("#${it.value}") })
            (keywords[$$"$ref"] as? KsonString)?.let { follow(schema, innerBaseUri.resolve(it.value)) }
            val schemaObject = node.value as? KsonObject
            if (schemaObject == null || hasHidingRef(schemaObject, draft)) return
            draft.keywords.flatMap { it.declaredSubschemas(schemaObject) }.forEach { declared ->
                register(schema.below(declared.path))
            }
        }

        private fun follow(schema: Place, refUri: UriReference) {
            referringSchemas.getOrPut(refUri.withoutFragment(), ::mutableSetOf) += schema
            placeNamedBy(refUri)?.let(::register)
        }

        /**
         * Identifies [schema] by [idUri], the URI its `$id` resolves to under its current base URI, or by none if
         * [idUri] is null, as when its current draft hides its `$id`.  [schema] stops being identified by the URI its
         * `$id` resolved to before, if that was different.
         */
        private fun identifyById(schema: Place, idUri: UriReference?) {
            val previousIdUri = if (idUri == null) idUris.remove(schema) else idUris.put(schema, idUri)
            if (previousIdUri == idUri) return
            if (previousIdUri != null) {
                schemasById.getValue(previousIdUri) -= schema
                unread += referringSchemas[previousIdUri].orEmpty()
            }
            if (idUri != null) {
                schemasById.getOrPut(idUri, ::mutableSetOf) += schema
                unread += referringSchemas[idUri].orEmpty()
            }
        }

        /**
         * Identifies [schema] by [anchorUri], the URI its `$anchor` resolves to under its current base URI, or by none
         * if [anchorUri] is null, as when its current draft has no `$anchor`.  [schema] stops being identified by the
         * URI its `$anchor` resolved to before, if that was different.
         */
        private fun identifyByAnchor(schema: Place, anchorUri: UriReference?) {
            val previousAnchorUri =
                if (anchorUri == null) anchorUris.remove(schema) else anchorUris.put(schema, anchorUri)
            if (previousAnchorUri != null) {
                schemasByAnchor.getValue(previousAnchorUri) -= schema
            }
            if (anchorUri != null) {
                schemasByAnchor.getOrPut(anchorUri, ::mutableSetOf) += schema
            }
        }
    }
}

/** The base URI this document's root `$id` resolves against: its retrieval URI without a fragment, or else empty */
private val SchemaDocument.initialBaseUri: UriReference
    get() = retrievalUri?.withoutFragment() ?: UriReference.parse("")

/** Whether [value] has a string `$id`, even one that a `$ref` beside it hides */
private fun hasId(value: KsonValue): Boolean = (value as? KsonObject)?.propertyLookup?.get($$"$id") is KsonString

/** The keywords of [schema] that apply in [draft]: a string `$ref` alone when it hides the keywords beside it */
private fun keywordsOf(schema: KsonValue, draft: SchemaDraft): Map<String, KsonValue> {
    val schemaObject = schema as? KsonObject ?: return emptyMap()
    val keywords = schemaObject.propertyLookup
    return if (hasHidingRef(schemaObject, draft)) mapOf($$"$ref" to keywords.getValue($$"$ref")) else keywords
}

/**
 * The base URI for [schema]'s keywords when [schema] is read under this URI: [schema]'s `$id` resolved against this
 * URI, or this URI when [schema] has no `$id` that applies
 */
internal fun UriReference.withIdOf(schema: KsonValue, draft: SchemaDraft): UriReference {
    val id = keywordsOf(schema, draft)[$$"$id"] as? KsonString ?: return this
    return resolve(id.value).withoutEmptyFragment()
}

/** This URI without its fragment when that is empty, as `other.json#` identifies the same schema as `other.json` */
private fun UriReference.withoutEmptyFragment(): UriReference = if (fragment == "") withoutFragment() else this

/** The schema a `$ref` names, or why it names none */
sealed interface RefResolution {
    data class Found(val target: SchemaNode) : RefResolution

    /** Why a `$ref` names no schema */
    sealed interface Failure : RefResolution

    /** No schema has the URI the `$ref` names */
    data object NotFound : Failure

    /** The `$ref`'s fragment is a malformed JSON Pointer, as [message] explains */
    data class MalformedPointer(val message: Message) : Failure
}
