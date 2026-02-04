import * as vscode from 'vscode';
import { getLanguageConfiguration, BundledSchemaMapping } from './languageConfig';

import type { BundledSchemaConfig } from 'kson-language-server';
export type { BundledSchemaConfig };

/**
 * Load all bundled schemas defined in the language configuration.
 * Uses vscode.workspace.fs for cross-platform file access (works in both browser and Node.js).
 *
 * @param extensionUri The URI of the extension root
 * @param logger Optional logger for debugging
 * @returns Array of loaded bundled schema configurations
 */
export async function loadBundledSchemas(
    extensionUri: vscode.Uri,
    logger?: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
): Promise<BundledSchemaConfig[]> {
    const { bundledSchemas } = getLanguageConfiguration();
    const loadedSchemas: BundledSchemaConfig[] = [];

    for (const mapping of bundledSchemas) {
        try {
            const schemaContent = await loadSchemaFile(extensionUri, mapping, logger);
            if (schemaContent) {
                loadedSchemas.push({
                    fileExtension: mapping.fileExtension,
                    schemaContent
                });
            }
        } catch (error) {
            logger?.warn(`Failed to load bundled schema for ${mapping.fileExtension}: ${error}`);
        }
    }

    logger?.info(`Loaded ${loadedSchemas.length} bundled schemas`);
    return loadedSchemas;
}

/**
 * Load a single schema file from the extension.
 */
async function loadSchemaFile(
    extensionUri: vscode.Uri,
    mapping: BundledSchemaMapping,
    logger?: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
): Promise<string | undefined> {
    try {
        const schemaUri = vscode.Uri.joinPath(extensionUri, mapping.schemaPath);
        const schemaBytes = await vscode.workspace.fs.readFile(schemaUri);
        const schemaContent = new TextDecoder().decode(schemaBytes);
        logger?.info(`Loaded bundled schema for .${mapping.fileExtension} from ${mapping.schemaPath}`);
        return schemaContent;
    } catch (error) {
        logger?.warn(`Schema file not found for .${mapping.fileExtension}: ${mapping.schemaPath}`);
        return undefined;
    }
}

/**
 * Check if bundled schemas are enabled via VS Code settings.
 */
export function areBundledSchemasEnabled(): boolean {
    const config = vscode.workspace.getConfiguration('kson');
    return config.get<boolean>('enableBundledSchemas', true);
}
