package com.breuninger.arch.ir

/**
 * Type representations in the IR
 *
 * These are language-agnostic type representations that can be mapped to both Scala and Kotlin types.
 */
sealed trait Type {
  def isNullable: Boolean
  def makeNullable: Type
  def makeNonNullable: Type
}

object Type {
  /**
   * Primitive types that exist in both Scala and Kotlin
   */
  sealed trait PrimitiveType extends Type {
    override def isNullable: Boolean = false
    override def makeNullable: Type = NullableType(this)
    override def makeNonNullable: Type = this
  }

  case object IntType extends PrimitiveType
  case object LongType extends PrimitiveType
  case object DoubleType extends PrimitiveType
  case object FloatType extends PrimitiveType
  case object BooleanType extends PrimitiveType
  case object StringType extends PrimitiveType
  case object UnitType extends PrimitiveType // Unit in Scala, Unit in Kotlin

  /**
   * Reference to a named type (class, interface, trait)
   * @param qualifiedName Fully qualified name (e.g., "com.example.User")
   * @param typeArguments Generic type arguments
   */
  case class NamedType(
    qualifiedName: String,
    typeArguments: List[Type] = Nil
  ) extends Type {
    override def isNullable: Boolean = false
    override def makeNullable: Type = NullableType(this)
    override def makeNonNullable: Type = this
  }

  /**
   * Generic type parameter (e.g., T, A, B)
   * @param name Name of the type parameter
   * @param bounds Upper bounds for the type parameter
   */
  case class TypeParameter(
    name: String,
    bounds: List[Type] = Nil
  ) extends Type {
    override def isNullable: Boolean = false
    override def makeNullable: Type = NullableType(this)
    override def makeNonNullable: Type = this
  }

  /**
   * Nullable wrapper for types
   * In Scala: Option[T]
   * In Kotlin: T?
   */
  case class NullableType(underlying: Type) extends Type {
    override def isNullable: Boolean = true
    override def makeNullable: Type = this
    override def makeNonNullable: Type = underlying
  }

  /**
   * Collection types
   */
  sealed trait CollectionType extends Type {
    def elementType: Type
    override def isNullable: Boolean = false
    override def makeNullable: Type = NullableType(this)
    override def makeNonNullable: Type = this
  }

  case class ListType(elementType: Type) extends CollectionType
  case class SetType(elementType: Type) extends CollectionType
  case class MapType(keyType: Type, valueType: Type) extends Type {
    override def isNullable: Boolean = false
    override def makeNullable: Type = NullableType(this)
    override def makeNonNullable: Type = this
  }

  /**
   * Function type (for higher-order functions)
   * @param parameterTypes Input parameter types
   * @param returnType Return type
   */
  case class FunctionType(
    parameterTypes: List[Type],
    returnType: Type
  ) extends Type {
    override def isNullable: Boolean = false
    override def makeNullable: Type = NullableType(this)
    override def makeNonNullable: Type = this
  }
}
