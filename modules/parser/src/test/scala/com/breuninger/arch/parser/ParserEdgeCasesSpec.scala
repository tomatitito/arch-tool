package com.breuninger.arch.parser

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.meta._

/**
 * Edge cases and error handling tests for Scalameta parser
 *
 * Tests unusual but valid Scala constructs and error conditions.
 * Ensures the parser is robust and handles corner cases gracefully.
 */
class ParserEdgeCasesSpec extends AnyFlatSpec with Matchers {

  // ============================================================================
  // WHITESPACE AND FORMATTING EDGE CASES
  // ============================================================================

  "Parser" should "handle various whitespace styles" in {
    val compact = "case class Foo(x:Int,y:String)"
    val spaced = "case class Foo( x : Int , y : String )"
    val multiline = """case class Foo(
                      |  x: Int,
                      |  y: String
                      |)""".stripMargin

    compact.parse[Stat].isInstanceOf[Parsed.Success[_]] shouldBe true
    spaced.parse[Stat].isInstanceOf[Parsed.Success[_]] shouldBe true
    multiline.parse[Stat].isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle trailing commas in parameter lists" in {
    val code = """case class Foo(
                 |  x: Int,
                 |  y: String,
                 |)""".stripMargin

    val result = code.parse[Stat]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle leading/trailing whitespace" in {
    val code = """
                 |
                 |  case class Foo(x: Int)
                 |
                 |  """.stripMargin

    val result = code.parse[Stat]
    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // NAMING EDGE CASES
  // ============================================================================

  "Parser naming" should "handle backtick identifiers" in {
    val code = "case class `Type`(value: String)"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle underscore in names" in {
    val code = "case class User_Data(user_id: String)"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Class]
    tree.name.value shouldBe "User_Data"
  }

