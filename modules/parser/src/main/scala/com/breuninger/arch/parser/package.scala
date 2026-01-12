package com.breuninger.arch

/**
 * Parser module - Scalameta integration
 *
 * Responsible for parsing Scala source code into the IR (Intermediate Representation).
 * Uses Scalameta to analyze Scala AST and extract architectural elements.
 *
 * PUBLIC API:
 * - ScalaParser: Main parsing interface
 * - ParseResult: Result containing domain models and ports
 * - ParseError: Error information with location
 *
 * DEPENDENCY RULES:
 * - Depends ONLY on ir module
 * - Must not reference Kotlin-specific concepts
 * - Must not perform validation (delegate to validator)
 * - Must not generate code (delegate to renderer)
 */
package object parser {
  // Public API defined in ScalaParser.scala
}
