import {describe, it} from 'mocha';
import assert from 'assert';
import {SelectionRangeService} from '../../../core/features/SelectionRangeService.js';
import {createKsonDocument} from '../../TestHelpers.js';
import {Position, SelectionRange} from 'vscode-languageserver';

/**
 * Flatten a SelectionRange parent chain into an array of ranges
 * from innermost to outermost.
 */
function flattenChain(sr: SelectionRange): Array<{startLine: number, startChar: number, endLine: number, endChar: number}> {
    const result = [];
    let current: SelectionRange | undefined = sr;
    while (current) {
        result.push({
            startLine: current.range.start.line,
            startChar: current.range.start.character,
            endLine: current.range.end.line,
            endChar: current.range.end.character
        });
        current = current.parent;
    }
    return result;
}

describe('SelectionRangeService', () => {
    const service = new SelectionRangeService();

    it('should return document range for empty document', () => {
        const doc = createKsonDocument('');
        const results = service.getSelectionRanges(doc, [{line: 0, character: 0}]);
        assert.strictEqual(results.length, 1);
        // Should at least have the document range
        assert.ok(results[0].range);
    });

    it('should return a chain for simple string value', () => {
        const doc = createKsonDocument('"hello"');
        const results = service.getSelectionRanges(doc, [{line: 0, character: 2}]);
        assert.strictEqual(results.length, 1);
        const chain = flattenChain(results[0]);
        // innermost: the string value, outermost: document
        assert.ok(chain.length >= 1);
    });

    it('should return nested chain for object property value', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '  age: 30',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on "Alice" string value (line 1, inside the string)
        const results = service.getSelectionRanges(doc, [{line: 1, character: 10}]);
        assert.strictEqual(results.length, 1);

        const chain = flattenChain(results[0]);
        // Should have multiple levels: string -> property -> object -> document
        assert.ok(chain.length >= 3, `Expected at least 3 levels, got ${chain.length}`);

        // Innermost should be the string value range
        // Outermost should be the full document range
        const outermost = chain[chain.length - 1];
        assert.strictEqual(outermost.startLine, 0);
        assert.strictEqual(outermost.startChar, 0);
    });

    it('should return chain for cursor on property key', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on "name" key (line 1, character 3)
        const results = service.getSelectionRanges(doc, [{line: 1, character: 3}]);
        assert.strictEqual(results.length, 1);

        const chain = flattenChain(results[0]);
        // Should have: key -> property -> object -> document
        assert.ok(chain.length >= 3, `Expected at least 3 levels, got ${chain.length}`);
    });

    it('should handle multiple positions', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '  age: 30',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        const positions: Position[] = [
            {line: 1, character: 10},  // on "Alice"
            {line: 2, character: 8}    // on 30
        ];
        const results = service.getSelectionRanges(doc, positions);
        assert.strictEqual(results.length, 2);

        // Both should have valid chains
        assert.ok(flattenChain(results[0]).length >= 2);
        assert.ok(flattenChain(results[1]).length >= 2);
    });

    it('should handle nested objects', () => {
        const content = [
            '{',
            '  person: {',
            '    name: "Alice"',
            '  }',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on "Alice" (line 2, character 12)
        const results = service.getSelectionRanges(doc, [{line: 2, character: 12}]);
        const chain = flattenChain(results[0]);

        // Should have: string -> property (name:Alice) -> inner object -> property (person:{...}) -> outer object -> document
        assert.ok(chain.length >= 4, `Expected at least 4 levels for nested object, got ${chain.length}`);
    });

    it('should handle arrays', () => {
        const content = [
            '[',
            '  "one"',
            '  "two"',
            '  "three"',
            ']'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on "two" (line 2, character 3)
        const results = service.getSelectionRanges(doc, [{line: 2, character: 3}]);
        const chain = flattenChain(results[0]);

        // Should have: string -> array -> document
        assert.ok(chain.length >= 2, `Expected at least 2 levels for array element, got ${chain.length}`);
    });

    it('should handle cursor on container delimiter', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on opening brace (line 0, character 0)
        const results = service.getSelectionRanges(doc, [{line: 0, character: 0}]);
        const chain = flattenChain(results[0]);

        // Should have: object range -> document range
        assert.ok(chain.length >= 1);
    });

    it('should produce a strictly expanding chain (each parent contains child)', () => {
        const content = [
            '{',
            '  items: [',
            '    "hello"',
            '  ]',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on "hello" (line 2, character 6)
        const results = service.getSelectionRanges(doc, [{line: 2, character: 6}]);
        const chain = flattenChain(results[0]);

        // Verify each range is contained within (or equal to) the next
        for (let i = 0; i < chain.length - 1; i++) {
            const inner = chain[i];
            const outer = chain[i + 1];

            const innerStartsBefore = inner.startLine < outer.startLine ||
                (inner.startLine === outer.startLine && inner.startChar < outer.startChar);
            const innerEndsAfter = inner.endLine > outer.endLine ||
                (inner.endLine === outer.endLine && inner.endChar > outer.endChar);

            assert.ok(!innerStartsBefore,
                `Level ${i} starts before level ${i + 1}: (${inner.startLine}:${inner.startChar}) vs (${outer.startLine}:${outer.startChar})`);
            assert.ok(!innerEndsAfter,
                `Level ${i} ends after level ${i + 1}: (${inner.endLine}:${inner.endChar}) vs (${outer.endLine}:${outer.endChar})`);
        }
    });
});
