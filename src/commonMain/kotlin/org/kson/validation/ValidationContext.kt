package org.kson.validation

/**
 * Context information available during validation.
 * This allows validators to access metadata about the document being validated,
 * such as its filepath, which can be used to determine which validation rules to apply.
 *
 * @param filepath The filepath of the document being validated, if available
 */
data class ValidationContext(
    val filepath: String? = null
)