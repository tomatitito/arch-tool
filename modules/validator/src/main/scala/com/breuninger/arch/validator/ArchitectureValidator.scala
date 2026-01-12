package com.breuninger.arch.validator

import com.breuninger.arch.ir._

/**
 * Public API for validating architectural constraints.
 *
 * Validates that IR conforms to:
 * - Hexagonal architecture (ports/adapters pattern)
 * - Layer boundaries and dependency rules
 * - Type safety and contract compliance
 * - Domain-driven design patterns
 */
trait ArchitectureValidator {
  /**
   * Validate domain models against architectural rules
   */
  def validateModels(models: List[DomainModel]): ValidationResult

  /**
   * Validate port interfaces against hexagonal architecture rules
   */
  def validatePorts(ports: List[PortInterface]): ValidationResult

  /**
   * Validate complete architecture (models + ports)
   */
  def validate(
    models: List[DomainModel],
    ports: List[PortInterface]
  ): ValidationResult
}

/**
 * Result of architectural validation
 */
case class ValidationResult(
  isValid: Boolean,
  errors: List[ValidationError],
  warnings: List[ValidationWarning]
) {
  def ++(other: ValidationResult): ValidationResult = {
    ValidationResult(
      isValid = this.isValid && other.isValid,
      errors = this.errors ++ other.errors,
      warnings = this.warnings ++ other.warnings
    )
  }
}

object ValidationResult {
  def valid: ValidationResult = ValidationResult(
    isValid = true,
    errors = List.empty,
    warnings = List.empty
  )

  def invalid(errors: List[ValidationError]): ValidationResult = {
    ValidationResult(
      isValid = false,
      errors = errors,
      warnings = List.empty
    )
  }
}

/**
 * Validation error (blocks migration)
 */
case class ValidationError(
  message: String,
  location: Option[Location] = None,
  rule: String
)

/**
 * Validation warning (does not block migration)
 */
case class ValidationWarning(
  message: String,
  location: Option[Location] = None,
  suggestion: Option[String] = None
)

/**
 * Architectural validation rules
 */
sealed trait ValidationRule {
  def name: String
  def description: String
}

case object HexagonalArchitecture extends ValidationRule {
  val name = "hexagonal-architecture"
  val description = "Enforce ports and adapters pattern"
}

case object LayerBoundaries extends ValidationRule {
  val name = "layer-boundaries"
  val description = "Enforce layer dependency rules"
}

case object DomainModelIntegrity extends ValidationRule {
  val name = "domain-model-integrity"
  val description = "Validate domain model invariants"
}

case object TypeSafety extends ValidationRule {
  val name = "type-safety"
  val description = "Ensure type compatibility across boundaries"
}
