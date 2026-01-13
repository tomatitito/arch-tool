# Application Service Pattern Audit

## Overview

This document audits application service patterns documented in the arch_tool codebase, based on the produkt-assembler example application referenced throughout the migration documentation.

**Note**: No standalone produkt-assembler Scala codebase was found. This audit is based on examples and patterns documented in:
- ARCHITECTURE_GRAMMAR.md
- SPRING_BOOT_MIGRATION.md
- TOOL_SCOPE_SUMMARY.md

## Application Service Location

### Package Structure

Application services reside in the **Application Layer**:

```
com.breuninger.application.service
  └── BestandAssemblerService.scala
```

**Example Reference**: SPRING_BOOT_MIGRATION.md:657

### Layer Hierarchy

```
┌─────────────────────────────────────────────┐
│           Application Layer                 │
│         (Services, Use Cases)               │
│         • BestandAssemblerService           │
└──────────────┬──────────────────────────────┘
               │ depends on ↓
┌──────────────▼──────────────────────────────┐
│           Domain Layer                      │
│     (Ports = Interfaces, Models)            │
│     • BestandRepository (interface)         │
│     • ProduktStammdatenRepository (port)    │
└──────────────┬──────────────────────────────┘
               │ ↑ implemented by
┌──────────────▼──────────────────────────────┐
│           Ports Layer                       │
│  (Adapters = Implementations)               │
│  • MongoBestandRepository                   │
│  • MongoProduktStammdatenRepository         │
└─────────────────────────────────────────────┘
```

**Key Principle**: Application services depend on domain ports (interfaces), never on adapters (implementations).

---

## Application Service Patterns

### 1. Constructor Injection

**Scala Example** (SPRING_BOOT_MIGRATION.md:661-664):

```scala
class BestandAssemblerService(
  bestandRepository: BestandRepository,
  stammdatenRepository: ProduktStammdatenRepository
) {
  // ...
}
```

**Pattern Characteristics**:
- Dependencies declared as constructor parameters
- All dependencies are **domain ports** (interfaces), not adapters
- No infrastructure types (MongoDB, Kafka, etc.) injected directly
- Enables loose coupling and testability

**Kotlin + Spring Boot Migration** (SPRING_BOOT_MIGRATION.md:694-697):

```kotlin
@Service
class BestandAssemblerService(
  private val bestandRepository: BestandRepository,
  private val stammdatenRepository: ProduktStammdatenRepository
) {
  // ...
}
```

**Changes**:
- `@Service` annotation for Spring component scanning
- `private val` for immutable dependencies
- Spring auto-wires dependencies (no explicit DI module needed)

---

### 2. Service Orchestration Logic

**Purpose**: Application services orchestrate multiple domain operations to fulfill use cases.

**Scala Example** (SPRING_BOOT_MIGRATION.md:666-680):

```scala
def assembleBestand(artikelId: ArtikelId): IO[AssembledBestand] = {
  for {
    bestand <- bestandRepository.getByIds(List(artikelId))
      .map(_.headOption)
      .flatMap {
        case Some(b) => IO.pure(b)
        case None => IO.raiseError(NotFoundException(s"Bestand not found"))
      }
    stammdaten <- stammdatenRepository.getById(artikelId)
      .flatMap {
        case Some(s) => IO.pure(s)
        case None => IO.raiseError(NotFoundException(s"Stammdaten not found"))
      }
  } yield AssembledBestand(bestand, stammdaten)
}
```

**Pattern Characteristics**:
1. **Multiple repository calls** - Orchestrates calls to `bestandRepository` and `stammdatenRepository`
2. **Effect composition** - Uses `IO[A]` monad for effect management
3. **Error handling** - Raises domain exceptions (`NotFoundException`)
4. **Domain model assembly** - Combines data from multiple sources into `AssembledBestand`

**Kotlin Migration** (SPRING_BOOT_MIGRATION.md:699-707):

```kotlin
suspend fun assembleBestand(artikelId: ArtikelId): AssembledBestand {
  val bestand = bestandRepository.getByIds(listOf(artikelId)).firstOrNull()
    ?: throw NotFoundException("Bestand not found for $artikelId")

  val stammdaten = stammdatenRepository.getById(artikelId)
    ?: throw NotFoundException("Stammdaten not found for $artikelId")

  return AssembledBestand(bestand, stammdaten)
}
```

