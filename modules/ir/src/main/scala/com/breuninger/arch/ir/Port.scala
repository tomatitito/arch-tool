package com.breuninger.arch.ir

/**
 * Port representations in the IR
 *
 * Ports are interface contracts that define boundaries between architectural layers
 * (e.g., repository interfaces, service interfaces)
 */

/**
 * Port interface definition
 * In Scala: trait
 * In Kotlin: interface
 *
 * @param name Simple name of the port interface
 * @param packageName Package/namespace
 * @param typeParameters Generic type parameters
 * @param methods Abstract methods defined in the port
 * @param superInterfaces Parent interfaces this port extends
 * @param visibility Visibility modifier
 * @param annotations Annotations applied to the port
 * @param documentation Documentation comment
 * @param portType The architectural type of this port
 */
case class Port(
  name: String,
  packageName: String,
  typeParameters: List[Type.TypeParameter] = Nil,
  methods: List[Method] = Nil,
  superInterfaces: List[Type.NamedType] = Nil,
  visibility: Visibility = Visibility.Public,
  annotations: List[Annotation] = Nil,
  documentation: Option[String] = None,
  portType: PortType = PortType.Generic
) {
  def qualifiedName: String = s"$packageName.$name"
}

/**
 * Type of port in the architecture
 */
sealed trait PortType

object PortType {
  /** Generic port interface */
  case object Generic extends PortType

  /** Repository port - data access interface */
  case object Repository extends PortType

  /** Service port - application service interface */
  case object Service extends PortType

  /** Use case port - domain use case interface */
  case object UseCase extends PortType

  /** Event handler port - event processing interface */
  case object EventHandler extends PortType
}

/**
 * Port implementation (adapter)
 * Represents a concrete implementation of a port interface
 *
 * @param name Simple name of the implementation
 * @param packageName Package/namespace
 * @param implementedPort The port interface being implemented
 * @param typeParameters Generic type parameters
 * @param constructorParameters Dependencies injected via constructor
 * @param methods Method implementations
 * @param visibility Visibility modifier
 * @param annotations Annotations applied to the implementation
 * @param documentation Documentation comment
 */
case class PortImplementation(
  name: String,
  packageName: String,
  implementedPort: Type.NamedType,
  typeParameters: List[Type.TypeParameter] = Nil,
  constructorParameters: List[Parameter] = Nil,
  methods: List[Method] = Nil,
  visibility: Visibility = Visibility.Public,
  annotations: List[Annotation] = Nil,
  documentation: Option[String] = None
) {
  def qualifiedName: String = s"$packageName.$name"
}
