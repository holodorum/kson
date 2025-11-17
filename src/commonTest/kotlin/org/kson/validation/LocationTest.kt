package org.kson.validation

import org.kson.KsonCore
import org.kson.KsonCoreTestError
import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.value.EmbedBlock
import org.kson.value.KsonBoolean
import org.kson.value.KsonList
import org.kson.value.KsonNull
import org.kson.value.KsonNumber
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocationTest: KsonCoreTestError {

    /**
     * dm todo doc, note the START / END convention
     */
    fun assertSubLocationMessageLogged(ksonSource: String, locationExpectations: List<Location>) {
        val parseResult = KsonCore.parseToAst(ksonSource)
        val ksonValue = parseResult.ksonValue
        assertNotNull(ksonValue)
        val messageSink = MessageSink()
        startEndFinder(ksonValue, messageSink)
        assertEquals(locationExpectations, messageSink.loggedMessages().map { it.location })
    }

    fun startEndFinder(ksonValue: KsonValue, messageSink: MessageSink) {
        when (ksonValue) {
            is EmbedBlock -> {
                val startIdx = ksonValue.embedContent.value.indexOf("START")
                val endIdx = ksonValue.embedContent.value.indexOf("END") + 3
                messageSink.error(ksonValue.location.subLocation(startIdx, endIdx),
                    // dm todo need a proper message type
                    MessageType.BLANK_SOURCE.create())
            }
            is KsonString -> {
                val startIdx = ksonValue.value.indexOf("START")
                if (startIdx == -1) {
                    return
                }
                val endIdx = ksonValue.value.indexOf("END")
                messageSink.error(ksonValue.location.subLocation(startIdx, endIdx),
                    // dm todo need a proper message type
                    MessageType.BLANK_SOURCE.create())
            }
            is KsonObject -> {
                ksonValue.propertyMap.values.forEach {
                    startEndFinder(it.propName, messageSink)
                    startEndFinder(it.propValue, messageSink)
                }
            }
            is KsonList -> ksonValue.elements.forEach {
                startEndFinder(it, messageSink)
            }
            // dm todo these always do nothing, yeah?
            is KsonBoolean -> TODO()
            is KsonNull -> TODO()
            is KsonNumber -> TODO()
        }
    }

    @Test
    fun testSubLocationInSimpleStrings() {
        // dm todo test sublocations
        val ksonUnquotedString = """
            key: a_STARTsimpleEND_string
        """.trimIndent()

        assertSubLocationMessageLogged(ksonUnquotedString, listOf(Location.create(0, 7, 0, 20, 7, 20)))

        // dm todo the sublocation calculation here seems to get screwed up by the string quotes
        val ksonQuotedPlainString = """
            key: 'a plainSTART quoted ENDstring'
        """.trimIndent()

        assertSubLocationMessageLogged(ksonQuotedPlainString, listOf(Location.create(0, 13, 0, 29, 13, 29)))

        val ksonQuotedStringWithNewlines = """
            key: 'a quoted string
            with newlines'
        """.trimIndent()
    }

    @Test
    fun testSubLocationInStringWithEscaping() {
        // dm todo test sublocations
        val ksonStringWithEscapes = """
            key: 'this stringSTART\t\n\t ENDhas escapes'
        """.trimIndent()
    }

    @Test
    fun testSubLocationInPlainEmbed() {
        // dm todo test sublocations
        val ksonPlainEmbed = """
            %
            this is a very simple embed, with no escapes or
            indent stripping.  It is equivalent to a string
            with newlines
            %%
        """.trimIndent()
    }

    @Test
    fun testSubLocationInEmbedWithIndent() {
        // dm todo test sublocations
        val ksonIndentedEmbed = """
            key: %
                this is an indented simple embed, with no escapes
                for testing accuracy of sub-location generation
                %%
        """.trimIndent()
    }

    @Test
    fun testSubLocationInEmbedWithEscapes() {
        // dm todo test sublocations
        val ksonIndentedEmbed = """
            %
            this is an indent-free embed block with escaped %\%
            embed delimiters %\\\\% for testing accuracy of
            sub-location generation
            %%
        """.trimIndent()

        // dm todo test sublocations
        val ksonIndentedEmbedWithEscapes = """
            key: %
                this is an indented embed block with escaped %\%
                embed delimiters %\\\\% for testing accuracy of
                sub-location generation
                %%
        """.trimIndent()
    }
}
