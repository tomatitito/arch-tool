package com.breuninger.arch

/**
 * Renderer module - KotlinPoet integration
 *
 * Responsible for generating Kotlin source code from the IR (Intermediate Representation).
 * Uses KotlinPoet to generate idiomatic Kotlin code with Spring Boot annotations.
 *
 * Main components:
 * - KotlinRenderer: Core renderer using KotlinPoet to generate Kotlin code
 */
package object renderer {

  /**
   * Configuration for Kotlin code rendering
   */
  case class RenderConfig(
    indentSize: Int = 4,
    useDataClasses: Boolean = true,
    generateKDoc: Boolean = true
  )

  /**
   * Error during rendering
   */
  case class RenderError(
    message: String,
    cause: Option[Throwable] = None
  )
}
