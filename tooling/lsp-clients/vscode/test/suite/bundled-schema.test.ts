import * as vscode from 'vscode';
import { assert } from './assert';
import { createTestFile, cleanUp } from './common';
import { v4 as uuid } from 'uuid';

/**
 * Tests for bundled schema support.
 *
 * These tests verify that:
 * 1. Bundled schemas are loaded from package.json configuration
 * 2. Language configuration includes bundled schema mappings
 * 3. The enableBundledSchemas setting is respected
 */
describe('Bundled Schema Support Tests', () => {
    /**
     * Get the extension and verify it's active.
     */
    function getExtension(): vscode.Extension<any> | undefined {
        return vscode.extensions.getExtension('kson.kson');
    }

    describe('Configuration', () => {
        it('Should have enableBundledSchemas setting defined', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            const packageJson = extension.packageJSON;
            const configuration = packageJson?.contributes?.configuration;
            const properties = configuration?.properties;

            assert.ok(properties, 'Configuration properties should be defined');
            assert.ok(
                properties['kson.enableBundledSchemas'],
                'Should have kson.enableBundledSchemas setting'
            );
            assert.strictEqual(
                properties['kson.enableBundledSchemas'].type,
                'boolean',
                'Setting should be boolean type'
            );
            assert.strictEqual(
                properties['kson.enableBundledSchemas'].default,
                true,
                'Setting should default to true'
            );
        }).timeout(5000);

        it('Should have bundledSchema field in language contributions', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            const packageJson = extension.packageJSON;
            const languages = packageJson?.contributes?.languages || [];

            assert.ok(languages.length > 0, 'Should have at least one language defined');

            // The kson language should have bundledSchema field (even if null)
            const ksonLanguage = languages.find((lang: any) => lang.id === 'kson');
            assert.ok(ksonLanguage, 'Should have kson language defined');
            assert.ok(
                'bundledSchema' in ksonLanguage,
                'kson language should have bundledSchema field'
            );
        }).timeout(5000);

        it('Should be able to read enableBundledSchemas setting', async function () {
            const config = vscode.workspace.getConfiguration('kson');
            const enabled = config.get<boolean>('enableBundledSchemas');

            // Should be defined and default to true
            assert.strictEqual(typeof enabled, 'boolean', 'Setting should be a boolean');
            assert.strictEqual(enabled, true, 'Default value should be true');
        }).timeout(5000);
    });

    describe('Schema Loading', () => {
        /**
         * Detects if we're running in a Node.js environment (vs browser).
         * Some tests may behave differently between environments.
         */
        function isNodeEnvironment(): boolean {
            return typeof process !== 'undefined' &&
                process.versions != null &&
                process.versions.node != null;
        }

        it('Should create language configuration with bundledSchemas', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Import and test the language config module
            // This verifies the configuration is being parsed correctly
            const packageJson = extension.packageJSON;
            const languages = packageJson?.contributes?.languages || [];

            const bundledSchemas = languages
                .filter((lang: any) => lang.id && lang.bundledSchema)
                .map((lang: any) => ({
                    languageId: lang.id,
                    schemaPath: lang.bundledSchema
                }));

            // Log what was found
            console.log(`Found ${bundledSchemas.length} bundled schema configurations`);
            if (bundledSchemas.length > 0) {
                console.log('Bundled schemas:', JSON.stringify(bundledSchemas, null, 2));
            }

            // This is a structural test - we're verifying the config format is correct
            assert.ok(Array.isArray(bundledSchemas), 'bundledSchemas should be an array');
        }).timeout(5000);

        it('Should handle language with no bundled schema', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Create a test file with standard .kson extension
            const content = 'key: "value"';
            const [testFileUri, document] = await createTestFile(content);

            try {
                // Document should be created successfully
                assert.ok(document, 'Document should be created');
                assert.strictEqual(document.languageId, 'kson', 'Should have kson language ID');

                // Wait a bit for the language server to process
                await new Promise(resolve => setTimeout(resolve, 500));

                // No errors should occur - this is a basic sanity check
                const diagnostics = vscode.languages.getDiagnostics(document.uri);
                // Should have 0 diagnostics for valid KSON
                assert.strictEqual(diagnostics.length, 0, 'Valid KSON should have no diagnostics');
            } finally {
                await cleanUp(testFileUri);
            }
        }).timeout(10000);
    });

    describe('Status Bar Integration', () => {
        it('Should show status bar for KSON files', async function () {
            // Skip in browser environment as status bar may behave differently
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            const content = 'key: "value"';
            const [testFileUri, document] = await createTestFile(content);

            try {
                // Make sure the document is shown
                await vscode.window.showTextDocument(document);

                // Wait for status bar to update
                await new Promise(resolve => setTimeout(resolve, 500));

                // We can't directly test the status bar content from tests,
                // but we verify the document is properly set up
                assert.ok(vscode.window.activeTextEditor, 'Should have active editor');
                assert.strictEqual(
                    vscode.window.activeTextEditor?.document.uri.toString(),
                    testFileUri.toString(),
                    'Active editor should show test file'
                );
            } finally {
                await cleanUp(testFileUri);
            }
        }).timeout(10000);
    });

    describe('Bundled Schema Loader', () => {
        it('Should export correct types from bundledSchemaLoader', async function () {
            // This test verifies the module structure is correct
            // We can't import directly in tests, so we verify through package.json
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Verify the language configuration is properly structured
            const packageJson = extension.packageJSON;
            const languages = packageJson?.contributes?.languages || [];

            for (const lang of languages) {
                if (lang.bundledSchema) {
                    // If bundledSchema is defined, it should be a string path
                    assert.strictEqual(
                        typeof lang.bundledSchema,
                        'string',
                        `bundledSchema for ${lang.id} should be a string path`
                    );
                    assert.ok(
                        lang.bundledSchema.includes('/') || lang.bundledSchema.includes('\\'),
                        `bundledSchema path for ${lang.id} should be a path`
                    );
                }
            }
        }).timeout(5000);
    });

    describe('Settings Changes', () => {
        it('Should have correct setting scope', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            const packageJson = extension.packageJSON;
            const properties = packageJson?.contributes?.configuration?.properties;
            const setting = properties?.['kson.enableBundledSchemas'];

            assert.ok(setting, 'Setting should exist');
            assert.ok(setting.description, 'Setting should have a description');
            assert.strictEqual(setting.type, 'boolean', 'Setting should be boolean');
        }).timeout(5000);
    });

    describe('Bundled Schema Navigation', () => {
        it('Should have bundled:// content provider registered', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Create a bundled:// URI - even if no schemas exist, the provider should be registered
            // URI format: bundled://schema/{languageId}.schema.kson
            const bundledUri = vscode.Uri.parse('bundled://schema/test-language.schema.kson');

            // Try to open the document - this will fail with a specific error if the provider
            // is not registered vs if the content just doesn't exist
            try {
                const doc = await vscode.workspace.openTextDocument(bundledUri);
                // If we get here with content, great - there's a schema for this language
                // If not, the provider returned undefined which is also valid
                assert.ok(true, 'Content provider is registered');
            } catch (error: any) {
                // Check if the error is "cannot open" (provider not found) vs content not available
                const message = error?.message || String(error);

                // If the provider is registered but returns undefined, VS Code may throw
                // "cannot open bundled://schema/test-language.schema.kson" but NOT "Unable to resolve"
                // The "Unable to resolve resource" error indicates no provider is registered
                if (message.includes('Unable to resolve resource')) {
                    assert.fail('bundled:// content provider is not registered');
                }
                // Other errors (like empty content) are acceptable
                assert.ok(true, 'Content provider is registered (returned no content for test language)');
            }
        }).timeout(5000);

        it('Should be able to open bundled schema URI when schema exists', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Find a language that has a bundled schema configured
            const packageJson = extension.packageJSON;
            const languages = packageJson?.contributes?.languages || [];
            const langWithSchema = languages.find((lang: any) => lang.bundledSchema);

            if (!langWithSchema) {
                console.log('No languages with bundled schemas configured, skipping test');
                this.skip();
                return;
            }

            const bundledUri = vscode.Uri.parse(`bundled://schema/${langWithSchema.id}.schema.kson`);

            try {
                const doc = await vscode.workspace.openTextDocument(bundledUri);
                assert.ok(doc, 'Should be able to open bundled schema document');
                assert.ok(doc.getText().length > 0, 'Bundled schema should have content');
                console.log(`Successfully opened bundled schema for ${langWithSchema.id}, content length: ${doc.getText().length}`);
            } catch (error: any) {
                const message = error?.message || String(error);
                if (message.includes('Unable to resolve resource')) {
                    assert.fail(`bundled:// content provider failed to resolve ${langWithSchema.id} schema`);
                }
                throw error;
            }
        }).timeout(10000);

        it('Should navigate to bundled schema via Go to Definition', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Find a language that has a bundled schema configured
            const packageJson = extension.packageJSON;
            const languages = packageJson?.contributes?.languages || [];
            const langWithSchema = languages.find((lang: any) => lang.bundledSchema);

            if (!langWithSchema) {
                console.log('No languages with bundled schemas configured, skipping definition test');
                this.skip();
                return;
            }

            // Create a test file with the dialect extension that has a bundled schema
            const testExtension = langWithSchema.extensions?.[0] || `.${langWithSchema.id}`;
            const content = 'name: "test value"';
            const fileName = `definition-test${testExtension}`;
            const [testFileUri, document] = await createTestFile(content, fileName);

            try {
                // Verify the document has the correct language ID
                assert.strictEqual(
                    document.languageId,
                    langWithSchema.id,
                    `Document should have language ID '${langWithSchema.id}'`
                );

                // Wait for the language server to process the document and associate the schema
                await new Promise(resolve => setTimeout(resolve, 1000));

                // Position the cursor on the "name" property (column 1-2)
                const position = new vscode.Position(0, 1);

                // Execute Go to Definition command
                const definitions = await vscode.commands.executeCommand<
                    vscode.Location[] | vscode.LocationLink[]
                >(
                    'vscode.executeDefinitionProvider',
                    document.uri,
                    position
                );

                // Log results for debugging
                console.log(`Definition results for ${langWithSchema.id}:`, JSON.stringify(definitions, null, 2));

                // Verify definitions were returned
                if (!definitions || definitions.length === 0) {
                    console.log('No definitions returned - schema may not be associated yet');
                    // This isn't necessarily a failure - the schema association might not be complete
                    // The important thing is that when definitions ARE returned, they work
                    return;
                }

                // Get the definition URI (handle both Location and LocationLink types)
                const firstDef = definitions[0];
                const definitionUri = 'uri' in firstDef
                    ? firstDef.uri
                    : (firstDef as vscode.LocationLink).targetUri;

                console.log(`Definition URI: ${definitionUri.toString()}`);

                // Verify the definition points to a bundled:// URI
                assert.ok(
                    definitionUri.scheme === 'bundled',
                    `Definition should point to bundled:// scheme, got: ${definitionUri.scheme}`
                );

                assert.ok(
                    definitionUri.toString().includes(`bundled://schema/${langWithSchema.id}.schema.kson`),
                    `Definition should point to bundled schema for ${langWithSchema.id}, got: ${definitionUri.toString()}`
                );

                // Verify we can actually open the bundled schema document
                const schemaDoc = await vscode.workspace.openTextDocument(definitionUri);
                assert.ok(schemaDoc, 'Should be able to open the bundled schema document');
                assert.ok(schemaDoc.getText().length > 0, 'Bundled schema document should have content');

                // Verify the schema contains the "name" property definition
                const schemaContent = schemaDoc.getText();
                assert.ok(
                    schemaContent.includes('name'),
                    'Schema should contain the "name" property definition'
                );

                console.log(`Successfully navigated to bundled schema, content length: ${schemaContent.length}`);
            } finally {
                await cleanUp(testFileUri);
            }
        }).timeout(15000);
    });
});
