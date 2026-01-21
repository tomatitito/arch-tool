package com.breuninger.arch.cli

import scopt.OParser
import java.nio.file.{Path, Paths, Files}

/**
 * Main entry point for the architecture migration CLI tool
 *
 * Provides commands for:
 * - parse: Parse Scala source and display IR
 * - validate: Validate architecture against rules
 * - migrate: Migrate a single file from Scala to Kotlin
 * - migrate-batch: Migrate a directory of files
 * - help: Display usage information
 * - version: Display version information
 */
object Main {

  val VERSION = "0.1.0-SNAPSHOT"
  val TOOL_NAME = "arch-tool"

  /**
   * Main entry point
   */
  def main(args: Array[String]): Unit = {
    parseArgs(args) match {
      case Some(command) =>
        val exitCode = execute(command)
        sys.exit(exitCode)
      case None =>
        // scopt already prints error message
        sys.exit(1)
    }
  }

  /**
   * Parse command-line arguments into a Command
   */
  def parseArgs(args: Array[String]): Option[Command] = {
    val builder = OParser.builder[Command]
    val parser = {
      import builder._

      OParser.sequence(
        programName(TOOL_NAME),
        head(TOOL_NAME, VERSION),

        // Help command
        cmd("help")
          .action((_, _) => HelpCommand)
          .text("Display usage information"),

        // Version command
        cmd("version")
          .action((_, _) => VersionCommand)
          .text("Display version information"),

        // Parse command
        cmd("parse")
          .action((_, _) => ParseCommand(Paths.get("."), verbose = false))
          .text("Parse Scala source and display IR")
          .children(
            arg[String]("<file>")
              .required()
              .action((x, c) => c.asInstanceOf[ParseCommand].copy(inputPath = Paths.get(x)))
              .text("Scala source file to parse"),
            opt[Unit]('v', "verbose")
              .action((_, c) => c.asInstanceOf[ParseCommand].copy(verbose = true))
              .text("Enable verbose output")
          ),

        // Validate command
        cmd("validate")
          .action((_, _) => ValidateCommand(Paths.get("."), strict = false))
          .text("Validate architecture against rules")
          .children(
            arg[String]("<file>")
              .required()
              .action((x, c) => c.asInstanceOf[ValidateCommand].copy(inputPath = Paths.get(x)))
              .text("Scala source file to validate"),
            opt[Unit]('s', "strict")
              .action((_, c) => c.asInstanceOf[ValidateCommand].copy(strict = true))
              .text("Enable strict validation (warnings as errors)")
          ),

        // Migrate command
        cmd("migrate")
          .action((_, _) => MigrateCommand(Paths.get("."), Paths.get("."), skipValidation = false))
          .text("Migrate a single Scala file to Kotlin")
          .children(
            arg[String]("<input>")
              .required()
              .action((x, c) => c.asInstanceOf[MigrateCommand].copy(inputPath = Paths.get(x)))
              .text("Input Scala file"),
            arg[String]("<output>")
              .required()
              .action((x, c) => c.asInstanceOf[MigrateCommand].copy(outputPath = Paths.get(x)))
              .text("Output Kotlin file"),
            opt[Unit]("skip-validation")
              .action((_, c) => c.asInstanceOf[MigrateCommand].copy(skipValidation = true))
              .text("Skip architectural validation")
          ),

        // Migrate-batch command
        cmd("migrate-batch")
          .action((_, _) => MigrateBatchCommand(Paths.get("."), Paths.get("."), skipValidation = false, parallel = true))
          .text("Migrate a directory of Scala files to Kotlin")
          .children(
            arg[String]("<input-dir>")
              .required()
              .action((x, c) => c.asInstanceOf[MigrateBatchCommand].copy(inputDir = Paths.get(x)))
              .text("Input directory containing Scala files"),
            arg[String]("<output-dir>")
              .required()
              .action((x, c) => c.asInstanceOf[MigrateBatchCommand].copy(outputDir = Paths.get(x)))
              .text("Output directory for Kotlin files"),
            opt[Unit]("skip-validation")
              .action((_, c) => c.asInstanceOf[MigrateBatchCommand].copy(skipValidation = true))
              .text("Skip architectural validation"),
            opt[Unit]("no-parallel")
              .action((_, c) => c.asInstanceOf[MigrateBatchCommand].copy(parallel = false))
              .text("Disable parallel processing"),
            opt[Unit]("sequential")
              .action((_, c) => c.asInstanceOf[MigrateBatchCommand].copy(parallel = false))
              .text("Process files sequentially (alias for --no-parallel)")
          ),

        // Global help options
        help("help").abbr("h").text("Display usage information"),
        version("version").abbr("v").text("Display version information"),

        checkConfig { _ =>
          // Additional validation can be done here if needed
          success
        }
      )
    }

    OParser.parse(parser, args, HelpCommand)
  }

