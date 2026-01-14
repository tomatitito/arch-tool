package com.breuninger.arch.parser

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.meta._

/**
 * Test suite for parser conversion logic
 *
 * Tests converting Scalameta AST into our IR (Intermediate Representation).
 * This is the bridge between parsing and code generation.
 *
 * Note: These tests assume IR model structures exist in the IR module.
 * Once IR is implemented, update imports and assertions accordingly.
 */
class ParserConverterSpec extends AnyFlatSpec with Matchers {

  // ============================================================================
  // VALUE OBJECT CONVERSION TESTS
  // ============================================================================

  "ValueObject converter" should "convert simple value object to IR" in {
    val code = "case class ArtikelId(value: String) extends AnyVal"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Class]

    // Expected IR structure (pseudo-code):
    // ValueObject(
    //   name = "ArtikelId",
    //   field = Field(name = "value", fieldType = PrimitiveType("String")),
    //   packageName = ""
    // )

    // Verify AST has required information for conversion
    tree.mods.exists { case Mod.Case() => true; case _ => false } shouldBe true
    tree.name.value shouldBe "ArtikelId"

    val hasAnyVal = tree.templ.inits.exists {
      case Init(Type.Name("AnyVal"), _, _) => true
      case _ => false
    }
    hasAnyVal shouldBe true

