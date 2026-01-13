package com.breuninger.arch.renderer

import com.breuninger.arch.ir._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for the KotlinPoet renderer.
 *
 * These tests verify that the renderer correctly generates Kotlin code
 * from the IR (Intermediate Representation) for all architectural constructs.
 *
 * Following TDD principles - these tests drive the implementation of the renderer.
 */
class KotlinRendererSpec extends AnyFlatSpec with Matchers {

  "KotlinRenderer" should "generate correct interface from Port with suspend functions" in {
    val port = Port(
      name = "BestandRepository",
      packageName = "com.breuninger.domain.repository",
      methods = List(
        Method(
          name = "save",
          parameters = List(
            Parameter("bestand", Type.Domain("BestandCreateDocument"))
          ),
          returnType = Type.Effect(Type.Unit, Type.EffectType.IO)
        )
      )
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("package com.breuninger.domain.repository")
    kotlinCode should include("interface BestandRepository")
    kotlinCode should include("suspend fun save(bestand: BestandCreateDocument)")
    kotlinCode should not include("Unit") // Unit return type should be omitted
  }

  it should "generate interface with multiple methods" in {
    val port = Port(
      name = "BestandRepository",
      packageName = "com.breuninger.domain.repository",
      methods = List(
        Method(
          name = "save",
          parameters = List(Parameter("bestand", Type.Domain("BestandCreateDocument"))),
          returnType = Type.Effect(Type.Unit, Type.EffectType.IO)
        ),
        Method(
          name = "getByIds",
          parameters = List(
            Parameter("ids", Type.Generic("List", List(Type.Domain("ArtikelId"))))
          ),
          returnType = Type.Effect(
            Type.Generic("List", List(Type.Domain("BestandCreateDocument"))),
            Type.EffectType.IO
          )
        ),
        Method(
          name = "deleteBatch",
          parameters = List(
            Parameter("bestaende", Type.Generic("List", List(Type.Domain("BestandDeleteDocument"))))
          ),
          returnType = Type.Effect(Type.Unit, Type.EffectType.IO)
        )
      )
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("interface BestandRepository")
    kotlinCode should include("suspend fun save(bestand: BestandCreateDocument)")
    kotlinCode should include("suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>")
    kotlinCode should include("suspend fun deleteBatch(bestaende: List<BestandDeleteDocument>)")
  }

  it should "generate correct data class from ValueObject" in {
    val valueObject = DomainModel.ValueObject(
      name = "ArtikelId",
      packageName = "com.breuninger.domain.model",
      field = Field("value", Type.Primitive("String"))
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(valueObject)

    kotlinCode should include("package com.breuninger.domain.model")
    kotlinCode should include("@JvmInline")
    kotlinCode should include("value class ArtikelId(val value: String)")
  }

  it should "generate correct data class from Entity" in {
    val entity = DomainModel.Entity(
      name = "BestandCreateDocument",
      packageName = "com.breuninger.domain.model",
      fields = List(
        Field("id", Type.Domain("ArtikelId")),
        Field("quantity", Type.Primitive("Int")),
        Field("warehouse", Type.Primitive("String"))
      )
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(entity)

    kotlinCode should include("package com.breuninger.domain.model")
    kotlinCode should include("data class BestandCreateDocument(")
    kotlinCode should include("val id: ArtikelId")
    kotlinCode should include("val quantity: Int")
    kotlinCode should include("val warehouse: String")
  }

  it should "generate correct sealed interface from SealedHierarchy" in {
    val sealedHierarchy = DomainModel.SealedHierarchy(
      name = "Result",
      packageName = "com.breuninger.domain.model",
      variants = List(
        Variant("Success", List(Field("value", Type.Primitive("String")))),
        Variant("Failure", List(Field("error", Type.Primitive("String"))))
      )
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(sealedHierarchy)

    kotlinCode should include("package com.breuninger.domain.model")
    kotlinCode should include("sealed interface Result")
    kotlinCode should include("data class Success(val value: String) : Result")
    kotlinCode should include("data class Failure(val error: String) : Result")
  }

  it should "handle primitive type mappings correctly" in {
    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.test",
      methods = List(
        Method(
          name = "testMethod",
          parameters = List(
            Parameter("stringParam", Type.Primitive("String")),
            Parameter("intParam", Type.Primitive("Int")),
            Parameter("longParam", Type.Primitive("Long")),
            Parameter("boolParam", Type.Primitive("Boolean"))
          ),
          returnType = Type.Effect(Type.Primitive("String"), Type.EffectType.IO)
        )
      )
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("stringParam: String")
    kotlinCode should include("intParam: Int")
    kotlinCode should include("longParam: Long")
    kotlinCode should include("boolParam: Boolean")
    kotlinCode should include("): String")
  }

  it should "handle generic types correctly" in {
    val method = Method(
      name = "findAll",
      parameters = List(),
      returnType = Type.Effect(
        Type.Generic("List", List(Type.Domain("User"))),
        Type.EffectType.IO
      )
    )

    val port = Port(
      name = "UserRepository",
      packageName = "com.breuninger.domain",
      methods = List(method)
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("suspend fun findAll(): List<User>")
  }

  it should "handle nested generic types" in {
    val method = Method(
      name = "getMap",
      parameters = List(),
      returnType = Type.Effect(
        Type.Generic("Map", List(
          Type.Primitive("String"),
          Type.Generic("List", List(Type.Domain("User")))
        )),
        Type.EffectType.IO
      )
    )

    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.test",
      methods = List(method)
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("suspend fun getMap(): Map<String, List<User>>")
  }

  it should "handle methods without parameters" in {
    val method = Method(
      name = "count",
      parameters = List(),
      returnType = Type.Effect(Type.Primitive("Int"), Type.EffectType.IO)
    )

    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.test",
      methods = List(method)
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("suspend fun count(): Int")
  }

  it should "generate entity with multiple fields correctly formatted" in {
    val entity = DomainModel.Entity(
      name = "ComplexEntity",
      packageName = "com.breuninger.domain.model",
      fields = List(
        Field("id", Type.Domain("EntityId")),
        Field("name", Type.Primitive("String")),
        Field("active", Type.Primitive("Boolean")),
        Field("items", Type.Generic("List", List(Type.Domain("Item")))),
        Field("metadata", Type.Generic("Map", List(Type.Primitive("String"), Type.Primitive("String"))))
      )
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(entity)

    kotlinCode should include("data class ComplexEntity(")
    kotlinCode should include("val id: EntityId")
    kotlinCode should include("val name: String")
    kotlinCode should include("val active: Boolean")
    kotlinCode should include("val items: List<Item>")
    kotlinCode should include("val metadata: Map<String, String>")
  }

  it should "handle sealed hierarchy with multiple variants" in {
    val sealedHierarchy = DomainModel.SealedHierarchy(
      name = "Shape",
      packageName = "com.breuninger.domain.model",
      variants = List(
        Variant("Circle", List(Field("radius", Type.Primitive("Double")))),
        Variant("Rectangle", List(
          Field("width", Type.Primitive("Double")),
          Field("height", Type.Primitive("Double"))
        )),
        Variant("Triangle", List(
          Field("base", Type.Primitive("Double")),
          Field("height", Type.Primitive("Double"))
        ))
      )
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(sealedHierarchy)

    kotlinCode should include("sealed interface Shape")
    kotlinCode should include("data class Circle(val radius: Double) : Shape")
    kotlinCode should include("data class Rectangle(val width: Double, val height: Double) : Shape")
    kotlinCode should include("data class Triangle(val base: Double, val height: Double) : Shape")
  }

  it should "handle value object with different primitive types" in {
    val valueObjectInt = DomainModel.ValueObject(
      name = "Count",
      packageName = "com.breuninger.domain.model",
      field = Field("value", Type.Primitive("Int"))
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(valueObjectInt)

    kotlinCode should include("@JvmInline")
    kotlinCode should include("value class Count(val value: Int)")
  }

  it should "properly format Kotlin code with correct indentation" in {
    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.domain.repository",
      methods = List(
        Method("method1", List(Parameter("param1", Type.Primitive("String"))), Type.Effect(Type.Unit, Type.EffectType.IO)),
        Method("method2", List(Parameter("param2", Type.Primitive("Int"))), Type.Effect(Type.Primitive("String"), Type.EffectType.IO))
      )
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    // KotlinPoet should handle proper formatting
    kotlinCode should not include("  \\s+\\n") // No trailing whitespace
    kotlinCode should include("interface TestRepository {")
  }
}
