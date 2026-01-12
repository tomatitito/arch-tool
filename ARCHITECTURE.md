# Architecture Documentation

## Overview

This is a grammar-based architecture migration tool that translates Scala domain models into Kotlin Spring Boot code while preserving hexagonal architecture constraints.

## Module Boundaries and Contracts

### Architecture Principles

1. **Dependency Flow**: All modules depend on `ir` (the core domain model). The `cli` module orchestrates all other modules.
2. **No Circular Dependencies**: Modules must not create circular dependency chains.
3. **Clean Boundaries**: Each module has a single, well-defined responsibility.
4. **Language Agnostic Core**: The `ir` module is language-agnostic and framework-agnostic.

### Module Structure

```
arch-tool/
├── modules/
│   ├── ir/         # Core abstract model (no dependencies)
│   ├── parser/     # Scala parsing (depends on ir)
│   ├── renderer/   # Kotlin generation (depends on ir)
│   ├── validator/  # Architecture validation (depends on ir)
│   └── cli/        # Orchestration (depends on all)
```

## Module Responsibilities

### IR Module (`modules/ir`)

**Purpose**: Define the language-agnostic intermediate representation (IR) for architectural concepts.

**Responsibilities**:
- Define domain model types (value objects, entities, sealed hierarchies)
- Define port interfaces (repository contracts, service contracts)
- Define type signatures and type mappings
- Provide the abstract model that all other modules work with

**Dependencies**: None (core module)

**Public API Contracts**:
- Domain model case classes and sealed traits
- Port interface definitions
- Type system abstractions
- Validation result types

**Enforcement Rules**:
- Must not depend on any other module
- Must not reference external frameworks (Spring, Scalameta, KotlinPoet)
- Must be purely functional data structures
- All types must be immutable

**Example Types** (to be implemented):
```scala
package com.breuninger.arch.ir

// Domain model representation
case class DomainModel(
  name: String,
  fields: List[Field],
  modelType: ModelType
)

sealed trait ModelType
case object ValueObject extends ModelType
case object Entity extends ModelType
case class SealedHierarchy(cases: List[String]) extends ModelType

// Field representation
case class Field(
  name: String,
  fieldType: TypeRef,
  constraints: List[Constraint]
)

// Type reference
sealed trait TypeRef
case class SimpleType(name: String) extends TypeRef
case class GenericType(base: String, args: List[TypeRef]) extends TypeRef
case class OptionType(inner: TypeRef) extends TypeRef
```

### Parser Module (`modules/parser`)

**Purpose**: Parse Scala source code into IR using Scalameta.

**Responsibilities**:
- Parse Scala AST using Scalameta
- Extract domain models (case classes, sealed traits)
- Extract port interfaces (trait definitions)
- Map Scala types to IR type representations
- Detect architectural patterns (value objects, entities, ports)

**Dependencies**: `ir` only

**Public API Contracts**:
```scala
package com.breuninger.arch.parser

trait ScalaParser {
  def parseFile(path: Path): Either[ParseError, List[DomainModel]]
  def parseString(source: String): Either[ParseError, List[DomainModel]]
}

case class ParseError(message: String, location: Option[Location])
```

**Enforcement Rules**:
- Must only depend on `ir` module
- Must not reference Kotlin-specific concepts
- Must not perform validation (delegate to validator module)
- Must not generate code (delegate to renderer module)

### Renderer Module (`modules/renderer`)

**Purpose**: Generate Kotlin code from IR using KotlinPoet.

**Responsibilities**:
- Generate Kotlin data classes from IR domain models
- Generate Spring Boot annotations (@Entity, @Component, etc.)
- Generate repository interfaces with Spring Data JPA
- Apply Kotlin idioms and conventions
- Format generated code

**Dependencies**: `ir` only

**Public API Contracts**:
```scala
package com.breuninger.arch.renderer

trait KotlinRenderer {
  def renderDomainModel(model: DomainModel): String
  def renderPort(port: PortInterface): String
  def renderFile(models: List[DomainModel]): String
}
```

**Enforcement Rules**:
- Must only depend on `ir` module
- Must not reference Scala-specific parsing logic
- Must not perform validation (delegate to validator module)
- Generated code must be valid Kotlin syntax
- Must apply framework conventions (Spring Boot)

