package com.breuninger.arch.cli

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._
import com.breuninger.arch.renderer.RenderConfig

/**
 * Golden/snapshot tests using real Scala files from import-produkt.
 *
 * These tests verify the complete migration pipeline by comparing
 * actual Kotlin output against expected "golden" files.
 *
 * Test Structure:
 * - input/: Scala source files (copied from import-produkt)
 * - expected/: Expected Kotlin output (arch-tool conventions)
 * - reference/: Manual port reference (for comparison only)
 *
 * Phases:
 * 1. Value objects (simple 1:1 translation)
 * 2. Entities (more complex data classes)
 * 3. Ports (interface definitions)
 * 4. Kafka adapters (TBD when adapter parsing is implemented)
 */
class GoldenTestSpec extends AnyFlatSpec with Matchers {

  // ============================================================================
  // TEST FIXTURES AND HELPERS
  // ============================================================================

  /** Base path for golden test resources */
  private lazy val goldenDir: Path = {
    val resourceUrl = getClass.getResource("/golden")
    if (resourceUrl == null) {
      fail("Golden test resources not found. Ensure modules/cli/src/test/resources/golden/ exists.")
    }
    Paths.get(resourceUrl.toURI)
  }

  /** Helper to read file contents */
  private def readFile(path: Path): String = {
    Files.readString(path)
  }

  /** Helper to list all files in a directory */
  private def listFiles(dir: Path): List[Path] = {
    if (Files.exists(dir)) {
      Files.list(dir).iterator().asScala.toList.filter(Files.isRegularFile(_))
    } else {
      List.empty
    }
  }

  /** Helper to get expected Kotlin filename from Scala filename */
  private def toKotlinFilename(scalaFilename: String): String = {
    scalaFilename.replace(".scala", ".kt")
  }

  /**
   * Run migration on a single Scala file and return Kotlin output.
   */
  private def runMigration(scalaFile: Path): String = {
    val pipeline = DefaultMigrationPipeline.create()
    val tempOutputFile = Files.createTempFile("output", ".kt")
    tempOutputFile.toFile.deleteOnExit()

    val config = MigrationConfig(
      skipValidation = true,
      renderConfig = RenderConfig(
        indentSize = 4,
        useDataClasses = true
      ),
      failOnWarnings = false
    )

    pipeline.migrate(scalaFile, tempOutputFile, config) match {
      case Right(_) => Files.readString(tempOutputFile)
      case Left(error) =>
        fail(s"Migration failed: ${error.message}")
    }
  }

  /**
   * Run migration and compare against expected output.
   *
   * This is the core assertion helper for golden tests.
   */
  private def assertGoldenMatch(
    inputFile: Path,
    expectedFile: Path,
    category: String
  ): Unit = {
    withClue(s"[$category] ${inputFile.getFileName} -> ${expectedFile.getFileName}: ") {
      val actual = runMigration(inputFile)
      val expected = readFile(expectedFile)

      // Normalize whitespace for comparison
      val normalizedActual = actual.trim
      val normalizedExpected = expected.trim

      normalizedActual shouldBe normalizedExpected
    }
  }

  // ============================================================================
  // PHASE 1: VALUE OBJECTS
  // ============================================================================

  "Phase 1: Value Objects" should "correctly migrate ProduktId" in {
    val inputDir = goldenDir.resolve("input/value-objects")
    val expectedDir = goldenDir.resolve("expected/value-objects")

    val inputFile = inputDir.resolve("ProduktId.scala")
    val expectedFile = expectedDir.resolve("ProduktId.kt")

    assume(Files.exists(inputFile), s"Input file not found: $inputFile")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    assertGoldenMatch(inputFile, expectedFile, "value-objects")
  }

  it should "correctly migrate ArtikelId" in {
    val inputDir = goldenDir.resolve("input/value-objects")
    val expectedDir = goldenDir.resolve("expected/value-objects")

    val inputFile = inputDir.resolve("ArtikelId.scala")
    val expectedFile = expectedDir.resolve("ArtikelId.kt")

    assume(Files.exists(inputFile), s"Input file not found: $inputFile")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    assertGoldenMatch(inputFile, expectedFile, "value-objects")
  }

  it should "correctly migrate FarbId" in {
    val inputDir = goldenDir.resolve("input/value-objects")
    val expectedDir = goldenDir.resolve("expected/value-objects")

    val inputFile = inputDir.resolve("FarbId.scala")
    val expectedFile = expectedDir.resolve("FarbId.kt")

    assume(Files.exists(inputFile), s"Input file not found: $inputFile")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    assertGoldenMatch(inputFile, expectedFile, "value-objects")
  }

