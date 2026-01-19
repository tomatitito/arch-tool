package com.breuninger.arch.ir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AdapterSpec extends AnyFlatSpec with Matchers {

  "Adapter" should "represent persistence adapters" in {
    val portType = Type.NamedType("com.example.ports.UserRepository")

    val adapter = Adapter(
      name = "MongoUserRepository",
      packageName = "com.example.adapters.persistence",
      implementedPort = portType,
      adapterType = AdapterType.Persistence,
      infrastructureDependencies = List(
        InfrastructureDependency.Database(
          parameterName = "collection",
          dependencyType = Type.NamedType("org.mongodb.MongoCollection"),
          databaseType = DatabaseType.MongoCollection
        )
      ),
      methods = List(
        Method(
          name = "findById",
          parameters = List(Parameter("id", Type.StringType)),
          returnType = Type.NullableType(Type.NamedType("com.example.User")),
          isAbstract = false
        )
      )
    )

    adapter.name shouldBe "MongoUserRepository"
    adapter.qualifiedName shouldBe "com.example.adapters.persistence.MongoUserRepository"
    adapter.adapterType shouldBe AdapterType.Persistence
    adapter.infrastructureDependencies should have size 1
  }

  it should "represent messaging adapters" in {
    val portType = Type.NamedType("com.example.ports.OrderEventPublisher")

    val adapter = Adapter(
      name = "KafkaOrderEventPublisher",
      packageName = "com.example.adapters.messaging",
      implementedPort = portType,
      adapterType = AdapterType.Messaging,
      infrastructureDependencies = List(
        InfrastructureDependency.MessageQueue(
          parameterName = "producer",
          dependencyType = Type.NamedType("org.apache.kafka.KafkaProducer"),
          queueType = MessageQueueType.KafkaProducer
        )
      )
    )

    adapter.adapterType shouldBe AdapterType.Messaging
    adapter.infrastructureDependencies.head match {
      case InfrastructureDependency.MessageQueue(_, _, queueType) =>
        queueType shouldBe MessageQueueType.KafkaProducer
      case _ => fail("Expected MessageQueue dependency")
    }
  }

  it should "represent REST adapters" in {
    val portType = Type.NamedType("com.example.ports.UserApi")

    val adapter = Adapter(
      name = "UserController",
      packageName = "com.example.adapters.rest",
      implementedPort = portType,
      adapterType = AdapterType.Rest,
      infrastructureDependencies = List(
        InfrastructureDependency.Http(
          parameterName = "routes",
          dependencyType = Type.NamedType("io.ktor.routing.Route"),
          httpType = HttpType.HttpRoutes
        )
      ),
      annotations = List(Annotation("RestController"))
    )

    adapter.adapterType shouldBe AdapterType.Rest
    adapter.annotations.head.name shouldBe "RestController"
  }

  it should "generate constructor parameters from dependencies" in {
    val portType = Type.NamedType("com.example.ports.UserRepository")

    val adapter = Adapter(
      name = "PostgresUserRepository",
      packageName = "com.example.adapters",
      implementedPort = portType,
      infrastructureDependencies = List(
        InfrastructureDependency.Database(
          parameterName = "dataSource",
          dependencyType = Type.NamedType("javax.sql.DataSource"),
          databaseType = DatabaseType.JdbcDataSource
        )
      ),
      otherDependencies = List(
        Parameter("mapper", Type.NamedType("com.example.UserMapper"))
      )
    )

    val params = adapter.constructorParameters
    params should have size 2
    params.head.name shouldBe "dataSource"
    params(1).name shouldBe "mapper"
  }

  it should "convert to PortImplementation for compatibility" in {
    val portType = Type.NamedType("com.example.ports.UserRepository")

    val adapter = Adapter(
      name = "InMemoryUserRepository",
      packageName = "com.example.adapters",
      implementedPort = portType
    )

    val portImpl = adapter.toPortImplementation
    portImpl.name shouldBe "InMemoryUserRepository"
    portImpl.implementedPort shouldBe portType
  }

  "AdapterType" should "classify different adapter types" in {
    AdapterType.Persistence shouldBe AdapterType.Persistence
    AdapterType.Messaging shouldBe AdapterType.Messaging
    AdapterType.Rest shouldBe AdapterType.Rest
    AdapterType.Event shouldBe AdapterType.Event
    AdapterType.Generic shouldBe AdapterType.Generic
  }

  "InfrastructureDependency" should "represent database dependencies" in {
    val dbDep = InfrastructureDependency.Database(
      parameterName = "collection",
      dependencyType = Type.NamedType("MongoCollection"),
      databaseType = DatabaseType.MongoCollection
    )

    dbDep.parameterName shouldBe "collection"
    dbDep.databaseType shouldBe DatabaseType.MongoCollection
  }

  it should "represent message queue dependencies" in {
    val mqDep = InfrastructureDependency.MessageQueue(
      parameterName = "consumer",
      dependencyType = Type.NamedType("KafkaConsumer"),
      queueType = MessageQueueType.KafkaConsumer
    )

    mqDep.parameterName shouldBe "consumer"
    mqDep.queueType shouldBe MessageQueueType.KafkaConsumer
  }

  it should "represent HTTP dependencies" in {
    val httpDep = InfrastructureDependency.Http(
      parameterName = "client",
      dependencyType = Type.NamedType("HttpClient"),
      httpType = HttpType.HttpClient
    )

    httpDep.parameterName shouldBe "client"
    httpDep.httpType shouldBe HttpType.HttpClient
  }
}
