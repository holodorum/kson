package org.kson.tooling.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.versionOption
import org.kson.tooling.cli.commands.JsonCommand
import org.kson.tooling.cli.commands.YamlCommand
import org.kson.tooling.cli.commands.ValidateCommand
import org.kson.tooling.cli.commands.KsonFormatCommand
import org.kson.tooling.cli.generated.DIALECT_DISPLAY_NAME
import org.kson.tooling.cli.generated.DIALECT_EXTENSION
import org.kson.tooling.cli.generated.DIALECT_NAME
import org.kson.tooling.cli.generated.KSON_VERSION


class KsonCli : CliktCommand(name = DIALECT_NAME) {
    init {
        versionOption(KSON_VERSION, names = setOf("--version", "-V"))
    }
    override fun help(context: Context) = """
        |$DIALECT_DISPLAY_NAME CLI - A tool for working with $DIALECT_DISPLAY_NAME files.
        |
        |$DIALECT_DISPLAY_NAME is a human-friendly data serialization format that supports JSON and YAML conversion.
        |Use the subcommands to transpile, analyze, or validate $DIALECT_DISPLAY_NAME documents.
        |
        |Examples:
        |${"\u0085"}Convert $DIALECT_DISPLAY_NAME to JSON:
        |${"\u0085"}  $DIALECT_NAME json -i input.$DIALECT_EXTENSION -o output.json
        |${"\u0085"}
        |${"\u0085"}Convert $DIALECT_DISPLAY_NAME to YAML:
        |${"\u0085"}  $DIALECT_NAME yaml -i input.$DIALECT_EXTENSION -o output.yaml
        |${"\u0085"}
        |${"\u0085"}Format $DIALECT_DISPLAY_NAME with custom formatting options:
        |${"\u0085"}  $DIALECT_NAME format -i input.$DIALECT_EXTENSION --indent-spaces 4 -o formatted.$DIALECT_EXTENSION
        |${"\u0085"}
        |${"\u0085"}Analyze $DIALECT_DISPLAY_NAME for errors:
        |${"\u0085"}  $DIALECT_NAME analyze -i file.$DIALECT_EXTENSION
        |${"\u0085"}
        |${"\u0085"}Validate against a schema:
        |${"\u0085"}  $DIALECT_NAME json -i input.$DIALECT_EXTENSION -s schema.$DIALECT_EXTENSION -o output.json
        |${"\u0085"}
        |${"\u0085"}Read from stdin (use - or omit filename):
        |${"\u0085"}  cat data.$DIALECT_EXTENSION | $DIALECT_NAME json
        |
        |For more help on a specific command, use: $DIALECT_NAME <command> --help
    """.trimMargin()

    init {
        context {
            allowInterspersedArgs = false
        }
    }

    override fun run() = Unit
}

fun main(args: Array<String>) {
    KsonCli()
        .subcommands(
            KsonFormatCommand(),
            ValidateCommand(),
            JsonCommand(),
            YamlCommand(),
        )
        .main(args)
}