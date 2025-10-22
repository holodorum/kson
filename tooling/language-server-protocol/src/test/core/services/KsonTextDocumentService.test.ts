import {TextDocument} from "vscode-languageserver-textdocument";
import {
    CompletionList,
    CompletionParams,
    Diagnostic,
    DiagnosticSeverity,
    DidOpenTextDocumentParams,
    DocumentFormattingParams,
    ResponseError,
    SemanticTokensParams,
    TextEdit
} from "vscode-languageserver";
import assert from "assert";
import {beforeEach, describe, it} from 'mocha';
import {ConnectionStub} from "../../ConnectionStub";
import {KsonDocumentsManager} from "../../../core/document/KsonDocumentsManager.js";
import {KsonTextDocumentService} from "../../../core/services/KsonTextDocumentService.js";
import {FullDocumentDiagnosticReport} from "vscode-languageserver-protocol/lib/common/protocol.diagnostic";

describe('KsonTextDocumentService', () => {
    let connection: ConnectionStub;
    let service: KsonTextDocumentService;
    let documentsManager: KsonDocumentsManager;
    const TEST_URI = 'test://test.kson';

    beforeEach(() => {
        connection = new ConnectionStub();
        documentsManager = new KsonDocumentsManager();
        service = new KsonTextDocumentService(documentsManager);

        documentsManager.listen(connection);
        service.connect(connection)
    });

    function openDocument(content: string) {
        const document = TextDocument.create(TEST_URI, 'kson', 1, content);
        const params: DidOpenTextDocumentParams = {
            textDocument: {
                uri: document.uri,
                languageId: document.languageId,
                version: document.version,
                text: document.getText()
            }
        };
        connection.didOpenHandler(params);
        return document;
    }

    describe('Formatting', () => {
        async function assertFormatting(content: string, expected: string) {
            openDocument(content);
            const params: DocumentFormattingParams = {
                textDocument: {uri: TEST_URI},
                options: {
                    tabSize: 2,
                    insertSpaces: true
                }
            };
            const result = await connection.formattingHandler(params, {} as any, {} as any, undefined);
            assert.ok(result, "should not get a null or undefined result");

            /**
             * {@link result} is either an {@link ResponseError} or a {@link TextEdit[]}.
             * Here we do the cast if {@link result} is not an error or `fail` otherwise.
             */
            let textEdits: TextEdit[];
            if (result instanceof ResponseError) {
                assert.fail(`Should not have received a ResponseError. Message: ${result.message}`);
            } else {
                textEdits = result as TextEdit[];
            }

            assert.strictEqual(textEdits.length, 1, "Should be one edit");
            const formattedText = textEdits[0].newText;
            assert.deepStrictEqual(formattedText, expected);
        }


        it('should format a simple KSON string', async () => {
            const content = 'name:"John"';
            const expected = 'name: John';
            await assertFormatting(content, expected);
        });

        it('should format a KSON string with nested objects', async () => {
            const content = 'person:{name:"John",age:30}';
            const expected = [
                'person:',
                '  name: John',
                '  age: 30'
            ].join('\n');
            await assertFormatting(content, expected);
        });
    });

    describe('Semantic Tokens', () => {
        it('should provide semantic tokens for a document', async () => {
            const content = 'name: "value"';
            openDocument(content);

            const params: SemanticTokensParams = {
                textDocument: {uri: TEST_URI}
            };

            const result = await connection.semanticTokensHandler(params, {} as any, {} as any, undefined);

            assert.ok(result, "Result should not be null");
        });
    });

    describe('Diagnostics', () => {
        async function assertDiagnostics(content: string, expected: Diagnostic[]) {
            openDocument(content);
            const params = {
                textDocument: {uri: TEST_URI}
            }
            const result = await connection.diagnosticsHandler(params, {} as any, {} as any, undefined);

            /**
             * {@link result} is either an {@link ResponseError} or a {@link FullDocumentDiagnosticReport}.
             * Here we do the cast if {@link result} is not an error or `fail` otherwise.
             */
            let resultReport: FullDocumentDiagnosticReport;
            if (result instanceof ResponseError) {
                assert.fail(`Should not have received a ResponseError. Message: ${result.message}`);
            } else {
                resultReport = result as FullDocumentDiagnosticReport;
            }

            assert.deepStrictEqual(resultReport.items, expected)
        }

        it('should provide diagnostics for a document with errors', async () => {
            const content = 'name: "value" extra';
            const expected: Diagnostic[] = [
                {
                    "range": {
                        "start": {
                            "line": 0,
                            "character": 14
                        },
                        "end": {
                            "line": 0,
                            "character": 19
                        }
                    },
                    "severity": DiagnosticSeverity.Error,
                    "source": "kson",
                    "message": "Unexpected trailing content. The previous content parsed as a complete Kson document.",
                }
            ]
            await assertDiagnostics(content, expected)
        });
    });

    describe('Completions', () => {
        it('should provide enum value completions for status field', async () => {
            // Using the hardcoded TODO schema, create a document with a todo item
            const content = `{
    todos: [
        {
            id: "task-1"
            title: "Test task"
            status: "todo"
        }
    ]
}`;
            openDocument(content);

            // Request completions at the position of the "todo" value (line 5, column 25)
            const params: CompletionParams = {
                textDocument: {uri: TEST_URI},
                position: {line: 5, character: 25}
            };

            const result = await connection.completionHandler(params, {} as any, {} as any, undefined);

            // Should return completions
            assert.ok(result !== null, 'Should return completions');

            let completionList: CompletionList;
            if (result instanceof ResponseError) {
                assert.fail(`Should not have received a ResponseError. Message: ${result.message}`);
            } else {
                completionList = result as CompletionList;
            }

            assert.ok(completionList.items.length > 0, 'Should have completion items');

            // Check that enum values from the schema are included
            const labels = completionList.items.map(item => item.label);
            assert.ok(labels.includes('todo'), 'Should include "todo" enum value');
            assert.ok(labels.includes('in_progress'), 'Should include "in_progress" enum value');
            assert.ok(labels.includes('blocked'), 'Should include "blocked" enum value');
            assert.ok(labels.includes('done'), 'Should include "done" enum value');
            assert.ok(labels.includes('cancelled'), 'Should include "cancelled" enum value');
        });

        it('should provide priority enum completions', async () => {
            // Create a document with priority field
            const content = `{
    todos: [
        {
            id: "task-1"
            title: "Test task"
            status: "todo"
            priority: "high"
        }
    ]
}`;
            openDocument(content);

            // Request completions at the priority value position
            const params: CompletionParams = {
                textDocument: {uri: TEST_URI},
                position: {line: 6, character: 24}
            };

            const result = await connection.completionHandler(params, {} as any, {} as any, undefined);

            // Should return completions
            assert.ok(result !== null, 'Should return completions for priority');

            let completionList: CompletionList;
            if (result instanceof ResponseError) {
                assert.fail(`Should not have received a ResponseError. Message: ${result.message}`);
            } else {
                completionList = result as CompletionList;
            }

            assert.ok(completionList.items.length > 0, 'Should have completion items');

            // Check that priority enum values are included (low, medium, high, critical)
            const labels = completionList.items.map(item => item.label);
            assert.ok(labels.includes('low') || labels.includes('high'), 'Should include priority enum values');
        });
    });
});