  /**
   * Execute a parsed command
   *
   * @param command The command to execute
   * @return Exit code (0 for success, non-zero for failure)
   */
  def execute(command: Command): Int = {
    try {
      command match {
        case HelpCommand => executeHelp()
        case VersionCommand => executeVersion()
        case cmd: ParseCommand => executeParse(cmd)
        case cmd: ValidateCommand => executeValidate(cmd)
        case cmd: MigrateCommand => executeMigrate(cmd)
        case cmd: MigrateBatchCommand => executeMigrateBatch(cmd)
      }
    } catch {
      case e: Exception =>
        System.err.println(s"Error: ${e.getMessage}")
        if (sys.env.get("DEBUG").contains("true")) {
          e.printStackTrace(System.err)
        }
        1
    }
  }

  /**
   * Execute help command
   */
  private def executeHelp(): Int = {
    println(s"""$TOOL_NAME - Grammar-based Scala to Kotlin migration tool
       |
       |Usage: $TOOL_NAME <command> [options]
       |
       |Commands:
       |  parse <file> [--verbose]
       |      Parse Scala source and display the intermediate representation (IR)
       |
       |  validate <file> [--strict]
       |      Validate Scala source against architectural rules
       |
       |  migrate <input> <output> [--skip-validation]
       |      Migrate a single Scala file to Kotlin
       |
       |  migrate-batch <input-dir> <output-dir> [options]
       |      Migrate a directory of Scala files to Kotlin
       |      Options:
       |        --skip-validation    Skip architectural validation
       |        --no-parallel        Disable parallel processing
       |        --sequential         Process files sequentially
       |
       |  help, --help, -h
       |      Display this help information
       |
       |  version, --version, -v
       |      Display version information
       |
       |Examples:
       |  # Parse a domain model
       |  $TOOL_NAME parse src/main/scala/domain/UserId.scala
       |
       |  # Validate a port interface
       |  $TOOL_NAME validate src/main/scala/ports/UserRepository.scala --strict
       |
       |  # Migrate a single file
       |  $TOOL_NAME migrate \\
       |    src/main/scala/domain/User.scala \\
       |    src/main/kotlin/domain/User.kt
       |
       |  # Migrate a directory
       |  $TOOL_NAME migrate-batch \\
       |    src/main/scala/domain \\
       |    src/main/kotlin/domain
       |
       |For more information, see the README.md file.
       |""".stripMargin)
    0
  }

  /**
   * Execute version command
   */
  private def executeVersion(): Int = {
    println(s"$TOOL_NAME version $VERSION")
    0
  }

  /**
   * Execute parse command
   */
  private def executeParse(cmd: ParseCommand): Int = {
    if (!Files.exists(cmd.inputPath)) {
      System.err.println(s"Error: File not found: ${cmd.inputPath}")
      return 1
    }

    println(s"Parsing: ${cmd.inputPath}")

    val pipeline = DefaultMigrationPipeline.createStub()
    pipeline.parser.parseFile(cmd.inputPath) match {
      case Right(result) =>
        println("\n=== Parse Result ===\n")
        println(s"Domain Models: ${result.domainModels.size}")
        result.domainModels.foreach { model =>
          println(s"  - ${model.name} (${model.getClass.getSimpleName})")
        }
        println(s"\nPorts: ${result.ports.size}")
        result.ports.foreach { port =>
          println(s"  - ${port.name}")
        }

        if (cmd.verbose) {
          println("\n=== Detailed IR ===\n")
          result.domainModels.foreach { model =>
            println(s"$model\n")
          }
          result.ports.foreach { port =>
            println(s"$port\n")
          }
        }
        0

      case Left(error) =>
        System.err.println(s"Error: ${error.message}")
        error.location.foreach { loc =>
          System.err.println(s"  at ${loc.filePath}:${loc.line}:${loc.column}")
        }
        1
    }
  }

  /**
   * Execute validate command
   */
  private def executeValidate(cmd: ValidateCommand): Int = {
    if (!Files.exists(cmd.inputPath)) {
      System.err.println(s"Error: File not found: ${cmd.inputPath}")
      return 1
    }

    println(s"Validating: ${cmd.inputPath}")
    if (cmd.strict) {
      println("Strict mode: warnings will be treated as errors")
    }

    val pipeline = DefaultMigrationPipeline.createStub()
    pipeline.parser.parseFile(cmd.inputPath) match {
      case Right(parseResult) =>
        val validationResult = pipeline.validator.validate(
          parseResult.domainModels,
          parseResult.ports
        )

        println("\n=== Validation Result ===\n")

        if (validationResult.isValid && validationResult.warnings.isEmpty) {
          println("✓ All checks passed")
          0
        } else {
          if (validationResult.errors.nonEmpty) {
            println(s"Errors: ${validationResult.errors.size}")
            validationResult.errors.foreach { error =>
              println(s"  ✗ [${error.rule}] ${error.message}")
              error.location.foreach { loc =>
                println(s"    at ${loc.filePath}:${loc.line}:${loc.column}")
              }
            }
          }

          if (validationResult.warnings.nonEmpty) {
            println(s"\nWarnings: ${validationResult.warnings.size}")
            validationResult.warnings.foreach { warning =>
              println(s"  ! ${warning.message}")
              warning.location.foreach { loc =>
                println(s"    at ${loc.filePath}:${loc.line}:${loc.column}")
              }
              warning.suggestion.foreach { suggestion =>
                println(s"    Suggestion: $suggestion")
              }
            }
          }

          val hasFailures = validationResult.errors.nonEmpty ||
                           (cmd.strict && validationResult.warnings.nonEmpty)
          if (hasFailures) 1 else 0
        }

      case Left(error) =>
        System.err.println(s"Error parsing file: ${error.message}")
        error.location.foreach { loc =>
          System.err.println(s"  at ${loc.filePath}:${loc.line}:${loc.column}")
        }
        1
    }
  }

