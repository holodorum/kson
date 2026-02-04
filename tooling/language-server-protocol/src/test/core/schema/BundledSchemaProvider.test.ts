import {describe, it} from 'mocha';
import * as assert from 'assert';
import {BundledSchemaProvider, BundledSchemaConfig} from '../../../core/schema/BundledSchemaProvider';

describe('BundledSchemaProvider', () => {
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
        it('should create provider with no schemas', () => {
            const provider = new BundledSchemaProvider([], true, logger);
            assert.ok(provider);
            assert.strictEqual(provider.getAvailableFileExtensions().length, 0);
            assert.ok(logs.some(msg => msg.includes('initialized with 0 schemas')));
        });

        it('should create provider with schemas', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ksontest', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.ok(provider);
            assert.strictEqual(provider.getAvailableFileExtensions().length, 1);
            assert.ok(provider.hasBundledSchema('ksontest'));
            assert.ok(logs.some(msg => msg.includes('Loaded bundled schema for extension: ksontest')));
        });

        it('should create provider with multiple schemas', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ext-a', schemaContent: '{ "type": "object" }' },
                { fileExtension: 'ext-b', schemaContent: '{ "type": "array" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.strictEqual(provider.getAvailableFileExtensions().length, 2);
            assert.ok(provider.hasBundledSchema('ext-a'));
            assert.ok(provider.hasBundledSchema('ext-b'));
        });

        it('should respect enabled flag', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ksontest', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, false, logger);

            assert.strictEqual(provider.isEnabled(), false);
            assert.ok(logs.some(msg => msg.includes('enabled: false')));
        });
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when disabled', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ksontest', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, false, logger);

            const schema = provider.getSchemaForDocument('file:///test.ksontest');
            assert.strictEqual(schema, undefined);
        });

        it('should return undefined when no file extension in URI', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ksontest', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const schema = provider.getSchemaForDocument('file:///test');
            assert.strictEqual(schema, undefined);
        });

        it('should return undefined for unknown file extension', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ksontest', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const schema = provider.getSchemaForDocument('file:///test.unknown');
            assert.strictEqual(schema, undefined);
        });

        it('should return schema for matching file extension', () => {
            const schemaContent = '{ "type": "object" }';
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ksontest', schemaContent }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const schema = provider.getSchemaForDocument('file:///test.ksontest');
            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
            assert.strictEqual(schema!.uri, 'bundled://schema/ksontest.schema.kson');
        });

        it('should return same schema for different paths with same extension', () => {
            const schemaContent = '{ "type": "object" }';
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ksontest', schemaContent }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            // Different document URIs with same extension should return same schema
            const schema1 = provider.getSchemaForDocument('file:///a.ksontest');
            const schema2 = provider.getSchemaForDocument('file:///b.ksontest');

            assert.ok(schema1);
            assert.ok(schema2);
            assert.strictEqual(schema1!.uri, schema2!.uri);
        });

        it('should match multi-dot extensions correctly', () => {
            const ksonSchema = '{ "type": "kson" }';
            const orchestraSchema = '{ "type": "orchestra" }';
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kson', schemaContent: ksonSchema },
                { fileExtension: 'orchestra.kson', schemaContent: orchestraSchema }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            // Simple .kson file should match 'kson' extension
            const simpleSchema = provider.getSchemaForDocument('file:///test.kson');
            assert.ok(simpleSchema);
            assert.strictEqual(simpleSchema!.getText(), ksonSchema);

            // Multi-dot .orchestra.kson file should match the longer 'orchestra.kson' extension
            const orchestraResult = provider.getSchemaForDocument('file:///my-config.orchestra.kson');
            assert.ok(orchestraResult);
            assert.strictEqual(orchestraResult!.getText(), orchestraSchema);
        });

        it('should prefer longer extension when multiple match', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kson', schemaContent: '{ "short": true }' },
                { fileExtension: 'config.kson', schemaContent: '{ "long": true }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            // File ending in .config.kson should match the longer extension
            const schema = provider.getSchemaForDocument('file:///app.config.kson');
            assert.ok(schema);
            assert.strictEqual(schema!.getText(), '{ "long": true }');
        });
    });

    describe('isSchemaFile', () => {
        it('should return true for bundled schema URIs', () => {
            const provider = new BundledSchemaProvider([], true, logger);

            assert.strictEqual(provider.isSchemaFile('bundled://schema/test-lang.schema.kson'), true);
            assert.strictEqual(provider.isSchemaFile('bundled://schema/other.schema.kson'), true);
        });

        it('should return false for non-bundled URIs', () => {
            const provider = new BundledSchemaProvider([], true, logger);

            assert.strictEqual(provider.isSchemaFile('file:///test.kson'), false);
            assert.strictEqual(provider.isSchemaFile('untitled:///test.kson'), false);
        });
    });

    describe('setEnabled', () => {
        it('should toggle enabled state', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ksontest', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.strictEqual(provider.isEnabled(), true);

            provider.setEnabled(false);
            assert.strictEqual(provider.isEnabled(), false);

            // Should not return schema when disabled
            const schema = provider.getSchemaForDocument('file:///test.ksontest');
            assert.strictEqual(schema, undefined);

            provider.setEnabled(true);
            assert.strictEqual(provider.isEnabled(), true);

            // Should return schema when re-enabled
            const schema2 = provider.getSchemaForDocument('file:///test.ksontest');
            assert.ok(schema2);
        });
    });

    describe('reload', () => {
        it('should be a no-op (bundled schemas are immutable)', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ksontest', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            // reload should not throw or change anything
            provider.reload();

            assert.strictEqual(provider.getAvailableFileExtensions().length, 1);
            assert.ok(provider.hasBundledSchema('ksontest'));
        });
    });

    describe('hasBundledSchema', () => {
        it('should return true for configured extensions', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ext-a', schemaContent: '{}' },
                { fileExtension: 'ext-b', schemaContent: '{}' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.strictEqual(provider.hasBundledSchema('ext-a'), true);
            assert.strictEqual(provider.hasBundledSchema('ext-b'), true);
            assert.strictEqual(provider.hasBundledSchema('ext-c'), false);
        });
    });

    describe('getAvailableFileExtensions', () => {
        it('should return all configured file extensions', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'alpha', schemaContent: '{}' },
                { fileExtension: 'beta', schemaContent: '{}' },
                { fileExtension: 'gamma', schemaContent: '{}' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const extensions = provider.getAvailableFileExtensions();
            assert.strictEqual(extensions.length, 3);
            assert.ok(extensions.includes('alpha'));
            assert.ok(extensions.includes('beta'));
            assert.ok(extensions.includes('gamma'));
        });
    });
});
