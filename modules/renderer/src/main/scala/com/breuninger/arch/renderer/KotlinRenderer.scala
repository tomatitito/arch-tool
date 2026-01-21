package com.breuninger.arch.renderer

import com.breuninger.arch.ir._

/**
 * KotlinPoet-based renderer for generating Kotlin code from IR.
 *
 * Converts the language-agnostic IR (Intermediate Representation) to
 * idiomatic Kotlin code. Handles:
 * - Port interfaces with suspend functions
 * - Data classes from entities and value objects
 * - Sealed interfaces from sealed hierarchies
 * - Enum classes from enums
 * - Proper type mappings and formatting
 */
object KotlinRenderer {

  /**
   * Renders a Port (repository interface) to Kotlin code.
   *
   * @param port The port to render
   * @return Kotlin source code as a string
   */
  def renderPort(port: Port): String = {
    val sb = new StringBuilder

    // Package declaration
    if (port.packageName.nonEmpty) {
      sb.append(s"package ${port.packageName}\n\n")
    }

    // Interface declaration
    val typeParams = renderTypeParameters(port.typeParameters)
    sb.append(s"interface ${port.name}$typeParams {\n")

    // Methods
    port.methods.foreach { method =>
      sb.append(renderMethod(method, indent = 4))
    }

    sb.append("}\n")
    sb.toString
  }

  /**
   * Renders a DomainModel to Kotlin code.
   *
   * @param model The domain model to render
   * @return Kotlin source code as a string
   */
  def renderDomainModel(model: DomainModel): String = model match {
    case vo: DomainModel.ValueObject => renderValueObject(vo)
    case entity: DomainModel.Entity => renderEntity(entity)
    case hierarchy: DomainModel.SealedHierarchy => renderSealedHierarchy(hierarchy)
    case enumModel: DomainModel.Enum => renderEnum(enumModel)
  }

  /**
   * Renders a ValueObject to Kotlin code.
   * Uses @JvmInline value class for single-property value objects.
   */
  private def renderValueObject(vo: DomainModel.ValueObject): String = {
    val sb = new StringBuilder

    // Package declaration
    if (vo.packageName.nonEmpty) {
      sb.append(s"package ${vo.packageName}\n\n")
    }

    val typeParams = renderTypeParameters(vo.typeParameters)

    // Single property value objects become inline value classes
    if (vo.properties.size == 1) {
      val prop = vo.properties.head
      sb.append("@JvmInline\n")
      sb.append(s"value class ${vo.name}$typeParams(val ${prop.name}: ${renderTypeName(prop.propertyType)})\n")
    } else {
      // Multi-property becomes data class
      sb.append(s"data class ${vo.name}$typeParams(\n")
      vo.properties.zipWithIndex.foreach { case (prop, idx) =>
        val comma = if (idx < vo.properties.size - 1) "," else ""
        sb.append(s"    val ${prop.name}: ${renderTypeName(prop.propertyType)}$comma\n")
      }
      sb.append(")\n")
    }

    sb.toString
  }

  /**
   * Renders an Entity to Kotlin code as a data class.
   */
  private def renderEntity(entity: DomainModel.Entity): String = {
    val sb = new StringBuilder

    // Package declaration
    if (entity.packageName.nonEmpty) {
      sb.append(s"package ${entity.packageName}\n\n")
    }

    val typeParams = renderTypeParameters(entity.typeParameters)

    if (entity.properties.isEmpty) {
      sb.append(s"data class ${entity.name}$typeParams()\n")
    } else {
      sb.append(s"data class ${entity.name}$typeParams(\n")
      entity.properties.zipWithIndex.foreach { case (prop, idx) =>
        val comma = if (idx < entity.properties.size - 1) "," else ""
        val mutability = if (prop.isVal) "val" else "var"
        sb.append(s"    $mutability ${prop.name}: ${renderTypeName(prop.propertyType)}$comma\n")
      }
      sb.append(")\n")
    }

    sb.toString
  }

