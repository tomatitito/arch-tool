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

    // Verify package declaration
    kotlinCode should startWith regex "package com\\.breuninger\\.domain\\.repository"

    // Verify interface declaration
    kotlinCode should include("interface BestandRepository")

    // Verify all methods are present
    kotlinCode should include("suspend fun save(bestand: BestandCreateDocument)")
    kotlinCode should include("suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>")
    kotlinCode should include("suspend fun deleteBatch(bestaende: List<BestandDeleteDocument>)")

    // Verify proper Kotlin syntax (no Scala artifacts)
    kotlinCode should not include("def")
    kotlinCode should not include("trait")
    kotlinCode should not include(": Unit") // Unit should be omitted for suspend functions
  }

  it should "generate a complete domain model file with value object" in {
    val valueObject = DomainModel.ValueObject(
      name = "ArtikelId",
      packageName = "com.breuninger.domain.model",
      field = Field("value", Type.Primitive("String"))
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(valueObject)

    // Verify package declaration
    kotlinCode should startWith regex "package com\\.breuninger\\.domain\\.model"

    // Verify inline value class
    kotlinCode should include("@JvmInline")
    kotlinCode should include("value class ArtikelId(val value: String)")

    // Verify no Scala syntax
    kotlinCode should not include("case class")
    kotlinCode should not include("extends AnyVal")
  }

  it should "generate a complete domain model file with entity" in {
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

    // Verify package declaration
    kotlinCode should startWith regex "package com\\.breuninger\\.domain\\.model"

    // Verify data class
    kotlinCode should include("data class BestandCreateDocument(")
    kotlinCode should include("val id: ArtikelId")
    kotlinCode should include("val quantity: Int")
    kotlinCode should include("val warehouse: String")

    // Verify Kotlin conventions
    kotlinCode should not include("var") // Should use val for immutability
    kotlinCode should not include("case class")
  }

  it should "generate sealed hierarchy with proper Kotlin syntax" in {
    val sealedHierarchy = DomainModel.SealedHierarchy(
      name = "Result",
      packageName = "com.breuninger.domain.model",
      variants = List(
        Variant("Success", List(Field("value", Type.Primitive("String")))),
        Variant("Failure", List(Field("error", Type.Primitive("String"))))
      )
    )

    val kotlinCode = KotlinRenderer.renderDomainModel(sealedHierarchy)

    // Verify package declaration
    kotlinCode should startWith regex "package com\\.breuninger\\.domain\\.model"

    // Verify sealed interface
    kotlinCode should include("sealed interface Result")

    // Verify variants implement the sealed interface
    kotlinCode should include("data class Success(val value: String) : Result")
    kotlinCode should include("data class Failure(val error: String) : Result")

    // Verify no Scala syntax
    kotlinCode should not include("sealed trait")
    kotlinCode should not include("extends Result")
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
          Type.Generic("Map", List(
            Type.Primitive("String"),
            Type.Generic("List", List(
              Type.Generic("Pair", List(
                Type.Domain("ArtikelId"),
                Type.Primitive("Int")
              ))
            ))
          ))
        )
      ),
      returnType = Type.Effect(
        Type.Generic("List", List(Type.Domain("Result"))),
        Type.EffectType.IO
      )
    )

    val port = Port(
      name = "ComplexRepository",
      packageName = "com.breuninger.test",
      methods = List(method)
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("suspend fun complexMethod")
    kotlinCode should include("data: Map<String, List<Pair<ArtikelId, Int>>>")
    kotlinCode should include("): List<Result>")
  }

  it should "preserve method parameter names correctly" in {
    val method = Method(
      name = "saveAll",
      parameters = List(
        Parameter("documents", Type.Generic("List", List(Type.Domain("Document")))),
        Parameter("overwrite", Type.Primitive("Boolean")),
        Parameter("batchSize", Type.Primitive("Int"))
      ),
      returnType = Type.Effect(Type.Unit, Type.EffectType.IO)
    )

    val port = Port(
      name = "TestRepository",
      packageName = "com.breuninger.test",
      methods = List(method)
    )

    val kotlinCode = KotlinRenderer.renderPort(port)

    kotlinCode should include("documents: List<Document>")
    kotlinCode should include("overwrite: Boolean")
    kotlinCode should include("batchSize: Int")
  }

  it should "not generate imports for primitive types" in {
    val port = Port(
      name = "SimpleRepository",
      packageName = "com.breuninger.domain.repository",
      methods = List(
        Method(
          name = "count",
          parameters = List(),
          returnType = Type.Effect(Type.Primitive("Int"), Type.EffectType.IO)
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
        Method("method1", List(), Type.Effect(Type.Unit, Type.EffectType.IO))
      )
    )

    val kotlinCode1 = KotlinRenderer.renderPort(port)
    val kotlinCode2 = KotlinRenderer.renderPort(port)

    // Rendering should be deterministic
    kotlinCode1 shouldBe kotlinCode2
  }
}
