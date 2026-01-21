package com.breuninger.arch.validator

import com.breuninger.arch.ir._

/**
 * Default implementation of ArchitectureValidator.
 *
 * Validates that IR conforms to hexagonal architecture rules:
 * - Ports must have at least one method
 * - Domain models must have valid types
 * - Sealed hierarchies must have at least one subtype
 * - Method signatures must be valid
 */
class DefaultArchitectureValidator extends ArchitectureValidator {

  override def validateModels(models: List[DomainModel]): ValidationResult = {
    val results = models.map(validateModel)
    results.foldLeft(ValidationResult.valid)(_ ++ _)
  }

  override def validatePorts(ports: List[Port]): ValidationResult = {
    val results = ports.map(validatePort)
    results.foldLeft(ValidationResult.valid)(_ ++ _)
  }

  override def validate(
    models: List[DomainModel],
    ports: List[Port]
  ): ValidationResult = {
    validateModels(models) ++ validatePorts(ports) ++ validateCrossReferences(models, ports)
  }

  /**
   * Validate a single domain model.
   */
  private def validateModel(model: DomainModel): ValidationResult = model match {
    case vo: DomainModel.ValueObject => validateValueObject(vo)
    case entity: DomainModel.Entity => validateEntity(entity)
    case hierarchy: DomainModel.SealedHierarchy => validateSealedHierarchy(hierarchy)
    case enumModel: DomainModel.Enum => validateEnum(enumModel)
  }

  /**
   * Validate a value object.
   */
  private def validateValueObject(vo: DomainModel.ValueObject): ValidationResult = {
    val errors = List.newBuilder[ValidationError]
    val warnings = List.newBuilder[ValidationWarning]

    // Value objects should have at least one property
    if (vo.properties.isEmpty) {
      errors += ValidationError(
        message = s"Value object '${vo.name}' has no properties",
        rule = DomainModelIntegrity.name
      )
    }

    // Warn if value object has more than one property (might not be a true value object)
    if (vo.properties.size > 1) {
      warnings += ValidationWarning(
        message = s"Value object '${vo.name}' has ${vo.properties.size} properties",
        suggestion = Some("Consider if this should be an Entity instead, or split into multiple value objects")
      )
    }

    // Validate property types
    vo.properties.foreach { prop =>
      validateType(prop.propertyType, s"${vo.name}.${prop.name}").foreach(errors += _)
    }

    ValidationResult(
      isValid = errors.result().isEmpty,
      errors = errors.result(),
      warnings = warnings.result()
    )
  }

  /**
   * Validate an entity.
   */
  private def validateEntity(entity: DomainModel.Entity): ValidationResult = {
    val errors = List.newBuilder[ValidationError]
    val warnings = List.newBuilder[ValidationWarning]

    // Entities can be empty (though unusual)
    if (entity.properties.isEmpty) {
      warnings += ValidationWarning(
        message = s"Entity '${entity.name}' has no properties",
        suggestion = Some("Consider if this entity needs any data fields")
      )
    }

    // Validate property types
    entity.properties.foreach { prop =>
      validateType(prop.propertyType, s"${entity.name}.${prop.name}").foreach(errors += _)
    }

    ValidationResult(
      isValid = errors.result().isEmpty,
      errors = errors.result(),
      warnings = warnings.result()
    )
  }

