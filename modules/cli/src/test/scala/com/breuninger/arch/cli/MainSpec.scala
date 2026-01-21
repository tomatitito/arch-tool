package com.breuninger.arch.cli

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.file.{Files, Path, Paths}
import scala.util.Using

/**
 * Test specification for Main CLI entry point
 *
 * Tests follow TDD approach - write tests first, implement later.
 * These tests define the expected behavior of the CLI.
 */
class MainSpec extends AnyFlatSpec with Matchers {

  // ============================================================================
  // TEST HELPERS
  // ============================================================================

  /** Capture stdout during test execution */
  def captureStdout(block: => Unit): String = {
    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos)
    val oldOut = System.out
    try {
      System.setOut(ps)
      block
      baos.toString("UTF-8")
    } finally {
      System.setOut(oldOut)
      ps.close()
    }
  }

  /** Create a temporary Scala file for testing */
  def createTempScalaFile(content: String): Path = {
    val tempFile = Files.createTempFile("test", ".scala")
    Files.writeString(tempFile, content)
    tempFile.toFile.deleteOnExit()
    tempFile
  }

  /** Create a temporary directory for testing */
  def createTempDir(): Path = {
    val tempDir = Files.createTempDirectory("test")
    tempDir.toFile.deleteOnExit()
    tempDir
  }

  // ============================================================================
  // COMMAND PARSING TESTS
  // ============================================================================

  "Main argument parser" should "parse 'help' command" in {
    val args = Array("help")
    val command = Main.parseArgs(args)
    command shouldBe Some(HelpCommand)
  }

  it should "parse 'version' command" in {
    val args = Array("version")
    val command = Main.parseArgs(args)
    command shouldBe Some(VersionCommand)
  }

  it should "parse 'parse' command with file path" in {
    val args = Array("parse", "src/Domain.scala")
    val command = Main.parseArgs(args)
    command match {
      case Some(ParseCommand(inputPath, verbose)) =>
        inputPath.toString should include("Domain.scala")
        verbose shouldBe false
      case _ => fail("Expected ParseCommand")
    }
  }

  it should "parse 'parse' command with --verbose flag" in {
    val args = Array("parse", "src/Domain.scala", "--verbose")
    val command = Main.parseArgs(args)
    command match {
      case Some(ParseCommand(_, verbose)) =>
        verbose shouldBe true
      case _ => fail("Expected ParseCommand with verbose=true")
    }
  }

  it should "parse 'validate' command with file path" in {
    val args = Array("validate", "src/Domain.scala")
    val command = Main.parseArgs(args)
    command match {
      case Some(ValidateCommand(inputPath, strict)) =>
        inputPath.toString should include("Domain.scala")
        strict shouldBe false
      case _ => fail("Expected ValidateCommand")
    }
  }

  it should "parse 'validate' command with --strict flag" in {
    val args = Array("validate", "src/Domain.scala", "--strict")
    val command = Main.parseArgs(args)
    command match {
      case Some(ValidateCommand(_, strict)) =>
        strict shouldBe true
      case _ => fail("Expected ValidateCommand with strict=true")
    }
  }

  it should "parse 'migrate' command with input and output paths" in {
    val args = Array("migrate", "src/Domain.scala", "out/Domain.kt")
    val command = Main.parseArgs(args)
    command match {
      case Some(MigrateCommand(inputPath, outputPath, skipValidation)) =>
        inputPath.toString should include("Domain.scala")
        outputPath.toString should include("Domain.kt")
        skipValidation shouldBe false
      case _ => fail("Expected MigrateCommand")
    }
  }

  it should "parse 'migrate' command with --skip-validation flag" in {
    val args = Array("migrate", "src/Domain.scala", "out/Domain.kt", "--skip-validation")
    val command = Main.parseArgs(args)
    command match {
      case Some(MigrateCommand(_, _, skipValidation)) =>
        skipValidation shouldBe true
      case _ => fail("Expected MigrateCommand with skipValidation=true")
    }
  }

  it should "parse 'migrate-batch' command with directories" in {
    val args = Array("migrate-batch", "src/", "out/")
    val command = Main.parseArgs(args)
    command match {
      case Some(MigrateBatchCommand(inputDir, outputDir, skipValidation, parallel)) =>
        inputDir.toString should include("src")
        outputDir.toString should include("out")
        skipValidation shouldBe false
        parallel shouldBe true // default
      case _ => fail("Expected MigrateBatchCommand")
    }
  }

  it should "parse 'migrate-batch' command with --no-parallel flag" in {
    val args = Array("migrate-batch", "src/", "out/", "--no-parallel")
    val command = Main.parseArgs(args)
    command match {
      case Some(MigrateBatchCommand(_, _, _, parallel)) =>
        parallel shouldBe false
      case _ => fail("Expected MigrateBatchCommand with parallel=false")
    }
  }

  it should "return None for invalid command" in {
    val args = Array("invalid-command")
    val command = Main.parseArgs(args)
    command shouldBe None
  }

  it should "return None for missing required arguments" in {
    val args = Array("parse") // missing file path
    val command = Main.parseArgs(args)
    command shouldBe None
  }

  // ============================================================================
  // HELP COMMAND TESTS
  // ============================================================================

  "Main help command" should "print usage information" in {
    val output = captureStdout {
      Main.execute(HelpCommand)
    }

    output should include("arch-tool")
    output should include("Usage:")
    output should include("parse")
    output should include("validate")
    output should include("migrate")
    output should include("help")
    output should include("version")
  }

  // ============================================================================
  // VERSION COMMAND TESTS
  // ============================================================================

  "Main version command" should "print version information" in {
    val output = captureStdout {
      Main.execute(VersionCommand)
    }

    output should include("arch-tool")
    output should include("version")
  }

  // ============================================================================
  // PARSE COMMAND TESTS
  // ============================================================================

  "Main parse command" should "parse a simple value object and print IR" in {
    val scalaCode = """
      |package com.example.domain
      |
      |case class UserId(value: String) extends AnyVal
      |""".stripMargin

    val tempFile = createTempScalaFile(scalaCode)

    val output = captureStdout {
      Main.execute(ParseCommand(tempFile, verbose = false))
    }

    output should include("UserId")
    output should include("ValueObject")
  }

  it should "parse a port interface and print IR" in {
    val scalaCode = """
      |package com.example.ports
      |
      |trait UserRepository {
      |  def findById(id: String): IO[Option[User]]
      |  def save(user: User): IO[Unit]
      |}
      |""".stripMargin

    val tempFile = createTempScalaFile(scalaCode)

    val output = captureStdout {
      Main.execute(ParseCommand(tempFile, verbose = false))
    }

    output should include("UserRepository")
    output should include("Port")
  }

  it should "print detailed output with --verbose flag" in {
    val scalaCode = """
      |package com.example.domain
      |
      |case class UserId(value: String) extends AnyVal
      |""".stripMargin

    val tempFile = createTempScalaFile(scalaCode)

    val output = captureStdout {
      Main.execute(ParseCommand(tempFile, verbose = true))
    }

    // Verbose mode should include more details
    output should include("UserId")
    output should include("ValueObject")
    output.length should be > 100 // Verbose output should be longer
  }

  it should "handle parse errors gracefully" in {
    val invalidScalaCode = """
      |this is not valid Scala code {{{
      |""".stripMargin

    val tempFile = createTempScalaFile(invalidScalaCode)

    val output = captureStdout {
      Main.execute(ParseCommand(tempFile, verbose = false))
    }

    output should include("Error")
    output should include("parse")
  }

  it should "handle missing file gracefully" in {
    val nonExistentFile = Paths.get("/tmp/does-not-exist-12345.scala")

    val output = captureStdout {
      Main.execute(ParseCommand(nonExistentFile, verbose = false))
    }

    output should include("Error")
    output should (include("not found") or include("does not exist"))
  }

  // ============================================================================
  // VALIDATE COMMAND TESTS
  // ============================================================================

  "Main validate command" should "validate correct architecture and report success" in {
    val scalaCode = """
      |package com.example.ports
      |
      |trait UserRepository {
      |  def findById(id: String): IO[Option[User]]
      |}
      |""".stripMargin

    val tempFile = createTempScalaFile(scalaCode)

    val output = captureStdout {
      Main.execute(ValidateCommand(tempFile, strict = false))
    }

    output should (include("valid") or include("passed") or include("OK"))
  }

  it should "report validation errors for invalid architecture" in {
    // This would need an example of invalid architecture
    // For now, just test that validation runs
    val scalaCode = """
      |package com.example
      |
      |case class Test(x: Int)
      |""".stripMargin

    val tempFile = createTempScalaFile(scalaCode)

    val output = captureStdout {
      Main.execute(ValidateCommand(tempFile, strict = false))
    }

    // Should complete without crashing
    output should not be empty
  }

  it should "be more strict with --strict flag" in {
    val scalaCode = """
      |package com.example
      |
      |case class Test(x: Int)
      |""".stripMargin

    val tempFile = createTempScalaFile(scalaCode)

    val strictOutput = captureStdout {
      Main.execute(ValidateCommand(tempFile, strict = true))
    }

    val normalOutput = captureStdout {
      Main.execute(ValidateCommand(tempFile, strict = false))
    }

    // Strict mode might produce different output (more warnings/errors)
    strictOutput should not be empty
    normalOutput should not be empty
  }

  // ============================================================================
  // MIGRATE COMMAND TESTS
  // ============================================================================

  "Main migrate command" should "migrate a value object to Kotlin" in {
    val scalaCode = """
      |package com.example.domain
      |
      |case class UserId(value: String) extends AnyVal
      |""".stripMargin

    val inputFile = createTempScalaFile(scalaCode)
    val outputFile = Files.createTempFile("output", ".kt")
    outputFile.toFile.deleteOnExit()

    Main.execute(MigrateCommand(inputFile, outputFile, skipValidation = false))

    val kotlinCode = Files.readString(outputFile)
    kotlinCode should include("UserId")
    kotlinCode should include("data class")
    kotlinCode should include("String")
  }

  it should "migrate a port to Kotlin interface" in {
    val scalaCode = """
      |package com.example.ports
      |
      |trait UserRepository {
      |  def findById(id: String): IO[Option[User]]
      |}
      |""".stripMargin

    val inputFile = createTempScalaFile(scalaCode)
    val outputFile = Files.createTempFile("output", ".kt")
    outputFile.toFile.deleteOnExit()

    Main.execute(MigrateCommand(inputFile, outputFile, skipValidation = false))

    val kotlinCode = Files.readString(outputFile)
    kotlinCode should include("UserRepository")
    kotlinCode should include("interface")
  }

  it should "skip validation when --skip-validation is set" in {
    // Even with invalid architecture, migration should proceed
    val scalaCode = """
      |package com.example
      |
      |case class Test(x: Int)
      |""".stripMargin

    val inputFile = createTempScalaFile(scalaCode)
    val outputFile = Files.createTempFile("output", ".kt")
    outputFile.toFile.deleteOnExit()

    // Should not throw even if validation would fail
    Main.execute(MigrateCommand(inputFile, outputFile, skipValidation = true))

    val kotlinCode = Files.readString(outputFile)
    kotlinCode should not be empty
  }

  // ============================================================================
  // MIGRATE-BATCH COMMAND TESTS
  // ============================================================================

  "Main migrate-batch command" should "migrate multiple files in a directory" in {
    val inputDir = createTempDir()
    val outputDir = createTempDir()

    // Create multiple test files
    Files.writeString(
      inputDir.resolve("UserId.scala"),
      "package com.example.domain\ncase class UserId(value: String) extends AnyVal"
    )
    Files.writeString(
      inputDir.resolve("User.scala"),
      "package com.example.domain\ncase class User(id: UserId, name: String)"
    )

    Main.execute(MigrateBatchCommand(inputDir, outputDir, skipValidation = false, parallel = false))

    // Check that output files were created
    Files.exists(outputDir.resolve("UserId.kt")) shouldBe true
    Files.exists(outputDir.resolve("User.kt")) shouldBe true

    // Verify content
    val userIdKt = Files.readString(outputDir.resolve("UserId.kt"))
    userIdKt should include("UserId")
  }

  it should "preserve directory structure when migrating" in {
    val inputDir = createTempDir()
    val outputDir = createTempDir()

    // Create subdirectory structure
    val domainDir = inputDir.resolve("domain")
    Files.createDirectories(domainDir)

    Files.writeString(
      domainDir.resolve("UserId.scala"),
      "package com.example.domain\ncase class UserId(value: String) extends AnyVal"
    )

    Main.execute(MigrateBatchCommand(inputDir, outputDir, skipValidation = false, parallel = false))

    // Check that directory structure is preserved
    Files.exists(outputDir.resolve("domain/UserId.kt")) shouldBe true
  }

  it should "handle parallel processing when --parallel is set" in {
    val inputDir = createTempDir()
    val outputDir = createTempDir()

    // Create multiple files
    (1 to 5).foreach { i =>
      Files.writeString(
        inputDir.resolve(s"Class$i.scala"),
        s"package com.example\ncase class Class$i(value: Int)"
      )
    }

    // Should complete successfully with parallel processing
    Main.execute(MigrateBatchCommand(inputDir, outputDir, skipValidation = false, parallel = true))

    // All output files should exist
    (1 to 5).foreach { i =>
      Files.exists(outputDir.resolve(s"Class$i.kt")) shouldBe true
    }
  }

  it should "report progress for batch operations" in {
    val inputDir = createTempDir()
    val outputDir = createTempDir()

    Files.writeString(
      inputDir.resolve("UserId.scala"),
      "package com.example.domain\ncase class UserId(value: String) extends AnyVal"
    )

    val output = captureStdout {
      Main.execute(MigrateBatchCommand(inputDir, outputDir, skipValidation = false, parallel = false))
    }

    // Should show some progress indication
    output should (include("Processing") or include("Migrating") or include("Complete"))
  }
}