### Validator Module (`modules/validator`)

**Purpose**: Validate that IR conforms to hexagonal architecture constraints.

**Responsibilities**:
- Validate hexagonal architecture (ports/adapters pattern)
- Enforce layer boundaries
- Validate type safety and contract compliance
- Check dependency directions
- Verify architectural constraints are met

**Dependencies**: `ir` only

**Public API Contracts**:
```scala
package com.breuninger.arch.validator

trait ArchitectureValidator {
  def validate(models: List[DomainModel]): ValidationResult
  def validatePorts(ports: List[PortInterface]): ValidationResult
}

case class ValidationResult(
  isValid: Boolean,
  errors: List[ValidationError],
  warnings: List[ValidationWarning]
)

case class ValidationError(message: String, location: Option[Location])
case class ValidationWarning(message: String, location: Option[Location])
```

**Enforcement Rules**:
- Must only depend on `ir` module
- Must not perform parsing (delegate to parser module)
- Must not generate code (delegate to renderer module)
- Must define clear architectural rules
- Must provide actionable error messages

### CLI Module (`modules/cli`)

**Purpose**: Orchestrate all modules and provide command-line interface.

**Responsibilities**:
- Provide CLI commands (parse, validate, migrate, migrate-batch)
- Orchestrate parsing → validation → rendering pipeline
- Handle file I/O and batch processing
- Report errors and results to user
- Coordinate module interactions

**Dependencies**: `ir`, `parser`, `renderer`, `validator`

**Public API Contracts**:
```scala
package com.breuninger.arch.cli

object Main {
  def main(args: Array[String]): Unit
}

// Commands
trait Command
case class ParseCommand(inputPath: Path) extends Command
case class ValidateCommand(inputPath: Path) extends Command
case class MigrateCommand(inputPath: Path, outputPath: Path) extends Command
case class MigrateBatchCommand(inputDir: Path, outputDir: Path) extends Command
```

**Enforcement Rules**:
- Must depend on all other modules
- Coordinates the pipeline: parse → validate → render
- Handles file system operations
- No business logic (delegate to appropriate modules)
- No direct Scalameta or KotlinPoet usage (use parser/renderer)

## Dependency Graph

```
       ┌─────────────┐
       │     cli     │
       └──────┬──────┘
              │
    ┌─────────┼─────────┐
    │         │         │
    ▼         ▼         ▼
┌────────┐ ┌──────────┐ ┌──────────┐
│ parser │ │ renderer │ │validator │
└───┬────┘ └────┬─────┘ └────┬─────┘
    │           │            │
    └───────────┼────────────┘
                ▼
            ┌───────┐
            │   ir  │
            └───────┘
```

## Build Configuration

The dependency rules are enforced in `build.sbt`:

```scala
// IR: no dependencies
lazy val ir = (project in file("modules/ir"))

// Parser: depends on ir only
lazy val parser = (project in file("modules/parser"))
  .dependsOn(ir)

// Renderer: depends on ir only
lazy val renderer = (project in file("modules/renderer"))
  .dependsOn(ir)

// Validator: depends on ir only
lazy val validator = (project in file("modules/validator"))
  .dependsOn(ir)

// CLI: orchestrates all modules
lazy val cli = (project in file("modules/cli"))
  .dependsOn(ir, parser, renderer, validator)
```

## Data Flow

1. **Input**: Scala source files
2. **Parser**: Scala → IR (abstract model)
3. **Validator**: IR → validation results
4. **Renderer**: IR → Kotlin code
5. **Output**: Kotlin source files + validation report

## Testing Strategy

Each module must have its own test suite:

- **IR tests**: Test domain model construction and invariants
- **Parser tests**: Test Scala → IR conversion with example inputs
- **Renderer tests**: Test IR → Kotlin generation with expected outputs
- **Validator tests**: Test architectural rule validation
- **CLI tests**: Test end-to-end pipeline integration

## Extension Points

Future extensions should follow the established patterns:

- New parsers (Java, Kotlin) → create new module depending on `ir`
- New renderers (TypeScript, Go) → create new module depending on `ir`
- New validators (DDD rules, event sourcing) → extend validator module
- New CLI commands → extend cli module

All extensions must respect the module boundaries and dependency rules.
