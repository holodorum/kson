import * as vscode from 'vscode';
import { getLanguageConfiguration, BundledSchemaMapping } from './languageConfig';

/**
 * Configuration for a bundled schema to be passed to the LSP server.
 *
 * ## Architecture Note: Why content is passed instead of file URIs
 *
 * Originally, the LSP server created schema documents with `bundled://schema/{fileExtension}` URIs.
 * This caused a bug where "Go to Definition" navigation failed with:
 * "Unable to resolve resource bundled://schema/xxx"
 *
 * VS Code doesn't natively understand the `bundled://` scheme, so we added a
 * `BundledSchemaContentProvider` (see `client/common/BundledSchemaContentProvider.ts`)
 * to handle these URIs and return the schema content.
 *
 * An alternative would be to pass the actual file:// URIs to the server, but this approach
 * was chosen because:
 * 1. Browser environments may not have access to extension files via file://
 * 2. The server doesn't need to know the extension's installation path
 * 3. Schema content is loaded once at startup and kept in memory
 *
 * Integration tests for bundled schema navigation are in `test/suite/bundled-schema.test.ts`.
 */
export interface BundledSchemaConfig {
    /** The file extension this schema applies to */
    fileExtension: string;
    /** The pre-loaded schema content as a string */
    schemaContent: string;
}

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
