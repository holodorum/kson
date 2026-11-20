package org.kson.schema.validators

import org.kson.value.KsonList
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonArrayValidator
import org.kson.schema.JsonSchema
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

internal class ContainsValidator(
    private val containsSchema: JsonSchema,
    private val minContains: Long = 1,
    private val maxContains: Long? = null
) : JsonArrayValidator() {
    override fun validateArray(node: KsonList, messageSink: MessageSink, sourceContext: SourceContext) {
        val numMatches = node.elements.count { element ->
            val containsMessageSink = MessageSink()
            containsSchema.validate(element, containsMessageSink)
            !containsMessageSink.hasMessages()
        }

        // too many matches is a contradiction even in a half-typed array, since adding items can't remove matches
        if (maxContains != null && numMatches > maxContains) {
            messageSink.error(
                node.location,
                MessageType.SCHEMA_CONTAINS_TOO_MANY.create(maxContains.toString(), numMatches.toString())
            )
        }

        // a half-typed array may not have enough matching items yet — incompleteness, not a contradiction
        if (sourceContext.mode == ValidationMode.PARTIAL) return

        if (numMatches < minContains) {
            if (minContains == 1L) {
                messageSink.error(node.location, MessageType.SCHEMA_CONTAINS_VALIDATION_FAILED.create())
            } else {
                messageSink.error(
                    node.location,
                    MessageType.SCHEMA_CONTAINS_TOO_FEW.create(minContains.toString(), numMatches.toString())
                )
            }
        }
    }
}
