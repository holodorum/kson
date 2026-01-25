import {ToolingSchemaProvider} from './ToolingSchemaProvider.js';
import {SchemaProviderResult} from './SchemaProviderResult.js';
import {URI} from "vscode-uri";

/**
 * Browser-specific schema provider factory.
 * Attempts to create a {@link ToolingSchemaProvider} for extension-registered schemas,
 * but falls back gracefully if the SchemaRegistry is not available in the browser environment.
 */
export async function createSchemaProvider(
    workspaceRootUri: URI | undefined,
    logger: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
): Promise<SchemaProviderResult | undefined> {
    try {
        logger.info('Running in browser environment, attempting to use ToolingSchemaProvider');
        const toolingProvider = new ToolingSchemaProvider(logger);

        return {
            provider: toolingProvider,
            toolingProvider
        };
    } catch (error) {
        // SchemaRegistry may not be available in all browser environments
        logger.info(`ToolingSchemaProvider not available in browser: ${error}`);
        return undefined;
    }
}