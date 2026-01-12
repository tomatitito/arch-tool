package com.breuninger.arch.ir

/**
 * Source code location for error reporting
 */
case class Location(
  filePath: String,
  line: Int,
  column: Int
)
