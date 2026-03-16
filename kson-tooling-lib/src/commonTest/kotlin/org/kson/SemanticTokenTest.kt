package org.kson

import kotlin.test.*

class SemanticTokenTest {

    @Test
    fun testKeyVsStringDistinction() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("key: string"))

        val keyTokens = tokens.filter { it.tokenType == SemanticTokenKind.KEY }
        val stringTokens = tokens.filter { it.tokenType == SemanticTokenKind.STRING }

        assertEquals(1, keyTokens.size, "Should have 1 key token")
        assertEquals(1, stringTokens.size, "Should have 1 string token")
        assertEquals(3, keyTokens[0].length) // "key"
        assertEquals(6, stringTokens[0].length) // "string"
    }

    @Test
    fun testQuotedKeyVsQuotedString() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("""{ "key": "value" }"""))

        val keyTokens = tokens.filter { it.tokenType == SemanticTokenKind.KEY }
        val stringTokens = tokens.filter { it.tokenType == SemanticTokenKind.STRING }

        // Key tokens should all be KEY, value tokens should all be STRING
        assertTrue(keyTokens.isNotEmpty(), "Should have key tokens")
        assertTrue(stringTokens.isNotEmpty(), "Should have string tokens")
        // Keys and values should not overlap — every string-like token is either KEY or STRING
        val totalStringLike = keyTokens.size + stringTokens.size
        assertEquals(6, totalStringLike, "Should have 6 string-like tokens total (3 for key + 3 for value)")
    }

    @Test
    fun testNumberToken() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("number: 42"))

        val numberTokens = tokens.filter { it.tokenType == SemanticTokenKind.NUMBER }
        assertEquals(1, numberTokens.size, "Should have 1 number token")
        assertEquals(2, numberTokens[0].length) // "42"
    }

    @Test
    fun testBooleanAndNullKeywords() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("flag: true\nempty: null\ndisabled: false"))

        val keywords = tokens.filter { it.tokenType == SemanticTokenKind.KEYWORD }
        assertEquals(3, keywords.size, "Should have 3 keyword tokens (true, null, false)")
    }

    @Test
    fun testOperators() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("key: value"))

        val operators = tokens.filter { it.tokenType == SemanticTokenKind.OPERATOR }
        assertEquals(1, operators.size, "Should have 1 operator (colon)")
    }

    @Test
    fun testAllPunctuation() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("""{ "key": [ "v1", "v2" ] }"""))

        val operators = tokens.filter { it.tokenType == SemanticTokenKind.OPERATOR }
        // { [ , ] } = 5 operators + : = 6 total
        assertEquals(6, operators.size, "Should have operators for { : [ , ] }")
    }

    @Test
    fun testNestedObjectKeys() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("outer:\n  inner:\n    deep: value"))

        val keyTokens = tokens.filter { it.tokenType == SemanticTokenKind.KEY }
        assertEquals(3, keyTokens.size, "Should have 3 key tokens for outer, inner, deep")
    }

    @Test
    fun testSkipsWhitespaceAndEof() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("key: value"))

        for (token in tokens) {
            assertNotEquals(SemanticTokenKind.STRING, token.tokenType.takeIf { token.length == 0 },
                "Should not have zero-length tokens")
        }
        // Verify no whitespace or EOF tokens leak through
        assertTrue(tokens.all { it.length > 0 }, "All tokens should have positive length")
    }

    @Test
    fun testEmptyDocument() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse(""))
        // Should not crash, may have no tokens
        assertNotNull(tokens)
    }

    @Test
    fun testEmbedBlock() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("embedBlock: \$tag\n    content\n   \$\$"))

        val embedTags = tokens.filter { it.tokenType == SemanticTokenKind.EMBED_TAG }
        val embedDelims = tokens.filter { it.tokenType == SemanticTokenKind.EMBED_DELIM }

        assertTrue(embedTags.isNotEmpty(), "Should have embed tag tokens")
        assertTrue(embedDelims.isNotEmpty(), "Should have embed delimiter tokens")
    }

    @Test
    fun testAbsolutePositions() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("key: value"))

        // "key" should start at line 0, column 0
        val keyToken = tokens.first { it.tokenType == SemanticTokenKind.KEY }
        assertEquals(0, keyToken.line)
        assertEquals(0, keyToken.column)

        // ":" should be at column 3
        val colonToken = tokens.first { it.tokenType == SemanticTokenKind.OPERATOR }
        assertEquals(0, colonToken.line)
        assertEquals(3, colonToken.column)
    }

    @Test
    fun testArrayWithDashes() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("items:\n  - first\n  - second"))

        val operators = tokens.filter { it.tokenType == SemanticTokenKind.OPERATOR }
        // colons (1) + dashes (2) = 3
        assertEquals(3, operators.size, "Should have operators for : and two -")

        val strings = tokens.filter { it.tokenType == SemanticTokenKind.STRING }
        assertEquals(2, strings.size, "Should have 2 string tokens (first, second)")
    }

    @Test
    fun testMixedContent() {
        val tokens = KsonTooling.getSemanticTokens(KsonTooling.parse("{\n  name: \"Alice\"\n  age: 30\n  active: true\n  role: null\n}"))

        val kinds = tokens.map { it.tokenType }.toSet()
        assertTrue(SemanticTokenKind.KEY in kinds, "Should have key tokens")
        assertTrue(SemanticTokenKind.STRING in kinds, "Should have string tokens")
        assertTrue(SemanticTokenKind.NUMBER in kinds, "Should have number tokens")
        assertTrue(SemanticTokenKind.KEYWORD in kinds, "Should have keyword tokens")
        assertTrue(SemanticTokenKind.OPERATOR in kinds, "Should have operator tokens")
    }
}
