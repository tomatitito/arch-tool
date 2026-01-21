package com.breuninger.arch.parser

import com.breuninger.arch.ir.{
  Annotation,
  DomainModel,
  Location,
  Method,
  Parameter,
  Port,
  PortType,
  Property,
  Visibility
}
import com.breuninger.arch.ir.{Type => IRType}
import java.nio.file.{Path, Files}
import scala.meta._
import scala.util.{Try, Success, Failure}

/**
 * Scalameta-based implementation of ScalaParser.
 *
 * Parses Scala source code using Scalameta and converts the AST to IR:
 * - Value objects (case class with AnyVal)
 * - Entities (case class without AnyVal)
 * - Ports (trait interfaces)
 * - Sealed hierarchies
 * - Enums (sealed trait with case objects)
 */
class ScalametaParser extends ScalaParser {

  override def parseFile(path: Path): Either[ParseError, ParseResult] = {
    Try(Files.readString(path)) match {
      case Success(content) =>
        parseString(content).left.map { error =>
          error.copy(location = error.location.orElse(Some(Location(path.toString, 0, 0))))
        }
      case Failure(ex) =>
        Left(ParseError(s"Failed to read file: ${ex.getMessage}", cause = Some(ex)))
    }
  }

  override def parseString(source: String): Either[ParseError, ParseResult] = {
    source.parse[Source] match {
      case Parsed.Success(tree) =>
        extractFromSource(tree)
      case Parsed.Error(pos, message, _) =>
        Left(ParseError(message, Some(Location("<input>", pos.startLine, pos.startColumn))))
    }
  }

  /**
   * Extract IR elements from a parsed Scala source tree.
   */
  private def extractFromSource(source: Source): Either[ParseError, ParseResult] = {
    val packageName = extractPackageName(source)
    val stats = extractTopLevelStats(source)

    val (sealedHierarchies, otherStats) = extractSealedHierarchies(stats, packageName)
    val domainModels = sealedHierarchies ++ extractDomainModels(otherStats, packageName)
    val ports = extractPorts(otherStats, packageName)

    Right(ParseResult(domainModels, ports))
  }

  /**
   * Extract the package name from the source.
   */
  private def extractPackageName(source: Source): String = {
    source.stats.collectFirst {
      case pkg: Pkg => pkg.ref.syntax
    }.getOrElse("")
  }

  /**
   * Extract top-level statements, handling package wrapping.
   */
  private def extractTopLevelStats(source: Source): List[Stat] = {
    source.stats.flatMap {
      case pkg: Pkg => pkg.stats.filterNot(_.isInstanceOf[scala.meta.Import])
      case stat => List(stat)
    }
  }

