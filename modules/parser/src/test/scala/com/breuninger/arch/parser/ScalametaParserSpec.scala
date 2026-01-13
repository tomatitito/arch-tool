package com.breuninger.arch.parser

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.meta._

/**
 * Comprehensive test suite for Scalameta parser
 *
 * Tests cover all supported Scala constructs for architecture migration:
 * - Value objects (case class with AnyVal)
 * - Entities (case class without AnyVal)
 * - Ports (trait interfaces)
 * - Sealed trait hierarchies
 * - Type extraction (primitives, generics, effects)
 * - Edge cases and error handling
 */
class ScalametaParserSpec extends AnyFlatSpec with Matchers {

  // Test fixtures - Sample Scala code to parse
  object Fixtures {
    val simpleValueObject: String =
      """case class ArtikelId(value: String) extends AnyVal"""

    val entityWithMultipleFields: String =
      """case class BestandCreateDocument(
        |  id: ArtikelId,
        |  quantity: Int,
        |  warehouse: String,
        |  timestamp: Long
        |)""".stripMargin

    val simplePort: String =
      """trait BestandRepository {
        |  def save(bestand: BestandCreateDocument): IO[Unit]
        |  def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]]
        |}""".stripMargin

    val portWithPackageAndImports: String =
      """package com.breuninger.domain.repository
        |
        |import cats.effect.IO
        |
        |trait BestandRepository {
        |  def save(bestand: BestandCreateDocument): IO[Unit]
        |  def delete(id: ArtikelId): IO[Boolean]
        |}""".stripMargin

    val sealedTraitHierarchy: String =
      """sealed trait Result
        |case class Success(value: String) extends Result
        |case class Failure(error: String) extends Result
        |case object NotFound extends Result""".stripMargin

    val complexTypes: String =
      """trait ComplexRepository {
        |  def findAll(): IO[List[Document]]
        |  def findById(id: String): IO[Option[Document]]
        |  def count(): IO[Int]
        |  def exists(id: String): IO[Boolean]
        |  def findByIds(ids: List[String]): IO[Map[String, Document]]
        |}""".stripMargin

    val multipleParameterLists: String =
      """trait ServicePort {
        |  def process(input: String)(implicit ec: ExecutionContext): IO[Result]
        |}""".stripMargin

    val genericPort: String =
      """trait Repository[A] {
        |  def save(entity: A): IO[Unit]
        |  def findById(id: String): IO[Option[A]]
        |}""".stripMargin

    val nestedTypes: String =
      """trait MessageRepository {
        |  def send(message: Message): IO[Either[Error, Success]]
        |  def receive(): IO[List[Either[Error, Message]]]
        |}""".stripMargin
  }

  // ============================================================================
  // PARSING TESTS - Verify Scalameta can parse various constructs
  // ============================================================================

  "Scalameta parser" should "successfully parse a simple value object" in {
    val result = Fixtures.simpleValueObject.parse[Stat]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true

    val tree = result.get
    tree shouldBe a[Defn.Class]

    val classDefn = tree.asInstanceOf[Defn.Class]
    classDefn.mods should contain(Mod.Case())
    classDefn.name.value shouldBe "ArtikelId"
  }

  it should "successfully parse an entity with multiple fields" in {
    val result = Fixtures.entityWithMultipleFields.parse[Stat]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true

    val classDefn = result.get.asInstanceOf[Defn.Class]
    classDefn.name.value shouldBe "BestandCreateDocument"

    val params = classDefn.ctor.paramss.flatten
    params should have length 4
    params.map(_.name.value) should contain allOf("id", "quantity", "warehouse", "timestamp")
  }

  it should "successfully parse a trait interface (port)" in {
    val result = Fixtures.simplePort.parse[Stat]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true

    val traitDefn = result.get.asInstanceOf[Defn.Trait]
    traitDefn.name.value shouldBe "BestandRepository"

    val methods = traitDefn.templ.stats.collect { case m: Decl.Def => m }
    methods should have length 2
    methods.map(_.name.value) should contain allOf("save", "getByIds")
  }

  it should "successfully parse code with package and imports" in {
    val result = Fixtures.portWithPackageAndImports.parse[Source]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true

    val source = result.get
    source.stats should have length 1

    val pkg = source.stats.head.asInstanceOf[Pkg]
    pkg.stats should have length 2 // Import + Trait
  }

