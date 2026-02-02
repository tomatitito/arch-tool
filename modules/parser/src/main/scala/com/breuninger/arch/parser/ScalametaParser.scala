package com.breuninger.arch.parser

import com.breuninger.arch.ir.*
import java.nio.file.{Path, Files}
import scala.meta.{Type as MetaType, *}
import scala.util.{Try, Success, Failure}

/**
 * Scalameta-based implementation of ScalaParser.
 *
 * Parses Scala source code into the IR (Intermediate Representation),
 * extracting domain models (ValueObject, Entity, SealedHierarchy),
 * port interfaces, and application services.
 */
class ScalametaParser extends ScalaParser {

  /**
   * Parse a Scala source file into IR domain models and ports.
   */
  override def parseFile(path: Path): Either[ParseError, ParseResult] = {
    Try(Files.readString(path)) match {
      case Success(content) =>
        parseString(content, Some(path.toString))
      case Failure(ex) =>
        Left(ParseError(
          message = s"Failed to read file: ${ex.getMessage}",
          location = Some(Location(path.toString, 0, 0)),
          cause = Some(ex)
        ))
    }
  }

  /**
   * Parse Scala source code from a string.
   */
  override def parseString(source: String): Either[ParseError, ParseResult] = {
    parseString(source, None)
  }

  /**
   * Parse Scala source code with optional file path for error reporting.
   */
  private def parseString(source: String, filePath: Option[String]): Either[ParseError, ParseResult] = {
    source.parse[Source] match {
      case Parsed.Success(tree) =>
        extractFromSource(tree, filePath.getOrElse(""))

      case Parsed.Error(pos, message, _) =>
        Left(ParseError(
          message = s"Scala parse error: $message",
          location = LocationExtractor.fromPosition(pos, filePath.getOrElse(""))
        ))
    }
  }

  /**
   * Extract domain models, ports, and services from a parsed Scala source.
   */
  private def extractFromSource(source: Source, filePath: String): Either[ParseError, ParseResult] = {
    // Try to extract from package or top-level stats
    val (packageName, stats) = source.stats match {
      case List(pkg: Pkg) =>
        (pkg.ref.syntax, pkg.stats)
      case stats =>
        // No package declaration, stats are at top level
        ("", stats)
    }

    // Collect all classes, traits, and objects
    val classes = collectClasses(stats)
    val traits = collectTraits(stats)
    val objects = collectObjects(stats)

    // Separate service classes from domain model classes
    val (serviceClasses, nonServiceClasses) = classes.partition(ServiceConverter.isService)

    // Convert domain models (excluding service classes)
    val domainModels = extractDomainModels(nonServiceClasses, traits, objects, packageName, filePath)

    // Convert ports
    val ports = extractPorts(traits, packageName, filePath)

    // Convert services
    val services = extractServices(serviceClasses, packageName, filePath)

    Right(ParseResult(
      domainModels = domainModels,
      ports = ports,
      services = services
    ))
  }

  /**
   * Collect all class definitions from stats (including nested in packages).
   */
  private def collectClasses(stats: List[Stat]): List[Defn.Class] = {
    stats.flatMap {
      case c: Defn.Class => List(c)
      case pkg: Pkg => collectClasses(pkg.stats)
      case _ => Nil
    }
  }

  /**
   * Collect all trait definitions from stats.
   */
  private def collectTraits(stats: List[Stat]): List[Defn.Trait] = {
    stats.flatMap {
      case t: Defn.Trait => List(t)
      case pkg: Pkg => collectTraits(pkg.stats)
      case _ => Nil
    }
  }

  /**
   * Collect all object definitions from stats.
   */
  private def collectObjects(stats: List[Stat]): List[Defn.Object] = {
    stats.flatMap {
      case o: Defn.Object => List(o)
      case pkg: Pkg => collectObjects(pkg.stats)
      case _ => Nil
    }
  }

  /**
   * Extract domain models (ValueObject, Entity, SealedHierarchy) from AST.
   */
  private def extractDomainModels(
    classes: List[Defn.Class],
    traits: List[Defn.Trait],
    objects: List[Defn.Object],
    packageName: String,
    filePath: String
  ): List[DomainModel] = {
    // Find sealed traits for hierarchy detection
    val sealedTraits = traits.filter(isSealedTrait)
    val sealedTraitNames = sealedTraits.map(_.name.value).toSet

    // Convert sealed hierarchies
    val sealedHierarchies = sealedTraits.map { sealedTrait =>
      DomainModelConverter.convertSealedHierarchy(
        sealedTrait,
        classes.filter(c => extendsType(c, sealedTrait.name.value)),
        objects.filter(o => objectExtendsType(o, sealedTrait.name.value)),
        packageName,
        filePath
      )
    }

    // Convert classes that are NOT part of a sealed hierarchy
    val nonHierarchyClasses = classes.filterNot { c =>
      sealedTraitNames.exists(name => extendsType(c, name))
    }

    val classModels = nonHierarchyClasses.flatMap { cls =>
      DomainModelConverter.convertClass(cls, packageName, filePath)
    }

    sealedHierarchies ++ classModels
  }

  /**
   * Extract ports (trait interfaces) from AST.
   * Only converts non-sealed traits that have method declarations.
   */
  private def extractPorts(
    traits: List[Defn.Trait],
    packageName: String,
    filePath: String
  ): List[Port] = {
    traits
      .filterNot(isSealedTrait)  // Exclude sealed traits (they become SealedHierarchy)
      .map(t => PortConverter.convertTrait(t, packageName, filePath))
  }

  /**
   * Extract services (application layer classes) from AST.
   */
  private def extractServices(
    serviceClasses: List[Defn.Class],
    packageName: String,
    filePath: String
  ): List[Service] = {
    serviceClasses.map(cls => ServiceConverter.convertClass(cls, packageName, filePath))
  }

  /**
   * Check if a trait is sealed.
   */
  private def isSealedTrait(trt: Defn.Trait): Boolean = {
    trt.mods.exists {
      case _: Mod.Sealed => true
      case _ => false
    }
  }

  /**
   * Check if a class extends a given type name.
   */
  private def extendsType(cls: Defn.Class, typeName: String): Boolean = {
    cls.templ.inits.exists {
      case Init.After_4_6_0(MetaType.Name(`typeName`), _, _) => true
      case _ => false
    }
  }

  /**
   * Check if an object extends a given type name.
   */
  private def objectExtendsType(obj: Defn.Object, typeName: String): Boolean = {
    obj.templ.inits.exists {
      case Init.After_4_6_0(MetaType.Name(`typeName`), _, _) => true
      case _ => false
    }
  }
}

/**
 * Companion object for easy access to a parser instance.
 */
object ScalametaParser {
  def apply(): ScalametaParser = new ScalametaParser()
}
