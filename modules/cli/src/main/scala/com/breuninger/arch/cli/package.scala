package com.breuninger.arch

/**
 * CLI module - Command-line interface
 *
 * Provides the command-line interface for the architecture migration tool.
 * Commands: parse, validate, migrate, migrate-batch
 *
 * PUBLIC API:
 * - Command: CLI command types
 * - MigrationPipeline: Orchestrates parse → validate → render
 * - MigrationConfig, MigrationResult: Configuration and results
 * - PipelineError: Error types for pipeline execution
 *
 * DEPENDENCY RULES:
 * - Depends on ALL other modules (ir, parser, renderer, validator)
 * - Orchestrates the complete pipeline
 * - Handles file I/O and batch processing
 * - No business logic (delegate to appropriate modules)
 */
package object cli {
  // Public API defined in Commands.scala and Pipeline.scala
}