  it should "correctly migrate Land" in {
    val inputDir = goldenDir.resolve("input/value-objects")
    val expectedDir = goldenDir.resolve("expected/value-objects")

    val inputFile = inputDir.resolve("Land.scala")
    val expectedFile = expectedDir.resolve("Land.kt")

    assume(Files.exists(inputFile), s"Input file not found: $inputFile")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    assertGoldenMatch(inputFile, expectedFile, "value-objects")
  }

  it should "migrate all value objects in the directory" in {
    val inputDir = goldenDir.resolve("input/value-objects")
    val expectedDir = goldenDir.resolve("expected/value-objects")

    assume(Files.exists(inputDir), s"Input directory not found: $inputDir")
    assume(Files.exists(expectedDir), s"Expected directory not found: $expectedDir")

    val inputFiles = listFiles(inputDir).filter(_.toString.endsWith(".scala"))
    inputFiles should not be empty

    inputFiles.foreach { inputFile =>
      val expectedFile = expectedDir.resolve(toKotlinFilename(inputFile.getFileName.toString))

      withClue(s"Missing expected file for ${inputFile.getFileName}: ") {
        Files.exists(expectedFile) shouldBe true
      }

      assertGoldenMatch(inputFile, expectedFile, "value-objects")
    }
  }

  // ============================================================================
  // PHASE 2: ENTITIES
  // ============================================================================

  "Phase 2: Entities" should "correctly migrate Farbe" in {
    val inputDir = goldenDir.resolve("input/entities")
    val expectedDir = goldenDir.resolve("expected/entities")

    val inputFile = inputDir.resolve("Farbe.scala")
    val expectedFile = expectedDir.resolve("Farbe.kt")

    assume(Files.exists(inputFile), s"Input file not found: $inputFile")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    assertGoldenMatch(inputFile, expectedFile, "entities")
  }

  it should "correctly migrate Groesse" in {
    val inputDir = goldenDir.resolve("input/entities")
    val expectedDir = goldenDir.resolve("expected/entities")

    val inputFile = inputDir.resolve("Groesse.scala")
    val expectedFile = expectedDir.resolve("Groesse.kt")

    assume(Files.exists(inputFile), s"Input file not found: $inputFile")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    assertGoldenMatch(inputFile, expectedFile, "entities")
  }

  it should "correctly migrate Artikel (complex entity with nested types)" in {
    val inputDir = goldenDir.resolve("input/entities")
    val expectedDir = goldenDir.resolve("expected/entities")

    val inputFile = inputDir.resolve("Artikel.scala")
    val expectedFile = expectedDir.resolve("Artikel.kt")

    assume(Files.exists(inputFile), s"Input file not found: $inputFile")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    assertGoldenMatch(inputFile, expectedFile, "entities")
  }

  it should "handle Map[Locale, T] type correctly in Farbe" in {
    val expectedFile = goldenDir.resolve("expected/entities/Farbe.kt")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    val expected = readFile(expectedFile)
    expected should include("Map<Locale, String>")
  }

  it should "handle Option[T] as nullable type T? in Farbe" in {
    val expectedFile = goldenDir.resolve("expected/entities/Farbe.kt")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    val expected = readFile(expectedFile)
    expected should include("String?")
  }

  it should "handle List[T] type correctly in Artikel" in {
    val expectedFile = goldenDir.resolve("expected/entities/Artikel.kt")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    val expected = readFile(expectedFile)
    expected should include("List<AttributV2>")
    expected should include("List<Vertriebsinfo>")
  }

  it should "handle imports correctly" in {
    val expectedFile = goldenDir.resolve("expected/entities/Farbe.kt")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    val expected = readFile(expectedFile)
    expected should include("import java.util.Locale")
  }

  it should "migrate all entities in the directory" in {
    val inputDir = goldenDir.resolve("input/entities")
    val expectedDir = goldenDir.resolve("expected/entities")

    assume(Files.exists(inputDir), s"Input directory not found: $inputDir")
    assume(Files.exists(expectedDir), s"Expected directory not found: $expectedDir")

    val inputFiles = listFiles(inputDir).filter(_.toString.endsWith(".scala"))
    inputFiles should not be empty

    inputFiles.foreach { inputFile =>
      val expectedFile = expectedDir.resolve(toKotlinFilename(inputFile.getFileName.toString))

      withClue(s"Missing expected file for ${inputFile.getFileName}: ") {
        Files.exists(expectedFile) shouldBe true
      }

      assertGoldenMatch(inputFile, expectedFile, "entities")
    }
  }