  it should "successfully parse a sealed trait hierarchy" in {
    val result = Fixtures.sealedTraitHierarchy.parse[Source]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true

    val source = result.get
    source.stats should have length 4 // sealed trait + 3 case classes/objects

    val sealedTrait = source.stats.head.asInstanceOf[Defn.Trait]
    sealedTrait.mods should contain(Mod.Sealed())
    sealedTrait.name.value shouldBe "Result"
  }

  it should "successfully parse complex type signatures" in {
    val result = Fixtures.complexTypes.parse[Stat]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true

    val traitDefn = result.get.asInstanceOf[Defn.Trait]
    val methods = traitDefn.templ.stats.collect { case m: Decl.Def => m }
    methods should have length 5
  }

  it should "successfully parse methods with multiple parameter lists" in {
    val result = Fixtures.multipleParameterLists.parse[Stat]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true

    val traitDefn = result.get.asInstanceOf[Defn.Trait]
    val method = traitDefn.templ.stats.collect { case m: Decl.Def => m }.head

    method.paramss should have length 2
  }

  it should "successfully parse generic type parameters" in {
    val result = Fixtures.genericPort.parse[Stat]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true

    val traitDefn = result.get.asInstanceOf[Defn.Trait]
    traitDefn.tparams should have length 1
    traitDefn.tparams.head.name.value shouldBe "A"
  }

  it should "successfully parse nested generic types (Either[A, B])" in {
    val result = Fixtures.nestedTypes.parse[Stat]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true

    val traitDefn = result.get.asInstanceOf[Defn.Trait]
    val methods = traitDefn.templ.stats.collect { case m: Decl.Def => m }
    methods should have length 2
  }

  // ============================================================================
  // TYPE EXTRACTION TESTS - Extract type information from AST
  // ============================================================================

  "Type extractor" should "extract simple type names" in {
    val code = "case class Foo(name: String, age: Int)"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Class]
    val params = tree.ctor.paramss.flatten

