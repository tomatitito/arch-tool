package com.breuninger.arch.cli

import java.nio.file.Path

/**
 * CLI commands for the architecture migration tool
 */
sealed trait Command

/**
 * Parse Scala source and display IR
 */
case class ParseCommand(
  inputPath: Path,
  verbose: Boolean = false
) extends Command

/**
 * Validate Scala source against architectural rules
 */
case class ValidateCommand(
  inputPath: Path,
  strict: Boolean = false
) extends Command

/**
 * Migrate a single Scala file to Kotlin
 */
case class MigrateCommand(
  inputPath: Path,
  outputPath: Path,
  skipValidation: Boolean = false
) extends Command

/**
 * Migrate a directory of Scala files to Kotlin
 */
case class MigrateBatchCommand(
  inputDir: Path,
  outputDir: Path,
  skipValidation: Boolean = false,
  parallel: Boolean = true
) extends Command

/**
 * Display help information
 */
case object HelpCommand extends Command

/**
 * Display version information
 */
case object VersionCommand extends Command
