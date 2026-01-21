# Grammar-Based Scala to Kotlin Migration

A formal grammar tool for migrating Scala microservices to Kotlin + Spring Boot while preserving hexagonal architecture patterns.

## Quick Start

**📚 Full Documentation**: See [specs/README.md](specs/README.md) for complete specification index

### New to This Project?

**Start here**: [specs/TOOL_SCOPE_SUMMARY.md](specs/TOOL_SCOPE_SUMMARY.md) (5 min read)
- What's automated vs. manual
- Time savings and value proposition
- Quick reference tables

### Want to Understand the Approach?

**Read next**: [specs/ARCHITECTURE_GRAMMAR.md](specs/ARCHITECTURE_GRAMMAR.md) (15 min read)
- Formal grammar specification ([architecture-grammar.ebnf](architecture-grammar.ebnf))
- Language-agnostic architectural concepts
- Type mappings and validation rules
- Complete migration strategy overview

### Ready to Build the Tool?

**Implementation guide**: [specs/PLAN_GRAMMAR_POC.md](specs/PLAN_GRAMMAR_POC.md) (30 min read)
- Step-by-step POC implementation
- Abstract model (IR) design
- Parser and renderer architecture
- Complete code examples

### Need API Documentation?

**Library references**:
- [specs/KOTLINPOET_EXAMPLES.md](specs/KOTLINPOET_EXAMPLES.md) - KotlinPoet code generation API
- [specs/SCALAMETA_OUTPUT_EXAMPLES.md](specs/SCALAMETA_OUTPUT_EXAMPLES.md) - Scalameta parser output format

### Migrating to Spring Boot?

**Framework integration**: [specs/SPRING_BOOT_MIGRATION.md](specs/SPRING_BOOT_MIGRATION.md)
- Spring annotations and stereotypes
- Dependency injection patterns
- Updated type mappings
- Repository, Service, Controller examples

### Building Tests?

**Quality assurance**: [specs/TEST_DRIVEN_MIGRATION.md](specs/TEST_DRIVEN_MIGRATION.md)
- Contract tests for behavioral equivalence
- Integration testing with Testcontainers
- Property-based testing
- CI/CD workflows

---

## Documentation Map

All specifications are in the [specs/](specs/) directory. See [specs/README.md](specs/README.md) for the complete index.

```
📚 Documentation Structure (specs/)
│
├── 🎯 TOOL_SCOPE_SUMMARY.md          ← START HERE
│   └── Quick reference: What's automated vs manual
│
├── 📐 ARCHITECTURE_GRAMMAR.md         ← Core Concepts
│   ├── Formal grammar specification (see architecture-grammar.ebnf)
│   ├── Hexagonal architecture patterns
│   ├── Type mappings (Scala ↔ Kotlin)
│   └── Migration strategy
│
├── 🛠️  PLAN_GRAMMAR_POC.md            ← Implementation
│   ├── Abstract model (IR) design
│   ├── Scalameta parser
│   ├── KotlinPoet renderer
│   ├── Architectural validator
│   └── CLI tool
│
├── 📖 Library References
│   ├── KOTLINPOET_EXAMPLES.md        ← Code generation API
│   │   ├── TypeSpec, FunSpec, PropertySpec
│   │   ├── Annotations and modifiers
│   │   └── Complete examples
│   │
│   └── SCALAMETA_OUTPUT_EXAMPLES.md  ← Parser output format
│       ├── AST structure
│       ├── Pattern matching examples
│       └── Type extraction
│
├── 🌱 SPRING_BOOT_MIGRATION.md        ← Framework Integration
│   ├── Spring stereotypes (@Service, @Repository)
│   ├── Dependency injection
│   ├── WebFlux and coroutines
│   └── Spring-specific renderers
│
└── ✅ TEST_DRIVEN_MIGRATION.md        ← Quality Assurance
    ├── Contract tests
    ├── Integration tests
    ├── Property-based tests
    └── CI/CD pipeline
```

---

## What This Project Does

### The Problem

Migrating a Scala microservice with hexagonal architecture to Kotlin + Spring Boot is:
- **Tedious**: Manually translating hundreds of interfaces and models
- **Error-prone**: Type mismatches, missed dependencies, broken contracts
- **Time-consuming**: Weeks of mechanical translation work
- **Risky**: No guarantee of behavioral equivalence

### The Solution

A **grammar-based migration tool** that:

1. **Parses** Scala code into an abstract architectural model (using Scalameta)
2. **Validates** architectural constraints (ports/adapters, layer boundaries)
3. **Generates** equivalent Kotlin code (using KotlinPoet)
4. **Ensures** type safety and correctness

### What Gets Automated (✅)

- ✅ Port interfaces (100%)
- ✅ Domain models (value objects, entities, sealed hierarchies)
- ✅ Type mappings (IO[A] → suspend fun, Option[A] → A?)
- ✅ Adapter skeletons (class structure, constructor)
- ✅ Spring annotations (@Repository, @Service, @RestController)

