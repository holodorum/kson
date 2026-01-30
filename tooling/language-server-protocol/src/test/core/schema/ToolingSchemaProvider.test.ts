import {describe, it, beforeEach, afterEach} from 'mocha';
import * as assert from 'assert';
import {ToolingSchemaProvider} from '../../../core/schema/ToolingSchemaProvider.js';
import {SchemaLookup} from 'kson-tooling';

describe('ToolingSchemaProvider', () => {
    let logs: string[] = [];

    const logger = {
        info: (msg: string) => logs.push(`INFO: ${msg}`),
        warn: (msg: string) => logs.push(`WARN: ${msg}`),
        error: (msg: string) => logs.push(`ERROR: ${msg}`)
    };

    beforeEach(() => {
        logs = [];
        // Clear any previously registered schemas
        SchemaLookup.getInstance()._clearForTesting();
    });

    afterEach(() => {
        SchemaLookup.getInstance()._clearForTesting();
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when no schemas are registered', () => {
            const provider = new ToolingSchemaProvider(logger);

            const schema = provider.getSchemaForDocument('file:///test/file.kson');

            assert.strictEqual(schema, undefined);
        });

        it('should return schema for matching file extension', () => {
            const schemaContent = '{ type: "object" }';
            SchemaLookup.getInstance()._registerForTesting(
                'test-extension',
                'test://schema/myschema',
                schemaContent,
                ['.myext']
            );

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///test/file.myext');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
            assert.strictEqual(schema!.uri, 'test://schema/myschema');
        });

        it('should return schema for matching file extension with .kson suffix', () => {
            const schemaContent = '{ type: "object" }';
            SchemaLookup.getInstance()._registerForTesting(
                'test-extension',
                'test://schema/myschema',
                schemaContent,
                ['.myext']
            );

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///test/file.myext.kson');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
        });

        it('should return schema for matching .kson file extension', () => {
            const schemaContent = '{ type: "object" }';
            SchemaLookup.getInstance()._registerForTesting(
                'test-extension',
                'test://schema/config',
                schemaContent,
                ['.kson']
            );

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///project/config/app.kson');

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
        });

        it('should return undefined for non-matching file', () => {
            SchemaLookup.getInstance()._registerForTesting(
                'test-extension',
                'test://schema/myschema',
                '{}',
                ['.myext']
            );

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///test/file.other');

            assert.strictEqual(schema, undefined);
        });

        it('should return first matching schema when multiple extensions match', () => {
            const schema1 = '{ title: "first" }';
            const schema2 = '{ title: "second" }';

            SchemaLookup.getInstance()._registerForTesting(
                'ext1',
                'test://first',
                schema1,
                ['.myext']
            );
            SchemaLookup.getInstance()._registerForTesting(
                'ext2',
                'test://second',
                schema2,
                ['.myext']
            );

            const provider = new ToolingSchemaProvider(logger);
            const schema = provider.getSchemaForDocument('file:///test/file.myext');

            assert.ok(schema);
            // First registered extension should be matched
            assert.strictEqual(schema!.getText(), schema1);
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
            SchemaLookup.getInstance()._registerForTesting(
                'test-extension',
                'test://schema/myschema',
                '{}',
                ['.myext']
            );

            const provider = new ToolingSchemaProvider(logger);

            // Extension schemas aren't file-based, so isSchemaFile always returns false
            assert.strictEqual(provider.isSchemaFile('test://schema/myschema'), false);
            assert.strictEqual(provider.isSchemaFile('file:///any/path'), false);
        });
    });

});