  /**
   * Extract sealed hierarchies (sealed trait + variants).
   * Returns (sealed hierarchies, remaining stats that aren't part of a hierarchy)
   */
  private def extractSealedHierarchies(stats: List[Stat], packageName: String): (List[DomainModel], List[Stat]) = {
    // Find sealed traits
    val sealedTraits = stats.collect {
      case t: Defn.Trait if isSealed(t) => t
    }

    // For each sealed trait, find its variants
    val hierarchies = sealedTraits.map { sealedTrait =>
      val traitName = sealedTrait.name.value
      val variants = findVariants(stats, traitName)

      // Check if it's an enum (all case objects) or a regular sealed hierarchy
      val allCaseObjects = variants.forall {
        case _: Defn.Object => true
        case _ => false
      }

      if (allCaseObjects && variants.nonEmpty) {
        // It's an enum
        val enumValues = variants.collect {
          case obj: Defn.Object =>
            DomainModel.EnumValue(obj.name.value, extractDocumentation(obj.mods))
        }
        DomainModel.Enum(
          name = traitName,
          packageName = packageName,
          typeParameters = extractTraitTypeParameters(sealedTrait),
          values = enumValues,
          visibility = extractVisibility(sealedTrait.mods),
          documentation = extractDocumentation(sealedTrait.mods)
        )
      } else {
        // It's a sealed hierarchy with data variants
        val subtypes = variants.collect {
          case c: Defn.Class if isCaseClass(c) =>
            DomainModel.SealedSubtype(
              name = c.name.value,
              properties = extractClassProperties(c),
              documentation = extractDocumentation(c.mods)
            )
          case obj: Defn.Object if isCaseObject(obj) =>
            DomainModel.SealedSubtype(
              name = obj.name.value,
              properties = Nil,
              documentation = extractDocumentation(obj.mods)
            )
        }
        DomainModel.SealedHierarchy(
          name = traitName,
          packageName = packageName,
          typeParameters = extractTraitTypeParameters(sealedTrait),
          methods = extractMethods(sealedTrait.templ.stats, packageName),
          subtypes = subtypes,
          visibility = extractVisibility(sealedTrait.mods),
          documentation = extractDocumentation(sealedTrait.mods)
        )
      }
    }

    // Filter out stats that are part of sealed hierarchies
    val variantNames = sealedTraits.flatMap { trait_ =>
      findVariants(stats, trait_.name.value).map {
        case c: Defn.Class => c.name.value
        case o: Defn.Object => o.name.value
        case t: Defn.Trait => t.name.value
      }
    }.toSet

    val sealedTraitNames = sealedTraits.map(_.name.value).toSet
    val remaining = stats.filterNot {
      case c: Defn.Class => variantNames.contains(c.name.value)
      case o: Defn.Object => variantNames.contains(o.name.value)
      case t: Defn.Trait => sealedTraitNames.contains(t.name.value)
      case _ => false
    }

    (hierarchies, remaining)
  }

  /**
   * Find all variants that extend a sealed trait.
   */
  private def findVariants(stats: List[Stat], sealedTraitName: String): List[Stat] = {
    stats.filter {
      case c: Defn.Class => extendsType(c.templ.inits, sealedTraitName)
      case o: Defn.Object => extendsType(o.templ.inits, sealedTraitName)
      case _ => false
    }
  }

  /**
   * Check if inits extend a specific type.
   */
  private def extendsType(inits: List[Init], typeName: String): Boolean = {
    inits.exists {
      case Init.After_4_6_0(Type.Name(name), _, _) => name == typeName
      case Init.After_4_6_0(Type.Select(_, Type.Name(name)), _, _) => name == typeName
      case _ => false
    }
  }

  /**
   * Extract domain models (value objects and entities) from statements.
   */
  private def extractDomainModels(stats: List[Stat], packageName: String): List[DomainModel] = {
    stats.collect {
      case c: Defn.Class if isCaseClass(c) =>
        if (isValueClass(c)) {
          DomainModel.ValueObject(
            name = c.name.value,
            packageName = packageName,
            typeParameters = extractClassTypeParameters(c),
            properties = extractClassProperties(c),
            methods = extractMethodsFromClass(c),
            visibility = extractVisibility(c.mods),
            documentation = extractDocumentation(c.mods)
          )
        } else {
          DomainModel.Entity(
            name = c.name.value,
            packageName = packageName,
            typeParameters = extractClassTypeParameters(c),
            properties = extractClassProperties(c),
            methods = extractMethodsFromClass(c),
            constructorParameters = extractClassConstructorParameters(c),
            visibility = extractVisibility(c.mods),
            documentation = extractDocumentation(c.mods)
          )
        }
    }
  }

  /**
   * Extract ports (traits) from statements.
   */
  private def extractPorts(stats: List[Stat], packageName: String): List[Port] = {
    stats.collect {
      case t: Defn.Trait if !isSealed(t) =>
        Port(
          name = t.name.value,
          packageName = packageName,
          typeParameters = extractTraitTypeParameters(t),
          methods = extractMethods(t.templ.stats, packageName),
          superInterfaces = extractSuperInterfaces(t.templ.inits),
          visibility = extractVisibility(t.mods),
          annotations = extractAnnotations(t.mods),
          documentation = extractDocumentation(t.mods),
          portType = inferPortType(t.name.value)
        )
    }
  }

