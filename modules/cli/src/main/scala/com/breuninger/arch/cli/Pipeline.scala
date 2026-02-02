package com.breuninger.arch.cli

import com.breuninger.arch.ir._
import com.breuninger.arch.parser._
import com.breuninger.arch.renderer._
import com.breuninger.arch.validator._
import java.nio.file.Path

/**
 * Orchestrates the migration pipeline: parse → validate → render
 */
trait MigrationPipeline {
  def parser: ScalaParser
  def validator: ArchitectureValidator
  def renderer: KotlinRenderer.type

  /**
   * Execute the complete migration pipeline
   */
  def migrate(
    inputPath: Path,
    outputPath: Path,
    config: MigrationConfig
  ): Either[PipelineError, MigrationResult]
}

/**
 * Configuration for migration
 */
case class MigrationConfig(
  skipValidation: Boolean = false,
  renderConfig: RenderConfig,
  failOnWarnings: Boolean = false
)

/**
 * Result of migration
 */
case class MigrationResult(
  inputPath: Path,
  outputPath: Path,
  modelsProcessed: Int,
  portsProcessed: Int,
  servicesProcessed: Int = 0,
  validationResult: Option[ValidationResult]
)

/**
 * Pipeline execution error
 */
sealed trait PipelineError {
  def message: String
}

case class ParseFailure(parseError: ParseError) extends PipelineError {
  def message: String = s"Parse failed: ${parseError.message}"
}

case class ValidationFailure(validationResult: ValidationResult) extends PipelineError {
  def message: String = {
    val errorCount = validationResult.errors.size
    s"Validation failed with $errorCount error(s)"
  }
}

case class RenderFailure(renderError: RenderError) extends PipelineError {
  def message: String = s"Render failed: ${renderError.message}"
}

case class IOFailure(ioError: Throwable) extends PipelineError {
  def message: String = s"I/O error: ${ioError.getMessage}"
}
