package com.breuninger.arch.parser

import com.breuninger.arch.ir.*
import com.breuninger.arch.ir.DomainModel.*
import com.breuninger.arch.ir.Type as IRType
import scala.meta.{Type as MetaType, *}

/**
 * Converts Scalameta class/trait definitions to IR DomainModel types.
 *
 * Detects and converts:
 * - ValueObject: case class extending AnyVal
 * - Entity: case class not extending AnyVal
 * - SealedHierarchy: sealed trait with subtypes
 */
object DomainModelConverter {

  /**
   * Convert a class definition to a DomainModel.
   *
   * @param cls The Scalameta class definition
   * @param packageName The package name for this class
   * @param filePath The source file path (for error reporting)
   * @return Some(DomainModel) if the class is a domain model, None otherwise
   */
  def convertClass(cls: Defn.Class, packageName: String, filePath: String): Option[DomainModel] = {
    if (!isCaseClass(cls)) {
      return None
    }

    if (isValueObject(cls)) {
      Some(convertToValueObject(cls, packageName))
    } else {
      Some(convertToEntity(cls, packageName))
    }
  }

  /**
   * Convert a case class extending AnyVal to a ValueObject.
   */
  private def convertToValueObject(cls: Defn.Class, packageName: String): ValueObject = {
    ValueObject(
      name = cls.name.value,
      packageName = packageName,
      typeParameters = extractTypeParameters(cls),
      properties = extractProperties(cls),
      methods = Nil, // ValueObjects don't have custom methods in our model
      visibility = extractVisibility(cls.mods),
      documentation = extractDocumentation(cls)
    )
  }

  /**
   * Convert a case class to an Entity.
   */
  private def convertToEntity(cls: Defn.Class, packageName: String): Entity = {
    Entity(
      name = cls.name.value,
      packageName = packageName,
      typeParameters = extractTypeParameters(cls),
      properties = extractProperties(cls),
      methods = Nil, // Methods not extracted for simple entities
      constructorParameters = Nil, // Same as properties for case classes
      visibility = extractVisibility(cls.mods),
      documentation = extractDocumentation(cls)
    )
  }

  /**
   * Convert a sealed trait and its subtypes to a SealedHierarchy.
   */
  def convertSealedHierarchy(
    sealedTrait: Defn.Trait,
    subClasses: List[Defn.Class],
    subObjects: List[Defn.Object],
    packageName: String,
    filePath: String
  ): SealedHierarchy = {
    val classSubtypes = subClasses.map { cls =>
      SealedSubtype(
        name = cls.name.value,
        properties = extractProperties(cls),
        documentation = extractDocumentation(cls)
      )
    }

    val objectSubtypes = subObjects.map { obj =>
      SealedSubtype(
        name = obj.name.value,
        properties = Nil, // Objects don't have constructor properties
        documentation = None // Simplified for now
      )
    }

    SealedHierarchy(
      name = sealedTrait.name.value,
      packageName = packageName,
      typeParameters = sealedTrait.tparamClause.values.map(TypeConverter.convertTypeParameter).toList,
      methods = extractTraitMethods(sealedTrait),
      subtypes = classSubtypes ++ objectSubtypes,
      visibility = extractVisibility(sealedTrait.mods),
      documentation = extractDocumentation(sealedTrait)
    )
  }

  /**
   * Check if a class is a case class.
   */
  private def isCaseClass(cls: Defn.Class): Boolean = {
    cls.mods.exists {
      case _: Mod.Case => true
      case _ => false
    }
  }

  /**
   * Check if a case class is a value object (extends AnyVal).
   */
  private def isValueObject(cls: Defn.Class): Boolean = {
    val isCaseClazz = isCaseClass(cls)
    val extendsAnyVal = cls.templ.inits.exists {
      case Init.After_4_6_0(MetaType.Name("AnyVal"), _, _) => true
      case _ => false
    }
    isCaseClazz && extendsAnyVal
  }

  /**
   * Extract properties from class constructor parameters.
   */
  private def extractProperties(cls: Defn.Class): List[Property] = {
    val firstParamClause = cls.ctor.paramClauses.headOption
    firstParamClause.map(_.values).getOrElse(Nil).flatMap { param =>
      param.decltpe.map { tpe =>
        Property(
          name = param.name.value,
          propertyType = TypeConverter.convertType(tpe),
          isVal = isValParameter(param),
          visibility = extractParamVisibility(param),
          documentation = None
        )
      }
    }.toList
  }

  /**
   * Extract type parameters from a class.
   */
  private def extractTypeParameters(cls: Defn.Class): List[IRType.TypeParameter] = {
    cls.tparamClause.values.map(TypeConverter.convertTypeParameter).toList
  }

  /**
   * Extract method declarations from a trait.
   */
  private def extractTraitMethods(trt: Defn.Trait): List[Method] = {
    trt.templ.stats.collect {
      case decl: Decl.Def => MethodConverter.convertDeclaration(decl)
    }
  }

  /**
   * Check if a parameter is a val (immutable).
   */
  private def isValParameter(param: Term.Param): Boolean = {
    // In case classes, all parameters are vals by default
    // Check for explicit var modifier
    !param.mods.exists {
      case _: Mod.VarParam => true
      case _ => false
    }
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
   * Extract visibility from parameter modifiers.
   */
  private def extractParamVisibility(param: Term.Param): Visibility = {
    param.mods.collectFirst {
      case _: Mod.Private => Visibility.Private
      case _: Mod.Protected => Visibility.Protected
    }.getOrElse(Visibility.Public)
  }

  /**
   * Extract documentation from a definition.
   * TODO: Implement Scaladoc extraction in a future phase.
   */
  private def extractDocumentation(defn: Defn): Option[String] = {
    // Scaladoc extraction is complex and deferred to Phase 7
    None
  }
}