  /**
   * Validate a sealed hierarchy.
   */
  private def validateSealedHierarchy(hierarchy: DomainModel.SealedHierarchy): ValidationResult = {
    val errors = List.newBuilder[ValidationError]
    val warnings = List.newBuilder[ValidationWarning]

    // Sealed hierarchies should have at least one subtype
    if (hierarchy.subtypes.isEmpty) {
      errors += ValidationError(
        message = s"Sealed hierarchy '${hierarchy.name}' has no subtypes",
        rule = DomainModelIntegrity.name
      )
    }

    // Validate subtype names are unique
    val subtypeNames = hierarchy.subtypes.map(_.name)
    val duplicates = subtypeNames.diff(subtypeNames.distinct)
    duplicates.foreach { dup =>
      errors += ValidationError(
        message = s"Sealed hierarchy '${hierarchy.name}' has duplicate subtype name: '$dup'",
        rule = DomainModelIntegrity.name
      )
    }

    // Validate each subtype
    hierarchy.subtypes.foreach { subtype =>
      subtype.properties.foreach { prop =>
        validateType(prop.propertyType, s"${hierarchy.name}.${subtype.name}.${prop.name}").foreach(errors += _)
      }
    }

    ValidationResult(
      isValid = errors.result().isEmpty,
      errors = errors.result(),
      warnings = warnings.result()
    )
  }

  /**
   * Validate an enum.
   */
  private def validateEnum(enumModel: DomainModel.Enum): ValidationResult = {
    val errors = List.newBuilder[ValidationError]
    val warnings = List.newBuilder[ValidationWarning]

    // Enums should have at least one value
    if (enumModel.values.isEmpty) {
      errors += ValidationError(
        message = s"Enum '${enumModel.name}' has no values",
        rule = DomainModelIntegrity.name
      )
    }

    // Validate value names are unique
    val valueNames = enumModel.values.map(_.name)
    val duplicates = valueNames.diff(valueNames.distinct)
    duplicates.foreach { dup =>
      errors += ValidationError(
        message = s"Enum '${enumModel.name}' has duplicate value name: '$dup'",
        rule = DomainModelIntegrity.name
      )
    }

    // Warn on very large enums
    if (enumModel.values.size > 50) {
      warnings += ValidationWarning(
        message = s"Enum '${enumModel.name}' has ${enumModel.values.size} values",
        suggestion = Some("Consider if a different data structure would be more appropriate")
      )
    }

    ValidationResult(
      isValid = errors.result().isEmpty,
      errors = errors.result(),
      warnings = warnings.result()
    )
  }

  /**
   * Validate a port interface.
   */
  private def validatePort(port: Port): ValidationResult = {
    val errors = List.newBuilder[ValidationError]
    val warnings = List.newBuilder[ValidationWarning]

    // Ports should have at least one method (warning, not error)
    if (port.methods.isEmpty) {
      warnings += ValidationWarning(
        message = s"Port '${port.name}' has no methods",
        suggestion = Some("Consider if this port interface needs any operations")
      )
    }

    // Validate method names are unique
    val methodNames = port.methods.map(_.name)
    val duplicates = methodNames.diff(methodNames.distinct)
    duplicates.foreach { dup =>
      errors += ValidationError(
        message = s"Port '${port.name}' has duplicate method name: '$dup'",
        rule = HexagonalArchitecture.name
      )
    }

    // Validate each method
    port.methods.foreach { method =>
      validateMethod(method, port.name).foreach(errors += _)
    }

    // Warn if port naming doesn't follow conventions
    val nameConventions = List("Repository", "Port", "Service", "UseCase", "Handler", "Gateway")
    if (!nameConventions.exists(port.name.contains)) {
      warnings += ValidationWarning(
        message = s"Port '${port.name}' doesn't follow naming conventions",
        suggestion = Some(s"Consider using a suffix like: ${nameConventions.mkString(", ")}")
      )
    }

    ValidationResult(
      isValid = errors.result().isEmpty,
      errors = errors.result(),
      warnings = warnings.result()
    )
  }

  /**
   * Validate a method signature.
   */
  private def validateMethod(method: Method, context: String): List[ValidationError] = {
    val errors = List.newBuilder[ValidationError]

    // Validate parameter names are unique
    val paramNames = method.parameters.map(_.name)
    val duplicates = paramNames.diff(paramNames.distinct)
    duplicates.foreach { dup =>
      errors += ValidationError(
        message = s"Method '$context.${method.name}' has duplicate parameter name: '$dup'",
        rule = TypeSafety.name
      )
    }

    // Validate parameter types
    method.parameters.foreach { param =>
      validateType(param.parameterType, s"$context.${method.name}.${param.name}").foreach(errors += _)
    }

    // Validate return type
    validateType(method.returnType, s"$context.${method.name} return type").foreach(errors += _)

    errors.result()
  }

