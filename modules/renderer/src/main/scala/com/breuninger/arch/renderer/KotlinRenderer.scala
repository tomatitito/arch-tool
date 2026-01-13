package com.breuninger.arch.renderer

import com.breuninger.arch.ir._

/**
 * KotlinPoet-based renderer for generating Kotlin code from IR.
 *
 * Converts the language-agnostic IR (Intermediate Representation) to
 * idiomatic Kotlin code using KotlinPoet.
 *
 * This is a stub implementation to allow tests to compile.
 * TODO: Implement actual rendering logic using KotlinPoet
 */
object KotlinRenderer {

  /**
   * Renders a Port (repository interface) to Kotlin code.
   *
   * @param port The port to render
   * @return Kotlin source code as a string
   */
  def renderPort(port: Port): String = {
    // TODO: Implement using KotlinPoet
    // For now, return minimal stub to allow tests to compile
    s"""package ${port.packageName}
       |
       |interface ${port.name} {
       |}
       |""".stripMargin
  }

  /**
   * Renders a DomainModel to Kotlin code.
   *
   * @param model The domain model to render
   * @return Kotlin source code as a string
   */
  def renderDomainModel(model: DomainModel): String = {
    // TODO: Implement using KotlinPoet
    model match {
      case DomainModel.ValueObject(name, packageName, field) =>
        s"""package $packageName
           |
           |@JvmInline
           |value class $name(val ${field.name}: ${renderTypeNameSimple(field.fieldType)})
           |""".stripMargin

      case DomainModel.Entity(name, packageName, fields) =>
        s"""package $packageName
           |
           |data class $name(
           |)
           |""".stripMargin

      case DomainModel.SealedHierarchy(name, packageName, variants) =>
        s"""package $packageName
           |
           |sealed interface $name
           |""".stripMargin
    }
  }

  /**
   * Renders a TypeName to its Kotlin string representation.
   *
   * @param tpe The type to render
   * @return Kotlin type string
   */
  def renderTypeName(tpe: Type): String = {
    // TODO: Implement proper type name rendering
    renderTypeNameSimple(tpe)
  }

  /**
   * Simple type name rendering (stub implementation).
   */
  private def renderTypeNameSimple(tpe: Type): String = tpe match {
    case Type.Primitive(name) => name
    case Type.Domain(name) => name
    case Type.Generic(name, args) =>
      s"$name<${args.map(renderTypeNameSimple).mkString(", ")}>"
    case Type.Effect(wrapped, _) => renderTypeNameSimple(wrapped)
    case Type.Unit => "Unit"
  }
}