  /**
   * Extract methods from a trait body.
   */
  private def extractMethods(stats: List[Stat], packageName: String): List[Method] = {
    stats.collect {
      case m: Decl.Def =>
        val returnType = convertType(m.decltpe)
        val (unwrappedReturnType, isSuspend) = unwrapEffectType(returnType)
        Method(
          name = m.name.value,
          typeParameters = extractMethodTypeParameters(m),
          parameters = extractMethodParameters(m),
          returnType = unwrappedReturnType,
          visibility = extractVisibility(m.mods),
          isAbstract = true,
          isSuspend = isSuspend,
          annotations = extractAnnotations(m.mods),
          documentation = extractDocumentation(m.mods)
        )
      case m: Defn.Def =>
        val returnType = m.decltpe.map(convertType).getOrElse(IRType.UnitType)
        val (unwrappedReturnType, isSuspend) = unwrapEffectType(returnType)
        Method(
          name = m.name.value,
          typeParameters = extractDefnDefTypeParameters(m),
          parameters = extractDefnDefParameters(m),
          returnType = unwrappedReturnType,
          visibility = extractVisibility(m.mods),
          isAbstract = false,
          isSuspend = isSuspend,
          annotations = extractAnnotations(m.mods),
          documentation = extractDocumentation(m.mods)
        )
    }
  }

  /**
   * Extract methods defined in a class.
   */
  private def extractMethodsFromClass(c: Defn.Class): List[Method] = {
    c.templ.stats.collect {
      case m: Defn.Def =>
        val returnType = m.decltpe.map(convertType).getOrElse(IRType.UnitType)
        val (unwrappedReturnType, isSuspend) = unwrapEffectType(returnType)
        Method(
          name = m.name.value,
          typeParameters = extractDefnDefTypeParameters(m),
          parameters = extractDefnDefParameters(m),
          returnType = unwrappedReturnType,
          visibility = extractVisibility(m.mods),
          isAbstract = false,
          isSuspend = isSuspend,
          annotations = extractAnnotations(m.mods),
          documentation = extractDocumentation(m.mods)
        )
    }
  }

  /**
   * Extract properties from class constructor parameters.
   */
  private def extractClassProperties(c: Defn.Class): List[Property] = {
    c.ctor.paramClauses.toList.flatMap(_.values).map { param =>
      Property(
        name = param.name.value,
        propertyType = param.decltpe.map(convertType).getOrElse(IRType.NamedType("Any")),
        isVal = !param.mods.exists {
          case Mod.VarParam() => true
          case _ => false
        },
        visibility = extractVisibility(param.mods),
        documentation = None
      )
    }
  }

  /**
   * Extract constructor parameters from class.
   */
  private def extractClassConstructorParameters(c: Defn.Class): List[Parameter] = {
    c.ctor.paramClauses.toList.flatMap(_.values).map { param =>
      Parameter(
        name = param.name.value,
        parameterType = param.decltpe.map(convertType).getOrElse(IRType.NamedType("Any")),
        defaultValue = param.default.map(_.syntax)
      )
    }
  }

  /**
   * Extract type parameters from Decl.Def.
   */
  private def extractMethodTypeParameters(m: Decl.Def): List[IRType.TypeParameter] = {
    m.tparams.map { tparam =>
      IRType.TypeParameter(
        name = tparam.name.value,
        bounds = tparam.tbounds.hi.map(t => List(convertType(t))).getOrElse(Nil)
      )
    }
  }

  /**
   * Extract method parameters from Decl.Def.
   * Filters out implicit parameters.
   */
  private def extractMethodParameters(m: Decl.Def): List[Parameter] = {
    m.paramClauses.toList.flatMap { clause =>
      // Skip implicit parameter clauses
      val isImplicit = clause.values.exists(_.mods.exists {
        case Mod.Implicit() => true
        case Mod.Using() => true
        case _ => false
      })

      if (isImplicit) Nil
      else clause.values.map { param =>
        Parameter(
          name = param.name.value,
          parameterType = param.decltpe.map(convertType).getOrElse(IRType.NamedType("Any")),
          defaultValue = param.default.map(_.syntax)
        )
      }
    }
  }