**Changes**:
- `suspend fun` instead of `IO[A]` (Kotlin coroutines)
- Imperative style with elvis operator (`?:`)
- Simplified error handling with exceptions

---

### 3. Dependencies (What Services Inject)

**Valid Dependencies** (from domain layer):
- ✅ Repository ports (interfaces)
- ✅ Domain services (other application services)
- ✅ Domain models (value objects, entities)

**Invalid Dependencies** (architectural violation):
- ❌ Adapters (implementations like `MongoBestandRepository`)
- ❌ Infrastructure types (`MongoTemplate`, `KafkaProducer`)
- ❌ Framework-specific types (Spring, http4s)

**Validation Rule** (SPRING_BOOT_MIGRATION.md:584-593):

```scala
// Rule: Services must inject only domain ports, not adapters
def validateServicesUsePorts(services: List[Service]): List[ValidationError] = {
  services.flatMap { service =>
    service.dependencies.filter { dep =>
      isAdapterType(dep.typeName)
    }.map { dep =>
      ValidationError.ServiceInjectsAdapter(service, dep)
    }
  }
}
```

---

## How Application Services Differ from Ports/Adapters

### Comparison Matrix

| Aspect | Application Service | Port (Interface) | Adapter (Implementation) |
|--------|-------------------|------------------|-------------------------|
| **Layer** | Application | Domain | Ports |
| **Purpose** | Orchestrate use cases | Define contracts | Implement infrastructure |
| **Dependencies** | Depends on ports | No dependencies | Depends on ports + infrastructure |
| **Naming** | `*Service` | `*Repository`, `*Gateway` | `Mongo*Repository`, `Kafka*Producer` |
| **Business Logic** | Yes (orchestration) | No (just signature) | No (just I/O) |
| **Infrastructure** | No | No | Yes (MongoDB, Kafka, HTTP) |
| **Spring Annotation** | `@Service` | None (pure interface) | `@Repository`, `@Component` |
| **Example** | `BestandAssemblerService` | `BestandRepository` | `MongoBestandRepository` |

### Detailed Differences

#### 1. Architectural Layer

**Application Service** (ARCHITECTURE_GRAMMAR.md:118-121):
```ebnf
Application ::= Service*
Service ::= Identifier PortDependency+
PortDependency ::= Port
```

**Port** (ARCHITECTURE_GRAMMAR.md:94-96):
```ebnf
Port ::= PortDefinition Method+
PortDefinition ::= Identifier
Method ::= Identifier Parameter* ReturnType
```

**Adapter** (ARCHITECTURE_GRAMMAR.md:124-129):
```ebnf
Adapter ::= PersistenceAdapter | MessagingAdapter | RestAdapter
PersistenceAdapter ::= Identifier DatabaseDependency "implements" Port
MessagingAdapter ::= Identifier MessageQueueDependency "implements" Port
```

#### 2. Dependency Direction

```
Application Services → Domain Ports ← Adapters
```

- **Services** depend on ports (interfaces)
- **Adapters** implement ports (interfaces)
- **Ports** have no dependencies (pure domain)

**Invalid Example** (ARCHITECTURE_GRAMMAR.md:455-460):
```
✗ Service: MongoBestandService
  Location: ports/persistence/
  Error: Services must be in application layer, not ports layer
```

#### 3. Responsibility

**Application Service**:
- Orchestrates multiple domain operations
- Implements use case logic
- Coordinates repository calls
- Assembles complex domain models
- Example: Fetch bestand + stammdaten, combine into `AssembledBestand`

**Port (Interface)**:
- Defines contract for infrastructure
- No implementation
- Pure domain types only
- Example: `BestandRepository` with `save()`, `getByIds()`

**Adapter (Implementation)**:
- Implements port interface
- Handles infrastructure I/O (MongoDB queries, Kafka messages, HTTP calls)
- Translates between domain models and infrastructure formats
- Example: `MongoBestandRepository` with actual MongoDB queries

#### 4. Code Location Validation

**Valid Structure**:
```
src/main/scala/com/breuninger/
├── domain/
│   └── repository/
│       └── BestandRepository.scala      # Port (interface)
├── application/
│   └── service/
│       └── BestandAssemblerService.scala # Application Service
└── ports/
    └── persistence/
        └── bestand/
            └── MongoBestandRepository.scala # Adapter
```

