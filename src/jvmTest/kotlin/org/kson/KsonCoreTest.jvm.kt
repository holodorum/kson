package org.kson

import java.security.MessageDigest

actual internal fun writeTest(testData: TestData) {
    try {
        val outputDir = java.io.File("build/ksonsuite")
        outputDir.mkdirs()
        val isFailing = (testData.jsonContent == null && testData.yamlContent == null)

        val hashCode = MessageDigest.getInstance("SHA-256").digest(
            testData.inputKson.toByteArray()
        ).joinToString("") { "%02x".format(it) }

        val fileName = if(isFailing){
            "n_${hashCode}"
        }else{
            "y_${hashCode}"
        }

        val fileInputKson = java.io.File(outputDir, fileName + ".input.kson" )
        fileInputKson.writeText(testData.inputKson)

        if(!isFailing) {
            val fileExpectedKson = java.io.File(outputDir, fileName + ".expected.kson" )
            fileExpectedKson.writeText(testData.expectedKson!!)

            val fileJson = java.io.File(outputDir, fileName + ".expected.json")
            fileJson.writeText(testData.jsonContent!!)

            val fileYaml = java.io.File(outputDir, fileName + ".expected.yaml")
            fileYaml.writeText(testData.yamlContent!!)
        }
    } catch (e: Exception) {
        // Silently ignore file writing errors - this is just for test suite generation
        println("Warning: Could not write test file: ${e.message}")
    }
}
