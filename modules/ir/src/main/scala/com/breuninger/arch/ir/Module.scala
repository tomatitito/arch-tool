package com.breuninger.arch.ir

/**
 * Module and package representations in the IR
 *
 * Represents the organizational structure of the codebase
 */

/**
 * A module represents a cohesive unit of code (a package or namespace)
 *
 * @param name Module name (e.g., "domain", "infrastructure")
 * @param packageName Full package name (e.g., "com.example.domain")
 * @param domainModels Domain models defined in this module
 * @param ports Port interfaces defined in this module
 * @param portImplementations Port implementations in this module (legacy, use adapters)
 * @param services Application services defined in this module
 * @param adapters Adapter implementations in this module
 * @param subModules Nested sub-modules
 * @param imports External dependencies/imports
 * @param documentation Documentation comment
 */
case class Module(
  name: String,
  packageName: String,
  domainModels: List[DomainModel] = Nil,
  ports: List[Port] = Nil,
  portImplementations: List[PortImplementation] = Nil,
  services: List[Service] = Nil,
  adapters: List[Adapter] = Nil,
  subModules: List[Module] = Nil,
  imports: List[Import] = Nil,
  documentation: Option[String] = None
) {
  /** Get all types defined in this module (recursively) */
  def allTypes: List[String] = {
    val localTypes =
      domainModels.map(_.qualifiedName) ++
      ports.map(_.qualifiedName) ++
      portImplementations.map(_.qualifiedName)

    localTypes ++ subModules.flatMap(_.allTypes)
  }

  /** Find a domain model by name */
  def findDomainModel(name: String): Option[DomainModel] =
    domainModels.find(_.name == name)
      .orElse(subModules.flatMap(_.findDomainModel(name)).headOption)

  /** Find a port by name */
  def findPort(name: String): Option[Port] =
    ports.find(_.name == name)
      .orElse(subModules.flatMap(_.findPort(name)).headOption)

  /** Find a service by name */
  def findService(name: String): Option[Service] =
    services.find(_.name == name)
      .orElse(subModules.flatMap(_.findService(name)).headOption)

  /** Find an adapter by name */
  def findAdapter(name: String): Option[Adapter] =
    adapters.find(_.name == name)
      .orElse(subModules.flatMap(_.findAdapter(name)).headOption)
}

/**
 * Import statement
 * @param qualifiedName Fully qualified name being imported
 * @param alias Optional import alias
 */
case class Import(
  qualifiedName: String,
  alias: Option[String] = None
)

/**
 * Complete IR model representing a project or codebase
 *
 * @param name Project name
 * @param rootPackage Root package name
 * @param modules Top-level modules
 * @param metadata Additional metadata about the project
 */
case class Project(
  name: String,
  rootPackage: String,
  modules: List[Module],
  metadata: Map[String, String] = Map.empty
) {
  /** Get all modules (recursively) */
  def allModules: List[Module] = {
    def collectModules(module: Module): List[Module] =
      module :: module.subModules.flatMap(collectModules)

    modules.flatMap(collectModules)
  }

  /** Get all domain models across all modules */
  def allDomainModels: List[DomainModel] =
    allModules.flatMap(_.domainModels)

  /** Get all ports across all modules */
  def allPorts: List[Port] =
    allModules.flatMap(_.ports)

  /** Get all port implementations across all modules */
  def allPortImplementations: List[PortImplementation] =
    allModules.flatMap(_.portImplementations)

  /** Get all services across all modules */
  def allServices: List[Service] =
    allModules.flatMap(_.services)

  /** Get all adapters across all modules */
  def allAdapters: List[Adapter] =
    allModules.flatMap(_.adapters)
}
