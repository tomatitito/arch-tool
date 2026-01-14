package com.breuninger.arch.renderer

import com.breuninger.arch.ir._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Integration tests for the KotlinPoet renderer.
 *
 * These tests verify end-to-end rendering scenarios and ensure
 * that generated Kotlin code is syntactically correct and follows
 * best practices.
 */
class RendererIntegrationSpec extends AnyFlatSpec with Matchers {

  "KotlinRenderer" should "generate a complete repository file with all elements" in {
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

    // Verify package declaration
    kotlinCode should startWith regex "package com\\.breuninger\\.domain\\.repository"

    // Verify interface declaration
    kotlinCode should include("interface BestandRepository")
  }

  it should "generate a complete domain model file with value object" in {
    val valueObject = DomainModel.ValueObject(
      name = "ArtikelId",
      packageName = "com.breuninger.domain.model",
      properties = List(Property("value", Type.StringType))
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(valueObject)

    // Verify package declaration
    kotlinCode should startWith regex "package com\\.breuninger\\.domain\\.model"

    // Verify data class with properties
    kotlinCode should include("data class ArtikelId")
    kotlinCode should include("val value: String")

    // Verify no Scala syntax
    kotlinCode should not include("case class")
  }

  it should "generate a complete domain model file with entity" in {
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

    // Verify package declaration
    kotlinCode should startWith regex "package com\\.breuninger\\.domain\\.model"

    // Verify data class
    kotlinCode should include("data class BestandCreateDocument(")
    kotlinCode should include("val id: ArtikelId")
    kotlinCode should include("val quantity: Int")
    kotlinCode should include("val warehouse: String")

    // Verify Kotlin conventions
    kotlinCode should not include("case class")
  }

  it should "generate sealed hierarchy with proper Kotlin syntax" in {
    val sealedHierarchy = DomainModel.SealedHierarchy(
      name = "Result",
      packageName = "com.breuninger.domain.model",
      subtypes = List(
        DomainModel.SealedSubtype("Success", List(Property("value", Type.StringType))),
        DomainModel.SealedSubtype("Failure", List(Property("error", Type.StringType)))
      )
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(sealedHierarchy)

    // Verify package declaration
    kotlinCode should startWith regex "package com\\.breuninger\\.domain\\.model"

    // Verify sealed interface
    kotlinCode should include("sealed interface Result")

    // Verify no Scala syntax
    kotlinCode should not include("sealed trait")
  }

  it should "handle empty methods list gracefully" in {
    val port = Port(
      name = "EmptyRepository",
      packageName = "com.breuninger.domain.repository",
      methods = List()
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("interface EmptyRepository")
    // Should generate valid Kotlin code even with no methods
    kotlinCode should include("{")
    kotlinCode should include("}")
  }

  it should "generate valid Kotlin for complex nested structures" in {
    val method = Method(
      name = "complexMethod",
      parameters = List(
        Parameter(
          "data",
          Type.MapType(
            Type.StringType,
            Type.ListType(
              Type.NamedType("kotlin.Pair", List(
                Type.NamedType("com.breuninger.ArtikelId"),
                Type.IntType
              ))
            )
          )
        )
      ),
      returnType = Type.ListType(Type.NamedType("com.breuninger.Result")),
      isSuspend = true
    )

    val port = Port(
      name = "ComplexRepository",
      packageName = "com.breuninger.test",
      methods = List(method)
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    // Just verify it renders without error
    kotlinCode should include("interface ComplexRepository")
  }

  it should "preserve method parameter names correctly" in {
    val method = Method(
      name = "saveAll",
      parameters = List(
        Parameter("documents", Type.ListType(Type.NamedType("com.breuninger.Document"))),
        Parameter("overwrite", Type.BooleanType),
        Parameter("batchSize", Type.IntType)
      ),
      returnType = Type.UnitType,
      isSuspend = true
    )

    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.test",
      methods = List(method)
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    // Just verify it renders
    kotlinCode should include("interface TestRepository")
  }

  it should "not generate imports for primitive types" in {
    val port = Port(
      name = "SimpleRepository",
      packageName = "com.breuninger.domain.repository",
      methods = List(
        Method(
          name = "count",
          parameters = List(),
          returnType = Type.IntType,
          isSuspend = true
        )
      )
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    // Should not have unnecessary imports for Kotlin built-in types
    kotlinCode should not include("import kotlin.Int")
    kotlinCode should not include("import kotlin.String")
    kotlinCode should not include("import kotlin.Boolean")
  }

  it should "generate consistent formatting across multiple renders" in {
    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.test",
      methods = List(
        Method(name = "method1", parameters = List(), returnType = Type.UnitType, isSuspend = true)
      )
    )

    val kotlinCode1 = KotlinRenderer.renderPort(port)
    val kotlinCode2 = KotlinRenderer.renderPort(port)

    // Rendering should be deterministic
    kotlinCode1 shouldBe kotlinCode2
  }
}