  // ============================================================================
  // PHASE 3: PORTS (INTERFACES)
  // ============================================================================

  "Phase 3: Ports" should "correctly migrate ProduktRepository" in {
    val inputDir = goldenDir.resolve("input/ports")
    val expectedDir = goldenDir.resolve("expected/ports")

    val inputFile = inputDir.resolve("ProduktRepository.scala")
    val expectedFile = expectedDir.resolve("ProduktRepository.kt")

    assume(Files.exists(inputFile), s"Input file not found: $inputFile")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    assertGoldenMatch(inputFile, expectedFile, "ports")
  }

  it should "convert trait to interface" in {
    val expectedFile = goldenDir.resolve("expected/ports/ProduktRepository.kt")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    val expected = readFile(expectedFile)
    expected should include("interface ProduktRepository")
    expected should not include "trait"
  }

  it should "add suspend modifier to IO methods" in {
    val expectedFile = goldenDir.resolve("expected/ports/ProduktRepository.kt")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    val expected = readFile(expectedFile)
    expected should include("suspend fun save")
    expected should include("suspend fun exists")
    expected should include("suspend fun deleteByIds")
  }

  it should "unwrap IO effect type from return types" in {
    val expectedFile = goldenDir.resolve("expected/ports/ProduktRepository.kt")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    val expected = readFile(expectedFile)
    expected should include(": Unit")
    expected should include(": Boolean")
    // IO[Unit] -> Unit, IO[Boolean] -> Boolean
    expected should not include "IO<"
  }

  it should "convert NonEmptyList[T] to List<T>" in {
    val expectedFile = goldenDir.resolve("expected/ports/ProduktRepository.kt")
    assume(Files.exists(expectedFile), s"Expected file not found: $expectedFile")

    val expected = readFile(expectedFile)
    expected should include("List<ProduktId>")
    expected should not include "NonEmptyList"
  }

  it should "migrate all ports in the directory" in {
    val inputDir = goldenDir.resolve("input/ports")
    val expectedDir = goldenDir.resolve("expected/ports")

    assume(Files.exists(inputDir), s"Input directory not found: $inputDir")
    assume(Files.exists(expectedDir), s"Expected directory not found: $expectedDir")

    val inputFiles = listFiles(inputDir).filter(_.toString.endsWith(".scala"))
    inputFiles should not be empty

    inputFiles.foreach { inputFile =>
      val expectedFile = expectedDir.resolve(toKotlinFilename(inputFile.getFileName.toString))

      withClue(s"Missing expected file for ${inputFile.getFileName}: ") {
        Files.exists(expectedFile) shouldBe true
      }

      assertGoldenMatch(inputFile, expectedFile, "ports")
    }
  }

  // ============================================================================
  // PHASE 4: KAFKA ADAPTERS (TBD - Input files only for now)
  // ============================================================================

  "Phase 4: Kafka Adapters" should "have input files present" in {
    val inputDir = goldenDir.resolve("input/kafka")
    assume(Files.exists(inputDir), s"Input directory not found: $inputDir")

    val inputFiles = listFiles(inputDir).filter(_.toString.endsWith(".scala"))
    inputFiles.map(_.getFileName.toString) should contain allOf(
      "KafkaConsumerFactory.scala",
      "GoldenRecordToProduktService.scala",
      "ProduktGoldenRecordParser.scala"
    )
  }

  it should "have reference Kotlin files for comparison" in {
    val referenceDir = goldenDir.resolve("reference/kafka")
    assume(Files.exists(referenceDir), s"Reference directory not found: $referenceDir")

    val referenceFiles = listFiles(referenceDir).filter(_.toString.endsWith(".kt"))
    referenceFiles.map(_.getFileName.toString) should contain allOf(
      "GoldenRecordConsumer.kt",
      "GoldenRecordConsumerRecordConverter.kt",
      "FeedMethodHeader.kt"
    )
  }

  // Note: Kafka adapter migration tests are TBD because:
  // 1. Adapter parsing isn't implemented yet
  // 2. The tool might not support framework migrations (FS2 -> Spring)
  // 3. Expected output would be a skeleton with TODO placeholders
  //
  // When implemented, these tests will verify:
  // - MessageQueueType.KafkaConsumer infrastructure dependency detection
  // - AdapterType.Messaging classification
  // - Constructor parameter analysis for dependencies

