package com.breuninger.arch.ir

/**
 * Application layer service representations in the IR
 *
 * Services orchestrate domain logic using port dependencies.
 * They represent use cases and application-level business logic.
 */

/**
 * Application service that orchestrates domain logic
 * In Scala: class with injected dependencies
 * In Kotlin: class with constructor injection
 *
 * @param name Simple name of the service
 * @param packageName Package/namespace
 * @param typeParameters Generic type parameters
 * @param portDependencies Ports this service depends on (injected via constructor)
 * @param otherDependencies Other non-port dependencies (config, etc.)
 * @param methods Methods defined on the service (use cases)
 * @param visibility Visibility modifier
 * @param annotations Annotations applied to the service
 * @param documentation Documentation comment
 */
case class Service(
  name: String,
  packageName: String,
  typeParameters: List[Type.TypeParameter] = Nil,
  portDependencies: List[PortDependency] = Nil,
  otherDependencies: List[Parameter] = Nil,
  methods: List[Method] = Nil,
  visibility: Visibility = Visibility.Public,
  annotations: List[Annotation] = Nil,
  documentation: Option[String] = None
) {
  def qualifiedName: String = s"$packageName.$name"

  /** All constructor parameters (port dependencies + other dependencies) */
  def constructorParameters: List[Parameter] =
    portDependencies.map(_.asParameter) ++ otherDependencies
}

/**
 * A dependency on a port interface
 *
 * @param port Reference to the port type being depended upon
 * @param parameterName Name of the constructor parameter
 * @param documentation Documentation for this dependency
 */
case class PortDependency(
  port: Type.NamedType,
  parameterName: String,
  documentation: Option[String] = None
) {
  /** Convert to a Parameter for constructor injection */
  def asParameter: Parameter = Parameter(
    name = parameterName,
    parameterType = port
  )
}
