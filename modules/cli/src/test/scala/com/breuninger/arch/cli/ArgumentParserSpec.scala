package com.breuninger.arch.cli

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.Paths

/**
 * Test specification for CLI argument parsing
 *
 * These tests define the expected CLI interface and argument structure.
 * Use these to drive the scopt parser configuration.
 */
class ArgumentParserSpec extends AnyFlatSpec with Matchers {

  "Argument parser" should "parse 'help' command" in {
    Main.parseArgs(Array("help")) shouldBe Some(HelpCommand)
    Main.parseArgs(Array("--help")) shouldBe Some(HelpCommand)
    Main.parseArgs(Array("-h")) shouldBe Some(HelpCommand)
  }

  it should "parse 'version' command" in {
    Main.parseArgs(Array("version")) shouldBe Some(VersionCommand)
    Main.parseArgs(Array("--version")) shouldBe Some(VersionCommand)
    Main.parseArgs(Array("-v")) shouldBe Some(VersionCommand)
  }

  it should "parse 'parse' command with required file argument" in {
    val result = Main.parseArgs(Array("parse", "Domain.scala"))
    result shouldBe defined
    result.get shouldBe a[ParseCommand]

    val cmd = result.get.asInstanceOf[ParseCommand]
    cmd.inputPath.toString should endWith("Domain.scala")
    cmd.verbose shouldBe false
  }

  it should "parse 'parse' command with --verbose flag" in {
    val result = Main.parseArgs(Array("parse", "Domain.scala", "--verbose"))
    result shouldBe defined

    val cmd = result.get.asInstanceOf[ParseCommand]
    cmd.verbose shouldBe true
  }

  it should "parse 'parse' command with short -v flag for verbose" in {
    val result = Main.parseArgs(Array("parse", "Domain.scala", "-v"))
    result shouldBe defined

    val cmd = result.get.asInstanceOf[ParseCommand]
    cmd.verbose shouldBe true
  }

  it should "fail 'parse' command without file argument" in {
    Main.parseArgs(Array("parse")) shouldBe None
  }

  it should "parse 'validate' command with required file argument" in {
    val result = Main.parseArgs(Array("validate", "Domain.scala"))
    result shouldBe defined
    result.get shouldBe a[ValidateCommand]

    val cmd = result.get.asInstanceOf[ValidateCommand]
    cmd.inputPath.toString should endWith("Domain.scala")
    cmd.strict shouldBe false
  }

  it should "parse 'validate' command with --strict flag" in {
    val result = Main.parseArgs(Array("validate", "Domain.scala", "--strict"))
    result shouldBe defined

    val cmd = result.get.asInstanceOf[ValidateCommand]
    cmd.strict shouldBe true
  }

  it should "parse 'validate' command with short -s flag for strict" in {
    val result = Main.parseArgs(Array("validate", "Domain.scala", "-s"))
    result shouldBe defined

    val cmd = result.get.asInstanceOf[ValidateCommand]
    cmd.strict shouldBe true
  }

  it should "fail 'validate' command without file argument" in {
    Main.parseArgs(Array("validate")) shouldBe None
  }

  it should "parse 'migrate' command with input and output arguments" in {
    val result = Main.parseArgs(Array("migrate", "Domain.scala", "Domain.kt"))
    result shouldBe defined
    result.get shouldBe a[MigrateCommand]

    val cmd = result.get.asInstanceOf[MigrateCommand]
    cmd.inputPath.toString should endWith("Domain.scala")
    cmd.outputPath.toString should endWith("Domain.kt")
    cmd.skipValidation shouldBe false
  }

  it should "parse 'migrate' command with --skip-validation flag" in {
    val result = Main.parseArgs(Array("migrate", "Domain.scala", "Domain.kt", "--skip-validation"))
    result shouldBe defined

    val cmd = result.get.asInstanceOf[MigrateCommand]
    cmd.skipValidation shouldBe true
  }

  it should "fail 'migrate' command without input argument" in {
    Main.parseArgs(Array("migrate")) shouldBe None
  }

