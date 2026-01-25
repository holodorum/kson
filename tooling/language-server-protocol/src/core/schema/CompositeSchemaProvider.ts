import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';
import {SchemaProvider} from './SchemaProvider.js';

/**
 * Composite schema provider that combines multiple schema sources with priority ordering.
 *
 * Resolution priority (highest to lowest):
 * 1. FileSystemSchemaProvider - Workspace .kson-schema.kson configuration (explicit user config)
 * 2. ToolingSchemaProvider - Extension-registered schemas via SchemaRegistry
 *
 * This allows users to override extension-provided schemas with workspace-specific
 * configuration when needed, while providing automatic schema discovery for extensions.
 */
export class CompositeSchemaProvider implements SchemaProvider {
    private providers: SchemaProvider[];
    private logger?: {
        info: (message: string) => void;
        warn: (message: string) => void;
        error: (message: string) => void;
    };

    /**
     * Creates a new CompositeSchemaProvider.
     *
     * @param providers Array of schema providers in priority order (first = highest priority)
     * @param logger Optional logger for debugging
     */
    constructor(
        providers: SchemaProvider[],
        logger?: {
            info: (message: string) => void;
            warn: (message: string) => void;
            error: (message: string) => void;
        }
    ) {
        this.providers = providers;
        this.logger = logger;
    }

    /**
     * Get the schema for a given document URI.
     * Queries providers in priority order and returns the first match.
     *
     * @param documentUri The URI of the KSON document
     * @returns TextDocument containing the schema, or undefined if no provider has a match
     */
    getSchemaForDocument(documentUri: DocumentUri): TextDocument | undefined {
        for (const provider of this.providers) {
            const schema = provider.getSchemaForDocument(documentUri);
            if (schema) {
                return schema;
            }
        }
        return undefined;
    }

    /**
     * Reload the schema configuration for all providers.
     */
    reload(): void {
        for (const provider of this.providers) {
            provider.reload();
        }
    }

    /**
     * Check if a file URI is a schema file in any provider.
     *
     * @param fileUri The URI of the file to check
     * @returns True if any provider considers this a schema file
     */
    isSchemaFile(fileUri: DocumentUri): boolean {
        return this.providers.some(provider => provider.isSchemaFile(fileUri));
    }

    /**
     * Get the list of providers for testing or inspection.
     */
    getProviders(): SchemaProvider[] {
        return [...this.providers];
    }
}
