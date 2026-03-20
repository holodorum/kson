package org.kson.jetbrains.schema

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [KsonJsonSchemaEnabler] verifying that KSON files are recognized
 * by IntelliJ's JSON Schema infrastructure.
 */
class KsonJsonSchemaEnablerTest : BasePlatformTestCase() {

    private val enabler = KsonJsonSchemaEnabler()

    fun testEnabledForKsonFile() {
        val file = myFixture.addFileToProject("test.kson", "key: value")
        assertTrue(
            "Should be enabled for .kson files",
            enabler.isEnabledForFile(file.virtualFile, project)
        )
    }

    fun testNotEnabledForJsonFile() {
        val file = myFixture.addFileToProject("test.json", "{}")
        assertFalse(
            "Should not be enabled for .json files",
            enabler.isEnabledForFile(file.virtualFile, project)
        )
    }

    fun testShowsWidgetForKsonFile() {
        val file = myFixture.addFileToProject("test.kson", "key: value")
        assertTrue(
            "Should show switcher widget for .kson files",
            enabler.shouldShowSwitcherWidget(file.virtualFile)
        )
    }

    fun testKsonFileCanBeSchemaFile() {
        val file = myFixture.addFileToProject("config.schema.kson", "type: object")
        assertTrue(
            "KSON files should be accepted as schema files",
            enabler.canBeSchemaFile(file.virtualFile)
        )
    }

    fun testNonKsonFileCannotBeSchemaFile() {
        val file = myFixture.addFileToProject("schema.txt", "type: object")
        assertFalse(
            "Non-KSON files should not be accepted as schema files",
            enabler.canBeSchemaFile(file.virtualFile)
        )
    }
}