  /**
   * Extract type parameters from Defn.Def.
   */
  private def extractDefnDefTypeParameters(m: Defn.Def): List[IRType.TypeParameter] = {
    m.tparams.map { tparam =>
      IRType.TypeParameter(
        name = tparam.name.value,
        bounds = tparam.tbounds.hi.map(t => List(convertType(t))).getOrElse(Nil)
      )
    }
  }

  /**
   * Extract method parameters from Defn.Def.
   * Filters out implicit parameters.
   */
  private def extractDefnDefParameters(m: Defn.Def): List[Parameter] = {
    m.paramClauses.toList.flatMap { clause =>
      // Skip implicit parameter clauses
      val isImplicit = clause.values.exists(_.mods.exists {
        case Mod.Implicit() => true
        case Mod.Using() => true
        case _ => false
      })

      if (isImplicit) Nil
      else clause.values.map { param =>
        Parameter(
          name = param.name.value,
          parameterType = param.decltpe.map(convertType).getOrElse(IRType.NamedType("Any")),
          defaultValue = param.default.map(_.syntax)
        )
      }
    }
  }

  /**
   * Extract type parameters from trait.
   */
  private def extractTraitTypeParameters(t: Defn.Trait): List[IRType.TypeParameter] = {
    t.tparamClause.values.map { tparam =>
      IRType.TypeParameter(
        name = tparam.name.value,
        bounds = tparam.tbounds.hi.map(tp => List(convertType(tp))).getOrElse(Nil)
      )
    }
  }

  /**
   * Extract type parameters from class.
   */
  private def extractClassTypeParameters(c: Defn.Class): List[IRType.TypeParameter] = {
    c.tparamClause.values.map { tparam =>
      IRType.TypeParameter(
        name = tparam.name.value,
        bounds = tparam.tbounds.hi.map(tp => List(convertType(tp))).getOrElse(Nil)
      )
    }
  }

  /**
   * Extract super interfaces from init clauses.
   */
  private def extractSuperInterfaces(inits: List[Init]): List[IRType.NamedType] = {
    inits.collect {
      case Init.After_4_6_0(t: scala.meta.Type.Name, _, _) if t.value != "AnyVal" =>
        IRType.NamedType(t.value)
      case Init.After_4_6_0(t: scala.meta.Type.Apply, _, _) =>
        val name = t.tpe match {
          case Type.Name(n) => n
          case Type.Select(_, Type.Name(n)) => n
          case other => other.syntax
        }
        IRType.NamedType(name, t.argClause.map(convertType))
    }
  }

  /**
   * Convert Scalameta type to IR type.
   */
  private def convertType(t: scala.meta.Type): IRType = t match {
    case Type.Name("Int") => IRType.IntType
    case Type.Name("Long") => IRType.LongType
    case Type.Name("Double") => IRType.DoubleType
    case Type.Name("Float") => IRType.FloatType
    case Type.Name("Boolean") => IRType.BooleanType
    case Type.Name("String") => IRType.StringType
    case Type.Name("Unit") => IRType.UnitType

    case Type.Name(name) => IRType.NamedType(name)

    case Type.Select(qual, Type.Name(name)) =>
      IRType.NamedType(s"${qual.syntax}.$name")

    case Type.Apply(Type.Name("Option"), argClause) =>
      IRType.NullableType(convertType(argClause.head))

    case Type.Apply(Type.Name("List"), argClause) =>
      IRType.ListType(convertType(argClause.head))

    case Type.Apply(Type.Name("Seq"), argClause) =>
      IRType.ListType(convertType(argClause.head))

    case Type.Apply(Type.Name("Vector"), argClause) =>
      IRType.ListType(convertType(argClause.head))

    case Type.Apply(Type.Name("Set"), argClause) =>
      IRType.SetType(convertType(argClause.head))

    case Type.Apply(Type.Name("Map"), argClause) if argClause.size >= 2 =>
      IRType.MapType(convertType(argClause(0)), convertType(argClause(1)))

    case Type.Apply(tpe, argClause) =>
      val baseName = tpe match {
        case Type.Name(name) => name
        case Type.Select(qual, Type.Name(name)) => s"${qual.syntax}.$name"
        case other => other.syntax
      }
      IRType.NamedType(baseName, argClause.map(convertType))

    case Type.Function(params, res) =>
      IRType.FunctionType(params.map(convertType), convertType(res))

    case Type.ByName(tpe) =>
      convertType(tpe)

    case Type.Repeated(tpe) =>
      IRType.ListType(convertType(tpe))

    case Type.Tuple(args) =>
      // Convert tuple to a generic type representation
      IRType.NamedType(s"Tuple${args.size}", args.map(convertType))

    case other =>
      IRType.NamedType(other.syntax)
  }