  /**
   * Renders a SealedHierarchy to Kotlin code.
   * Creates a sealed interface with data class/object subtypes.
   */
  private def renderSealedHierarchy(hierarchy: DomainModel.SealedHierarchy): String = {
    val sb = new StringBuilder

    // Package declaration
    if (hierarchy.packageName.nonEmpty) {
      sb.append(s"package ${hierarchy.packageName}\n\n")
    }

    val typeParams = renderTypeParameters(hierarchy.typeParameters)

    // Sealed interface
    sb.append(s"sealed interface ${hierarchy.name}$typeParams")
    if (hierarchy.methods.nonEmpty) {
      sb.append(" {\n")
      hierarchy.methods.foreach { method =>
        sb.append(renderMethod(method, indent = 4))
      }
      sb.append("}\n")
    } else {
      sb.append("\n")
    }

    sb.append("\n")

    // Subtypes
    hierarchy.subtypes.foreach { subtype =>
      if (subtype.properties.isEmpty) {
        // Case object becomes data object
        sb.append(s"data object ${subtype.name} : ${hierarchy.name}$typeParams\n")
      } else {
        // Case class becomes data class
        sb.append(s"data class ${subtype.name}(\n")
        subtype.properties.zipWithIndex.foreach { case (prop, idx) =>
          val comma = if (idx < subtype.properties.size - 1) "," else ""
          sb.append(s"    val ${prop.name}: ${renderTypeName(prop.propertyType)}$comma\n")
        }
        sb.append(s") : ${hierarchy.name}$typeParams\n")
      }
      sb.append("\n")
    }

    sb.toString.stripSuffix("\n")
  }

  /**
   * Renders an Enum to Kotlin code as an enum class.
   */
  private def renderEnum(enumModel: DomainModel.Enum): String = {
    val sb = new StringBuilder

    // Package declaration
    if (enumModel.packageName.nonEmpty) {
      sb.append(s"package ${enumModel.packageName}\n\n")
    }

    val valuesStr = enumModel.values.map(_.name).mkString(",\n    ")
    sb.append(s"enum class ${enumModel.name} {\n")
    sb.append(s"    $valuesStr\n")
    sb.append("}\n")

    sb.toString
  }

  /**
   * Renders a Method to Kotlin code.
   */
  private def renderMethod(method: Method, indent: Int = 0): String = {
    val sb = new StringBuilder
    val indentStr = " " * indent

    // Annotations
    method.annotations.foreach { ann =>
      sb.append(s"$indentStr@${ann.name}")
      if (ann.parameters.nonEmpty) {
        val params = ann.parameters.map { case (k, v) =>
          if (k == "value") v else s"$k = $v"
        }.mkString(", ")
        sb.append(s"($params)")
      }
      sb.append("\n")
    }

    // Method signature
    val suspendModifier = if (method.isSuspend) "suspend " else ""
    val typeParams = renderTypeParameters(method.typeParameters)
    val params = method.parameters.map { p =>
      s"${p.name}: ${renderTypeName(p.parameterType)}"
    }.mkString(", ")

    val returnTypeStr = method.returnType match {
      case Type.UnitType => ""
      case other => s": ${renderTypeName(other)}"
    }

    sb.append(s"$indentStr${suspendModifier}fun $typeParams${method.name}($params)$returnTypeStr\n")

    sb.toString
  }

  /**
   * Renders type parameters.
   */
  private def renderTypeParameters(typeParams: List[Type.TypeParameter]): String = {
    if (typeParams.isEmpty) ""
    else {
      val params = typeParams.map { tp =>
        if (tp.bounds.isEmpty) tp.name
        else s"${tp.name} : ${tp.bounds.map(renderTypeName).mkString(" & ")}"
      }.mkString(", ")
      s"<$params>"
    }
  }

  /**
   * Renders a TypeName to its Kotlin string representation.
   *
   * @param tpe The type to render
   * @return Kotlin type string
   */
  def renderTypeName(tpe: Type): String = tpe match {
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
      else s"$simpleName<${typeArgs.map(renderTypeName).mkString(", ")}>"

    case Type.TypeParameter(name, _) => name

    case Type.NullableType(underlying) => s"${renderTypeName(underlying)}?"

    case Type.ListType(elementType) => s"List<${renderTypeName(elementType)}>"

    case Type.SetType(elementType) => s"Set<${renderTypeName(elementType)}>"

    case Type.MapType(keyType, valueType) =>
      s"Map<${renderTypeName(keyType)}, ${renderTypeName(valueType)}>"

    case Type.FunctionType(paramTypes, returnType) =>
      val params = paramTypes.map(renderTypeName).mkString(", ")
      s"($params) -> ${renderTypeName(returnType)}"
  }
}
