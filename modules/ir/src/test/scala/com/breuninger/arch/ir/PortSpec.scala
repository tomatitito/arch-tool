package com.breuninger.arch.ir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PortSpec extends AnyFlatSpec with Matchers {

  "Port" should "represent repository interfaces" in {
    val findById = Method(
      name = "findById",
      parameters = List(Parameter("id", Type.StringType)),
      returnType = Type.NullableType(Type.NamedType("com.example.User"))
    )

    val save = Method(
      name = "save",
      parameters = List(Parameter("user", Type.NamedType("com.example.User"))),
      returnType = Type.UnitType
    )

    val userRepository = Port(
      name = "UserRepository",
      packageName = "com.example.ports",
      methods = List(findById, save),
      portType = PortType.Repository
    )

    userRepository.name shouldBe "UserRepository"
    userRepository.qualifiedName shouldBe "com.example.ports.UserRepository"
    userRepository.methods should have size 2
    userRepository.portType shouldBe PortType.Repository
  }

  it should "support generic type parameters" in {
    val typeT = Type.TypeParameter("T")
    val genericRepo = Port(
      name = "Repository",
      packageName = "com.example.ports",
      typeParameters = List(typeT),
      methods = List(
        Method(name = "findById", parameters = List(Parameter("id", Type.StringType)), returnType = Type.NullableType(typeT)),
        Method(name = "save", parameters = List(Parameter("entity", typeT)), returnType = Type.UnitType)
      )
    )

    genericRepo.typeParameters should have size 1
    genericRepo.typeParameters.head.name shouldBe "T"
  }

  it should "support inheritance" in {
    val basePort = Type.NamedType("com.example.BasePort")
    val derivedPort = Port(
      name = "DerivedPort",
      packageName = "com.example.ports",
      superInterfaces = List(basePort)
    )

    derivedPort.superInterfaces should have size 1
    derivedPort.superInterfaces.head.qualifiedName shouldBe "com.example.BasePort"
  }

  "PortImplementation" should "implement a port interface" in {
    val portType = Type.NamedType("com.example.ports.UserRepository")

    val impl = PortImplementation(
      name = "InMemoryUserRepository",
      packageName = "com.example.adapters",
      implementedPort = portType,
      constructorParameters = List(
        Parameter("cache", Type.NamedType("com.example.Cache"))
      )
    )

    impl.name shouldBe "InMemoryUserRepository"
    impl.implementedPort.qualifiedName shouldBe "com.example.ports.UserRepository"
    impl.constructorParameters should have size 1
  }

  "Method" should "support suspend functions" in {
    val suspendMethod = Method(
      name = "fetchUser",
      parameters = List(Parameter("id", Type.StringType)),
      returnType = Type.NamedType("com.example.User"),
      isSuspend = true
    )

    suspendMethod.isSuspend shouldBe true
  }

  it should "be abstract by default" in {
    val method = Method(name = "doSomething", parameters = Nil, returnType = Type.UnitType)
    method.isAbstract shouldBe true
  }

  it should "support generic type parameters" in {
    val methodTypeT = Type.TypeParameter("T")
    val genericMethod = Method(
      name = "process",
      typeParameters = List(methodTypeT),
      parameters = List(Parameter("input", methodTypeT)),
      returnType = methodTypeT
    )

    genericMethod.typeParameters should have size 1
  }

  "PortType" should "classify different port types" in {
    PortType.Repository shouldBe PortType.Repository
    PortType.Service shouldBe PortType.Service
    PortType.UseCase shouldBe PortType.UseCase
    PortType.EventHandler shouldBe PortType.EventHandler
  }
}
