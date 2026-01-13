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
    val typeStr = KotlinRenderer.renderTypeName(Type.Primitive("String"))
    typeStr shouldBe "String"
  }

  it should "render primitive Int type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.Primitive("Int"))
    typeStr shouldBe "Int"
  }

  it should "render primitive Boolean type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.Primitive("Boolean"))
    typeStr shouldBe "Boolean"
  }

  it should "render primitive Long type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.Primitive("Long"))
    typeStr shouldBe "Long"
  }

  it should "render primitive Double type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.Primitive("Double"))
    typeStr shouldBe "Double"
  }

  it should "render domain type" in {
    val typeStr = KotlinRenderer.renderTypeName(Type.Domain("ArtikelId"))
    typeStr shouldBe "ArtikelId"
  }

  it should "render generic List type" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.Generic("List", List(Type.Primitive("String")))
    )
    typeStr shouldBe "List<String>"
  }

  it should "render generic Map type" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.Generic("Map", List(Type.Primitive("String"), Type.Primitive("Int")))
    )
    typeStr shouldBe "Map<String, Int>"
  }

  it should "render nested generic types" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.Generic("List", List(
        Type.Generic("Map", List(Type.Primitive("String"), Type.Domain("User")))
      ))
    )
    typeStr shouldBe "List<Map<String, User>>"
  }

  it should "render Effect type as suspend function return (non-Unit)" in {
    // Effect types with non-Unit returns should render just the wrapped type
    // The suspend modifier is added to the function, not the type
    val typeStr = KotlinRenderer.renderTypeName(
      Type.Effect(Type.Primitive("String"), Type.EffectType.IO)
    )
    typeStr shouldBe "String"
  }

  it should "handle Unit type in Effect" in {
    // Effect[Unit] should be handled specially - no return type in Kotlin
    val typeStr = KotlinRenderer.renderTypeName(
      Type.Effect(Type.Unit, Type.EffectType.IO)
    )
    // For Effect[Unit], we expect empty or Unit depending on implementation
    typeStr should (be("Unit") or be(""))
  }

  it should "render complex domain type with generics" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.Generic("Result", List(Type.Domain("User")))
    )
    typeStr shouldBe "Result<User>"
  }

  it should "handle deeply nested generic types" in {
    val typeStr = KotlinRenderer.renderTypeName(
      Type.Generic("Map", List(
        Type.Primitive("String"),
        Type.Generic("List", List(
          Type.Generic("Set", List(Type.Domain("ArtikelId")))
        ))
      ))
    )
    typeStr shouldBe "Map<String, List<Set<ArtikelId>>>"
  }
}
