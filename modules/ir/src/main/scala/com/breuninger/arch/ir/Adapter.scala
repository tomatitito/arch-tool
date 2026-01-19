package com.breuninger.arch.ir

/**
 * Adapter representations in the IR
 *
 * Adapters are concrete implementations of port interfaces that connect
 * to infrastructure (databases, message queues, HTTP, etc.)
 */

/**
 * Type of adapter based on infrastructure concern
 */
sealed trait AdapterType

object AdapterType {
  /** Generic adapter without specific infrastructure type */
  case object Generic extends AdapterType

  /** Persistence adapter - database access */
  case object Persistence extends AdapterType

  /** Messaging adapter - message queue integration */
  case object Messaging extends AdapterType

  /** REST adapter - HTTP API */
  case object Rest extends AdapterType

  /** Event adapter - event sourcing/handling */
  case object Event extends AdapterType
}

/**
 * Infrastructure dependency type
 */
sealed trait InfrastructureDependency {
  def parameterName: String
  def dependencyType: Type
}

object InfrastructureDependency {
  /**
   * Database dependency (MongoDB, PostgreSQL, Redis, etc.)
   */
  case class Database(
    parameterName: String,
    dependencyType: Type,
    databaseType: DatabaseType
  ) extends InfrastructureDependency

  /**
   * Message queue dependency (Kafka, RabbitMQ, etc.)
   */
  case class MessageQueue(
    parameterName: String,
    dependencyType: Type,
    queueType: MessageQueueType
  ) extends InfrastructureDependency

  /**
   * HTTP dependency (routes, controllers, clients)
   */
  case class Http(
    parameterName: String,
    dependencyType: Type,
    httpType: HttpType
  ) extends InfrastructureDependency

  /**
   * Generic infrastructure dependency
   */
  case class Other(
    parameterName: String,
    dependencyType: Type,
    description: Option[String] = None
  ) extends InfrastructureDependency
}

/**
 * Type of database infrastructure
 */
sealed trait DatabaseType

object DatabaseType {
  case object MongoCollection extends DatabaseType
  case object MongoDatabase extends DatabaseType
  case object PostgresConnection extends DatabaseType
  case object RedisClient extends DatabaseType
  case object JdbcDataSource extends DatabaseType
  case object Other extends DatabaseType
}

/**
 * Type of message queue infrastructure
 */
sealed trait MessageQueueType

object MessageQueueType {
  case object KafkaProducer extends MessageQueueType
  case object KafkaConsumer extends MessageQueueType
  case object RabbitMQChannel extends MessageQueueType
  case object RabbitMQConnection extends MessageQueueType
  case object Other extends MessageQueueType
}

/**
 * Type of HTTP infrastructure
 */
sealed trait HttpType

object HttpType {
  case object HttpRoutes extends HttpType
  case object RestController extends HttpType
  case object HttpClient extends HttpType
  case object Other extends HttpType
}

/**
 * Adapter - concrete implementation of a port interface
 * In Scala: class extending trait
 * In Kotlin: class implementing interface
 *
 * This extends the concept of PortImplementation with infrastructure-specific details.
 *
 * @param name Simple name of the adapter
 * @param packageName Package/namespace
 * @param implementedPort The port interface being implemented
 * @param adapterType The type of adapter (persistence, messaging, rest, etc.)
 * @param typeParameters Generic type parameters
 * @param infrastructureDependencies Infrastructure dependencies (DB, queues, HTTP)
 * @param otherDependencies Other non-infrastructure dependencies
 * @param methods Method implementations
 * @param visibility Visibility modifier
 * @param annotations Annotations applied to the adapter
 * @param documentation Documentation comment
 */
case class Adapter(
  name: String,
  packageName: String,
  implementedPort: Type.NamedType,
  adapterType: AdapterType = AdapterType.Generic,
  typeParameters: List[Type.TypeParameter] = Nil,
  infrastructureDependencies: List[InfrastructureDependency] = Nil,
  otherDependencies: List[Parameter] = Nil,
  methods: List[Method] = Nil,
  visibility: Visibility = Visibility.Public,
  annotations: List[Annotation] = Nil,
  documentation: Option[String] = None
) {
  def qualifiedName: String = s"$packageName.$name"

  /** All constructor parameters */
  def constructorParameters: List[Parameter] = {
    val infraParams = infrastructureDependencies.map { dep =>
      Parameter(
        name = dep.parameterName,
        parameterType = dep.dependencyType
      )
    }
    infraParams ++ otherDependencies
  }

  /** Convert to a PortImplementation for compatibility */
  def toPortImplementation: PortImplementation = PortImplementation(
    name = name,
    packageName = packageName,
    implementedPort = implementedPort,
    typeParameters = typeParameters,
    constructorParameters = constructorParameters,
    methods = methods,
    visibility = visibility,
    annotations = annotations,
    documentation = documentation
  )
}
