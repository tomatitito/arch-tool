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
    val methodsStr = port.methods.map(renderMethod).mkString("\n")
    val packageLine = if (port.packageName.nonEmpty) s"package ${port.packageName}\n\n" else ""

    if (port.methods.isEmpty) {
      s"""${packageLine}interface ${port.name} {
         |}
         |""".stripMargin
    } else {
      s"""${packageLine}interface ${port.name} {
         |$methodsStr
         |}
         |""".stripMargin
    }
  }

  /**
   * Renders a Method to Kotlin code.
   */
  private def renderMethod(method: Method): String = {
    val suspendMod = if (method.isSuspend) "suspend " else ""
    val paramsStr = method.parameters.map { p =>
      s"${p.name}: ${renderTypeNameSimple(p.parameterType)}"
    }.mkString(", ")
    val returnTypeStr = renderTypeNameSimple(method.returnType)

    s"    ${suspendMod}fun ${method.name}($paramsStr): $returnTypeStr"
  }

  /**
   * Renders a Service to Kotlin code.
   *
   * Generates a Spring @Service annotated class with:
   * - Constructor injection for dependencies
   * - Private val properties for port dependencies
   * - Suspend functions for methods with effect types
   *
   * @param service The service to render
   * @return Kotlin source code as a string
   */
  def renderService(service: Service): String = {
    val packageLine = if (service.packageName.nonEmpty) s"package ${service.packageName}\n\n" else ""

    // Collect imports
    val serviceImport = "import org.springframework.stereotype.Service"
    val paramTypes = service.portDependencies.map(_.port) ++ service.otherDependencies.map(_.parameterType)
    val methodParamTypes = service.methods.flatMap(m => m.parameters.map(_.parameterType) :+ m.returnType)
    val allTypes = paramTypes ++ methodParamTypes
    val typeImports = collectImports(allTypes)
    val allImports = (serviceImport :: typeImports).sorted.distinct
    val importsStr = allImports.mkString("\n") + "\n\n"

    // Build constructor parameters
    val constructorParams = service.constructorParameters.map { param =>
      s"    private val ${param.name}: ${renderTypeNameSimple(param.parameterType)}"
    }

    val constructorStr = if (constructorParams.isEmpty) {
      "()"
    } else if (constructorParams.length == 1) {
      s"(${constructorParams.head.trim})"
    } else {
      s"(\n${constructorParams.mkString(",\n")}\n)"
    }

    // Build methods
    val methodsStr = service.methods.map(renderServiceMethod).mkString("\n\n")

    val bodyStr = if (methodsStr.nonEmpty) {
      s" {\n$methodsStr\n}"
    } else {
      ""
    }

    s"""${packageLine}${importsStr}@Service
       |class ${service.name}$constructorStr$bodyStr
       |""".stripMargin
  }

  /**
   * Renders a service method to Kotlin code.
   */
  private def renderServiceMethod(method: Method): String = {
    val suspendMod = if (method.isSuspend) "suspend " else ""
    val paramsStr = method.parameters.map { p =>
      s"${p.name}: ${renderTypeNameSimple(p.parameterType)}"
    }.mkString(", ")

    val returnTypeStr = method.returnType match {
      case Type.UnitType => ""
      case other => s": ${renderTypeNameSimple(other)}"
    }

    // For now, generate TODO body since we're not migrating implementation
    s"""    ${suspendMod}fun ${method.name}($paramsStr)$returnTypeStr {
       |        TODO("Migrate implementation from Scala")
       |    }""".stripMargin
  }

  /**
   * Renders a DomainModel to Kotlin code.
   *
   * @param model The domain model to render
   * @return Kotlin source code as a string
   */
  def renderDomainModel(model: DomainModel): String = {
    model match {
      case vo: DomainModel.ValueObject =>
        renderDataClass(vo.name, vo.packageName, vo.properties)

      case entity: DomainModel.Entity =>
        renderDataClass(entity.name, entity.packageName, entity.properties)

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
   * Renders a data class with proper formatting.
   */
  private def renderDataClass(name: String, packageName: String, properties: List[Property]): String = {
    // Collect imports from types used
    val imports = collectImports(properties.map(_.propertyType))
    val importsStr = if (imports.nonEmpty) {
      imports.sorted.map(i => s"import $i").mkString("\n") + "\n\n"
    } else ""

    val packageLine = if (packageName.nonEmpty) s"package $packageName\n\n" else ""

    if (properties.isEmpty) {
      s"""${packageLine}${importsStr}data class $name()
         |""".stripMargin
    } else if (properties.length == 1) {
      // Single property - keep on one line
      val p = properties.head
      s"""${packageLine}${importsStr}data class $name(val ${p.name}: ${renderTypeNameSimple(p.propertyType)})
         |""".stripMargin
    } else {
      // Multiple properties - format multi-line
      val propsStr = properties.map { p =>
        s"    val ${p.name}: ${renderTypeNameSimple(p.propertyType)}"
      }.mkString(",\n")
      s"""${packageLine}${importsStr}data class $name(
         |$propsStr
         |)
         |""".stripMargin
    }
  }

  /**
   * Collects required imports from a list of types.
   */
  private def collectImports(types: List[Type]): List[String] = {
    types.flatMap(collectTypeImports).distinct
  }

  /**
   * Collects imports required for a single type.
   */
  private def collectTypeImports(tpe: Type): List[String] = tpe match {
    case Type.NamedType(qualifiedName, typeArgs) =>
      val directImport = qualifiedName match {
        case "Locale" => Some("java.util.Locale")
        case "Instant" => Some("java.time.Instant")
        case "Duration" => Some("java.time.Duration")
        case "LocalDate" => Some("java.time.LocalDate")
        case "LocalDateTime" => Some("java.time.LocalDateTime")
        case "UUID" => Some("java.util.UUID")
        case _ => None
      }
      directImport.toList ++ typeArgs.flatMap(collectTypeImports)

    case Type.NullableType(underlying) =>
      collectTypeImports(underlying)

    case Type.ListType(elementType) =>
      collectTypeImports(elementType)

    case Type.SetType(elementType) =>
      collectTypeImports(elementType)

    case Type.MapType(keyType, valueType) =>
      collectTypeImports(keyType) ++ collectTypeImports(valueType)

    case Type.FunctionType(paramTypes, returnType) =>
      paramTypes.flatMap(collectTypeImports) ++ collectTypeImports(returnType)

    case _ => Nil
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
