package com.breuninger.arch.parser

import com.breuninger.arch.ir.Location
import scala.meta.*

/**
 * Extracts source code locations from Scalameta AST nodes.
 *
 * Used for error reporting and debugging to provide precise
 * line/column information for parsed elements.
 */
object LocationExtractor {

  /**
   * Extract location from a Scalameta tree node.
   *
   * @param tree The Scalameta AST node
   * @param filePath Optional file path for the source file
   * @return Location if position information is available, None otherwise
   */
  def extractLocation(tree: Tree, filePath: String = ""): Option[Location] = {
    tree.pos match {
      case pos if pos != Position.None =>
        Some(Location(
          filePath = filePath,
          line = pos.startLine + 1, // Scalameta uses 0-based lines
          column = pos.startColumn + 1 // Also 0-based columns
        ))
      case _ => None
    }
  }

  /**
   * Extract location from a Scalameta position.
   *
   * @param pos The Scalameta position
   * @param filePath Optional file path for the source file
   * @return Location if position is valid, None otherwise
   */
  def fromPosition(pos: Position, filePath: String = ""): Option[Location] = {
    if (pos != Position.None) {
      Some(Location(
        filePath = filePath,
        line = pos.startLine + 1,
        column = pos.startColumn + 1
      ))
    } else {
      None
    }
  }

  /**
   * Create a Location with just file information (no line/column).
   *
   * @param filePath The file path
   * @return Location with line and column set to 0
   */
  def fileOnly(filePath: String): Location = {
    Location(filePath = filePath, line = 0, column = 0)
  }
}
