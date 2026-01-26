import {describe, it, beforeEach, afterEach} from 'mocha';
import * as assert from 'assert';
import {ToolingSchemaProvider} from '../../../core/schema/ToolingSchemaProvider.js';
import {KsonTooling, ExtensionSchema} from 'kson-tooling';
// Helper to create a mock JsonSchema with rawSchema property
const mockSchema = (content: string) => ({ rawSchema: content });

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
        KsonTooling.getInstance().setOnSchemaChangeListener(null);
        KsonTooling.getInstance().clearSchemaRegistry();
    });

    afterEach(() => {
        // Clear listener before clearing schemas to avoid triggering notifications
        KsonTooling.getInstance().setOnSchemaChangeListener(null);
        KsonTooling.getInstance().clearSchemaRegistry();
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when no schemas are registered', () => {
            const provider = new ToolingSchemaProvider(logger);

            const schema = provider.getSchemaForDocument('file:///test/file.kson');

            assert.strictEqual(schema, undefined);
        });

        it('should return schema for matching file extension', () => {
            const schemaContent = '{ type: "object" }';
            KsonTooling.getInstance().registerExtension('test-extension', [
                new ExtensionSchema(
                    'test://schema/myschema',
                    mockSchema(schemaContent),
                    ['.myext']
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
            KsonTooling.getInstance().registerExtension('test-extension', [
                new ExtensionSchema(
                    'test://schema/myschema',
                    mockSchema(schemaContent),
                    ['.myext']
                )
            ]);

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///test/file.myext.kson');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
        });

        it('should return undefined for non-matching file', () => {
            KsonTooling.getInstance().registerExtension('test-extension', [
                new ExtensionSchema(
                    'test://schema/myschema',
                    mockSchema('{}'),
                   ['.myext']
                )
            ]);

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///test/file.other');

            assert.strictEqual(schema, undefined);
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
            KsonTooling.getInstance().registerExtension('test-extension', [
                new ExtensionSchema(
                    'test://schema/myschema',
                    mockSchema('{}'),
                    ['.myext']
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

            KsonTooling.getInstance().registerExtension('new-extension', [
                new ExtensionSchema('test://schema', mockSchema('{}'), ['.test'])
            ]);
        });

        it('should receive notifications when schemas are unregistered', (done) => {
            KsonTooling.getInstance().registerExtension('existing-extension', [
                new ExtensionSchema('test://schema', mockSchema('{}'), ['.test'])
            ]);

            const provider = new ToolingSchemaProvider(logger);

            provider.setOnChangeListener((extensionId) => {
                assert.strictEqual(extensionId, 'existing-extension');
                done();
            });

            KsonTooling.getInstance().unregisterExtension('existing-extension');
        });
    });
});
