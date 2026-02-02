package com.breuninger.arch.parser

import com.breuninger.arch.ir._
import java.nio.file.Path

/**
 * Public API for parsing Scala source code into IR.
 *
 * Implementations use Scalameta to parse Scala AST and extract:
 * - Domain models (case classes, sealed traits)
 * - Port interfaces (trait definitions)
 * - Type signatures and annotations
 */
trait ScalaParser {
  /**
   * Parse a Scala source file into IR domain models
   */
  def parseFile(path: Path): Either[ParseError, ParseResult]

  /**
   * Parse Scala source code from a string
   */
  def parseString(source: String): Either[ParseError, ParseResult]
}

/**
 * Result of parsing Scala source code
 */
case class ParseResult(
  domainModels: List[DomainModel],
  ports: List[Port],
  services: List[Service] = Nil
)

/**
 * Error that occurred during parsing
 */
case class ParseError(
  message: String,
  location: Option[Location] = None,
  cause: Option[Throwable] = None
)
