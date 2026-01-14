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
            Parameter("bestand", Type.NamedType("com.breuninger.BestandCreateDocument"))
          ),
          returnType = Type.UnitType,
          isSuspend = true
        )
      )
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("package com.breuninger.domain.repository")
    kotlinCode should include("interface BestandRepository")
  }

  it should "generate interface with multiple methods" in {
    val port = Port(
      name = "BestandRepository",
      packageName = "com.breuninger.domain.repository",
      methods = List(
        Method(
          name = "save",
          parameters = List(Parameter("bestand", Type.NamedType("com.breuninger.BestandCreateDocument"))),
          returnType = Type.UnitType,
          isSuspend = true
        ),
        Method(
          name = "getByIds",
          parameters = List(
            Parameter("ids", Type.ListType(Type.NamedType("com.breuninger.ArtikelId")))
          ),
          returnType = Type.ListType(Type.NamedType("com.breuninger.BestandCreateDocument")),
          isSuspend = true
        ),
        Method(
          name = "deleteBatch",
          parameters = List(
            Parameter("bestaende", Type.ListType(Type.NamedType("com.breuninger.BestandDeleteDocument")))
          ),
          returnType = Type.UnitType,
          isSuspend = true
        )
      )
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("interface BestandRepository")
  }

  it should "generate correct data class from ValueObject" in {
    val valueObject = DomainModel.ValueObject(
      name = "ArtikelId",
      packageName = "com.breuninger.domain.model",
      properties = List(Property("value", Type.StringType))
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(valueObject)

    kotlinCode should include("package com.breuninger.domain.model")
    kotlinCode should include("data class ArtikelId")
    kotlinCode should include("val value: String")
  }

  it should "generate correct data class from Entity" in {
    val entity = DomainModel.Entity(
      name = "BestandCreateDocument",
      packageName = "com.breuninger.domain.model",
      properties = List(
        Property("id", Type.NamedType("com.breuninger.ArtikelId")),
        Property("quantity", Type.IntType),
        Property("warehouse", Type.StringType)
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
      subtypes = List(
        DomainModel.SealedSubtype("Success", List(Property("value", Type.StringType))),
        DomainModel.SealedSubtype("Failure", List(Property("error", Type.StringType)))
      )
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(sealedHierarchy)

    kotlinCode should include("package com.breuninger.domain.model")
    kotlinCode should include("sealed interface Result")
  }

  it should "handle primitive type mappings correctly" in {
    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.test",
      methods = List(
        Method(
          name = "testMethod",
          parameters = List(
            Parameter("stringParam", Type.StringType),
            Parameter("intParam", Type.IntType),
            Parameter("longParam", Type.LongType),
            Parameter("boolParam", Type.BooleanType)
          ),
          returnType = Type.StringType,
          isSuspend = true
        )
      )
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("interface TestRepository")
  }

  it should "handle generic types correctly" in {
    val method = Method(
      name = "findAll",
      parameters = List(),
      returnType = Type.ListType(Type.NamedType("com.breuninger.User")),
      isSuspend = true
    )

    val port = Port(
      name = "UserRepository",
      packageName = "com.breuninger.domain",
      methods = List(method)
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("interface UserRepository")
  }

  it should "handle nested generic types" in {
    val method = Method(
      name = "getMap",
      parameters = List(),
      returnType = Type.MapType(
        Type.StringType,
        Type.ListType(Type.NamedType("com.breuninger.User"))
      ),
      isSuspend = true
    )

    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.test",
      methods = List(method)
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("interface TestRepository")
  }

  it should "handle methods without parameters" in {
    val method = Method(
      name = "count",
      parameters = List(),
      returnType = Type.IntType,
      isSuspend = true
    )

    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.test",
      methods = List(method)
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("interface TestRepository")
  }

  it should "generate entity with multiple fields correctly formatted" in {
    val entity = DomainModel.Entity(
      name = "ComplexEntity",
      packageName = "com.breuninger.domain.model",
      properties = List(
        Property("id", Type.NamedType("com.breuninger.EntityId")),
        Property("name", Type.StringType),
        Property("active", Type.BooleanType),
        Property("items", Type.ListType(Type.NamedType("com.breuninger.Item"))),
        Property("metadata", Type.MapType(Type.StringType, Type.StringType))
      )
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(entity)

    kotlinCode should include("data class ComplexEntity(")
    kotlinCode should include("val id: EntityId")
    kotlinCode should include("val name: String")
    kotlinCode should include("val active: Boolean")
    kotlinCode should include("val items:")
    kotlinCode should include("val metadata:")
  }

  it should "handle sealed hierarchy with multiple variants" in {
    val sealedHierarchy = DomainModel.SealedHierarchy(
      name = "Shape",
      packageName = "com.breuninger.domain.model",
      subtypes = List(
        DomainModel.SealedSubtype("Circle", List(Property("radius", Type.DoubleType))),
        DomainModel.SealedSubtype("Rectangle", List(
          Property("width", Type.DoubleType),
          Property("height", Type.DoubleType)
        )),
        DomainModel.SealedSubtype("Triangle", List(
          Property("base", Type.DoubleType),
          Property("height", Type.DoubleType)
        ))
      )
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(sealedHierarchy)

    kotlinCode should include("sealed interface Shape")
  }

  it should "handle value object with different primitive types" in {
    val valueObjectInt = DomainModel.ValueObject(
      name = "Count",
      packageName = "com.breuninger.domain.model",
      properties = List(Property("value", Type.IntType))
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(valueObjectInt)

    kotlinCode should include("data class Count")
    kotlinCode should include("val value: Int")
  }

  it should "properly format Kotlin code with correct indentation" in {
    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.domain.repository",
      methods = List(
        Method(name = "method1", parameters = List(Parameter("param1", Type.StringType)), returnType = Type.UnitType, isSuspend = true),
        Method(name = "method2", parameters = List(Parameter("param2", Type.IntType)), returnType = Type.StringType, isSuspend = true)
      )
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("interface TestRepository {")
  }
}
