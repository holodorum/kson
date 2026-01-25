import {describe, it, beforeEach, afterEach} from 'mocha';
import * as assert from 'assert';
import {ToolingSchemaProvider} from '../../../core/schema/ToolingSchemaProvider.js';
import {SchemaRegistry, ExtensionSchema} from 'kson-tooling';

describe('ToolingSchemaProvider', () => {
    let logs: string[] = [];

    const logger = {
        info: (msg: string) => logs.push(`INFO: ${msg}`),
        warn: (msg: string) => logs.push(`WARN: ${msg}`),
        error: (msg: string) => logs.push(`ERROR: ${msg}`)
    };

    beforeEach(() => {
        logs = [];
        // Clear listener and any previously registered schemas
        SchemaRegistry.getInstance().setOnChangeListener(null);
        SchemaRegistry.getInstance().clear();
    });

    afterEach(() => {
        // Clear listener before clearing schemas to avoid triggering notifications
        SchemaRegistry.getInstance().setOnChangeListener(null);
        SchemaRegistry.getInstance().clear();
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when no schemas are registered', () => {
            const provider = new ToolingSchemaProvider(logger);

            const schema = provider.getSchemaForDocument('file:///test/file.kson');

            assert.strictEqual(schema, undefined);
        });

        it('should return schema for matching file extension', () => {
            const schemaContent = '{ type: "object" }';
            SchemaRegistry.getInstance().registerExtension('test-extension', [
                new ExtensionSchema(
                    'test://schema/myschema',
                    schemaContent,
                    ['.myext'],
                    []
                )
            ]);

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///test/file.myext');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
            assert.strictEqual(schema!.uri, 'test://schema/myschema');
        });

        it('should return schema for matching file extension with .kson suffix', () => {
            const schemaContent = '{ type: "object" }';
            SchemaRegistry.getInstance().registerExtension('test-extension', [
                new ExtensionSchema(
                    'test://schema/myschema',
                    schemaContent,
                    ['.myext'],
                    []
                )
            ]);

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///test/file.myext.kson');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
        });

        it('should return schema for matching glob pattern', () => {
            const schemaContent = '{ type: "object" }';
            SchemaRegistry.getInstance().registerExtension('test-extension', [
                new ExtensionSchema(
                    'test://schema/config',
                    schemaContent,
                    [],
                    ['config/app.kson']
                )
            ]);

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///project/config/app.kson');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
        });

        it('should return undefined for non-matching file', () => {
            SchemaRegistry.getInstance().registerExtension('test-extension', [
                new ExtensionSchema(
                    'test://schema/myschema',
                    '{}',
                    ['.myext'],
                    []
                )
            ]);

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///test/file.other');

            assert.strictEqual(schema, undefined);
        });

        it('should prioritize file extension over glob pattern', () => {
            const extSchema = '{ title: "extension" }';
            const globSchema = '{ title: "glob" }';

            SchemaRegistry.getInstance().registerExtension('ext1', [
                new ExtensionSchema('test://ext', extSchema, ['.myext'], [])
            ]);
            SchemaRegistry.getInstance().registerExtension('ext2', [
                new ExtensionSchema('test://glob', globSchema, [], ['*.myext'])
            ]);

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///test/file.myext');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), extSchema);
        });
    });

    describe('reload', () => {
        it('should be a no-op (registry is managed externally)', () => {
            const provider = new ToolingSchemaProvider(logger);

            // Should not throw
            provider.reload();
        });
    });

    describe('isSchemaFile', () => {
        it('should return false for extension schemas (not file-based)', () => {
            SchemaRegistry.getInstance().registerExtension('test-extension', [
                new ExtensionSchema(
                    'test://schema/myschema',
                    '{}',
                    ['.myext'],
                    []
                )
            ]);

            const provider = new ToolingSchemaProvider(logger);

            // Extension schemas aren't file-based, so isSchemaFile always returns false
            assert.strictEqual(provider.isSchemaFile('test://schema/myschema'), false);
            assert.strictEqual(provider.isSchemaFile('file:///any/path'), false);
        });
    });

    describe('setOnChangeListener', () => {
        it('should receive notifications when schemas are registered', (done) => {
            const provider = new ToolingSchemaProvider(logger);

            provider.setOnChangeListener((extensionId) => {
                assert.strictEqual(extensionId, 'new-extension');
                done();
            });

            SchemaRegistry.getInstance().registerExtension('new-extension', [
                new ExtensionSchema('test://schema', '{}', ['.test'], [])
            ]);
        });

        it('should receive notifications when schemas are unregistered', (done) => {
            SchemaRegistry.getInstance().registerExtension('existing-extension', [
                new ExtensionSchema('test://schema', '{}', ['.test'], [])
            ]);

            const provider = new ToolingSchemaProvider(logger);

            provider.setOnChangeListener((extensionId) => {
                assert.strictEqual(extensionId, 'existing-extension');
                done();
            });

            SchemaRegistry.getInstance().unregisterExtension('existing-extension');
        });
    });
});