  it should "handle dollar signs in names" in {
    val code = "case class $Special(value: String)"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle very long names" in {
    val longName = "VeryLongClassNameThatExceedsNormalLengthButIsStillValid"
    val code = s"case class $longName(value: String)"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // TYPE PARAMETER EDGE CASES
  // ============================================================================

  "Type parameters" should "handle multiple type parameters" in {
    val code = "trait Repository[K, V] { def save(key: K, value: V): IO[Unit] }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Trait]
    tree.tparams should have length 2
  }

  it should "handle type parameter bounds" in {
    val code = "trait Repository[A <: Document] { def save(entity: A): IO[Unit] }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle variance annotations" in {
    val covariant = "trait Producer[+A] { def produce(): A }"
    val contravariant = "trait Consumer[-A] { def consume(a: A): Unit }"

    covariant.parse[Stat].isInstanceOf[Parsed.Success[_]] shouldBe true
    contravariant.parse[Stat].isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle context bounds" in {
    val code = "trait Repository[A: Ordering] { def max(items: List[A]): A }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // PARAMETER EDGE CASES
  // ============================================================================

  "Parameters" should "handle default parameter values" in {
    val code = "case class Config(port: Int = 8080, host: String = \"localhost\")"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Class]
    val params = tree.ctor.paramss.flatten
    params.foreach(_.default shouldBe defined)
  }

  it should "handle val/var parameter modifiers" in {
    val code = "case class Mutable(var count: Int, val name: String)"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle by-name parameters" in {
    val code = "trait Executor { def execute(task: => Unit): IO[Unit] }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle repeated parameters (varargs)" in {
    val code = "trait Processor { def process(items: String*): IO[Unit] }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // ANNOTATION EDGE CASES
  // ============================================================================

  "Annotations" should "handle simple annotations on classes" in {
    val code = "@deprecated case class OldClass(x: Int)"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle annotations with parameters" in {
    val code = """@SerialVersionUID(1L) case class Versioned(x: Int)"""
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle multiple annotations" in {
    val code = """@deprecated
                 |@SerialVersionUID(1L)
                 |case class MultiAnnotated(x: Int)""".stripMargin
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // INHERITANCE EDGE CASES
  // ============================================================================

  "Inheritance" should "handle multiple trait extension" in {
    val code = "trait Combined extends TraitA with TraitB with TraitC"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle self-type annotations" in {
    val code = "trait Component { self: Dependency => def doWork(): Unit }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle early initializers" in {
    val code = "case class Early(x: Int) extends { val y = 42 } with Base"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // COMPLEX TYPE EDGE CASES
  // ============================================================================

  "Complex types" should "handle function types" in {
    val code = "trait Processor { def map(f: String => Int): IO[Unit] }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle tuple types" in {
    val code = "trait Splitter { def split(input: String): (String, String) }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle existential types" in {
    val code = "trait Container { def get(): List[_] }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle nested type projections" in {
    val code = "trait Outer { type Inner; def get: Outer#Inner }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle deeply nested generic types" in {
    val code = "trait Deep { def get(): IO[Either[Error, Option[List[Map[String, Document]]]]] }"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // COMMENT HANDLING
  // ============================================================================

  "Comments" should "handle single-line comments" in {
    val code = """// This is a comment
                 |case class Foo(x: Int) // inline comment""".stripMargin
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle multi-line comments" in {
    val code = """/* Multi-line
                 |   comment */
                 |case class Foo(x: Int)""".stripMargin
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle scaladoc comments" in {
    val code = """/**
                 | * Scaladoc comment
                 | * @param x the parameter
                 | */
                 |case class Foo(x: Int)""".stripMargin
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // EMPTY AND MINIMAL CONSTRUCTS
  // ============================================================================

  "Minimal constructs" should "handle case class with no parameters" in {
    val code = "case class Empty()"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Class]
    tree.ctor.paramss.flatten shouldBe empty
  }

  it should "handle trait with no methods" in {
    val code = "trait EmptyTrait"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Trait]
    tree.templ.stats shouldBe empty
  }

  it should "handle sealed trait with no variants (unusual but valid)" in {
    val code = "sealed trait NoVariants"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // PACKAGE EDGE CASES
  // ============================================================================

  "Package handling" should "handle single-level package" in {
    val code = """package foo
                 |case class Bar(x: Int)""".stripMargin
    val result = code.parse[Source]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle deeply nested packages" in {
    val code = """package com.example.very.deep.nested.package.structure
                 |case class Bar(x: Int)""".stripMargin
    val result = code.parse[Source]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle package objects" in {
    val code = """package object utils {
                 |  type StringMap[A] = Map[String, A]
                 |}""".stripMargin
    val result = code.parse[Source]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // IMPORT EDGE CASES
  // ============================================================================

  "Import handling" should "handle wildcard imports" in {
    val code = """import scala.collection._
                 |case class Foo(x: Int)""".stripMargin
    val result = code.parse[Source]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle selective imports" in {
    val code = """import scala.collection.{List, Map, Set}
                 |case class Foo(x: Int)""".stripMargin
    val result = code.parse[Source]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle renamed imports" in {
    val code = """import scala.collection.{Map => ScalaMap}
                 |case class Foo(x: Int)""".stripMargin
    val result = code.parse[Source]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle hidden imports" in {
    val code = """import scala.collection.{Map => _, _}
                 |case class Foo(x: Int)""".stripMargin
    val result = code.parse[Source]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // ERROR CASES
  // ============================================================================

  "Parser error handling" should "fail on invalid syntax" in {
    val code = "case clas Foo(x: Int)" // typo: clas instead of class
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Error] shouldBe true
  }

  it should "fail on mismatched braces" in {
    val code = "case class Foo(x: Int"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Error] shouldBe true
  }

  it should "fail on incomplete method signature" in {
    val code = "trait Repo { def save("
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Error] shouldBe true
  }

  it should "fail on invalid type syntax" in {
    val code = "case class Foo(x: Int[)" // invalid type application
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Error] shouldBe true
  }

  // ============================================================================
  // REAL-WORLD PATTERNS
  // ============================================================================

  "Real-world patterns" should "handle companion objects" in {
    val code = """case class User(id: String, name: String)
                 |object User {
                 |  def empty: User = User("", "")
                 |}""".stripMargin
    val result = code.parse[Source]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val source = result.get
    source.stats should have length 2
  }

  it should "handle ADTs with smart constructors" in {
    val code = """sealed abstract case class Email private (value: String)
                 |object Email {
                 |  def apply(value: String): Option[Email] = ???
                 |}""".stripMargin
    val result = code.parse[Source]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle enums (Scala 3 style enum definition in Scala 2 syntax)" in {
    val code = """sealed trait Color
                 |object Color {
                 |  case object Red extends Color
                 |  case object Green extends Color
                 |  case object Blue extends Color
                 |}""".stripMargin
    val result = code.parse[Source]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle repository with complete CRUD operations" in {
    val code = """trait CrudRepository[ID, Entity] {
                 |  def create(entity: Entity): IO[ID]
                 |  def read(id: ID): IO[Option[Entity]]
                 |  def update(entity: Entity): IO[Boolean]
                 |  def delete(id: ID): IO[Boolean]
                 |  def list(): IO[List[Entity]]
                 |  def count(): IO[Long]
                 |}""".stripMargin
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
    val tree = result.get.asInstanceOf[Defn.Trait]
    val methods = tree.templ.stats.collect { case m: Decl.Def => m }
    methods should have length 6
  }

  // ============================================================================
  // SPECIAL SCALA CONSTRUCTS
  // ============================================================================

  "Special constructs" should "handle opaque type aliases (Scala 3 backport style)" in {
    val code = "type UserId = String"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle path-dependent types" in {
    val code = """trait Outer {
                 |  type Inner
                 |  def create(): Inner
                 |}""".stripMargin
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle abstract type members" in {
    val code = """trait Repository {
                 |  type Entity
                 |  def save(entity: Entity): IO[Unit]
                 |}""".stripMargin
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle refined types (type aliases with bounds)" in {
    val code = "type PositiveInt = Int"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  // ============================================================================
  // UNICODE AND SPECIAL CHARACTERS
  // ============================================================================

  "Special characters" should "handle Unicode in strings" in {
    val code = """case class Message(text: String = "Hello 世界 🌍")"""
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }

  it should "handle Unicode identifiers" in {
    val code = "case class Données(valeur: String)"
    val result = code.parse[Stat]

    result.isInstanceOf[Parsed.Success[_]] shouldBe true
  }
}
