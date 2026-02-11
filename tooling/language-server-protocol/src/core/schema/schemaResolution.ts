import {TextDocument} from 'vscode-languageserver-textdocument';
import {Analysis, KsonValue, KsonValueType} from 'kson';
import {DocumentUri} from 'vscode-languageserver';
import {SchemaProvider, MetaSchemaProvider} from './SchemaProvider.js';

/**
 * Extract the $schema field value from a parsed KSON analysis result.
 *
 * @param analysis The KSON analysis result
 * @returns The $schema string value, or undefined if not present or not a string
 */
export function extractSchemaId(analysis: Analysis): string | undefined {
    const ksonValue = analysis.ksonValue;
    if (!ksonValue || ksonValue.type !== KsonValueType.OBJECT) {
        return undefined;
    }
    const obj = ksonValue as KsonValue.KsonObject;
    const schemaValue = obj.properties.asJsReadonlyMapView().get('$schema');
    if (!schemaValue || schemaValue.type !== KsonValueType.STRING) {
        return undefined;
    }
    return (schemaValue as KsonValue.KsonString).value;
}

/**
 * Resolve a schema for a document by trying URI-based resolution first,
 * then falling back to content-based metaschema resolution via $schema.
 */
export function resolveSchema(
    provider: SchemaProvider,
    metaSchemaProvider: MetaSchemaProvider | undefined,
    uri: DocumentUri,
    analysis: Analysis
): TextDocument | undefined {
    const schema = provider.getSchemaForDocument(uri);
    if (schema) return schema;

    if (metaSchemaProvider) {
        const schemaId = extractSchemaId(analysis);
        if (schemaId) return metaSchemaProvider.getMetaSchemaForId(schemaId);
    }

    return undefined;
}