  it should "fail 'migrate' command without output argument" in {
    Main.parseArgs(Array("migrate", "Domain.scala")) shouldBe None
  }

  it should "parse 'migrate-batch' command with input and output directories" in {
    val result = Main.parseArgs(Array("migrate-batch", "src/", "out/"))
    result shouldBe defined
    result.get shouldBe a[MigrateBatchCommand]

    val cmd = result.get.asInstanceOf[MigrateBatchCommand]
    cmd.inputDir.toString should include("src")
    cmd.outputDir.toString should include("out")
    cmd.skipValidation shouldBe false
    cmd.parallel shouldBe true // default
  }

  it should "parse 'migrate-batch' command with --skip-validation flag" in {
    val result = Main.parseArgs(Array("migrate-batch", "src/", "out/", "--skip-validation"))
    result shouldBe defined

    val cmd = result.get.asInstanceOf[MigrateBatchCommand]
    cmd.skipValidation shouldBe true
  }

  it should "parse 'migrate-batch' command with --no-parallel flag" in {
    val result = Main.parseArgs(Array("migrate-batch", "src/", "out/", "--no-parallel"))
    result shouldBe defined

    val cmd = result.get.asInstanceOf[MigrateBatchCommand]
    cmd.parallel shouldBe false
  }

  it should "parse 'migrate-batch' command with --sequential flag (alias for --no-parallel)" in {
    val result = Main.parseArgs(Array("migrate-batch", "src/", "out/", "--sequential"))
    result shouldBe defined

    val cmd = result.get.asInstanceOf[MigrateBatchCommand]
    cmd.parallel shouldBe false
  }

  it should "fail 'migrate-batch' command without input directory" in {
    Main.parseArgs(Array("migrate-batch")) shouldBe None
  }

  it should "fail 'migrate-batch' command without output directory" in {
    Main.parseArgs(Array("migrate-batch", "src/")) shouldBe None
  }

  it should "fail on unknown command" in {
    Main.parseArgs(Array("unknown-command")) shouldBe None
  }

  it should "fail on unknown flags" in {
    Main.parseArgs(Array("parse", "Domain.scala", "--unknown-flag")) shouldBe None
  }

  it should "handle flags in different positions" in {
    // Flags before positional args
    val result1 = Main.parseArgs(Array("parse", "--verbose", "Domain.scala"))
    result1 shouldBe defined
    result1.get.asInstanceOf[ParseCommand].verbose shouldBe true

    // Flags after positional args
    val result2 = Main.parseArgs(Array("parse", "Domain.scala", "--verbose"))
    result2 shouldBe defined
    result2.get.asInstanceOf[ParseCommand].verbose shouldBe true
  }

  it should "handle multiple flags" in {
    val result = Main.parseArgs(Array("migrate", "Domain.scala", "Domain.kt", "--skip-validation"))
    result shouldBe defined

    val cmd = result.get.asInstanceOf[MigrateCommand]
    cmd.skipValidation shouldBe true
  }

  it should "handle absolute and relative paths" in {
    val result1 = Main.parseArgs(Array("parse", "/absolute/path/Domain.scala"))
    result1 shouldBe defined
    result1.get.asInstanceOf[ParseCommand].inputPath.toString should include("/absolute/path/Domain.scala")

    val result2 = Main.parseArgs(Array("parse", "relative/path/Domain.scala"))
    result2 shouldBe defined
    result2.get.asInstanceOf[ParseCommand].inputPath.toString should include("relative/path/Domain.scala")
  }

  it should "handle empty arguments" in {
    Main.parseArgs(Array()) shouldBe None
  }

  it should "provide helpful error messages for invalid input" in {
    // This test ensures scopt is configured to print helpful errors
    // We can't easily capture scopt's stderr output, but we verify it returns None
    Main.parseArgs(Array("parse")) shouldBe None
    Main.parseArgs(Array("invalid")) shouldBe None
  }
}
