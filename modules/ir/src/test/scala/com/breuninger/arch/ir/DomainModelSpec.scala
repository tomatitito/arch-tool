package com.breuninger.arch.ir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DomainModelSpec extends AnyFlatSpec with Matchers {

  "ValueObject" should "represent immutable data classes" in {
    val userId = Property("id", Type.StringType, isVal = true)
    val userName = Property("name", Type.StringType, isVal = true)

    val user = DomainModel.ValueObject(
      name = "User",
      packageName = "com.example.domain",
      properties = List(userId, userName)
    )

    user.name shouldBe "User"
    user.qualifiedName shouldBe "com.example.domain.User"
    user.properties should have size 2
  }

  it should "support generic type parameters" in {
    val valueT = Type.TypeParameter("T")
    val wrapper = DomainModel.ValueObject(
      name = "Wrapper",
      packageName = "com.example",
      typeParameters = List(valueT),
      properties = List(Property("value", valueT))
    )

    wrapper.typeParameters should have size 1
    wrapper.typeParameters.head.name shouldBe "T"
  }

  "Entity" should "represent mutable domain entities" in {
    val entity = DomainModel.Entity(
      name = "Order",
      packageName = "com.example.domain",
      properties = List(
        Property("id", Type.StringType, isVal = true),
        Property("status", Type.StringType, isVal = false)
      )
    )

    entity.name shouldBe "Order"
    entity.properties should have size 2
  }

  "SealedHierarchy" should "represent closed type hierarchies" in {
    val result = DomainModel.SealedHierarchy(
      name = "Result",
      packageName = "com.example",
      typeParameters = List(Type.TypeParameter("T")),
      subtypes = List(
        DomainModel.SealedSubtype(
          name = "Success",
          properties = List(Property("value", Type.TypeParameter("T")))
        ),
        DomainModel.SealedSubtype(
          name = "Failure",
          properties = List(Property("error", Type.StringType))
        )
      )
    )

    result.subtypes should have size 2
    result.subtypes.head.name shouldBe "Success"
    result.subtypes(1).name shouldBe "Failure"
  }

  "Enum" should "represent simple enumerations" in {
    val status = DomainModel.Enum(
      name = "OrderStatus",
      packageName = "com.example.domain",
      values = List(
        DomainModel.EnumValue("PENDING"),
        DomainModel.EnumValue("CONFIRMED"),
        DomainModel.EnumValue("SHIPPED"),
        DomainModel.EnumValue("DELIVERED")
      )
    )

    status.name shouldBe "OrderStatus"
    status.values should have size 4
    status.values.head.name shouldBe "PENDING"
  }

  "Property" should "be immutable by default" in {
    val prop = Property("id", Type.StringType)
    prop.isVal shouldBe true
  }

  it should "support mutable properties" in {
    val prop = Property("status", Type.StringType, isVal = false)
    prop.isVal shouldBe false
  }

  it should "include documentation" in {
    val prop = Property(
      "id",
      Type.StringType,
      documentation = Some("Unique identifier")
    )
    prop.documentation shouldBe Some("Unique identifier")
  }
}
