import * as vscode from 'vscode';
import { assert } from './assert';
import { createTestFile, cleanUp } from './common';
import TextmateLanguageService from 'vscode-textmate-languageservice';

describe('Syntax Highlighting Tests', () => {
    let testFileUri: vscode.Uri | undefined;

    afterEach(async () => {
        if (testFileUri) {
            await cleanUp(testFileUri);
            testFileUri = undefined;
        }
    });

    async function getTokenScopesAtPosition(document: vscode.TextDocument, line: number, character: number): Promise<string[]> {
        const position = new vscode.Position(line, character);
        const tokenInfo = await TextmateLanguageService.api.getScopeInformationAtPosition(document, position);
        console.log(`Token at line ${line}, char ${character}:`, tokenInfo);
        return tokenInfo.scopes;
    }

    it('Should highlight Python embedded blocks', async () => {
        const content = `key: %python
            print("Hello, World!")
            def greet(name):
                return f"Hello, {name}!"
            %%`;

        const [uri, document] = await createTestFile(content);
        testFileUri = uri;

        // Check that Python code is tagged as python
        const pythonScopes = await getTokenScopesAtPosition(document, 1, 10);
        console.log("Python code scopes:", pythonScopes);
        assert.ok(pythonScopes.some(scope => scope.includes('source.python') || scope.includes('meta.embedded.python')));
    }).timeout(10000);

    /**
     * Every embed-block rule carries a `meta.embedded.block.<language>.kson` name, so the
     * scope on a block's body tells us which rule actually matched the tag. Unknown tags
     * fall through to the catch-all, which is named `...block.generic.kson`.
     */
    async function matchedEmbedLanguage(document: vscode.TextDocument, line: number): Promise<string> {
        const scopes = await getTokenScopesAtPosition(document, line, 5);
        const scope = scopes.find(s => /^meta\.embedded\.block\..+\.kson$/.test(s));
        assert.ok(scope, `No meta.embedded.block scope at line ${line}; got ${JSON.stringify(scopes)}`);
        return scope!.slice('meta.embedded.block.'.length, -'.kson'.length);
    }

    describe('Embed tag language matching', () => {
        // Language ids and aliases are pasted into a regex by
        // shared/scripts/generate-tm-embed-block.ts. Unescaped, the `c++` alias reads as
        // "one or more c" and swallows every tag starting with `c`. Unanchored, a tag only
        // has to *start with* a known name, so `%jsonnet` matches JavaScript. A KSON tag
        // runs from the delimiter to the newline (docs/readme.md:354).
        const cases: Array<{ tag: string, expected: string, why: string }> = [
            // Aliases containing regex metacharacters must be matched literally.
            {tag: '%c++', expected: 'cpp', why: 'the `c++` alias is escaped'},
            {tag: '%c#', expected: 'csharp', why: 'the `c#` alias is escaped'},
            {tag: '%cobol', expected: 'generic', why: '`c++` must not collapse to a bare `c`'},
            {tag: '%csharp', expected: 'csharp', why: 'the csharp rule must be reachable'},

            // The language name must be anchored, not merely a prefix of the tag.
            {tag: '%json', expected: 'json', why: 'the json rule must not be shadowed by `js`'},
            {tag: '%jsonnet', expected: 'generic', why: 'a longer tag must not match a shorter name'},
            {tag: '%pythonic', expected: 'generic', why: 'a longer tag must not match a shorter name'},
            {tag: '%rst', expected: 'generic', why: 'a longer tag must not match the `rs` alias'},

            // Tags that already resolved correctly, kept here so the anchor cannot over-tighten.
            {tag: '%python', expected: 'python', why: 'a plain tag still matches'},
            {tag: '%py', expected: 'python', why: 'a plain alias still matches'},
            {tag: '$python', expected: 'python', why: 'the `$` delimiter still matches'},
            {tag: '%kotlin', expected: 'generic', why: 'an unknown language falls through'},

            // Whitespace or end of line bounds the name; a colon is ordinary tag text.
            {tag: '%python v3', expected: 'python', why: 'metadata may follow the name'},
            {
                tag: '%sql "server=10.0.1.174;uid=root;database=company"',
                expected: 'sql',
                why: 'the tag from docs/readme.md:374 resolves'
            },
            {tag: '%python:', expected: 'generic', why: 'the tag is `python:`, not `python`'},
            {tag: '%python: v3', expected: 'generic', why: 'a colon does not end the name'},

            // The lexer skips inline whitespace after the delimiter (Lexer.kt).
            {tag: '% python', expected: 'python', why: 'a space may precede the tag'},
            {tag: '%\tpython', expected: 'python', why: 'a tab may precede the tag'},
            {tag: '$ python', expected: 'python', why: 'the `$` delimiter allows it too'},
        ];

        for (const {tag, expected, why} of cases) {
            it(`Should highlight \`${tag}\` as ${expected} because ${why}`, async () => {
                const [uri, document] = await createTestFile(`key: ${tag}\n    body\n    %%`);
                testFileUri = uri;

                assert.strictEqual(await matchedEmbedLanguage(document, 1), expected,
                    `\`${tag}\` matched the wrong embed-block rule`);
            }).timeout(10000);
        }

        // A mismatched grammar can leave a multi-line construct open. The embed block's own
        // `end` pattern is only tested when its rule is back on top of the TextMate rule
        // stack, so an unterminated injected grammar keeps the block open to end of file and
        // takes every following block with it.
        it('Should not let an unknown language swallow the rest of the file', async () => {
            const content = `weekly: %jsonnet
    { total: std.foldl(function(a, b) a + b, [4, 4.5], 0) }
    %%
after: %python
    print("still python")
    %%`;

            const [uri, document] = await createTestFile(content);
            testFileUri = uri;

            assert.strictEqual(await matchedEmbedLanguage(document, 1), 'generic',
                '`%jsonnet` should fall through to the catch-all');
            assert.strictEqual(await matchedEmbedLanguage(document, 4), 'python',
                'the block after `%jsonnet` should be unaffected');
        }).timeout(10000);
    });
    /**
     * An embed block's content "always ends at the first raw occurrence of the end-delimiter ...
     * without exception" (docs/readme.md:370), which includes a delimiter sitting at the end of
     * the last content line. A line-anchored `end` pattern cannot match one there, so the block
     * runs on and takes the rest of the document with it. The risk is per-language: only a block
     * whose rule `include`s another grammar has to outrace that grammar's own patterns, so the
     * languages below carry the risk and the untagged `%nosuchlang` case is the control.
     */
    describe('Embed block termination', () => {
        /** The embed-block rule covering a position, or undefined when it is outside any block. */
        async function embedBlockAtLine(document: vscode.TextDocument, line: number, character: number): Promise<string | undefined> {
            const scopes = await getTokenScopesAtPosition(document, line, character);
            const scope = scopes.find(s => /^meta\.embedded\.block\..+\.kson$/.test(s));
            return scope?.slice('meta.embedded.block.'.length, -'.kson'.length);
        }

        const cases: Array<{ tag: string, body: string, expected: string }> = [
            {tag: 'markdown', body: '# Heading `tick`', expected: 'markdown'},
            {tag: 'python', body: 'print("x")', expected: 'python'},
            {tag: 'ruby', body: 'puts "x"', expected: 'ruby'},
            {tag: 'sql', body: 'select 1', expected: 'sql'},
            {tag: 'javascript', body: 'let x = 1', expected: 'javascript'},
            {tag: 'nosuchlang', body: 'whatever', expected: 'generic'},
        ];

        for (const {tag, body, expected} of cases) {
            it(`Should end a \`%${tag}\` block at a closer on the last content line`, async () => {
                const [uri, document] = await createTestFile(`key: %${tag}\n  ${body}%%\nafter: 1`);
                testFileUri = uri;

                // The content still reaches the right language's grammar.
                assert.strictEqual(await embedBlockAtLine(document, 1, 2), expected,
                    `\`%${tag}\` content should be scoped as ${expected}`);

                // The closer scopes as the block's end, not as embedded content.
                const closerScopes = await getTokenScopesAtPosition(document, 1, 2 + body.length);
                assert.ok(closerScopes.includes('punctuation.section.embedded.end.kson'),
                    `the \`%%\` on the content line should close the block; got ${JSON.stringify(closerScopes)}`);

                // And the block ends there, rather than swallowing the key that follows.
                assert.strictEqual(await embedBlockAtLine(document, 2, 0), undefined,
                    `the \`%${tag}\` block ran on past its closer`);
            }).timeout(10000);
        }
    });
});