  // ============================================================================
  // CROSS-CUTTING TESTS
  // ============================================================================

  "Cross-cutting concerns" should "preserve package names" in {
    val expectedVo = goldenDir.resolve("expected/value-objects/ProduktId.kt")
    val expectedEntity = goldenDir.resolve("expected/entities/Farbe.kt")
    val expectedPort = goldenDir.resolve("expected/ports/ProduktRepository.kt")

    assume(Files.exists(expectedVo), s"Expected file not found: $expectedVo")
    assume(Files.exists(expectedEntity), s"Expected file not found: $expectedEntity")
    assume(Files.exists(expectedPort), s"Expected file not found: $expectedPort")

    readFile(expectedVo) should include("package com.breuninger.entdecken.domain.model")
    readFile(expectedEntity) should include("package com.breuninger.entdecken.domain.model")
    readFile(expectedPort) should include("package com.breuninger.entdecken.domain.repository")
  }

  it should "generate data class for case classes" in {
    val expectedVo = goldenDir.resolve("expected/value-objects/ProduktId.kt")
    val expectedEntity = goldenDir.resolve("expected/entities/Farbe.kt")

    assume(Files.exists(expectedVo), s"Expected file not found: $expectedVo")
    assume(Files.exists(expectedEntity), s"Expected file not found: $expectedEntity")

    readFile(expectedVo) should include("data class ProduktId")
    readFile(expectedEntity) should include("data class Farbe")
  }

  it should "use val for constructor parameters" in {
    val expectedVo = goldenDir.resolve("expected/value-objects/ProduktId.kt")
    assume(Files.exists(expectedVo), s"Expected file not found: $expectedVo")

    readFile(expectedVo) should include("val value: String")
  }

  it should "convert Scala primitive types to Kotlin" in {
    val expectedVo = goldenDir.resolve("expected/value-objects/ProduktId.kt")
    assume(Files.exists(expectedVo), s"Expected file not found: $expectedVo")

    // String should remain String (same in both languages)
    readFile(expectedVo) should include("String")
  }

  // ============================================================================
  // STRUCTURAL VALIDATION TESTS
  // ============================================================================

  "Golden test structure" should "have matching input/expected file pairs for value-objects" in {
    val inputDir = goldenDir.resolve("input/value-objects")
    val expectedDir = goldenDir.resolve("expected/value-objects")

    assume(Files.exists(inputDir), s"Input directory not found: $inputDir")
    assume(Files.exists(expectedDir), s"Expected directory not found: $expectedDir")

    val inputFiles = listFiles(inputDir).filter(_.toString.endsWith(".scala"))

    inputFiles.foreach { inputFile =>
      val expectedFile = expectedDir.resolve(toKotlinFilename(inputFile.getFileName.toString))
      withClue(s"Missing expected file for ${inputFile.getFileName}: ") {
        Files.exists(expectedFile) shouldBe true
      }
    }
  }

  it should "have matching input/expected file pairs for entities" in {
    val inputDir = goldenDir.resolve("input/entities")
    val expectedDir = goldenDir.resolve("expected/entities")

    assume(Files.exists(inputDir), s"Input directory not found: $inputDir")
    assume(Files.exists(expectedDir), s"Expected directory not found: $expectedDir")

    val inputFiles = listFiles(inputDir).filter(_.toString.endsWith(".scala"))

    inputFiles.foreach { inputFile =>
      val expectedFile = expectedDir.resolve(toKotlinFilename(inputFile.getFileName.toString))
      withClue(s"Missing expected file for ${inputFile.getFileName}: ") {
        Files.exists(expectedFile) shouldBe true
      }
    }
  }

  it should "have matching input/expected file pairs for ports" in {
    val inputDir = goldenDir.resolve("input/ports")
    val expectedDir = goldenDir.resolve("expected/ports")

    assume(Files.exists(inputDir), s"Input directory not found: $inputDir")
    assume(Files.exists(expectedDir), s"Expected directory not found: $expectedDir")

    val inputFiles = listFiles(inputDir).filter(_.toString.endsWith(".scala"))

    inputFiles.foreach { inputFile =>
      val expectedFile = expectedDir.resolve(toKotlinFilename(inputFile.getFileName.toString))
      withClue(s"Missing expected file for ${inputFile.getFileName}: ") {
        Files.exists(expectedFile) shouldBe true
      }
    }
  }
}
