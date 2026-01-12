package com.breuninger.arch.ir

/**
 * Represents an annotation in a language-agnostic way.
 * Can be mapped to Java/Scala annotations or Kotlin annotations.
 */
case class Annotation(
  name: String,
  parameters: Map[String, String] = Map.empty
)
