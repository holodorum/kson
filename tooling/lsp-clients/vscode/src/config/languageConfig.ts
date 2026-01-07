import * as vscode from 'vscode';

const EXTENSION_ID = 'kson.kson';

export interface LanguageConfiguration {
    languageIds: string[];
    fileExtensions: string[];
}

let cachedConfig: LanguageConfiguration | null = null;

/**
 * Get language configuration from the extension's package.json.
 * Result is cached after first call.
 */
export function getLanguageConfiguration(): LanguageConfiguration {
    if (!cachedConfig) {
        const packageJson = vscode.extensions.getExtension(EXTENSION_ID)!.packageJSON;
        const languages = packageJson?.contributes?.languages || [];

        cachedConfig = {
            languageIds: languages.map((lang: any) => lang.id).filter(Boolean),
            fileExtensions: languages
                .flatMap((lang: any) => lang.extensions || [])
                .filter(Boolean)
                .map((ext: string) => ext.replace(/^\./, ''))
        };
    }
    return cachedConfig;
}

/**
 * Check if a language ID is a KSON dialect.
 */
export function isKsonDialect(languageId: string): boolean {
    return getLanguageConfiguration().languageIds.includes(languageId);
}

// For testing only
export function initializeFromPackageJson(packageJson: any): void {
    const languages = packageJson?.contributes?.languages || [];
    cachedConfig = {
        languageIds: languages.map((lang: any) => lang.id).filter(Boolean),
        fileExtensions: languages
            .flatMap((lang: any) => lang.extensions || [])
            .filter(Boolean)
            .map((ext: string) => ext.replace(/^\./, ''))
    };
}

export function resetLanguageConfiguration(): void {
    cachedConfig = null;
}
