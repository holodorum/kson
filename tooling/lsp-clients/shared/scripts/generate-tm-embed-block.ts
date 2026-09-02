#!/usr/bin/env node

import {readFileSync, writeFileSync} from 'fs';
import {join, dirname} from 'path';
import {fileURLToPath} from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

interface LanguageConfig {
    id: string;
    aliases?: string[];
    scopeName: string;
    contentName: string;

    /**
     * TODO |       could we remove this and strip indent EVERYWHERE? Claude complains how the textmate for `shell`
     * TODO |       breaks in that case. I don't understand that exactly and don't see it break either...
     * TODO |       Intuitively stripping the indent everywhere feels better and less complex  since the indentation is not a part of the embed block.
     * TODO |       If we do keep this we can probably remove the comment. minimize comment
     * Strip the block's own indentation before the injected grammar sees a line, for
     * grammars whose rules are sensitive to leading whitespace. Markdown is one: it
     * reads four leading spaces as a code block. Emitted once per delimiter -- see
     * generateIndentGuard.
     */
    stripIndent?: boolean;
}

/**
 * To add a new language, add it to this array.
 * The generator will automatically create the TextMate pattern
 */
const languages: LanguageConfig[] = [
    {
        id: 'typescript',
        aliases: ['ts'],
        scopeName: 'source.ts',
        contentName: 'meta.embedded.typescript'
    },
    {
        id: 'jinja',
        aliases: ['jinja'],
        scopeName: 'source.jinja',
        contentName: 'meta.embedded.jinja'
    },
    {
        id: 'javascript',
        aliases: ['js'],
        scopeName: 'source.js',
        contentName: 'meta.embedded.javascript'
    },
    {
        id: 'sql',
        scopeName: 'source.sql',
        contentName: 'meta.embedded.sql'
    },
    {
        id: 'python',
        aliases: ['py'],
        scopeName: 'source.python',
        contentName: 'meta.embedded.python'
    },
    {
        id: 'json',
        scopeName: 'source.json',
        contentName: 'meta.embedded.json'
    },
    {
        id: 'html',
        scopeName: 'text.html.basic',
        contentName: 'meta.embedded.html'
    },
    {
        id: 'css',
        scopeName: 'source.css',
        contentName: 'meta.embedded.css'
    },
    {
        id: 'java',
        scopeName: 'source.java',
        contentName: 'meta.embedded.java'
    },
    {
        id: 'cpp',
        aliases: ['c++', 'cxx'],
        scopeName: 'source.cpp',
        contentName: 'meta.embedded.cpp'
    },
    {
        id: 'csharp',
        aliases: ['cs', 'c#'],
        scopeName: 'source.cs',
        contentName: 'meta.embedded.csharp'
    },
    {
        id: 'go',
        scopeName: 'source.go',
        contentName: 'meta.embedded.go'
    },
    {
        id: 'rust',
        aliases: ['rs'],
        scopeName: 'source.rust',
        contentName: 'meta.embedded.rust'
    },
    {
        id: 'ruby',
        aliases: ['rb'],
        scopeName: 'source.ruby',
        contentName: 'meta.embedded.ruby'
    },
    {
        id: 'yaml',
        aliases: ['yml'],
        scopeName: 'source.yaml',
        contentName: 'meta.embedded.yaml'
    },
    {
        id: 'xml',
        scopeName: 'text.xml',
        contentName: 'meta.embedded.xml'
    },
    {
        id: 'markdown',
        aliases: ['md'],
        scopeName: 'text.html.markdown',
        contentName: 'meta.embedded.markdown',
        stripIndent: true
    },
    {
        id: 'shell',
        aliases: ['sh', 'bash', 'zsh'],
        scopeName: 'source.shell',
        contentName: 'meta.embedded.shell'
    }
];

/**
 * Escape regex metacharacters so language ids and aliases are matched literally.
 * Without this, aliases like `c++` and `c#` are interpreted as regex syntax.
 */
const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * The two interchangeable embed block delimiters. A block opened with `%` closes
 * at `%%`, one opened with `$` closes at `$$`, so content may contain the pair it
 * did not open with.
 */
const DELIMITERS = ['%', '$'] as const;
type Delimiter = typeof DELIMITERS[number];

/**
 * // TODO minimize comment although this reads pretty easily.
 * // TODO Could this be the place for the end guard introduced in the previous commit?
 * // We write out the textmate grammar twice, once for each delimiter. So instead of having the end delimiter pattern
 * // introduced in the previous commit ("^(.*?)(\\1\\1)") we might just use $ or % immediately.
 * Build the indentation guard for a `stripIndent` language.
 *
 * `begin` captures the first content line's indent; the engine substitutes it into
 * `while` as a literal, so each line loses exactly that much and no more. Consuming
 * all leading whitespace would flatten indentation belonging to the content.
 *
 * `while` is also what terminates the block: it runs at the start of every line and
 * on failure discards everything stacked above it, so refusing to match the closing
 * delimiter unwinds the injected grammar and lets the block's own `end` be consulted.
 * `match` and `begin`/`end` rules were both tried and cannot do this.
 *
 * The blank-line alternative is required: an empty line has no indent to match, and a
 * rule restarted on the next line would re-measure against that line instead.
 *
 * @param delimiter - only this block's own closing pair is excluded, so the other
 *                    pair stays ordinary content
 */
