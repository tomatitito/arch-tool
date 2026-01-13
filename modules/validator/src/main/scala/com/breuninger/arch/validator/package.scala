package com.breuninger.arch

/**
 * Validator module - Architectural rules
 *
 * Validates that the IR (Intermediate Representation) conforms to architectural constraints:
 * - Hexagonal architecture (ports/adapters pattern)
 * - Layer boundaries and dependencies
 * - Type safety and contract compliance
 *
 * PUBLIC API:
 * - ArchitectureValidator: Main validation interface
 * - ValidationResult: Result with errors and warnings
 * - ValidationError, ValidationWarning: Validation feedback
 * - ValidationRule: Architectural rules
 *
 * DEPENDENCY RULES:
 * - Depends ONLY on ir module
 * - Must not perform parsing (delegate to parser)
 * - Must not generate code (delegate to renderer)
 * - Must define clear architectural rules
 */
package object validator {
  // Public API defined in ArchitectureValidator.scala
}
