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
      case vo: DomainModel.ValueObject =>
        val propsStr = vo.properties.map(p => s"val ${p.name}: ${renderTypeNameSimple(p.propertyType)}").mkString(", ")
        s"""package ${vo.packageName}
           |
           |data class ${vo.name}($propsStr)
           |""".stripMargin

      case entity: DomainModel.Entity =>
        val propsStr = entity.properties.map(p => s"val ${p.name}: ${renderTypeNameSimple(p.propertyType)}").mkString(", ")
        s"""package ${entity.packageName}
           |
           |data class ${entity.name}($propsStr)
           |""".stripMargin

      case hierarchy: DomainModel.SealedHierarchy =>
        s"""package ${hierarchy.packageName}
           |
           |sealed interface ${hierarchy.name}
           |""".stripMargin

      case enumModel: DomainModel.Enum =>
        val valuesStr = enumModel.values.map(_.name).mkString(", ")
        s"""package ${enumModel.packageName}
           |
           |enum class ${enumModel.name} { $valuesStr }
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
    case Type.IntType => "Int"
    case Type.LongType => "Long"
    case Type.DoubleType => "Double"
    case Type.FloatType => "Float"
    case Type.BooleanType => "Boolean"
    case Type.StringType => "String"
    case Type.UnitType => "Unit"
    case Type.NamedType(qualifiedName, typeArgs) =>
      val simpleName = qualifiedName.split('.').last
      if (typeArgs.isEmpty) simpleName
      else s"$simpleName<${typeArgs.map(renderTypeNameSimple).mkString(", ")}>"
    case Type.TypeParameter(name, _) => name
    case Type.NullableType(underlying) => s"${renderTypeNameSimple(underlying)}?"
    case Type.ListType(elementType) => s"List<${renderTypeNameSimple(elementType)}>"
    case Type.SetType(elementType) => s"Set<${renderTypeNameSimple(elementType)}>"
    case Type.MapType(keyType, valueType) => s"Map<${renderTypeNameSimple(keyType)}, ${renderTypeNameSimple(valueType)}>"
    case Type.FunctionType(paramTypes, returnType) =>
      s"(${paramTypes.map(renderTypeNameSimple).mkString(", ")}) -> ${renderTypeNameSimple(returnType)}"
  }
}
