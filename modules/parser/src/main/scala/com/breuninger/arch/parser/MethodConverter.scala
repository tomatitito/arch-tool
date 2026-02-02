package com.breuninger.arch.parser

import com.breuninger.arch.ir.*
import com.breuninger.arch.ir.Type as IRType
import scala.meta.{Type as MetaType, *}

/**
 * Converts Scalameta method declarations to IR Method representations.
 *
 * Handles:
 * - Method name extraction
 * - Parameter extraction (first parameter list only, ignoring implicits)
 * - Return type extraction with IO/Future unwrapping
 * - Setting isSuspend for effect types
 */
object MethodConverter {

  /**
   * Convert a method declaration (Decl.Def) to an IR Method.
   *
   * @param decl The Scalameta method declaration
   * @return IR Method representation
   */
  def convertDeclaration(decl: Decl.Def): Method = {
    val (isSuspend, returnType) = extractReturnType(decl.decltpe)

    Method(
      name = decl.name.value,
      typeParameters = extractTypeParameters(decl),
      parameters = extractParameters(decl),
      returnType = returnType,
      visibility = extractVisibility(decl.mods),
      isAbstract = true, // Method declarations in traits are abstract
      isSuspend = isSuspend,
      annotations = extractAnnotations(decl.mods),
      documentation = None
    )
  }

  /**
   * Convert a method definition (Defn.Def) to an IR Method.
   *
   * @param defn The Scalameta method definition
   * @return IR Method representation
   */
  def convertDefinition(defn: Defn.Def): Method = {
    val returnTpe = defn.decltpe.getOrElse(MetaType.Name("Unit"))
    val (isSuspend, returnType) = extractReturnType(returnTpe)

    Method(
      name = defn.name.value,
      typeParameters = extractMethodTypeParameters(defn),
      parameters = extractDefnParameters(defn),
      returnType = returnType,
      visibility = extractVisibility(defn.mods),
      isAbstract = false, // Definitions have implementations
      isSuspend = isSuspend,
      annotations = extractAnnotations(defn.mods),
      documentation = None
    )
  }

  /**
   * Extract return type and determine if method should be suspend.
   *
   * @param returnType The Scalameta return type
   * @return Tuple of (isSuspend, IR return type)
   */
  private def extractReturnType(returnType: scala.meta.Type): (Boolean, IRType) = {
    if (TypeConverter.isEffectType(returnType)) {
      // Unwrap the effect type and mark as suspend
      val unwrapped = TypeConverter.unwrapEffectType(returnType)
      (true, TypeConverter.convertType(unwrapped))
    } else {
      (false, TypeConverter.convertType(returnType))
    }
  }

  /**
   * Extract parameters from a method declaration.
   * Only extracts the first parameter list (ignoring implicit parameter lists).
   */
  private def extractParameters(decl: Decl.Def): List[Parameter] = {
    // Get the first non-implicit parameter list
    val firstParamList = decl.paramClauses.headOption.map(_.values).getOrElse(Nil)
    firstParamList.flatMap(convertParameter).toList
  }

  /**
   * Extract parameters from a method definition.
   */
  private def extractDefnParameters(defn: Defn.Def): List[Parameter] = {
    val firstParamList = defn.paramClauses.headOption.map(_.values).getOrElse(Nil)
    firstParamList.flatMap(convertParameter).toList
  }

  /**
   * Convert a Scalameta parameter to an IR Parameter.
   */
  private def convertParameter(param: Term.Param): Option[Parameter] = {
    // Skip implicit parameters
    if (param.mods.exists { case _: Mod.Implicit => true; case _ => false }) {
      return None
    }

    param.decltpe.map { tpe =>
      Parameter(
        name = param.name.value,
        parameterType = TypeConverter.convertType(tpe),
        defaultValue = param.default.map(_.syntax)
      )
    }
  }

  /**
   * Extract type parameters from a method declaration.
   */
  private def extractTypeParameters(decl: Decl.Def): List[IRType.TypeParameter] = {
    decl.tparams.map(TypeConverter.convertTypeParameter)
  }

  /**
   * Extract type parameters from a method definition.
   */
  private def extractMethodTypeParameters(defn: Defn.Def): List[IRType.TypeParameter] = {
    defn.tparams.map(TypeConverter.convertTypeParameter)
  }

  /**
   * Extract visibility from modifiers.
   */
  private def extractVisibility(mods: List[Mod]): Visibility = {
    mods.collectFirst {
      case _: Mod.Private => Visibility.Private
      case _: Mod.Protected => Visibility.Protected
    }.getOrElse(Visibility.Public)
  }

  /**
   * Extract annotations from modifiers.
   */
  private def extractAnnotations(mods: List[Mod]): List[Annotation] = {
    mods.collect {
      case Mod.Annot(init) =>
        val name = init.tpe match {
          case MetaType.Name(n) => n
          case MetaType.Select(_, MetaType.Name(n)) => n
          case other => other.syntax
        }
        Annotation(name = name, parameters = Map.empty)
    }
  }
}