  /**
   * Validate a type is well-formed.
   */
  private def validateType(tpe: Type, context: String): Option[ValidationError] = tpe match {
    case Type.NamedType(name, _) if name.isEmpty =>
      Some(ValidationError(
        message = s"Invalid empty type name in $context",
        rule = TypeSafety.name
      ))
    case Type.ListType(elementType) =>
      validateType(elementType, s"$context element")
    case Type.SetType(elementType) =>
      validateType(elementType, s"$context element")
    case Type.MapType(keyType, valueType) =>
      validateType(keyType, s"$context key").orElse(validateType(valueType, s"$context value"))
    case Type.NullableType(underlying) =>
      validateType(underlying, s"$context underlying")
    case Type.FunctionType(paramTypes, returnType) =>
      paramTypes.flatMap(p => validateType(p, s"$context parameter")).headOption
        .orElse(validateType(returnType, s"$context return"))
    case _ => None
  }

  /**
   * Validate cross-references between models and ports.
   */
  private def validateCrossReferences(
    models: List[DomainModel],
    ports: List[Port]
  ): ValidationResult = {
    val warnings = List.newBuilder[ValidationWarning]

    // Collect all domain model names
    val modelNames = models.map(_.name).toSet

    // Check if port methods reference domain types
    ports.foreach { port =>
      port.methods.foreach { method =>
        val typesUsed = collectTypeNames(method.returnType) ++
          method.parameters.flatMap(p => collectTypeNames(p.parameterType))

        // Warn about potential missing domain types (only for non-primitive, non-common types)
        val commonTypes = Set("String", "Int", "Long", "Double", "Float", "Boolean", "Unit", "Any",
          "List", "Set", "Map", "Option", "Either", "Future", "IO", "Task")
        val unknownTypes = typesUsed.filterNot(t => modelNames.contains(t) || commonTypes.contains(t))

        // Only warn if there are truly unknown types (skip qualified names)
        unknownTypes.filterNot(_.contains(".")).foreach { typeName =>
          warnings += ValidationWarning(
            message = s"Port '${port.name}.${method.name}' references type '$typeName' which is not in the parsed domain models",
            suggestion = Some("Ensure the type is defined in the same file or imported correctly")
          )
        }
      }
    }

    ValidationResult(
      isValid = true,
      errors = Nil,
      warnings = warnings.result()
    )
  }

  /**
   * Collect all type names referenced in a type.
   */
  private def collectTypeNames(tpe: Type): Set[String] = tpe match {
    case Type.NamedType(name, typeArgs) =>
      Set(name.split('.').last) ++ typeArgs.flatMap(collectTypeNames).toSet
    case Type.TypeParameter(name, bounds) =>
      Set(name) ++ bounds.flatMap(collectTypeNames).toSet
    case Type.NullableType(underlying) =>
      collectTypeNames(underlying)
    case Type.ListType(elementType) =>
      collectTypeNames(elementType)
    case Type.SetType(elementType) =>
      collectTypeNames(elementType)
    case Type.MapType(keyType, valueType) =>
      collectTypeNames(keyType) ++ collectTypeNames(valueType)
    case Type.FunctionType(paramTypes, returnType) =>
      paramTypes.flatMap(collectTypeNames).toSet ++ collectTypeNames(returnType)
    case _ => Set.empty
  }
}

/**
 * Companion object with factory method.
 */
object DefaultArchitectureValidator {
  def apply(): DefaultArchitectureValidator = new DefaultArchitectureValidator()
}
