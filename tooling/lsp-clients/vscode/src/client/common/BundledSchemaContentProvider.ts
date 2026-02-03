import * as vscode from 'vscode';
import { BundledSchemaConfig } from '../../config/bundledSchemaLoader';

/**
 * TextDocumentContentProvider for bundled:// URIs.
 *
 * ## Why this exists
 *
 * The LSP server creates schema documents with `bundled://schema/{languageId}` URIs
 * for schemas that are bundled with the extension. When "Go to Definition" returns
 * a location pointing to these URIs, VS Code needs to be able to open them.
 *
 * Without this provider, VS Code would fail with:
 * "Unable to resolve resource bundled://schema/xxx"
 *
 * This provider maps `bundled://schema/{languageId}` URIs to the pre-loaded schema
 * content, allowing navigation to work correctly.
 *
 * URI format: bundled://schema/{languageId}
 * Example: bundled://schema/kxt
 *
 * @see BundledSchemaConfig in bundledSchemaLoader.ts for architecture discussion
 * @see test/suite/bundled-schema.test.ts for integration tests
 */
export class BundledSchemaContentProvider implements vscode.TextDocumentContentProvider {
    private schemasByLanguageId: Map<string, string>;

    constructor(bundledSchemas: BundledSchemaConfig[]) {
        this.schemasByLanguageId = new Map();
        for (const schema of bundledSchemas) {
            this.schemasByLanguageId.set(schema.languageId, schema.schemaContent);
        }
    }

    provideTextDocumentContent(uri: vscode.Uri): string | undefined {
        // URI format: bundled://schema/{languageId}
        // uri.authority = "schema"
        // uri.path = "/{languageId}"
        const languageId = uri.path.replace(/^\//, '');
        return this.schemasByLanguageId.get(languageId);
    }
}

/**
 * Register the bundled schema content provider.
 *
 * @param context Extension context for disposable registration
 * @param bundledSchemas The loaded bundled schemas
 * @returns The registered disposable
 */
export function registerBundledSchemaContentProvider(
    context: vscode.ExtensionContext,
    bundledSchemas: BundledSchemaConfig[]
): vscode.Disposable {
    const provider = new BundledSchemaContentProvider(bundledSchemas);
    return vscode.workspace.registerTextDocumentContentProvider('bundled', provider);
}