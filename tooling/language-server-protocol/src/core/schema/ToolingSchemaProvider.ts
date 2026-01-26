import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';
import {SchemaProvider} from './SchemaProvider.js';
import {KsonTooling, SchemaLookup, ExtensionSchema} from 'kson-tooling';

/**
 * Schema provider that queries KsonTooling's SchemaRegistry for extension-provided schemas.
 *
 * This provider allows extensions (like KsonHub) to register schemas that are automatically
 * applied to documents without requiring manual .kson-schema.kson configuration.
 *
 * The SchemaRegistry is a Kotlin object compiled to JavaScript via Kotlin/JS multiplatform,
 * making it accessible from both Kotlin code (KsonHub) and TypeScript (Language Server).
 */
export class ToolingSchemaProvider implements SchemaProvider {
    private logger?: {
        info: (message: string) => void;
        warn: (message: string) => void;
        error: (message: string) => void;
    };

    constructor(logger?: {
        info: (message: string) => void;
        warn: (message: string) => void;
        error: (message: string) => void;
    }) {
        this.logger = logger;
    }

    /**
     * Get the schema for a given document URI by querying the SchemaRegistry.
     *
     * @param documentUri The URI of the KSON document
     * @returns TextDocument containing the schema, or undefined if no schema is registered
     */
    getSchemaForDocument(documentUri: DocumentUri): TextDocument | undefined {
        try {
            const lookup = SchemaLookup.getInstance();
            const schema: ExtensionSchema | null | undefined = lookup.getSchemaForFile(documentUri);

            if (schema) {
                this.logger?.info(`Found extension schema for ${documentUri}: ${schema.schemaUri}`);
                // Access the raw schema source from the JsonSchema object
                const rawSchema = schema.schema?.rawSchema;
                if (rawSchema) {
                    return TextDocument.create(
                        schema.schemaUri,
                        'kson',
                        1,
                        rawSchema
                    );
                }
            }

            return undefined;
        } catch (error) {
            this.logger?.error(`Error querying SchemaLookup: ${error}`);
            return undefined;
        }
    }

    /**
     * Reload is a no-op for ToolingSchemaProvider since the registry is managed externally.
     * Extensions register/unregister schemas directly with SchemaRegistry.
     */
    reload(): void {
        // No-op - the registry is managed by extensions
    }

    /**
     * Check if a file URI matches any registered extension schema.
     *
     * Note: Extension-provided schemas are identified by their schemaUri (e.g., "ksonhub://schemas/..."),
     * not by file paths. This method returns false since extension schemas aren't file-based.
     *
     * @param fileUri The URI of the file to check
     * @returns Always false for extension-provided schemas
     */
    isSchemaFile(fileUri: DocumentUri): boolean {
        // Extension schemas are not file-based, they're provided as content strings
        // with virtual URIs (e.g., "ksonhub://schemas/..."), so we return false here
        return false;
    }

    /**
     * Set up a listener for schema registry changes.
     *
     * @param callback Function to call when schemas change
     */
    setOnChangeListener(callback: (extensionId: string) => void): void {
        KsonTooling.getInstance().setOnSchemaChangeListener(callback);
    }
}
