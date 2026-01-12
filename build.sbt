// Multi-module Scala project for grammar-based architecture migration tool
name := "arch-tool"

// Common settings for all modules
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / organization := "com.breuninger"

// Common dependencies
lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-encoding", "utf8",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  ),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.17" % Test
  )
)

// Root project - aggregates all modules
lazy val root = (project in file("."))
  .aggregate(ir, parser, renderer, validator, cli)
  .settings(
    name := "arch-tool-root",
    publish / skip := true
  )

// IR module - Core abstract model (Intermediate Representation)
lazy val ir = (project in file("modules/ir"))
  .settings(
    name := "arch-tool-ir",
    commonSettings,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0"
    )
  )

// Parser module - Scalameta integration for Scala parsing
lazy val parser = (project in file("modules/parser"))
  .dependsOn(ir)
  .settings(
    name := "arch-tool-parser",
    commonSettings,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.8.15"
    )
  )

// Renderer module - KotlinPoet integration for Kotlin code generation
lazy val renderer = (project in file("modules/renderer"))
  .dependsOn(ir)
  .settings(
    name := "arch-tool-renderer",
    commonSettings,
    libraryDependencies ++= Seq(
      "com.squareup" % "kotlinpoet" % "1.15.3"
    )
  )

// Validator module - Architectural rules and validation
lazy val validator = (project in file("modules/validator"))
  .dependsOn(ir)
  .settings(
    name := "arch-tool-validator",
    commonSettings,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0"
    )
  )

// CLI module - Command-line interface
lazy val cli = (project in file("modules/cli"))
  .dependsOn(ir, parser, renderer, validator)
  .settings(
    name := "arch-tool-cli",
    commonSettings,
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "4.1.0"
    ),
    assembly / mainClass := Some("com.breuninger.arch.cli.Main"),
    assembly / assemblyJarName := "arch-tool.jar"
  )
  .enablePlugins(AssemblyPlugin)