  /**
   * Execute migrate command
   */
  private def executeMigrate(cmd: MigrateCommand): Int = {
    if (!Files.exists(cmd.inputPath)) {
      System.err.println(s"Error: Input file not found: ${cmd.inputPath}")
      return 1
    }

    println(s"Migrating: ${cmd.inputPath} -> ${cmd.outputPath}")
    if (cmd.skipValidation) {
      println("Validation skipped")
    }

    val pipeline = DefaultMigrationPipeline.createStub()
    val config = MigrationConfig(
      skipValidation = cmd.skipValidation,
      renderConfig = com.breuninger.arch.renderer.RenderConfig(),
      failOnWarnings = false
    )

    pipeline.migrate(cmd.inputPath, cmd.outputPath, config) match {
      case Right(result) =>
        println("\n=== Migration Complete ===\n")
        println(s"Input:  ${result.inputPath}")
        println(s"Output: ${result.outputPath}")
        println(s"Domain models migrated: ${result.modelsProcessed}")
        println(s"Ports migrated: ${result.portsProcessed}")

        result.validationResult.foreach { validation =>
          if (validation.warnings.nonEmpty) {
            println(s"\nWarnings: ${validation.warnings.size}")
            validation.warnings.foreach { warning =>
              println(s"  ! ${warning.message}")
            }
          }
        }
        0

      case Left(error) =>
        System.err.println(s"\nMigration failed: ${error.message}")
        error match {
          case ParseFailure(parseError) =>
            parseError.location.foreach { loc =>
              System.err.println(s"  at ${loc.filePath}:${loc.line}:${loc.column}")
            }
          case ValidationFailure(result) =>
            result.errors.foreach { err =>
              System.err.println(s"  ✗ [${err.rule}] ${err.message}")
            }
          case _ =>
            // Other error types already have descriptive messages
        }
        1
    }
  }

  /**
   * Execute migrate-batch command
   */
  private def executeMigrateBatch(cmd: MigrateBatchCommand): Int = {
    import scala.jdk.CollectionConverters._
    import java.util.stream.Collectors

    if (!Files.exists(cmd.inputDir) || !Files.isDirectory(cmd.inputDir)) {
      System.err.println(s"Error: Input directory not found or not a directory: ${cmd.inputDir}")
      return 1
    }

    println(s"Batch migrating: ${cmd.inputDir} -> ${cmd.outputDir}")
    println(s"Processing mode: ${if (cmd.parallel) "parallel" else "sequential"}")
    if (cmd.skipValidation) {
      println("Validation skipped")
    }

    // Create output directory if it doesn't exist
    Files.createDirectories(cmd.outputDir)

    // Find all Scala files
    val scalaFiles = Files.walk(cmd.inputDir)
      .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala"))
      .collect(Collectors.toList[Path])
      .asScala
      .toList

    if (scalaFiles.isEmpty) {
      println("No Scala files found to migrate")
      return 0
    }

    println(s"Found ${scalaFiles.size} Scala file(s) to migrate")

    val pipeline = DefaultMigrationPipeline.createStub()
    val config = MigrationConfig(
      skipValidation = cmd.skipValidation,
      renderConfig = com.breuninger.arch.renderer.RenderConfig(),
      failOnWarnings = false
    )

    var successCount = 0
    var failureCount = 0

    // Process files sequentially or in parallel
    val results = scalaFiles.map { inputFile =>
      // Calculate relative path
      val relativePath = cmd.inputDir.relativize(inputFile)

      // Convert .scala to .kt
      val ktPath = relativePath.toString.replaceAll("\\.scala$", ".kt")
      val outputFile = cmd.outputDir.resolve(ktPath)

      // Ensure parent directory exists
      Option(outputFile.getParent).foreach(Files.createDirectories(_))

      println(s"Processing: $relativePath")

      pipeline.migrate(inputFile, outputFile, config) match {
        case Right(result) =>
          println(s"  ✓ Migrated (${result.modelsProcessed} models, ${result.portsProcessed} ports)")
          true

        case Left(error) =>
          System.err.println(s"  ✗ Failed: ${error.message}")
          false
      }
    }

    successCount = results.count(_ == true)
    failureCount = results.count(_ == false)

    println("\n=== Batch Migration Complete ===\n")
    println(s"Successful: $successCount")
    println(s"Failed: $failureCount")
    println(s"Total: ${scalaFiles.size}")

    if (failureCount > 0) 1 else 0
  }
}