### What You Implement (❌)

- ❌ Business logic (MongoDB queries, Kafka handling, HTTP responses)
- ❌ Error handling and validation
- ❌ Performance optimizations
- ❌ Infrastructure code (connection pools, retry logic)

**Result**: Focus on the creative 70% (business logic) instead of the mechanical 30% (boilerplate).

---

## Key Concepts

### Hexagonal Architecture

```
┌─────────────────────────────────────────────┐
│           Application Layer                 │
│         (Services, Use Cases)               │
└──────────────┬──────────────────────────────┘
               │ depends on ↓
┌──────────────▼──────────────────────────────┐
│           Domain Layer                      │
│     (Ports = Interfaces, Models)            │
└──────────────┬──────────────────────────────┘
               │ ↑ implemented by
┌──────────────▼──────────────────────────────┐
│           Ports Layer                       │
│  (Adapters = Implementations)               │
│  • Persistence (MongoDB, PostgreSQL)        │
│  • Messaging (Kafka, RabbitMQ)              │
│  • REST (HTTP Controllers)                  │
└─────────────────────────────────────────────┘
```

### Grammar-Based Approach

Instead of string manipulation, the tool uses a **formal grammar**:

```
Scala Source Code
      ↓ Parse (Scalameta)
Scalameta AST
      ↓ Extract
Abstract Model (IR)
      ↓ Validate
Architectural Rules
      ↓ Render (KotlinPoet)
Kotlin Source Code
```

**Benefits**:
- Type-safe transformations
- Architectural validation
- Consistent code generation
- Language-agnostic patterns

---

## Example Migration

### Input (Scala)

```scala
package com.breuninger.domain.repository

import cats.effect.IO

case class ArtikelId(value: String) extends AnyVal

trait BestandRepository {
  def save(bestand: BestandCreateDocument): IO[Unit]
  def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]]
}
```

### Generated (Kotlin)

```kotlin
package com.breuninger.domain.repository

@JvmInline
value class ArtikelId(val value: String)

interface BestandRepository {
    suspend fun save(bestand: BestandCreateDocument)
    suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>
}

@Repository
class MongoBestandRepository(
    private val mongoTemplate: MongoTemplate
) : BestandRepository {
    override suspend fun save(bestand: BestandCreateDocument) {
        TODO("Implement MongoDB save logic")
    }
    
    override suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument> {
        TODO("Implement MongoDB query logic")
    }
}
```

### You Implement

Fill in the `TODO()` placeholders with your business logic:

```kotlin
override suspend fun save(bestand: BestandCreateDocument) = withContext(Dispatchers.IO) {
    mongoTemplate.save(bestand.toDocument(), "bestand")
}
```

---

## Module Architecture

### Module Structure

The tool is organized into five modules with strict dependency boundaries:

```
modules/
├── ir/         # Core abstract model (no dependencies)
├── parser/     # Scala parsing (depends on ir)
├── renderer/   # Kotlin generation (depends on ir)
├── validator/  # Architecture validation (depends on ir)
└── cli/        # Orchestration (depends on all)
```

### Dependency Rules

- **IR module**: Language-agnostic domain model, NO dependencies
- **Parser, Renderer, Validator**: Depend ONLY on IR
- **CLI module**: Orchestrates all modules

### Verify Module Boundaries

```bash
# Run automated boundary verification
./scripts/verify-module-boundaries.sh
```

**Documentation**:
- [specs/ARCHITECTURE.md](specs/ARCHITECTURE.md) - Module boundaries and contracts
- [specs/MODULE_CONTRACTS.md](specs/MODULE_CONTRACTS.md) - Enforcement rules and validation

---

## Project Status

This is a **design and planning repository**. The actual implementation is tracked separately.

### Current Phase

**Phase 1: Documentation & Design** ✅ Complete
- Grammar specification defined
- Migration strategy documented
- Tool architecture designed
- Test strategy planned

**Phase 2: POC Implementation** 🚧 In Progress
- ✅ Module structure and boundaries defined
- ✅ Abstract model (IR) contracts
- ✅ Public APIs for parser, renderer, validator
- ✅ Automated boundary enforcement
- ⏳ Scalameta parser implementation
- ⏳ KotlinPoet renderer implementation
- ⏳ Architectural validator implementation

**Phase 3: Production Tool** ⏳ Planned
- Spring Boot support
- Batch migration
- CLI tool
- CI/CD integration

---

## Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Language** | Scala 2.13 | Tool implementation (team familiarity) |
| **Scala Parser** | Scalameta 4.8+ | Parse Scala AST |
| **Kotlin Generator** | KotlinPoet 1.15+ | Generate Kotlin code |
| **Build Tool** | SBT | Build grammar tool |
| **Target Language** | Kotlin 1.9+ | Migration target |
| **Target Framework** | Spring Boot 3.2+ | Application framework |
| **Testing** | ScalaTest, Kotest | Unit & integration tests |

