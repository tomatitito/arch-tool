package com.breuninger.arch.parser

import com.breuninger.arch.ir.Type as IRType
import scala.meta.*

/**
 * Converts Scalameta Type AST nodes to IR Type representations.
 *
 * Handles:
 * - Primitive types (String, Int, Long, etc.)
 * - Collection types (List, Set, Map)
 * - Nullable types (Option)
 * - Effect types (IO, Future) - unwrapped, caller sets isSuspend
 * - Named types (custom domain types)
 * - Nested generic types
 */
object TypeConverter {

  /**
   * Convert a Scalameta Type to an IR Type.
   *
   * @param metaType The Scalameta type AST node
   * @return The corresponding IR Type
   */
  def convertType(metaType: scala.meta.Type): IRType = metaType match {
    // Primitive types
    case Type.Name("String")  => IRType.StringType
    case Type.Name("Int")     => IRType.IntType
    case Type.Name("Long")    => IRType.LongType
    case Type.Name("Double")  => IRType.DoubleType
    case Type.Name("Float")   => IRType.FloatType
    case Type.Name("Boolean") => IRType.BooleanType
    case Type.Name("Unit")    => IRType.UnitType

    // Option[A] -> NullableType(A)
    case Type.Apply.After_4_6_0(Type.Name("Option"), argClause) =>
      IRType.NullableType(convertType(argClause.values.head))

    // List[A] -> ListType(A)
    case Type.Apply.After_4_6_0(Type.Name("List" | "Seq" | "Vector"), argClause) =>
      IRType.ListType(convertType(argClause.values.head))

    // Set[A] -> SetType(A)
    case Type.Apply.After_4_6_0(Type.Name("Set"), argClause) =>
      IRType.SetType(convertType(argClause.values.head))

    // Map[K, V] -> MapType(K, V)
    case Type.Apply.After_4_6_0(Type.Name("Map"), argClause) if argClause.values.length >= 2 =>
      IRType.MapType(
        convertType(argClause.values.head),
        convertType(argClause.values(1))
      )

    // Effect types: IO[A], Future[A] -> unwrap to A (caller should set isSuspend=true)
    case Type.Apply.After_4_6_0(Type.Name("IO" | "Future" | "Task" | "ZIO"), argClause) =>
      // For effect types, we unwrap and return the inner type
      // The caller (MethodConverter) will set isSuspend=true
      convertType(argClause.values.head)

    // Either[L, R] -> NamedType("Either", [L, R])
    case Type.Apply.After_4_6_0(Type.Name("Either"), argClause) if argClause.values.length >= 2 =>
      IRType.NamedType(
        qualifiedName = "Either",
        typeArguments = argClause.values.map(convertType).toList
      )

    // Generic type applications (e.g., Repository[A], CustomType[X, Y])
    case Type.Apply.After_4_6_0(tpe, argClause) =>
      val baseName = extractTypeName(tpe)
      IRType.NamedType(
        qualifiedName = baseName,
        typeArguments = argClause.values.map(convertType).toList
      )

    // Simple named types (domain types, custom types)
    case Type.Name(name) =>
      IRType.NamedType(qualifiedName = name)

    // Type selection (e.g., scala.Option, cats.effect.IO)
    case Type.Select(qual, Type.Name(name)) =>
      IRType.NamedType(qualifiedName = s"${qual.syntax}.$name")

    // Fallback for any other type - treat as named type
    case other =>
      IRType.NamedType(qualifiedName = other.syntax)
  }

  /**
   * Check if a type is an effect type (IO, Future, Task, ZIO).
   * Used by MethodConverter to determine if a method should be marked as suspend.
   */
  def isEffectType(metaType: scala.meta.Type): Boolean = metaType match {
    case Type.Apply.After_4_6_0(Type.Name("IO" | "Future" | "Task" | "ZIO"), _) => true
    case _ => false
  }

  /**
   * Extract the unwrapped type from an effect type.
   * For non-effect types, returns the type as-is.
   */
  def unwrapEffectType(metaType: scala.meta.Type): scala.meta.Type = metaType match {
    case Type.Apply.After_4_6_0(Type.Name("IO" | "Future" | "Task" | "ZIO"), argClause) =>
      argClause.values.head
    case other => other
  }

  /**
   * Extract type name from various type representations.
   */
  private def extractTypeName(tpe: scala.meta.Type): String = tpe match {
    case Type.Name(name) => name
    case Type.Select(qual, Type.Name(name)) => s"${qual.syntax}.$name"
    case other => other.syntax
  }

  /**
   * Convert a Scalameta type parameter to an IR TypeParameter.
   */
  def convertTypeParameter(tparam: Type.Param): IRType.TypeParameter = {
    val bounds = tparam.tbounds match {
      case Type.Bounds(_, Some(hi)) => List(convertType(hi))
      case Type.Bounds(Some(lo), _) => List(convertType(lo))
      case _ => Nil
    }
    IRType.TypeParameter(
      name = tparam.name.value,
      bounds = bounds
    )
  }
}
