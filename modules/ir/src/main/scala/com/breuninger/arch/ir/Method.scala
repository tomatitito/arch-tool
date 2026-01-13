package com.breuninger.arch.ir

/**
 * Method and parameter representations in the IR
 */

/**
 * Method parameter
 * @param name Parameter name
 * @param parameterType Type of the parameter
 * @param defaultValue Optional default value (as string representation)
 */
case class Parameter(
  name: String,
  parameterType: Type,
  defaultValue: Option[String] = None
)

/**
 * Visibility modifier for methods and classes
 */
sealed trait Visibility
object Visibility {
  case object Public extends Visibility
  case object Protected extends Visibility
  case object Private extends Visibility
  case object Internal extends Visibility // Package-private in Scala, internal in Kotlin
}

/**
 * Method definition
 * @param name Method name
 * @param typeParameters Generic type parameters (e.g., <T, R>)
 * @param parameters Method parameters
 * @param returnType Return type
 * @param visibility Visibility modifier
 * @param isAbstract Whether the method is abstract (no implementation)
 * @param isSuspend Whether the method is a suspend function (Kotlin coroutines)
 * @param annotations Annotations applied to the method
 * @param documentation Documentation comment
 */
case class Method(
  name: String,
  typeParameters: List[Type.TypeParameter] = Nil,
  parameters: List[Parameter] = Nil,
  returnType: Type,
  visibility: Visibility = Visibility.Public,
  isAbstract: Boolean = true,
  isSuspend: Boolean = false,
  annotations: List[Annotation] = Nil,
  documentation: Option[String] = None
)
