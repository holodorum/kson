import {SchemaProvider} from './SchemaProvider.js';
import {FileSystemSchemaProvider} from './FileSystemSchemaProvider.js';
import {ToolingSchemaProvider} from './ToolingSchemaProvider.js';
import {CompositeSchemaProvider} from './CompositeSchemaProvider.js';
import {SchemaProviderResult} from './SchemaProviderResult.js';
import {URI} from "vscode-uri";

/**
 * Node.js-specific schema provider factory.
 * Creates a {@link CompositeSchemaProvider} that combines:
 * 1. {@link FileSystemSchemaProvider} - Workspace .kson-schema.kson configuration (highest priority)
 * 2. {@link ToolingSchemaProvider} - Extension-registered schemas via SchemaRegistry
 *
 * This allows extension schemas to be auto-discovered while allowing workspace
 * configuration to override when needed.
 */
export async function createSchemaProvider(
    workspaceRootUri: URI | undefined,
    logger: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
): Promise<SchemaProviderResult | undefined> {
    try {
        logger.info('Running in Node.js environment, creating CompositeSchemaProvider');

        const providers: SchemaProvider[] = [];

        // FileSystemSchemaProvider has highest priority (explicit user configuration)
        const fileSystemProvider = new FileSystemSchemaProvider(workspaceRootUri || null, logger);
        providers.push(fileSystemProvider);

        // ToolingSchemaProvider has lower priority (extension-registered schemas)
        const toolingProvider = new ToolingSchemaProvider(logger);
        providers.push(toolingProvider);

        const compositeProvider = new CompositeSchemaProvider(providers, logger);
        logger.info('CompositeSchemaProvider created with FileSystemSchemaProvider and ToolingSchemaProvider');

        return {
            provider: compositeProvider,
            toolingProvider
        };
    } catch (error) {
        logger.error(`Failed to create CompositeSchemaProvider: ${error}`);
        return undefined;
    }
}