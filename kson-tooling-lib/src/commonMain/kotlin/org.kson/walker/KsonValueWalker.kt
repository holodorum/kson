package org.kson.walker

import org.kson.parser.Location
import org.kson.value.*

/**
 * [KsonTreeWalker] implementation for [KsonValue] trees.
 *
 * This is the walker used by VSCode/LSP tooling via [org.kson.KsonTooling].
 * It delegates directly to the [KsonValue] sealed class hierarchy.
 */
object KsonValueWalker : KsonTreeWalker<KsonValue> {

    override fun isObject(node: KsonValue): Boolean = node is KsonObject

    override fun isArray(node: KsonValue): Boolean = node is KsonList

    override fun getObjectProperties(node: KsonValue): List<Pair<String, KsonValue>> {
        return (node as? KsonObject)?.propertyMap?.map { (key, prop) -> key to prop.propValue }
            ?: emptyList()
    }

    override fun getArrayElements(node: KsonValue): List<KsonValue> {
        return (node as? KsonList)?.elements ?: emptyList()
    }

    override fun getStringValue(node: KsonValue): String? {
        return (node as? KsonString)?.value
    }

    override fun getLocation(node: KsonValue): Location = node.location
}
