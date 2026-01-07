import { assert } from './assert';
import * as path from 'path';
import * as fs from 'fs';
import { initializeLanguageConfiguration, getLanguageConfiguration, isKsonDialect, resetLanguageConfiguration } from '../../src/config/languageConfig';

/**
 * Unit tests for language configuration helpers.
 *
 * These tests verify that the helper functions correctly parse package.json
 * and identify KSON dialects.
 */
describe('Language Configuration Tests', () => {
    let tempDir: string;

    beforeEach(() => {
        // Create a temporary directory for test package.json files
        // Use a fixed temp directory instead of os.tmpdir() for browser compatibility
        const baseTempDir = process.env.TMPDIR || '/tmp';
        tempDir = fs.mkdtempSync(path.join(baseTempDir, 'kson-test-'));

        // Reset language configuration before each test
        resetLanguageConfiguration();
    });

    afterEach(() => {
        // Clean up temporary directory
        if (fs.existsSync(tempDir)) {
            fs.rmSync(tempDir, { recursive: true, force: true });
        }

        // Reset language configuration after each test
        resetLanguageConfiguration();
    });

    function createMockPackageJson(contributes: any) {
        const packageJson = {
            name: 'test-extension',
            contributes
        };
        fs.writeFileSync(
            path.join(tempDir, 'package.json'),
            JSON.stringify(packageJson, null, 2)
        );
    }

    describe('getLanguageConfiguration', () => {
        it('Should extract kson language and extension', () => {
            createMockPackageJson({
                languages: [
                    {
                        id: 'kson',
                        extensions: ['.kson']
                    }
                ]
            });

            initializeLanguageConfiguration(tempDir);
            const config = getLanguageConfiguration();

            assert.deepStrictEqual(config.languageIds, ['kson']);
            assert.deepStrictEqual(config.fileExtensions, ['kson']);
        });

        it('Should extract multiple dialects', () => {
            createMockPackageJson({
                languages: [
                    {
                        id: 'kson',
                        extensions: ['.kson']
                    },
                    {
                        id: 'orchestra',
                        extensions: ['.orchestra']
                    }
                ]
            });

            initializeLanguageConfiguration(tempDir);
            const config = getLanguageConfiguration();

            assert.ok(config.languageIds.includes('kson'), 'Should include kson');
            assert.ok(config.languageIds.includes('orchestra'), 'Should include orchestra');
            assert.ok(config.fileExtensions.includes('kson'), 'Should include .kson extension');
            assert.ok(config.fileExtensions.includes('orchestra'), 'Should include .orchestra extension');
        });

        it('Should handle multiple extensions for one language', () => {
            createMockPackageJson({
                languages: [
                    {
                        id: 'kson',
                        extensions: ['.kson', '.json5']
                    }
                ]
            });

            initializeLanguageConfiguration(tempDir);
            const config = getLanguageConfiguration();

            assert.deepStrictEqual(config.languageIds, ['kson']);
            assert.ok(config.fileExtensions.includes('kson'));
            assert.ok(config.fileExtensions.includes('json5'));
        });

        it('Should strip leading dots from extensions', () => {
            createMockPackageJson({
                languages: [
                    {
                        id: 'kson',
                        extensions: ['.kson', 'other']
                    }
                ]
            });

            initializeLanguageConfiguration(tempDir);
            const config = getLanguageConfiguration();

            assert.ok(config.fileExtensions.includes('kson'), 'Should strip dot from .kson');
            assert.ok(config.fileExtensions.includes('other'), 'Should keep extension without dot');
        });

        it('Should fallback to kson if package.json is missing', () => {
            initializeLanguageConfiguration('/nonexistent/path');
            const config = getLanguageConfiguration();

            assert.deepStrictEqual(config.languageIds, ['kson']);
            assert.deepStrictEqual(config.fileExtensions, ['kson']);
        });

        it('Should fallback to kson if contributes is missing', () => {
            createMockPackageJson({});

            initializeLanguageConfiguration(tempDir);
            const config = getLanguageConfiguration();

            assert.deepStrictEqual(config.languageIds, ['kson']);
            assert.deepStrictEqual(config.fileExtensions, ['kson']);
        });

        it('Should add kson as fallback if not present', () => {
            createMockPackageJson({
                languages: [
                    {
                        id: 'orchestra',
                        extensions: ['.orchestra']
                    }
                ]
            });

            initializeLanguageConfiguration(tempDir);
            const config = getLanguageConfiguration();

            assert.ok(config.languageIds.includes('kson'), 'Should add kson as fallback');
            assert.ok(config.fileExtensions.includes('kson'), 'Should add .kson extension as fallback');
        });
    });

    describe('isKsonDialect', () => {
        it('Should return true for kson language', () => {
            createMockPackageJson({
                languages: [
                    {
                        id: 'kson',
                        extensions: ['.kson']
                    }
                ]
            });

            initializeLanguageConfiguration(tempDir);
            assert.strictEqual(isKsonDialect('kson'), true);
        });

        it('Should return true for registered dialect', () => {
            createMockPackageJson({
                languages: [
                    {
                        id: 'kson',
                        extensions: ['.kson']
                    },
                    {
                        id: 'orchestra',
                        extensions: ['.orchestra']
                    }
                ]
            });

            initializeLanguageConfiguration(tempDir);
            assert.strictEqual(isKsonDialect('orchestra'), true);
        });

        it('Should return false for unregistered language', () => {
            createMockPackageJson({
                languages: [
                    {
                        id: 'kson',
                        extensions: ['.kson']
                    }
                ]
            });

            initializeLanguageConfiguration(tempDir);
            assert.strictEqual(isKsonDialect('python'), false);
        });

        it('Should fallback to kson if not initialized', () => {
            // Don't initialize - test fallback behavior
            assert.strictEqual(isKsonDialect('kson'), true);
            assert.strictEqual(isKsonDialect('other'), false);
        });
    });
});