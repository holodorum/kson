import {describe, it, beforeEach} from 'mocha';
import * as assert from 'assert';
import {CompositeSchemaProvider} from '../../../core/schema/CompositeSchemaProvider.js';
import {SchemaProvider, NoOpSchemaProvider} from '../../../core/schema/SchemaProvider.js';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';

/**
 * Mock schema provider for testing
 */
class MockSchemaProvider implements SchemaProvider {
    private schemas: Map<string, TextDocument> = new Map();
    private schemaFiles: Set<string> = new Set();
    public reloadCalled = false;

    addSchema(pattern: string, content: string): void {
        this.schemas.set(pattern, TextDocument.create(`mock://schema/${pattern}`, 'kson', 1, content));
    }

    addSchemaFile(uri: string): void {
        this.schemaFiles.add(uri);
    }

    getSchemaForDocument(documentUri: DocumentUri): TextDocument | undefined {
        for (const [pattern, schema] of this.schemas) {
            if (documentUri.includes(pattern)) {
                return schema;
            }
        }
        return undefined;
    }

    reload(): void {
        this.reloadCalled = true;
    }

    isSchemaFile(fileUri: DocumentUri): boolean {
        return this.schemaFiles.has(fileUri);
    }
}

describe('CompositeSchemaProvider', () => {
    let logs: string[] = [];

    const logger = {
        info: (msg: string) => logs.push(`INFO: ${msg}`),
        warn: (msg: string) => logs.push(`WARN: ${msg}`),
        error: (msg: string) => logs.push(`ERROR: ${msg}`)
    };

    beforeEach(() => {
        logs = [];
    });

    describe('constructor', () => {
        it('should create with empty providers array', () => {
            const composite = new CompositeSchemaProvider([], logger);
            assert.ok(composite);
            assert.deepStrictEqual(composite.getProviders(), []);
        });

        it('should create with multiple providers', () => {
            const provider1 = new MockSchemaProvider();
            const provider2 = new MockSchemaProvider();

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);

            assert.strictEqual(composite.getProviders().length, 2);
        });
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when no providers have a match', () => {
            const provider1 = new MockSchemaProvider();
            const provider2 = new MockSchemaProvider();

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);
            const schema = composite.getSchemaForDocument('file:///test/file.kson');

            assert.strictEqual(schema, undefined);
        });

        it('should return schema from first matching provider', () => {
            const provider1 = new MockSchemaProvider();
            provider1.addSchema('test', '{ title: "first" }');

            const provider2 = new MockSchemaProvider();
            provider2.addSchema('test', '{ title: "second" }');

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);
            const schema = composite.getSchemaForDocument('file:///test/file.kson');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), '{ title: "first" }');
        });

        it('should fallback to second provider when first has no match', () => {
            const provider1 = new MockSchemaProvider();
            // provider1 has no schemas

            const provider2 = new MockSchemaProvider();
            provider2.addSchema('test', '{ title: "second" }');

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);
            const schema = composite.getSchemaForDocument('file:///test/file.kson');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), '{ title: "second" }');
        });

        it('should prioritize FileSystemSchemaProvider over ToolingSchemaProvider', () => {
            // Simulating: FileSystemSchemaProvider (workspace config) takes priority
            const fileSystemProvider = new MockSchemaProvider();
            fileSystemProvider.addSchema('config', '{ title: "workspace" }');

            // ToolingSchemaProvider (extension-registered)
            const toolingProvider = new MockSchemaProvider();
            toolingProvider.addSchema('config', '{ title: "extension" }');

            // FileSystem first (higher priority)
            const composite = new CompositeSchemaProvider(
                [fileSystemProvider, toolingProvider],
                logger
            );

            const schema = composite.getSchemaForDocument('file:///project/config/app.kson');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), '{ title: "workspace" }');
        });

        it('should use extension schema when no workspace config matches', () => {
            const fileSystemProvider = new MockSchemaProvider();
            // FileSystem provider has no match for this file

            const toolingProvider = new MockSchemaProvider();
            toolingProvider.addSchema('special', '{ title: "extension" }');

            const composite = new CompositeSchemaProvider(
                [fileSystemProvider, toolingProvider],
                logger
            );

            const schema = composite.getSchemaForDocument('file:///project/special/file.kson');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), '{ title: "extension" }');
        });
    });

    describe('reload', () => {
        it('should call reload on all providers', () => {
            const provider1 = new MockSchemaProvider();
            const provider2 = new MockSchemaProvider();

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);
            composite.reload();

            assert.strictEqual(provider1.reloadCalled, true);
            assert.strictEqual(provider2.reloadCalled, true);
        });

        it('should work with empty providers', () => {
            const composite = new CompositeSchemaProvider([], logger);

            // Should not throw
            composite.reload();
        });
    });

    describe('isSchemaFile', () => {
        it('should return false when no providers match', () => {
            const provider1 = new MockSchemaProvider();
            const provider2 = new MockSchemaProvider();

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);

            assert.strictEqual(composite.isSchemaFile('file:///some/file.json'), false);
        });

        it('should return true if any provider matches', () => {
            const provider1 = new MockSchemaProvider();

            const provider2 = new MockSchemaProvider();
            provider2.addSchemaFile('file:///schemas/test.json');

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);

            assert.strictEqual(composite.isSchemaFile('file:///schemas/test.json'), true);
        });

        it('should return true if first provider matches', () => {
            const provider1 = new MockSchemaProvider();
            provider1.addSchemaFile('file:///schemas/config.json');

            const provider2 = new MockSchemaProvider();

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);

            assert.strictEqual(composite.isSchemaFile('file:///schemas/config.json'), true);
        });
    });

    describe('getProviders', () => {
        it('should return a copy of the providers array', () => {
            const provider1 = new MockSchemaProvider();
            const composite = new CompositeSchemaProvider([provider1], logger);

            const providers = composite.getProviders();
            providers.push(new MockSchemaProvider());

            // Original should be unchanged
            assert.strictEqual(composite.getProviders().length, 1);
        });
    });
});