function generateIndentGuard(scopeName: string, delimiter: Delimiter) {
    const close = escapeRe(delimiter + delimiter);
    return {
        name: 'meta.embedded.indent.kson',
        begin: '^([ \\t]+)',
        while: `^(?!\\s*${close})(?:\\1|(?=\\s*$))`,
        patterns: [{include: scopeName}]
    };
}

/**
 * The patterns injected inside the block.
 *
 * A `stripIndent` language must be generated per delimiter: without one there is no
 * closing pair for its guard to exclude, and the rule would silently be the unguarded
 * one the flag exists to replace. Fail instead of emitting that.
 */
function injectedPatterns(lang: LanguageConfig, delimiter?: Delimiter) {
    if (!lang.stripIndent) {
        return [{include: lang.scopeName}];
    }
    if (!delimiter) {
        throw new Error(
            `${lang.id} sets stripIndent, so it must be generated once per delimiter`);
    }
    return [generateIndentGuard(lang.scopeName, delimiter), {include: lang.scopeName}];
}

/**
 * Generate the textmate grammar pattern for injecting a language in an embed block through the embed tag.
 *
 * @param lang - Optional language configuration. If not provided, generates a generic pattern.
 * @param delimiter - Optional single delimiter to match; omit to match both in one
 *                    rule, as everything but a `stripIndent` language does.
 */
function generateEmbedPattern(lang?: LanguageConfig, delimiter?: Delimiter) {
    const isGeneric = !lang;

    // Build the language pattern
    const langPattern = isGeneric
        ? '(.+)?'
        : `(${[lang.id, ...(lang.aliases ?? [])].map(escapeRe).join('|')})`;

    // Build the name
    const name = isGeneric
        ? 'meta.embedded.block.generic.kson'
        : `meta.embedded.block.${lang.id}.kson`;

    // Build the content name
    const contentName = isGeneric
        ? 'string.unquoted.embedded.kson'
        : lang.contentName;

    // Build the patterns array
    const patterns = isGeneric
        ? []
        : injectedPatterns(lang, delimiter);

    // `end` below closes on capture group 1 doubled, so this group must hold the
    // delimiter whether it is one alternative or both.
    const delimiterPattern = delimiter ? `(${escapeRe(delimiter)})` : '(%|\\$)';

    return {
        __comment: `IMPORTANT: Generated by ./scripts/generate-tm-enmbed-block.ts. Edit that file and re-run to update the TextMate grammar.`,
        name,
        // `[ \t\r]` is the lexer's inline whitespace; `\s` would also match the newline
        // that ends the tag. The lookahead is non-capturing so `end` can backreference \1.
        // TODO check whether this delimiterPattern could be used for the end delimiter change that we introduced in the
        // previous commit.
        begin: `${delimiterPattern}[ \\t\\r]*${langPattern}${isGeneric ? '' : '(?=[ \\t\\r]|$)'}(:|.|\\s)*?$`,
        beginCaptures: {
            "0": {
                name: "punctuation.section.embedded.begin.kson"
            },
            "1": {
                name: "punctuation.definition.embedded.kson"
            },
            "2": {
                name: "entity.name.tag.embedded.kson"
            }
        },
        // TODO both comments might be unnecessary.
        // `^` anchors the match at column 0 so it ties with an embedded grammar's own column-0
        // rules and wins (`end` is tried first). `.*?` is non-greedy, so the block ends at the
        // FIRST delimiter on that line, per docs/readme.md ("the first raw occurrence of the
        // end-delimiter ... without exception").
        end: "^(.*?)(\\1\\1)",
        endCaptures: {
            "1": {
                // Text before the closer is still content: hand it back to the embedded grammar and
                // keep `contentName`, so the closing line's scope stack matches every other content
                // line (package.json's `embeddedLanguages` is keyed on `meta.embedded.<lang>`).
                ...(isGeneric ? {} : {patterns}),
                name: contentName
            },
            "2": {
                name: "punctuation.section.embedded.end.kson"
            }
        },
        contentName,
        patterns
    };
}

/**
 * Since generating the injection patterns is pretty wieldy and error-prone we generate and update that part of the
 * TextMate Grammar automatically.
 */
function generateTmEmbedBlock() {
    const tmLanguagePath = join(__dirname, '..', 'extension', 'config', 'kson.tmLanguage.json');
    const tmLanguage = JSON.parse(readFileSync(tmLanguagePath, 'utf-8'));

    // Generate patterns for all configured languages. Languages that strip their
    // block's indentation need one rule per delimiter; the rest keep a single rule
    // matching both.
    const embedPatterns = languages.flatMap(lang =>
        lang.stripIndent
            ? DELIMITERS.map(delimiter => generateEmbedPattern(lang, delimiter))
            : [generateEmbedPattern(lang)]);

    // Add the generic catch-all pattern at the end
    embedPatterns.push(generateEmbedPattern());

    // Update the embed-blocks repository
    tmLanguage.repository['embed-blocks'] = {
        patterns: embedPatterns
    };

    // Write the updated file
    writeFileSync(tmLanguagePath, JSON.stringify(tmLanguage, null, 2));
    console.log(`Generated ${embedPatterns.length} embed patterns in ${tmLanguagePath}`);
}

// Run the generator
generateTmEmbedBlock();