    val params = tree.ctor.paramss.flatten
    params should have length 1
    params.head.name.value shouldBe "value"
    params.head.decltpe.get.structure shouldBe Type.Name("String").structure
  }

  // ============================================================================
  // ENTITY CONVERSION TESTS
  // ============================================================================

  "Entity converter" should "convert entity to IR" in {
    val code = """case class BestandCreateDocument(
                 |  id: ArtikelId,
                 |  quantity: Int,
                 |  warehouse: String
                 |)""".stripMargin

    val tree = code.parse[Stat].get.asInstanceOf[Defn.Class]

    // Expected IR structure:
    // Entity(
    //   name = "BestandCreateDocument",
    //   fields = List(
    //     Field("id", DomainType("ArtikelId")),
    //     Field("quantity", PrimitiveType("Int")),
    //     Field("warehouse", PrimitiveType("String"))
    //   ),
    //   packageName = ""
    // )

    tree.name.value shouldBe "BestandCreateDocument"

    val params = tree.ctor.paramss.flatten
    params should have length 3

    val fieldInfo = params.map { p =>
      (p.name.value, p.decltpe.get.structure)
    }

    fieldInfo should contain allOf(
      ("id", Type.Name("ArtikelId").structure),
      ("quantity", Type.Name("Int").structure),
      ("warehouse", Type.Name("String").structure)
    )
  }

  it should "handle entities with no fields" in {
    val code = "case class EmptyEntity()"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Class]

    tree.ctor.paramss.flatten shouldBe empty
  }

  // ============================================================================
  // PORT (TRAIT) CONVERSION TESTS
  // ============================================================================

  "Port converter" should "convert trait interface to IR" in {
    val code = """trait BestandRepository {
                 |  def save(bestand: BestandCreateDocument): IO[Unit]
                 |  def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]]
                 |}""".stripMargin

    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]

    // Expected IR structure:
    // Port(
    //   name = "BestandRepository",
    //   methods = List(
    //     Method(
    //       name = "save",
    //       parameters = List(Parameter("bestand", DomainType("BestandCreateDocument"))),
    //       returnType = EffectType("IO", PrimitiveType("Unit"))
    //     ),
    //     Method(
    //       name = "getByIds",
    //       parameters = List(Parameter("ids", GenericType("List", List(DomainType("ArtikelId"))))),
    //       returnType = EffectType("IO", GenericType("List", List(DomainType("BestandCreateDocument"))))
    //     )
    //   ),
    //   packageName = ""
    // )

    tree.name.value shouldBe "BestandRepository"

    val methods = tree.templ.stats.collect { case m: Decl.Def => m }
    methods should have length 2

    // Verify first method
    val saveMethod = methods.head
    saveMethod.name.value shouldBe "save"

    val saveParams = saveMethod.paramss.flatten
    saveParams should have length 1
    saveParams.head.name.value shouldBe "bestand"

    // Verify second method
    val getMethod = methods(1)
    getMethod.name.value shouldBe "getByIds"
  }

  it should "convert empty trait to IR" in {
    val code = "trait EmptyPort"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]

    tree.name.value shouldBe "EmptyPort"
    tree.templ.stats shouldBe empty
  }

  // ============================================================================
  // TYPE CONVERSION TESTS
  // ============================================================================

  "Type converter" should "convert primitive types" in {
    val primitives = Map(
      "String" -> Type.Name("String"),
      "Int" -> Type.Name("Int"),
      "Long" -> Type.Name("Long"),
      "Boolean" -> Type.Name("Boolean"),
      "Double" -> Type.Name("Double"),
      "Unit" -> Type.Name("Unit")
    )

    primitives.foreach { case (name, expectedType) =>
      val code = s"case class Test(field: $name)"
      val tree = code.parse[Stat].get.asInstanceOf[Defn.Class]
      val param = tree.ctor.paramss.flatten.head

      param.decltpe.get.structure shouldBe expectedType.structure
    }
  }

  it should "convert generic types (List[A])" in {
    val code = "case class Test(items: List[String])"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Class]
    val param = tree.ctor.paramss.flatten.head

    val typeApp = param.decltpe.get.asInstanceOf[Type.Apply]
    typeApp.tpe.structure shouldBe Type.Name("List").structure
    typeApp.args should have length 1
    typeApp.args.head.structure shouldBe Type.Name("String").structure
  }

  it should "convert effect types (IO[A])" in {
    val code = "trait Test { def foo(): IO[String] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    val typeApp = method.decltpe.asInstanceOf[Type.Apply]
    typeApp.tpe.structure shouldBe Type.Name("IO").structure
    typeApp.args.head.structure shouldBe Type.Name("String").structure
  }

  it should "convert Option types" in {
    val code = "trait Test { def find(): Option[Document] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    val typeApp = method.decltpe.asInstanceOf[Type.Apply]
    typeApp.tpe.structure shouldBe Type.Name("Option").structure
    typeApp.args.head.structure shouldBe Type.Name("Document").structure
  }

  it should "convert Either types" in {
    val code = "trait Test { def execute(): Either[Error, Result] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    val typeApp = method.decltpe.asInstanceOf[Type.Apply]
    typeApp.tpe.structure shouldBe Type.Name("Either").structure
    typeApp.args should have length 2
    typeApp.args(0).structure shouldBe Type.Name("Error").structure
    typeApp.args(1).structure shouldBe Type.Name("Result").structure
  }

  it should "convert nested generic types (IO[List[A]])" in {
    val code = "trait Test { def findAll(): IO[List[Document]] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    val outerType = method.decltpe.asInstanceOf[Type.Apply]
    outerType.tpe.structure shouldBe Type.Name("IO").structure

    val innerType = outerType.args.head.asInstanceOf[Type.Apply]
    innerType.tpe.structure shouldBe Type.Name("List").structure
    innerType.args.head.structure shouldBe Type.Name("Document").structure
  }

  it should "convert Map types" in {
    val code = "trait Test { def getMapping(): Map[String, Document] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    val typeApp = method.decltpe.asInstanceOf[Type.Apply]
    typeApp.tpe.structure shouldBe Type.Name("Map").structure
    typeApp.args should have length 2
    typeApp.args(0).structure shouldBe Type.Name("String").structure
    typeApp.args(1).structure shouldBe Type.Name("Document").structure
  }

  // ============================================================================
  // METHOD CONVERSION TESTS
  // ============================================================================

  "Method converter" should "extract method signature completely" in {
    val code = """trait Repository {
                 |  def save(document: Document, force: Boolean): IO[Unit]
                 |}""".stripMargin

    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    // Expected IR:
    // Method(
    //   name = "save",
    //   parameters = List(
    //     Parameter("document", DomainType("Document")),
    //     Parameter("force", PrimitiveType("Boolean"))
    //   ),
    //   returnType = EffectType("IO", PrimitiveType("Unit"))
    // )

    method.name.value shouldBe "save"

    val params = method.paramss.flatten
    params should have length 2
    params(0).name.value shouldBe "document"
    params(0).decltpe.get.structure shouldBe Type.Name("Document").structure
    params(1).name.value shouldBe "force"
    params(1).decltpe.get.structure shouldBe Type.Name("Boolean").structure

    val returnType = method.decltpe.asInstanceOf[Type.Apply]
    returnType.tpe.structure shouldBe Type.Name("IO").structure
    returnType.args.head.structure shouldBe Type.Name("Unit").structure
  }

  it should "handle methods with no parameters" in {
    val code = "trait Test { def count(): IO[Int] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    method.paramss.flatten shouldBe empty
  }

  it should "handle multiple parameter lists" in {
    val code = "trait Test { def process(input: String)(implicit ec: ExecutionContext): IO[Result] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    method.paramss should have length 2
    method.paramss(0) should have length 1
    method.paramss(1) should have length 1
  }

  // ============================================================================
  // SEALED TRAIT HIERARCHY CONVERSION
  // ============================================================================

  "Sealed hierarchy converter" should "identify sealed trait and variants" in {
    val code = """sealed trait Result
                 |case class Success(value: String) extends Result
                 |case class Failure(error: String) extends Result""".stripMargin

    val source = code.parse[Source].get

    // Expected IR:
    // SealedHierarchy(
    //   baseTrait = "Result",
    //   variants = List(
    //     Variant("Success", List(Field("value", PrimitiveType("String")))),
    //     Variant("Failure", List(Field("error", PrimitiveType("String"))))
    //   )
    // )

    val sealedTrait = source.stats.head.asInstanceOf[Defn.Trait]
    sealedTrait.mods.exists { case Mod.Sealed() => true; case _ => false } shouldBe true
    sealedTrait.name.value shouldBe "Result"

    val variants = source.stats.tail.collect { case c: Defn.Class => c }
    variants should have length 2
    variants.map(_.name.value) should contain allOf("Success", "Failure")

    // Verify variants extend the sealed trait
    variants.foreach { variant =>
      val extendsResult = variant.templ.inits.exists {
        case Init(Type.Name("Result"), _, _) => true
        case _ => false
      }
      extendsResult shouldBe true
    }
  }

  it should "handle case objects in sealed hierarchy" in {
    val code = """sealed trait Status
                 |case object Active extends Status
                 |case object Inactive extends Status""".stripMargin

    val source = code.parse[Source].get

    val sealedTrait = source.stats.head.asInstanceOf[Defn.Trait]
    sealedTrait.mods.exists { case Mod.Sealed() => true; case _ => false } shouldBe true

    val objects = source.stats.tail.collect { case o: Defn.Object => o }
    objects should have length 2
    objects.map(_.name.value) should contain allOf("Active", "Inactive")
  }

  it should "handle mixed case classes and objects in hierarchy" in {
    val code = """sealed trait Result
                 |case class Success(value: String) extends Result
                 |case object NotFound extends Result
                 |case class Failure(error: String) extends Result""".stripMargin

    val source = code.parse[Source].get

    val variants = source.stats.tail
    variants should have length 3

    val classes = variants.collect { case c: Defn.Class => c }
    classes should have length 2

    val objects = variants.collect { case o: Defn.Object => o }
    objects should have length 1
  }

  // ============================================================================
  // PACKAGE AND IMPORT CONVERSION
  // ============================================================================

  "Package converter" should "extract package path" in {
    val code = """package com.breuninger.domain.repository
                 |
                 |trait Repo""".stripMargin

    val source = code.parse[Source].get
    val pkg = source.stats.head.asInstanceOf[Pkg]

    pkg.ref.syntax shouldBe "com.breuninger.domain.repository"
  }

  it should "extract all imports" in {
    val code = """package foo
                 |
                 |import cats.effect.IO
                 |import scala.concurrent.Future
                 |import scala.util.Try
                 |
                 |trait Repo""".stripMargin

    val source = code.parse[Source].get
    val pkg = source.stats.head.asInstanceOf[Pkg]

    val imports = pkg.stats.collect { case i: Import => i }
    imports should have length 3
  }

  // ============================================================================
  // COMPLEX SCENARIOS
  // ============================================================================

  "Complex converter scenarios" should "handle complete repository module" in {
    val code = """package com.breuninger.domain.repository
                 |
                 |import cats.effect.IO
                 |
                 |case class DocumentId(value: String) extends AnyVal
                 |case class Document(id: DocumentId, data: String)
                 |
                 |trait DocumentRepository {
                 |  def save(doc: Document): IO[Unit]
                 |  def findById(id: DocumentId): IO[Option[Document]]
                 |  def findAll(): IO[List[Document]]
                 |}""".stripMargin

    val source = code.parse[Source].get
    val pkg = source.stats.head.asInstanceOf[Pkg]

    // Should contain: import, value object, entity, port
    val imports = pkg.stats.collect { case i: Import => i }
    imports should have length 1

    val classes = pkg.stats.collect { case c: Defn.Class => c }
    classes should have length 2

    val traits = pkg.stats.collect { case t: Defn.Trait => t }
    traits should have length 1

    // Validate value object
    val valueObject = classes.head
    valueObject.name.value shouldBe "DocumentId"
    val hasAnyVal = valueObject.templ.inits.exists {
      case Init(Type.Name("AnyVal"), _, _) => true
      case _ => false
    }
    hasAnyVal shouldBe true

    // Validate entity
    val entity = classes(1)
    entity.name.value shouldBe "Document"

    // Validate port
    val port = traits.head
    port.name.value shouldBe "DocumentRepository"
    val methods = port.templ.stats.collect { case m: Decl.Def => m }
    methods should have length 3
  }

  it should "handle domain model with sealed hierarchy" in {
    val code = """package com.breuninger.domain.model
                 |
                 |case class UserId(value: Long) extends AnyVal
                 |
                 |sealed trait UserEvent
                 |case class UserCreated(id: UserId, name: String) extends UserEvent
                 |case class UserUpdated(id: UserId, name: String) extends UserEvent
                 |case class UserDeleted(id: UserId) extends UserEvent""".stripMargin

    val source = code.parse[Source].get
    val pkg = source.stats.head.asInstanceOf[Pkg]

    // Value object
    val valueObject = pkg.stats.collectFirst { case c: Defn.Class if c.name.value == "UserId" => c }
    valueObject shouldBe defined

    // Sealed trait
    val sealedTrait = pkg.stats.collectFirst { case t: Defn.Trait if t.mods.exists { case Mod.Sealed() => true; case _ => false } => t }
    sealedTrait shouldBe defined
    sealedTrait.get.name.value shouldBe "UserEvent"

    // Variants
    val variants = pkg.stats.collect {
      case c: Defn.Class if c.templ.inits.exists {
        case Init(Type.Name("UserEvent"), _, _) => true
        case _ => false
      } => c
    }
    variants should have length 3
  }
}
