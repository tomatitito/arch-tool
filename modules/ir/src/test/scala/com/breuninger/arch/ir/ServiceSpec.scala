package com.breuninger.arch.ir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ServiceSpec extends AnyFlatSpec with Matchers {

  "Service" should "represent application services with port dependencies" in {
    val userRepo = Type.NamedType("com.example.ports.UserRepository")
    val orderRepo = Type.NamedType("com.example.ports.OrderRepository")

    val service = Service(
      name = "OrderService",
      packageName = "com.example.application",
      portDependencies = List(
        PortDependency(userRepo, "userRepository"),
        PortDependency(orderRepo, "orderRepository")
      ),
      methods = List(
        Method(
          name = "placeOrder",
          parameters = List(
            Parameter("userId", Type.StringType),
            Parameter("items", Type.ListType(Type.NamedType("com.example.OrderItem")))
          ),
          returnType = Type.NamedType("com.example.Order"),
          isAbstract = false
        )
      )
    )

    service.name shouldBe "OrderService"
    service.qualifiedName shouldBe "com.example.application.OrderService"
    service.portDependencies should have size 2
    service.methods should have size 1
  }

  it should "generate constructor parameters from dependencies" in {
    val userRepo = Type.NamedType("com.example.ports.UserRepository")
    val configParam = Parameter("config", Type.NamedType("com.example.Config"))

    val service = Service(
      name = "UserService",
      packageName = "com.example.application",
      portDependencies = List(PortDependency(userRepo, "userRepository")),
      otherDependencies = List(configParam)
    )

    val constructorParams = service.constructorParameters
    constructorParams should have size 2
    constructorParams.head.name shouldBe "userRepository"
    constructorParams(1).name shouldBe "config"
  }

  it should "support annotations" in {
    val service = Service(
      name = "AnnotatedService",
      packageName = "com.example",
      annotations = List(
        Annotation("Service"),
        Annotation("Transactional", Map("readOnly" -> "true"))
      )
    )

    service.annotations should have size 2
    service.annotations.head.name shouldBe "Service"
  }

  "PortDependency" should "convert to Parameter" in {
    val portType = Type.NamedType("com.example.ports.UserRepository")
    val dependency = PortDependency(portType, "userRepo")

    val param = dependency.asParameter
    param.name shouldBe "userRepo"
    param.parameterType shouldBe portType
  }

  it should "preserve port type information" in {
    val portType = Type.NamedType("com.example.ports.OrderRepository")
    val dependency = PortDependency(
      port = portType,
      parameterName = "orderRepository",
      documentation = Some("Repository for order persistence")
    )

    dependency.port.qualifiedName shouldBe "com.example.ports.OrderRepository"
    dependency.documentation shouldBe Some("Repository for order persistence")
  }
}
