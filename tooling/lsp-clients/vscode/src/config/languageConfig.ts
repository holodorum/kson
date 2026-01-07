import * as path from 'path';
import * as fs from 'fs';

/**
 * Language configuration extracted from package.json.
 */
export interface LanguageConfiguration {
    languageIds: string[];
    fileExtensions: string[];
}

/**
 * Cached language configuration to avoid repeated file I/O.
 * Initialized once during extension activation.
 */
let cachedConfig: LanguageConfiguration | null = null;

/**
 * Initialize language configuration by reading package.json.
 * This should be called once during extension activation.
 *
 * @param extensionPath Path to the extension directory
 */
export function initializeLanguageConfiguration(extensionPath: string): void {
    cachedConfig = loadLanguageConfiguration(extensionPath);
}

/**
 * Load language IDs and file extensions from package.json.
 * This allows the extension to support dynamically configured dialects.
 *
 * @param extensionPath Path to the extension directory
 * @returns Language configuration with IDs and file extensions
 */
function loadLanguageConfiguration(extensionPath: string): LanguageConfiguration {
    try {
        const packageJsonPath = path.join(extensionPath, 'package.json');
        const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));

        const languages = packageJson?.contributes?.languages || [];
        const languageIds = languages.map((lang: any) => lang.id).filter(Boolean);

        // Collect all file extensions from all languages
        const fileExtensions = languages
            .flatMap((lang: any) => lang.extensions || [])
            .filter(Boolean)
            .map((ext: string) => ext.startsWith('.') ? ext.substring(1) : ext);

        // Always include 'kson' as fallback
        if (!languageIds.includes('kson')) {
            languageIds.push('kson');
        }
        if (!fileExtensions.includes('kson')) {
            fileExtensions.push('kson');
        }

        return { languageIds, fileExtensions };
    } catch (error) {
        console.error('Failed to read package.json, falling back to kson only:', error);
        return { languageIds: ['kson'], fileExtensions: ['kson'] };
    }
}

/**
 * Get the cached language configuration.
 * Must call initializeLanguageConfiguration() first during extension activation.
 *
 * @returns Language configuration with IDs and file extensions
 */
export function getLanguageConfiguration(): LanguageConfiguration {
    if (!cachedConfig) {
        throw new Error('Language configuration not initialized. Call initializeLanguageConfiguration() first.');
    }
    return cachedConfig;
}

/**
 * Check if a language ID is a KSON dialect (including base kson).
 *
 * @param languageId The language ID to check
 * @returns True if the language ID is registered as a KSON dialect
 */
export function isKsonDialect(languageId: string): boolean {
    if (!cachedConfig) {
        // Fallback if not initialized (shouldn't happen in normal use)
        return languageId === 'kson';
    }
    return cachedConfig.languageIds.includes(languageId);
}

/**
 * Reset the cached configuration. Only used for testing.
 * @internal
 */
export function resetLanguageConfiguration(): void {
    cachedConfig = null;
}