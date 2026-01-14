package com.breuninger.arch.renderer

import com.breuninger.arch.ir._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests specifically for type rendering in KotlinPoet.
 *
 * Verifies that all IR type representations are correctly
 * converted to Kotlin type syntax.
 */
class TypeRendererSpec extends AnyFlatSpec with Matchers {

  "TypeRenderer" should "render primitive String type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.StringType)
    typeStr shouldBe "String"
  }

  it should "render primitive Int type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.IntType)
    typeStr shouldBe "Int"
  }

  it should "render primitive Boolean type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.BooleanType)
    typeStr shouldBe "Boolean"
  }

  it should "render primitive Long type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.LongType)
    typeStr shouldBe "Long"
  }

  it should "render primitive Double type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.DoubleType)
    typeStr shouldBe "Double"
  }

  it should "render domain type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.NamedType("com.example.ArtikelId"))
    typeStr shouldBe "ArtikelId"
  }

  it should "render generic List type" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.ListType(Type.StringType)
    )
    typeStr shouldBe "List<String>"
  }

  it should "render generic Map type" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.MapType(Type.StringType, Type.IntType)
    )
    typeStr shouldBe "Map<String, Int>"
  }

  it should "render nested generic types" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.ListType(
        Type.MapType(Type.StringType, Type.NamedType("com.example.User"))
      )
    )
    typeStr shouldBe "List<Map<String, User>>"
  }

  it should "render nullable types" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.NullableType(Type.StringType)
    )
    typeStr shouldBe "String?"
  }

  it should "render Unit type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.UnitType)
    typeStr shouldBe "Unit"
  }

  it should "render complex domain type with generics" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.NamedType("com.example.Result", List(Type.NamedType("com.example.User")))
    )
    typeStr shouldBe "Result<User>"
  }

  it should "handle deeply nested generic types" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.MapType(
        Type.StringType,
        Type.ListType(
          Type.SetType(Type.NamedType("com.example.ArtikelId"))
        )
      )
    )
    typeStr shouldBe "Map<String, List<Set<ArtikelId>>>"
  }

  it should "render type parameters" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.TypeParameter("T"))
    typeStr shouldBe "T"
  }

  it should "render function types" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.FunctionType(List(Type.StringType, Type.IntType), Type.BooleanType)
    )
    typeStr shouldBe "(String, Int) -> Boolean"
  }
}