  /**
   * Unwrap effect types (IO, Future, Task) to their inner type.
   * Returns (unwrapped type, isSuspend).
   */
  private def unwrapEffectType(t: IRType): (IRType, Boolean) = t match {
    case IRType.NamedType(name, List(inner)) if isEffectType(name) =>
      (inner, true)
    case other =>
      (other, false)
  }

  /**
   * Check if a type name is an effect type.
   */
  private def isEffectType(name: String): Boolean = {
    val effectTypes = Set("IO", "Future", "Task", "Async", "Effect")
    effectTypes.contains(name) || name.endsWith("IO") || name.endsWith("Task")
  }

  /**
   * Extract visibility from modifiers.
   */
  private def extractVisibility(mods: List[Mod]): Visibility = {
    mods.collectFirst {
      case Mod.Private(_) => Visibility.Private
      case Mod.Protected(_) => Visibility.Protected
    }.getOrElse(Visibility.Public)
  }

  /**
   * Extract annotations from modifiers.
   */
  private def extractAnnotations(mods: List[Mod]): List[Annotation] = {
    mods.collect {
      case Mod.Annot(init) =>
        val name = init.tpe match {
          case Type.Name(n) => n
          case Type.Select(_, Type.Name(n)) => n
          case other => other.syntax
        }
        val params = init.argClauses.flatMap(_.values).map { arg =>
          // Extract parameter name and value if available
          arg match {
            case Term.Assign(Term.Name(paramName), value) =>
              (paramName, value.syntax)
            case other =>
              ("value", other.syntax)
          }
        }.toMap
        Annotation(name, params)
    }
  }

  /**
   * Extract documentation from modifiers (looking for Scaladoc comments).
   */
  private def extractDocumentation(mods: List[Mod]): Option[String] = {
    // Scalameta doesn't directly expose comments, so this returns None
    // A more sophisticated implementation would parse comment tokens
    None
  }

  /**
   * Check if a trait is sealed.
   */
  private def isSealed(t: Defn.Trait): Boolean = {
    t.mods.exists {
      case Mod.Sealed() => true
      case _ => false
    }
  }

  /**
   * Check if a class is a case class.
   */
  private def isCaseClass(c: Defn.Class): Boolean = {
    c.mods.exists {
      case Mod.Case() => true
      case _ => false
    }
  }

  /**
   * Check if an object is a case object.
   */
  private def isCaseObject(o: Defn.Object): Boolean = {
    o.mods.exists {
      case Mod.Case() => true
      case _ => false
    }
  }

  /**
   * Check if a case class is a value class (extends AnyVal).
   */
  private def isValueClass(c: Defn.Class): Boolean = {
    extendsType(c.templ.inits, "AnyVal")
  }

  /**
   * Infer the port type based on naming conventions.
   */
  private def inferPortType(name: String): PortType = {
    val lowerName = name.toLowerCase
    if (lowerName.contains("repository") || lowerName.contains("repo")) {
      PortType.Repository
    } else if (lowerName.contains("usecase") || lowerName.contains("use_case")) {
      PortType.UseCase
    } else if (lowerName.contains("eventhandler") || lowerName.contains("event_handler") || lowerName.contains("listener")) {
      PortType.EventHandler
    } else if (lowerName.contains("service")) {
      PortType.Service
    } else {
      PortType.Generic
    }
  }
}

/**
 * Companion object with factory method.
 */
object ScalametaParser {
  def apply(): ScalametaParser = new ScalametaParser()
}
