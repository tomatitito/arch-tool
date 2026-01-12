package com.breuninger.arch.ir

/**
 * Represents a field in a domain model
 */
case class Field(
  name: String,
  fieldType: TypeRef,
  constraints: List[Constraint] = List.empty,
  defaultValue: Option[String] = None,
  documentation: Option[String] = None
)

/**
 * Constraint on a field value
 */
sealed trait Constraint
case class NotNull() extends Constraint
case class MinLength(value: Int) extends Constraint
case class MaxLength(value: Int) extends Constraint
case class Pattern(regex: String) extends Constraint
case class Range(min: Double, max: Double) extends Constraint