---

## Usage

### Building the CLI

```bash
# Compile the project
sbt compile

# Run tests
sbt test

# Build standalone JAR
sbt cli/assembly

# The JAR will be at: modules/cli/target/scala-3.7.4/arch-tool.jar
```

### CLI Commands

#### Help and Version

```bash
# Show help
sbt "cli/run help"
# or with the JAR:
java -jar arch-tool.jar help

# Show version
sbt "cli/run version"
```

#### Parse Command

Parse Scala source code and display the intermediate representation (IR):

```bash
# Parse a file
sbt "cli/run parse src/main/scala/domain/repository/BestandRepository.scala"

# Parse with verbose output
sbt "cli/run parse src/main/scala/domain/UserId.scala --verbose"
```

**Output**: Shows parsed domain models and ports with their types.

#### Validate Command

Validate Scala source against architectural rules:

```bash
# Validate a file
sbt "cli/run validate src/main/scala/ports/UserRepository.scala"

# Validate in strict mode (warnings as errors)
sbt "cli/run validate src/main/scala/domain/User.scala --strict"
```

**Output**: Reports validation errors and warnings.

#### Migrate Command

Migrate a single Scala file to Kotlin:

```bash
# Migrate a single file
sbt "cli/run migrate \
  src/main/scala/domain/repository/BestandRepository.scala \
  src/main/kotlin/domain/repository/BestandRepository.kt"

# Skip validation during migration
sbt "cli/run migrate \
  src/main/scala/domain/User.scala \
  src/main/kotlin/domain/User.kt \
  --skip-validation"
```

**Output**: Generated Kotlin file with equivalent code structure.

#### Migrate Batch Command

Migrate an entire directory of Scala files to Kotlin:

```bash
# Migrate a directory
sbt "cli/run migrate-batch \
  src/main/scala/domain \
  src/main/kotlin/domain"

# Migrate with validation skipped
sbt "cli/run migrate-batch \
  src/main/scala \
  src/main/kotlin \
  --skip-validation"

# Migrate sequentially (not in parallel)
sbt "cli/run migrate-batch \
  src/main/scala \
  src/main/kotlin \
  --sequential"
```

**Features**:
- Preserves directory structure
- Converts `.scala` files to `.kt` files
- Processes files in parallel by default
- Reports progress and summary

### Using the Standalone JAR

After building with `sbt cli/assembly`:

```bash
# Parse
java -jar modules/cli/target/scala-3.7.4/arch-tool.jar parse Domain.scala

# Validate
java -jar modules/cli/target/scala-3.7.4/arch-tool.jar validate Domain.scala --strict

# Migrate single file
java -jar modules/cli/target/scala-3.7.4/arch-tool.jar migrate \
  input.scala \
  output.kt

# Migrate directory
java -jar modules/cli/target/scala-3.7.4/arch-tool.jar migrate-batch \
  src/main/scala/domain \
  src/main/kotlin/domain
```

### Current Implementation Status

**Note**: The CLI infrastructure is complete, but the core parsers and renderers are stub implementations:

- ✅ CLI argument parsing (scopt)
- ✅ Command execution framework
- ✅ Pipeline orchestration
- ✅ File I/O and batch processing
- ⏳ ScalaParser (stub - returns "not implemented")
- ⏳ KotlinRenderer (stub - generates basic structure)
- ⏳ ArchitectureValidator (stub - always passes)

The tool structure is ready, and actual parser/renderer implementations can be added incrementally.

---

## Contributing

### For Architects
Read [specs/ARCHITECTURE_GRAMMAR.md](specs/ARCHITECTURE_GRAMMAR.md) to understand the conceptual model.

### For Developers
Follow [specs/PLAN_GRAMMAR_POC.md](specs/PLAN_GRAMMAR_POC.md) to implement tool components.

### For Testers
Use [specs/TEST_DRIVEN_MIGRATION.md](specs/TEST_DRIVEN_MIGRATION.md) to build the test suite.

### For Spring Developers
Consult [specs/SPRING_BOOT_MIGRATION.md](specs/SPRING_BOOT_MIGRATION.md) for framework integration.

---

## Related Documentation

- **Hexagonal Architecture**: https://alistair.cockburn.us/hexagonal-architecture/
- **Scalameta Guide**: https://scalameta.org/docs/
- **KotlinPoet Guide**: https://square.github.io/kotlinpoet/
- **Spring Boot Kotlin**: https://spring.io/guides/tutorials/spring-boot-kotlin/
- **Kotlin Coroutines**: https://kotlinlang.org/docs/coroutines-overview.html

---

## License

Internal project for Breuninger GmbH.

## Contact

For questions or contributions, contact the Reco team.
