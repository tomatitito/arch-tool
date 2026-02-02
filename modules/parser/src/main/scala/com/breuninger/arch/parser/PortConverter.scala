package com.breuninger.arch.parser

import com.breuninger.arch.ir.*
import com.breuninger.arch.ir.Type as IRType
import scala.meta.{Type as MetaType, *}

/**
 * Converts Scalameta trait definitions to IR Port representations.
 *
 * Ports are trait interfaces that define boundaries between architectural layers
 * (e.g., repository interfaces, service interfaces).
 *
 * Detects port type from naming conventions:
 * - *Repository -> PortType.Repository
 * - *Service -> PortType.Service
 * - *UseCase -> PortType.UseCase
 * - Handler or EventHandler -> PortType.EventHandler
 * - Others -> PortType.Generic
 */
object PortConverter {

  /**
   * Convert a trait definition to an IR Port.
   *
   * @param trt The Scalameta trait definition
   * @param packageName The package name for this port
   * @param filePath The source file path (for error reporting)
   * @return IR Port representation
   */
  def convertTrait(trt: Defn.Trait, packageName: String, filePath: String): Port = {
    Port(
      name = trt.name.value,
      packageName = packageName,
      typeParameters = extractTypeParameters(trt),
      methods = extractMethods(trt),
      superInterfaces = extractSuperInterfaces(trt),
      visibility = extractVisibility(trt.mods),
      annotations = extractAnnotations(trt.mods),
      documentation = None, // Scaladoc extraction deferred to Phase 7
      portType = inferPortType(trt.name.value)
    )
  }

  /**
   * Extract type parameters from a trait.
   */
  private def extractTypeParameters(trt: Defn.Trait): List[IRType.TypeParameter] = {
    trt.tparamClause.values.map(TypeConverter.convertTypeParameter).toList
  }

  /**
   * Extract methods from a trait's body.
   */
  private def extractMethods(trt: Defn.Trait): List[Method] = {
    trt.templ.stats.flatMap {
      case decl: Decl.Def => Some(MethodConverter.convertDeclaration(decl))
      case defn: Defn.Def => Some(MethodConverter.convertDefinition(defn))
      case _ => None
    }
  }

  /**
   * Extract super interfaces from trait's extends clause.
   */
  private def extractSuperInterfaces(trt: Defn.Trait): List[IRType.NamedType] = {
    trt.templ.inits.flatMap { init =>
      init.tpe match {
        case MetaType.Name(name) if !isIgnoredParent(name) =>
          Some(IRType.NamedType(qualifiedName = name))
        case MetaType.Apply.After_4_6_0(MetaType.Name(name), argClause) if !isIgnoredParent(name) =>
          Some(IRType.NamedType(
            qualifiedName = name,
            typeArguments = argClause.values.map(TypeConverter.convertType).toList
          ))
        case MetaType.Select(qual, MetaType.Name(name)) if !isIgnoredParent(name) =>
          Some(IRType.NamedType(qualifiedName = s"${qual.syntax}.$name"))
        case _ => None
      }
    }
  }

  /**
   * Check if a parent type should be ignored (built-in Scala types).
   */
  private def isIgnoredParent(name: String): Boolean = {
    Set(
      "AnyRef", "Any", "Product", "Serializable", "Equals"
    ).contains(name)
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

  /**
   * Infer the port type from the trait name.
   */
  private def inferPortType(name: String): PortType = {
    if (name.endsWith("Repository")) {
      PortType.Repository
    } else if (name.endsWith("Service")) {
      PortType.Service
    } else if (name.endsWith("UseCase")) {
      PortType.UseCase
    } else if (name.endsWith("Handler") || name.endsWith("EventHandler")) {
      PortType.EventHandler
    } else {
      PortType.Generic
    }
  }
}
