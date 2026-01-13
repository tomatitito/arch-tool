package com.breuninger.arch.ir

/**
 * Domain model representations in the IR
 *
 * Represents domain concepts like value objects, entities, and sealed hierarchies
 */

/**
 * Property/field in a domain model
 * @param name Property name
 * @param propertyType Type of the property
 * @param isVal Whether the property is immutable (val in Scala, val in Kotlin)
 * @param visibility Visibility modifier
 * @param documentation Documentation comment
 */
case class Property(
  name: String,
  propertyType: Type,
  isVal: Boolean = true,
  visibility: Visibility = Visibility.Public,
  documentation: Option[String] = None
)

/**
 * Base trait for all domain model types
 */
sealed trait DomainModel {
  def name: String
  def packageName: String
  def qualifiedName: String = s"$packageName.$name"
  def typeParameters: List[Type.TypeParameter]
  def visibility: Visibility
  def documentation: Option[String]
}

object DomainModel {
  /**
   * Value object - immutable data holder
   * In Scala: case class
   * In Kotlin: data class
   *
   * @param name Simple name of the value object
   * @param packageName Package/namespace
   * @param typeParameters Generic type parameters
   * @param properties Properties of the value object
   * @param methods Additional methods beyond auto-generated ones
   * @param visibility Visibility modifier
   * @param documentation Documentation comment
   */
  case class ValueObject(
    name: String,
    packageName: String,
    typeParameters: List[Type.TypeParameter] = Nil,
    properties: List[Property],
    methods: List[Method] = Nil,
    visibility: Visibility = Visibility.Public,
    documentation: Option[String] = None
  ) extends DomainModel

  /**
   * Entity - object with identity, may be mutable
   * In Scala: class
   * In Kotlin: class
   *
   * @param name Simple name of the entity
   * @param packageName Package/namespace
   * @param typeParameters Generic type parameters
   * @param properties Properties of the entity
   * @param methods Methods defined on the entity
   * @param constructorParameters Constructor parameters (may differ from properties)
   * @param visibility Visibility modifier
   * @param documentation Documentation comment
   */
  case class Entity(
    name: String,
    packageName: String,
    typeParameters: List[Type.TypeParameter] = Nil,
    properties: List[Property],
    methods: List[Method] = Nil,
    constructorParameters: List[Parameter] = Nil,
    visibility: Visibility = Visibility.Public,
    documentation: Option[String] = None
  ) extends DomainModel

  /**
   * Sealed hierarchy - closed set of subtypes
   * In Scala: sealed trait
   * In Kotlin: sealed class/interface
   *
   * @param name Simple name of the sealed type
   * @param packageName Package/namespace
   * @param typeParameters Generic type parameters
   * @param methods Abstract methods defined in the sealed type
   * @param subtypes The closed set of subtypes
   * @param visibility Visibility modifier
   * @param documentation Documentation comment
   */
  case class SealedHierarchy(
    name: String,
    packageName: String,
    typeParameters: List[Type.TypeParameter] = Nil,
    methods: List[Method] = Nil,
    subtypes: List[SealedSubtype],
    visibility: Visibility = Visibility.Public,
    documentation: Option[String] = None
  ) extends DomainModel

  /**
   * Subtype of a sealed hierarchy
   *
   * @param name Simple name of the subtype
   * @param properties Properties specific to this subtype
   * @param documentation Documentation comment
   */
  case class SealedSubtype(
    name: String,
    properties: List[Property] = Nil,
    documentation: Option[String] = None
  )

  /**
   * Enum type - simple enumeration of named values
   * In Scala: Enumeration or sealed trait with case objects
   * In Kotlin: enum class
   *
   * @param name Simple name of the enum
   * @param packageName Package/namespace
   * @param values Enum values
   * @param visibility Visibility modifier
   * @param documentation Documentation comment
   */
  case class Enum(
    name: String,
    packageName: String,
    typeParameters: List[Type.TypeParameter] = Nil,
    values: List[EnumValue],
    visibility: Visibility = Visibility.Public,
    documentation: Option[String] = None
  ) extends DomainModel

  /**
   * Single value in an enum
   */
  case class EnumValue(
    name: String,
    documentation: Option[String] = None
  )
}
