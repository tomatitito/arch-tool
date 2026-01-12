package com.breuninger.arch.ir

/**
 * Type reference in the IR.
 * Represents types in a language-agnostic way.
 */
sealed trait TypeRef

case class SimpleType(name: String) extends TypeRef
case class GenericType(base: String, args: List[TypeRef]) extends TypeRef
case class OptionType(inner: TypeRef) extends TypeRef
case class ListType(element: TypeRef) extends TypeRef
case class MapType(key: TypeRef, value: TypeRef) extends TypeRef
case class FunctionType(params: List[TypeRef], returnType: TypeRef) extends TypeRef
