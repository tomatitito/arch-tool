package com.breuninger.arch.cli

import com.breuninger.arch.ir._
import com.breuninger.arch.parser._
import com.breuninger.arch.renderer._
import com.breuninger.arch.validator._
import java.nio.file.{Path, Files}
import scala.util.Try

/**
 * Default implementation of MigrationPipeline
 *
 * Orchestrates the complete migration workflow:
 * 1. Parse Scala source -> IR
 * 2. Validate IR against architectural rules
 * 3. Render IR -> Kotlin code
 */
class DefaultMigrationPipeline(
  val parser: ScalaParser,
  val validator: ArchitectureValidator,
  val renderer: KotlinRenderer.type
) extends MigrationPipeline {

  /**
   * Execute the complete migration pipeline
   */
  override def migrate(
    inputPath: Path,
    outputPath: Path,
    config: MigrationConfig
  ): Either[PipelineError, MigrationResult] = {
    for {
      // Step 1: Parse Scala source
      parseResult <- parser.parseFile(inputPath)
        .left.map(ParseFailure.apply)

      // Step 2: Validate architecture (if not skipped)
      validationResult <- {
        if (config.skipValidation) {
          Right(None)
        } else {
          val result = validator.validate(
            parseResult.domainModels,
            parseResult.ports
          )

          if (!result.isValid && config.failOnWarnings) {
            Left(ValidationFailure(result))
          } else if (!result.isValid) {
            Left(ValidationFailure(result))
          } else {
            Right(Some(result))
          }
        }
      }

      // Step 3: Render Kotlin code
      kotlinCode <- renderAll(parseResult)

      // Step 4: Write output file
      _ <- writeOutput(outputPath, kotlinCode)

    } yield {
      MigrationResult(
        inputPath = inputPath,
        outputPath = outputPath,
        modelsProcessed = parseResult.domainModels.size,
        portsProcessed = parseResult.ports.size,
        validationResult = validationResult
      )
    }
  }

  /**
   * Render all IR elements to Kotlin code
   */
  private def renderAll(parseResult: ParseResult): Either[PipelineError, String] = {
    Try {
      val sb = new StringBuilder

      // Render domain models
      parseResult.domainModels.foreach { model =>
        sb.append(renderer.renderDomainModel(model))
        sb.append("\n")
      }

      // Render ports
      parseResult.ports.foreach { port =>
        sb.append(renderer.renderPort(port))
        sb.append("\n")
      }

      sb.toString
    }.toEither.left.map { ex =>
      RenderFailure(RenderError(s"Failed to render Kotlin code: ${ex.getMessage}", Some(ex)))
    }
  }

  /**
   * Write output to file
   */
  private def writeOutput(outputPath: Path, content: String): Either[PipelineError, Unit] = {
    Try {
      // Create parent directories if they don't exist
      Option(outputPath.getParent).foreach(Files.createDirectories(_))

      // Write the file
      Files.writeString(outputPath, content)
      () // Return Unit
    }.toEither.left.map { ex =>
      IOFailure(ex): PipelineError
    }
  }
}

/**
 * Companion object for creating pipeline instances
 */
object DefaultMigrationPipeline {
  /**
   * Create a pipeline with the real ScalametaParser implementation.
   */
  def create(): DefaultMigrationPipeline = {
    new DefaultMigrationPipeline(
      parser = ScalametaParser(),
      validator = StubArchitectureValidator,
      renderer = KotlinRenderer
    )
  }

  /**
   * Create a pipeline with stub implementations
   * (kept for backward compatibility with tests)
   */
  def createStub(): DefaultMigrationPipeline = {
    new DefaultMigrationPipeline(
      parser = StubScalaParser,
      validator = StubArchitectureValidator,
      renderer = KotlinRenderer
    )
  }
}

/**
 * Stub parser implementation for testing
 */
object StubScalaParser extends ScalaParser {
  override def parseFile(path: Path): Either[ParseError, ParseResult] = {
    Left(ParseError("Parser not yet implemented - this is a stub"))
  }

  override def parseString(source: String): Either[ParseError, ParseResult] = {
    Left(ParseError("Parser not yet implemented - this is a stub"))
  }
}

/**
 * Stub validator implementation for testing
 */
object StubArchitectureValidator extends ArchitectureValidator {
  override def validateModels(models: List[DomainModel]): ValidationResult = {
    ValidationResult.valid
  }

  override def validatePorts(ports: List[Port]): ValidationResult = {
    ValidationResult.valid
  }

  override def validate(
    models: List[DomainModel],
    ports: List[Port]
  ): ValidationResult = {
    ValidationResult.valid
  }
}
