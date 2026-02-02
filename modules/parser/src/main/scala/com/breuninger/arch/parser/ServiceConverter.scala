package com.breuninger.arch.parser

import com.breuninger.arch.ir.*
import com.breuninger.arch.ir.Type as IRType
import scala.meta.{Type as MetaType, *}

/**
 * Converts Scalameta class definitions to IR Service representations.
 *
 * Services are application layer classes that:
 * - Are non-case classes
 * - Have constructor dependencies (typically on ports like repositories)
 * - Contain methods that orchestrate business logic
 *
 * Detects port dependencies from constructor parameters by naming convention:
 * - *Repository -> port dependency
 * - *Service -> port dependency (when injected)
 * - Other types -> other dependencies
 */
object ServiceConverter {

  /**
   * Check if a class definition represents a service.
   *
   * A class is considered a service if:
   * - It's NOT a case class (regular class)
   * - It has constructor parameters (dependencies)
   * - Its name ends with "Service"
   *
   * @param cls The Scalameta class definition
   * @return true if this class should be treated as a service
   */
  def isService(cls: Defn.Class): Boolean = {
    val isNotCaseClass = !cls.mods.exists {
      case _: Mod.Case => true
      case _ => false
    }
    val hasConstructorParams = cls.ctor.paramClauses.headOption.exists(_.values.nonEmpty)
    val hasServiceName = cls.name.value.endsWith("Service")

    isNotCaseClass && hasConstructorParams && hasServiceName
  }

  /**
   * Convert a class definition to an IR Service.
   *
   * @param cls The Scalameta class definition
   * @param packageName The package name for this service
   * @param filePath The source file path (for error reporting)
   * @return IR Service representation
   */
  def convertClass(cls: Defn.Class, packageName: String, filePath: String): Service = {
    val (portDeps, otherDeps) = extractDependencies(cls)

    Service(
      name = cls.name.value,
      packageName = packageName,
      typeParameters = extractTypeParameters(cls),
      portDependencies = portDeps,
      otherDependencies = otherDeps,
      methods = extractMethods(cls),
      visibility = extractVisibility(cls.mods),
      annotations = extractAnnotations(cls.mods),
      documentation = None
    )
  }

  /**
   * Extract constructor dependencies, separating port dependencies from other dependencies.
   *
   * Port dependencies are identified by naming conventions:
   * - *Repository
   * - *Service (when different from the class itself)
   * - *UseCase
   * - *Port
   * - *Gateway
   *
   * @return Tuple of (port dependencies, other dependencies)
   */
  private def extractDependencies(cls: Defn.Class): (List[PortDependency], List[Parameter]) = {
    val constructorParams = cls.ctor.paramClauses.headOption
      .map(_.values)
      .getOrElse(Nil)
      .filterNot(isImplicitParam)

    val portDeps = constructorParams.flatMap { param =>
      param.decltpe.flatMap { tpe =>
        val typeName = extractTypeName(tpe)
        if (isPortType(typeName)) {
          Some(PortDependency(
            port = TypeConverter.convertType(tpe).asInstanceOf[IRType.NamedType],
            parameterName = param.name.value,
            documentation = None
          ))
        } else {
          None
        }
      }
    }.toList

    val otherDeps = constructorParams.flatMap { param =>
      param.decltpe.flatMap { tpe =>
        val typeName = extractTypeName(tpe)
        if (!isPortType(typeName)) {
          Some(Parameter(
            name = param.name.value,
            parameterType = TypeConverter.convertType(tpe),
            defaultValue = param.default.map(_.syntax)
          ))
        } else {
          None
        }
      }
    }.toList

    (portDeps, otherDeps)
  }

  /**
   * Check if a type name indicates a port dependency.
   */
  private def isPortType(typeName: String): Boolean = {
    typeName.endsWith("Repository") ||
      typeName.endsWith("Service") ||
      typeName.endsWith("UseCase") ||
      typeName.endsWith("Port") ||
      typeName.endsWith("Gateway")
  }

  /**
   * Extract the simple type name from a Scalameta type.
   */
  private def extractTypeName(tpe: scala.meta.Type): String = tpe match {
    case MetaType.Name(name) => name
    case MetaType.Select(_, MetaType.Name(name)) => name
    case MetaType.Apply.After_4_6_0(base, _) => extractTypeName(base)
    case other => other.syntax.split('.').last
  }

  /**
   * Check if a parameter is implicit.
   */
  private def isImplicitParam(param: Term.Param): Boolean = {
    param.mods.exists {
      case _: Mod.Implicit => true
      case _: Mod.Using => true
      case _ => false
    }
  }

  /**
   * Extract methods from the class body.
   */
  private def extractMethods(cls: Defn.Class): List[Method] = {
    cls.templ.stats.flatMap {
      case defn: Defn.Def if !isPrivateMethod(defn) =>
        Some(MethodConverter.convertDefinition(defn))
      case _ => None
    }
  }

  /**
   * Check if a method is private.
   */
  private def isPrivateMethod(defn: Defn.Def): Boolean = {
    defn.mods.exists {
      case _: Mod.Private => true
      case _ => false
    }
  }

  /**
   * Extract type parameters from a class.
   */
  private def extractTypeParameters(cls: Defn.Class): List[IRType.TypeParameter] = {
    cls.tparamClause.values.map(TypeConverter.convertTypeParameter).toList
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