**Invalid Structure** (ARCHITECTURE_GRAMMAR.md:455-460):
```
src/main/scala/com/breuninger/ports/
└── persistence/
    └── MongoBestandService.scala  # ❌ Service in ports layer
```

#### 5. Type Dependencies

**Application Service** - Only domain types:
```scala
class BestandAssemblerService(
  bestandRepository: BestandRepository,        // ✅ Domain port
  stammdatenRepository: ProduktStammdatenRepository // ✅ Domain port
) {
  def assembleBestand(artikelId: ArtikelId): IO[AssembledBestand]
  //                           ^^^^^^^^^        ^^^^^^^^^^^^^^^^
  //                           Domain type      Domain type
}
```

**Adapter** - Infrastructure types allowed:
```scala
class MongoBestandRepository(
  bestandCollection: MongoCollection[Document] @@ BestandTag  // ✅ Infrastructure
) extends BestandRepository {
  // ...
}
```

**Port** - Only domain types (ARCHITECTURE_GRAMMAR.md:462-468):
```scala
trait BestandRepository {
  def save(doc: BestandCreateDocument): IO[Unit]  // ✅ Domain type
  // ❌ INVALID: def save(doc: MongoDocument): IO[Unit]
}
```

---

## Grammar Definition for Application Services

From ARCHITECTURE_GRAMMAR.md:118-121:

```ebnf
(* ===== APPLICATION LAYER ===== *)
Application ::= Service*
Service ::= Identifier PortDependency+
PortDependency ::= Port
```

**Interpretation**:
- An application consists of zero or more services
- Each service has an identifier (name)
- Each service has one or more port dependencies
- Port dependencies reference domain ports (interfaces)

---

## Spring Boot Migration Specifics

### Spring Stereotypes

**Application Service**: `@Service`
```kotlin
@Service
class BestandAssemblerService(
  private val bestandRepository: BestandRepository
) {
  suspend fun assembleBestand(artikelId: ArtikelId): AssembledBestand
}
```

**Adapter**: `@Repository` or `@Component`
```kotlin
@Repository
class MongoBestandRepository(
  private val mongoTemplate: ReactiveMongoTemplate
) : BestandRepository {
  override suspend fun save(bestand: BestandCreateDocument)
}
```

**Port**: No annotation (pure interface)
```kotlin
interface BestandRepository {
  suspend fun save(bestand: BestandCreateDocument)
}
```

### Validation Rule (SPRING_BOOT_MIGRATION.md:545-552)

```scala
// Rule: Domain layer must not have Spring annotations
def validateDomainLayerPure(model: ArchitectureModel): List[ValidationError] = {
  model.ports.filter { port =>
    port.springAnnotations.nonEmpty
  }.map { port =>
    ValidationError.DomainLayerHasSpringAnnotation(port)
  }
}
```

---

## Summary

### Application Service Characteristics

1. **Location**: `application/service/` package
2. **Dependencies**: Constructor injection of domain ports only
3. **Responsibility**: Orchestrate domain operations for use cases
4. **Naming**: `*Service` suffix (e.g., `BestandAssemblerService`)
5. **Effect Type**: `IO[A]` in Scala, `suspend fun` in Kotlin
6. **Spring Annotation**: `@Service` (in Spring Boot migration)
7. **Layer**: Application layer (between domain and ports)

### Key Differences from Ports/Adapters

| **Application Service** | **Port** | **Adapter** |
|------------------------|----------|-------------|
| Orchestrates use cases | Defines contract | Implements infrastructure |
| Depends on ports | No dependencies | Depends on ports + infra |
| Business logic | No logic | I/O logic |
| `@Service` | No annotation | `@Repository` |
| Application layer | Domain layer | Ports layer |

### Architectural Rules

1. **Services must be in application layer** (not domain or ports)
2. **Services depend only on domain ports** (never adapters)
3. **Services use domain types only** (no infrastructure types)
4. **Domain ports have no Spring annotations** (keep domain pure)
5. **Adapters implement ports** (one-to-many relationship)

---

## References

- ARCHITECTURE_GRAMMAR.md:77-226 - Abstract grammar and architectural constraints
- SPRING_BOOT_MIGRATION.md:95-122 - Application layer with Spring Boot
- SPRING_BOOT_MIGRATION.md:654-709 - Service migration example
- SPRING_BOOT_MIGRATION.md:533-595 - Spring architectural validation rules
