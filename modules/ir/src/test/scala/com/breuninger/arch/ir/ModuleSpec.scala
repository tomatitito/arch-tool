package com.breuninger.arch.ir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModuleSpec extends AnyFlatSpec with Matchers {

  "Module" should "organize domain models and ports" in {
    val user = DomainModel.ValueObject(
      name = "User",
      packageName = "com.example.domain",
      properties = List(Property("id", Type.StringType))
    )

    val userRepo = Port(
      name = "UserRepository",
      packageName = "com.example.ports",
      portType = PortType.Repository
    )

    val module = Module(
      name = "user",
      packageName = "com.example",
      domainModels = List(user),
      ports = List(userRepo)
    )

    module.name shouldBe "user"
    module.domainModels should have size 1
    module.ports should have size 1
  }

  it should "find domain models by name" in {
    val user = DomainModel.ValueObject(
      name = "User",
      packageName = "com.example.domain",
      properties = List(Property("id", Type.StringType))
    )

    val module = Module(
      name = "user",
      packageName = "com.example",
      domainModels = List(user)
    )

    module.findDomainModel("User") shouldBe Some(user)
    module.findDomainModel("NonExistent") shouldBe None
  }

  it should "find ports by name" in {
    val userRepo = Port(
      name = "UserRepository",
      packageName = "com.example.ports"
    )

    val module = Module(
      name = "user",
      packageName = "com.example",
      ports = List(userRepo)
    )

    module.findPort("UserRepository") shouldBe Some(userRepo)
    module.findPort("NonExistent") shouldBe None
  }

  it should "support nested sub-modules" in {
    val subModule = Module(
      name = "entities",
      packageName = "com.example.domain.entities"
    )

    val parentModule = Module(
      name = "domain",
      packageName = "com.example.domain",
      subModules = List(subModule)
    )

    parentModule.subModules should have size 1
    parentModule.subModules.head.name shouldBe "entities"
  }

  it should "collect all types recursively" in {
    val user = DomainModel.ValueObject(
      name = "User",
      packageName = "com.example.domain",
      properties = Nil
    )

    val order = DomainModel.ValueObject(
      name = "Order",
      packageName = "com.example.domain.entities",
      properties = Nil
    )

    val subModule = Module(
      name = "entities",
      packageName = "com.example.domain.entities",
      domainModels = List(order)
    )

    val parentModule = Module(
      name = "domain",
      packageName = "com.example.domain",
      domainModels = List(user),
      subModules = List(subModule)
    )

    val allTypes = parentModule.allTypes
    allTypes should have size 2
    allTypes should contain("com.example.domain.User")
    allTypes should contain("com.example.domain.entities.Order")
  }

  "Project" should "organize multiple modules" in {
    val domainModule = Module(
      name = "domain",
      packageName = "com.example.domain"
    )

    val infraModule = Module(
      name = "infrastructure",
      packageName = "com.example.infrastructure"
    )

    val project = Project(
      name = "my-project",
      rootPackage = "com.example",
      modules = List(domainModule, infraModule)
    )

    project.name shouldBe "my-project"
    project.modules should have size 2
  }

  it should "collect all modules recursively" in {
    val subModule = Module(
      name = "entities",
      packageName = "com.example.domain.entities"
    )

    val domainModule = Module(
      name = "domain",
      packageName = "com.example.domain",
      subModules = List(subModule)
    )

    val project = Project(
      name = "my-project",
      rootPackage = "com.example",
      modules = List(domainModule)
    )

    val allModules = project.allModules
    allModules should have size 2
    allModules.map(_.name) should contain allOf ("domain", "entities")
  }

  it should "collect all domain models across modules" in {
    val user = DomainModel.ValueObject(
      name = "User",
      packageName = "com.example.domain",
      properties = Nil
    )

    val order = DomainModel.ValueObject(
      name = "Order",
      packageName = "com.example.domain",
      properties = Nil
    )

    val module1 = Module(
      name = "module1",
      packageName = "com.example.module1",
      domainModels = List(user)
    )

    val module2 = Module(
      name = "module2",
      packageName = "com.example.module2",
      domainModels = List(order)
    )

    val project = Project(
      name = "my-project",
      rootPackage = "com.example",
      modules = List(module1, module2)
    )

    val allModels = project.allDomainModels
    allModels should have size 2
    allModels.map(_.name) should contain allOf ("User", "Order")
  }

  it should "collect all ports across modules" in {
    val userRepo = Port(name = "UserRepository", packageName = "com.example")
    val orderRepo = Port(name = "OrderRepository", packageName = "com.example")

    val module1 = Module(
      name = "module1",
      packageName = "com.example.module1",
      ports = List(userRepo)
    )

    val module2 = Module(
      name = "module2",
      packageName = "com.example.module2",
      ports = List(orderRepo)
    )

    val project = Project(
      name = "my-project",
      rootPackage = "com.example",
      modules = List(module1, module2)
    )

    val allPorts = project.allPorts
    allPorts should have size 2
    allPorts.map(_.name) should contain allOf ("UserRepository", "OrderRepository")
  }

  "Import" should "represent import statements" in {
    val simpleImport = Import("com.example.User")
    simpleImport.qualifiedName shouldBe "com.example.User"
    simpleImport.alias shouldBe None
  }

  it should "support import aliases" in {
    val aliasedImport = Import("com.example.VeryLongClassName", Some("Short"))
    aliasedImport.qualifiedName shouldBe "com.example.VeryLongClassName"
    aliasedImport.alias shouldBe Some("Short")
  }
}