    params(0).decltpe.get shouldBe Type.Name("String")
    params(1).decltpe.get shouldBe Type.Name("Int")
  }

  it should "extract generic type applications" in {
    val code = "trait Repo { def findAll(): List[String] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    method.decltpe shouldBe a[Type.Apply]
    val typeApp = method.decltpe.asInstanceOf[Type.Apply]
    typeApp.tpe shouldBe Type.Name("List")
    typeApp.args should have length 1
    typeApp.args.head shouldBe Type.Name("String")
  }

  it should "extract effect types (IO[A])" in {
    val code = "trait Repo { def save(doc: Document): IO[Unit] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    method.decltpe shouldBe a[Type.Apply]
    val typeApp = method.decltpe.asInstanceOf[Type.Apply]
    typeApp.tpe shouldBe Type.Name("IO")
    typeApp.args.head shouldBe Type.Name("Unit")
  }

  it should "extract nested generic types (IO[List[A]])" in {
    val code = "trait Repo { def findAll(): IO[List[Document]] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    val outerType = method.decltpe.asInstanceOf[Type.Apply]
    outerType.tpe shouldBe Type.Name("IO")

    val innerType = outerType.args.head.asInstanceOf[Type.Apply]
    innerType.tpe shouldBe Type.Name("List")
    innerType.args.head shouldBe Type.Name("Document")
  }

  it should "extract Option types" in {
    val code = "trait Repo { def findById(id: String): Option[Document] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    val typeApp = method.decltpe.asInstanceOf[Type.Apply]
    typeApp.tpe shouldBe Type.Name("Option")
    typeApp.args.head shouldBe Type.Name("Document")
  }

  // ============================================================================
  // MODIFIER EXTRACTION TESTS - Extract modifiers from definitions
  // ============================================================================

  "Modifier extractor" should "identify case classes" in {
    val code = "case class Foo(x: Int)"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Class]

    tree.mods should contain(Mod.Case())
  }

  it should "identify sealed traits" in {
    val code = "sealed trait Result"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]

    tree.mods should contain(Mod.Sealed())
  }

  it should "identify value classes (extends AnyVal)" in {
    val code = "case class ArtikelId(value: String) extends AnyVal"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Class]

    val extendsAnyVal = tree.templ.inits.exists {
      case Init(Type.Name("AnyVal"), _, _) => true
      case _ => false
    }
    extendsAnyVal shouldBe true
  }

  it should "distinguish between value objects and entities" in {
    val valueObject = "case class ArtikelId(value: String) extends AnyVal"
    val entity = "case class Document(id: String, data: String)"

    val valueTree = valueObject.parse[Stat].get.asInstanceOf[Defn.Class]
    val entityTree = entity.parse[Stat].get.asInstanceOf[Defn.Class]

    val valueExtendsAnyVal = valueTree.templ.inits.exists {
      case Init(Type.Name("AnyVal"), _, _) => true
      case _ => false
    }

    val entityExtendsAnyVal = entityTree.templ.inits.exists {
      case Init(Type.Name("AnyVal"), _, _) => true
      case _ => false
    }

    valueExtendsAnyVal shouldBe true
    entityExtendsAnyVal shouldBe false
  }

  // ============================================================================
  // METHOD SIGNATURE EXTRACTION TESTS
  // ============================================================================

  "Method signature extractor" should "extract method name" in {
    val code = "trait Repo { def save(doc: Document): IO[Unit] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    method.name.value shouldBe "save"
  }

  it should "extract method parameters" in {
    val code = "trait Repo { def save(doc: Document, force: Boolean): IO[Unit] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    val params = method.paramss.flatten
    params should have length 2
    params(0).name.value shouldBe "doc"
    params(1).name.value shouldBe "force"
  }

  it should "extract method return type" in {
    val code = "trait Repo { def count(): IO[Int] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    val returnType = method.decltpe.asInstanceOf[Type.Apply]
    returnType.tpe shouldBe Type.Name("IO")
    returnType.args.head shouldBe Type.Name("Int")
  }

  it should "handle methods with no parameters" in {
    val code = "trait Repo { def findAll(): IO[List[Document]] }"
    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head

    method.paramss.flatten should have length 0
  }

  // ============================================================================
  // PACKAGE AND IMPORT EXTRACTION TESTS
  // ============================================================================

  "Package extractor" should "extract package name" in {
    val code = """package com.breuninger.domain.repository
                 |
                 |trait Repo""".stripMargin
    val tree = code.parse[Source].get
    val pkg = tree.stats.head.asInstanceOf[Pkg]

    pkg.ref.syntax shouldBe "com.breuninger.domain.repository"
  }

  it should "extract multiple import statements" in {
    val code = """package foo
                 |
                 |import cats.effect.IO
                 |import scala.concurrent.Future
                 |
                 |trait Repo""".stripMargin
    val tree = code.parse[Source].get
    val pkg = tree.stats.head.asInstanceOf[Pkg]

    val imports = pkg.stats.collect { case i: Import => i }
    imports should have length 2
  }

  // ============================================================================
  // SEALED TRAIT HIERARCHY TESTS
  // ============================================================================

  "Sealed trait parser" should "identify all variants in a sealed hierarchy" in {
    val code = """sealed trait Result
                 |case class Success(value: String) extends Result
                 |case class Failure(error: String) extends Result
                 |case object NotFound extends Result""".stripMargin

    val source = code.parse[Source].get
    val allStats = source.stats

    allStats should have length 4

    val sealedTrait = allStats.head.asInstanceOf[Defn.Trait]
    sealedTrait.mods should contain(Mod.Sealed())

    val variants = allStats.tail
    variants should have length 3
  }

  it should "identify case objects in sealed hierarchy" in {
    val code = """sealed trait Status
                 |case object Active extends Status
                 |case object Inactive extends Status""".stripMargin

    val source = code.parse[Source].get
    val objects = source.stats.tail.collect { case o: Defn.Object => o }

    objects should have length 2
    objects.foreach { obj =>
      obj.mods should contain(Mod.Case())
    }
  }

  // ============================================================================
  // EDGE CASES AND ERROR HANDLING
  // ============================================================================

  "Parser edge cases" should "handle empty trait body" in {
    val code = "trait EmptyPort"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Trait]
    tree.templ.stats shouldBe empty
  }

  it should "handle trait with only one method" in {
    val code = "trait SingleMethod { def foo(): String }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Trait]
    val methods = tree.templ.stats.collect { case m: Decl.Def => m }
    methods should have length 1
  }

  it should "handle case class with single field" in {
    val code = "case class SingleField(value: String)"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Class]
    tree.ctor.paramss.flatten should have length 1
  }

  it should "handle case class with no fields (case object equivalent)" in {
    val code = "case class NoFields()"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Class]
    tree.ctor.paramss.flatten should have length 0
  }

  it should "reject invalid Scala syntax" in {
    val code = "this is not valid scala code"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Error] shouldBe true
  }

  it should "handle methods returning Unit (no IO wrapper)" in {
    val code = "trait Service { def doSomething(): Unit }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Trait]
    val method = tree.templ.stats.collect { case m: Decl.Def => m }.head
    method.decltpe shouldBe Type.Name("Unit")
  }

  it should "handle complex nested types (Either[Error, Option[Result]])" in {
    val code = "trait Service { def execute(): IO[Either[Error, Option[Result]]] }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // PATTERN MATCHING TESTS - Verify we can traverse and extract from AST
  // ============================================================================

  "AST traversal" should "collect all class definitions from source" in {
    val code = """package foo
                 |
                 |case class A(x: Int)
                 |case class B(y: String)
                 |trait C""".stripMargin

    val source = code.parse[Source].get
    val pkg = source.stats.head.asInstanceOf[Pkg]

    val classes = pkg.stats.collect { case c: Defn.Class => c }
    classes should have length 2
    classes.map(_.name.value) should contain allOf("A", "B")
  }

  it should "collect all trait definitions from source" in {
    val code = """package foo
                 |
                 |trait RepoA
                 |trait RepoB
                 |case class Data(x: Int)""".stripMargin

    val source = code.parse[Source].get
    val pkg = source.stats.head.asInstanceOf[Pkg]

    val traits = pkg.stats.collect { case t: Defn.Trait => t }
    traits should have length 2
    traits.map(_.name.value) should contain allOf("RepoA", "RepoB")
  }

  it should "collect all methods from a trait" in {
    val code = """trait Repository {
                 |  def save(doc: Doc): IO[Unit]
                 |  def findById(id: String): IO[Option[Doc]]
                 |  def delete(id: String): IO[Boolean]
                 |  def count(): IO[Long]
                 |}""".stripMargin

    val tree = code.parse[Stat].get.asInstanceOf[Defn.Trait]
    val methods = tree.templ.stats.collect { case m: Decl.Def => m }

    methods should have length 4
    methods.map(_.name.value) should contain allOf("save", "findById", "delete", "count")
  }

  // ============================================================================
  // FULL INTEGRATION TESTS - Parse complete architectural patterns
  // ============================================================================

  "Full integration" should "parse a complete repository port definition" in {
    val code = """package com.breuninger.domain.repository
                 |
                 |import cats.effect.IO
                 |
                 |case class ArtikelId(value: String) extends AnyVal
                 |
                 |case class BestandCreateDocument(
                 |  id: ArtikelId,
                 |  quantity: Int,
                 |  warehouse: String
                 |)
                 |
                 |trait BestandRepository {
                 |  def save(bestand: BestandCreateDocument): IO[Unit]
                 |  def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]]
                 |  def deleteBatch(bestaende: List[BestandCreateDocument]): IO[Unit]
                 |}""".stripMargin

    val source = code.parse[Source].get
    val pkg = source.stats.head.asInstanceOf[Pkg]

    // Should have: import + value object + entity + port
    pkg.stats should have length 4

    val imports = pkg.stats.collect { case i: Import => i }
    imports should have length 1

    val classes = pkg.stats.collect { case c: Defn.Class => c }
    classes should have length 2
    classes.map(_.name.value) should contain allOf("ArtikelId", "BestandCreateDocument")

    val traits = pkg.stats.collect { case t: Defn.Trait => t }
    traits should have length 1
    traits.head.name.value shouldBe "BestandRepository"
  }

  it should "parse a complete sealed trait hierarchy" in {
    val code = """package com.breuninger.domain.model
                 |
                 |sealed trait ProcessingResult
                 |
                 |case class Success(
                 |  documentId: String,
                 |  timestamp: Long
                 |) extends ProcessingResult
                 |
                 |case class PartialSuccess(
                 |  successfulIds: List[String],
                 |  failedIds: List[String]
                 |) extends ProcessingResult
                 |
                 |case class Failure(
                 |  error: String,
                 |  retryable: Boolean
                 |) extends ProcessingResult
                 |
                 |case object Cancelled extends ProcessingResult""".stripMargin

    val source = code.parse[Source].get
    val pkg = source.stats.head.asInstanceOf[Pkg]

    // Should have sealed trait + 3 case classes + 1 case object
    pkg.stats should have length 5

    val sealedTrait = pkg.stats.head.asInstanceOf[Defn.Trait]
    sealedTrait.mods should contain(Mod.Sealed())
    sealedTrait.name.value shouldBe "ProcessingResult"

    val caseClasses = pkg.stats.tail.collect { case c: Defn.Class => c }
    caseClasses should have length 3

    val caseObjects = pkg.stats.tail.collect { case o: Defn.Object => o }
    caseObjects should have length 1
  }
}
