import {SchemaProvider} from './SchemaProvider.js';
import {ToolingSchemaProvider} from './ToolingSchemaProvider.js';

/**
 * Result of creating a schema provider, including both the provider
 * and the tooling provider for setting up change listeners.
 */
export interface SchemaProviderResult {
    provider: SchemaProvider;
    toolingProvider: ToolingSchemaProvider | undefined;
}
