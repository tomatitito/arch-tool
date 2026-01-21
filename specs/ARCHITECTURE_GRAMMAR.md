# Formal Grammar for Hexagonal Architecture

> **Note**: The formal EBNF grammar specification is available in [`architecture-grammar.ebnf`](./architecture-grammar.ebnf). This document provides explanations, examples, and language-specific mappings.

## Overview

This document defines a formal, language-agnostic grammar for describing the hexagonal architecture (ports and adapters pattern) used in the produkt-assembler codebase. By separating abstract architectural concepts from concrete language syntax, this grammar enables:

- **Validation** - Enforce architectural constraints across languages
- **Code generation** - Generate code in multiple target languages (Scala, Kotlin, etc.)
- **Migration** - Safely migrate between languages while preserving architecture
- **Documentation** - Auto-generate architecture diagrams and guides

The key insight: **grammars describe architectural patterns, not language syntax**. A Repository port is a Repository whether written in Scala, Kotlin, or any other language.

## Code Generation Scope

### What the Grammar Tool Generates vs. What You Implement

| Component | Generated Automatically | Implemented Manually |
|-----------|------------------------|----------------------|
| **Port Interfaces** | ✅ Complete interfaces with all method signatures | - |
| **Domain Models** | ✅ Value objects, entities, sealed hierarchies | - |
| **Type Mappings** | ✅ Scala → Kotlin type conversions | - |
| **Adapter Skeletons** | ✅ Class declarations implementing ports | ❌ Business logic inside methods |
| **Service Classes** | 🟡 Class structure with dependencies | ❌ Orchestration and business rules |
| **Controllers** | 🟡 Endpoint signatures with annotations | ❌ Request/response handling logic |
| **Tests** | 🟡 Test class skeletons | ❌ Test assertions and scenarios |

**Legend:**
- ✅ Fully automated
- 🟡 Skeleton/structure only
- ❌ Requires manual implementation

### Why Business Logic Cannot Be Automated

The grammar tool operates at the **architectural level**, not the **implementation level**. It understands:
- What ports exist (BestandRepository)
- What methods they define (save, getByIds)
- What types they use (BestandCreateDocument, ArtikelId)

But it **does not understand**:
- How to query MongoDB efficiently
- What business rules apply to data validation
- How to handle errors and retries
- What performance optimizations are needed
- How to transform data between layers

These implementation details require domain knowledge and cannot be mechanically derived from interface definitions.

### The Value Proposition

Even though implementations are manual, the grammar tool provides significant value:

1. **Eliminates Tedious Work** - No manual translation of interfaces and data classes
2. **Ensures Consistency** - All generated code follows the same patterns
3. **Enforces Architecture** - Validates that implementations respect layer boundaries
4. **Provides Safety** - Type-checked migrations prevent subtle bugs
5. **Speeds Migration** - Focus on business logic, not boilerplate

**Bottom line:** The tool handles the mechanical 30% (interfaces, models, structure) so you can focus on the creative 70% (business logic, optimizations, error handling).

## Architecture Structure

The codebase has three clear layers:

### 1. Domain Layer (`domain/repository/`) - Port Interfaces (Contracts)
- `BestandRepository`
- `MessageRepository` 
- `PreisV2Repository`
- `ProduktStammdatenRepository`
- `FarbartikelKategorieRepository`

### 2. Ports Layer - Adapter Implementations
- **Persistence** (`ports/persistence/`) - MongoDB implementations
- **Kafka** (`ports/kafka/`) - Kafka messaging implementations
- **REST** (`ports/rest/`) - HTTP API implementations

### 3. Application Layer - Use Cases and Orchestration
- Services that orchestrate domain logic using repository ports

---

## Abstract Grammar (Language-Agnostic)

This grammar describes architectural concepts independent of any programming language.

```ebnf
(* ===== TOP-LEVEL SYSTEM ===== *)
System ::= Domain Application Ports

(* ===== DOMAIN LAYER ===== *)
Domain ::= Port* DomainModel*

(* Port: Interface defining a contract *)
Port ::= PortDefinition Method+
PortDefinition ::= Identifier
Method ::= Identifier Parameter* ReturnType

(* Domain Models *)
DomainModel ::= ValueObject | Entity | Aggregate | Enumeration

ValueObject ::= Identifier Field+
Entity ::= Identifier Field+
Aggregate ::= Identifier Field+ Invariant*
Enumeration ::= Identifier Variant+

Field ::= Identifier Type
Variant ::= Identifier Field*
Invariant ::= Constraint

(* Types *)
Type ::= PrimitiveType | DomainType | GenericType | EffectType
PrimitiveType ::= "String" | "Int" | "Boolean" | "Long" | "Double"
DomainType ::= Identifier
GenericType ::= Identifier "<" Type+ ">"
EffectType ::= EffectConstructor "<" Type ">"
EffectConstructor ::= "Effect" | "IO" | "Async"

(* ===== APPLICATION LAYER ===== *)
Application ::= Service*
Service ::= Identifier PortDependency+
PortDependency ::= Port

(* ===== PORTS LAYER (ADAPTERS) ===== *)
Ports ::= Adapter+
Adapter ::= PersistenceAdapter | MessagingAdapter | RestAdapter

PersistenceAdapter ::= Identifier DatabaseDependency "implements" Port
MessagingAdapter ::= Identifier MessageQueueDependency "implements" Port
RestAdapter ::= Identifier HttpDependency "implements" Port

DatabaseDependency ::= "MongoCollection" | "PostgresConnection" | "RedisClient"
MessageQueueDependency ::= "KafkaProducer" | "KafkaConsumer" | "RabbitMQChannel"
HttpDependency ::= "HttpRoutes" | "RestController"

(* ===== DEPENDENCY INJECTION ===== *)
DependencyInjection ::= Module+
Module ::= Identifier ModuleDependency*
ModuleDependency ::= "includes" Module

(* ===== TYPE TAGGING (Phantom Types) ===== *)
TypeTag ::= Identifier "Tag"
TaggedType ::= Type "tagged" TypeTag
```

---

## Concrete Syntax Mappings

The abstract grammar maps to concrete language syntax through renderers.

### Scala Syntax Mapping

```ebnf
(* Scala concrete syntax *)
ScalaPort ::= "trait" Identifier "{" ScalaMethod+ "}"
ScalaMethod ::= "def" Identifier "(" ScalaParameter* ")" ":" ScalaType

ScalaValueObject ::= "case class" Identifier "(" ScalaField+ ")" "extends AnyVal"
ScalaEntity ::= "case class" Identifier "(" ScalaField+ ")"
ScalaEnumeration ::= "sealed trait" Identifier | "enum" Identifier

ScalaAdapter ::= "class" Identifier "(" ScalaDependency+ ")" "extends" ScalaPort

ScalaModule ::= "trait" Identifier "extends" ScalaModule*

ScalaTypeTag ::= "trait" Identifier "Tag"
ScalaTaggedType ::= Type "@@" ScalaTypeTag

ScalaEffect ::= "IO[" Type "]"
```

### Kotlin Syntax Mapping

```ebnf
(* Kotlin concrete syntax *)
KotlinPort ::= "interface" Identifier "{" KotlinMethod+ "}"
KotlinMethod ::= "suspend fun" Identifier "(" KotlinParameter* ")" ":" KotlinType

KotlinValueObject ::= "@JvmInline value class" Identifier "(" KotlinField ")"
KotlinEntity ::= "data class" Identifier "(" KotlinField+ ")"
KotlinEnumeration ::= "sealed interface" Identifier | "enum class" Identifier

KotlinAdapter ::= "class" Identifier "(" KotlinDependency+ ")" ":" KotlinPort

KotlinModule ::= "object" Identifier ":" KotlinModule*

KotlinTypeTag ::= "interface" Identifier "Tag"
KotlinTaggedType ::= Type  (* Uses inline value classes instead *)

KotlinEffect ::= "suspend fun" | "Effect<" Type ">"
```

---

## Architectural Constraints (Semantic Rules)

These rules apply regardless of implementation language.

### Rule 1: Dependency Direction
```
Application → Domain ← Ports
    ↓          ↓        ↓
Services   Repositories Adapters
```

**Constraint**: Ports (adapters) depend on Domain (ports), never the reverse.

### Rule 2: Port Implementation
```
Port (Interface)
    ↓
Adapter (Implementation)
    ↓
Infrastructure (Mongo, Kafka, HTTP)
```

**Constraint**: Every Port must have at least one Adapter implementation.

### Rule 3: Layer Isolation
```
domain/     - No dependencies on ports/ or application/
application/ - Depends on domain/, not on ports/
ports/      - Depends on domain/, not on application/
```

**Constraint**: Domain layer must be pure (no infrastructure dependencies).

### Rule 4: Type Safety
```
Port methods must use Domain types only
Port methods must not reference infrastructure types
```

**Constraint**: No MongoDB types, Kafka types, or HTTP types in Port signatures.

### Rule 5: Module Composition
```
RootModule includes ApplicationModule
RootModule includes PortModule
PortModule includes (PersistenceModule | KafkaModule | RestModule)+
```

**Constraint**: Dependency injection follows the layer hierarchy.

---

## Concrete Examples

### Example 1: Port Definition

**Abstract Model:**
```
Port: BestandRepository
Methods:
  - save(bestand: BestandCreateDocument) -> Effect[Unit]
  - getByIds(ids: List[ArtikelId]) -> Effect[List[BestandCreateDocument]]
  - deleteBatch(bestaende: List[BestandDeleteDocument]) -> Effect[Unit]
```

**Scala Rendering:**
```scala
// domain/repository/BestandRepository.scala
trait BestandRepository {
  def save(bestand: BestandCreateDocument): IO[Unit]
  def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]]
  def deleteBatch(bestaende: List[BestandDeleteDocument]): IO[Unit]
}
```

**Kotlin Rendering:**
```kotlin
// domain/repository/BestandRepository.kt
interface BestandRepository {
  suspend fun save(bestand: BestandCreateDocument)
  suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>
  suspend fun deleteBatch(bestaende: List<BestandDeleteDocument>)
}
```

### Example 2: Domain Models

**Abstract Model:**
```
ValueObject: ArtikelId
  Field: value: String

Entity: BestandCreateDocument
  Field: id: ArtikelId
  Field: quantity: Int
  Field: warehouse: String
```

**Scala Rendering:**
```scala
// domain/model/ArtikelId.scala
case class ArtikelId(value: String) extends AnyVal

// domain/model/BestandCreateDocument.scala
case class BestandCreateDocument(
  id: ArtikelId,
  quantity: Int,
  warehouse: String
)
```

**Kotlin Rendering:**
```kotlin
// domain/model/ArtikelId.kt
@JvmInline
value class ArtikelId(val value: String)

// domain/model/BestandCreateDocument.kt
data class BestandCreateDocument(
  val id: ArtikelId,
  val quantity: Int,
  val warehouse: String
)
```

### Example 3: Sealed Hierarchies

**Abstract Model:**
```
Enumeration: Result
  Variant: Success(value: String)
  Variant: Failure(error: String)
```

**Scala Rendering:**
```scala
sealed trait Result
case class Success(value: String) extends Result
case class Failure(error: String) extends Result
```

**Kotlin Rendering:**
```kotlin
sealed interface Result
data class Success(val value: String) : Result
data class Failure(val error: String) : Result
```

### Example 4: Adapter Implementation (Skeleton Only)

**Abstract Model:**
```
PersistenceAdapter: MongoBestandRepository
  Dependency: MongoCollection<Document> tagged BestandTag
  Implements: BestandRepository
```

**What the Tool Generates (Scala):**
```scala
// ports/persistence/bestand/MongoBestandRepository.scala
// ✅ Generated: Class structure, constructor, method signatures
class MongoBestandRepository(
  bestandDocumentCollection: MongoCollection[Document] @@ BestandTag
) extends BestandRepository {
  // ❌ Manual: You implement the actual MongoDB logic
  override def save(bestand: BestandCreateDocument): IO[Unit] = ???
  override def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]] = ???
  override def deleteBatch(bestaende: List[BestandDeleteDocument]): IO[Unit] = ???
}
```

**What the Tool Generates (Kotlin):**
```kotlin
// ports/persistence/bestand/MongoBestandRepository.kt
// ✅ Generated: Class structure, constructor, method signatures
class MongoBestandRepository(
  private val bestandDocumentCollection: MongoCollection<Document>
) : BestandRepository {
  // ❌ Manual: You implement the actual MongoDB logic
  override suspend fun save(bestand: BestandCreateDocument) { 
    TODO("Implement: Save document to MongoDB collection")
  }
  
  override suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument> { 
    TODO("Implement: Query MongoDB by IDs and map results")
  }
  
  override suspend fun deleteBatch(bestaende: List<BestandDeleteDocument>) { 
    TODO("Implement: Delete multiple documents from MongoDB")
  }
}
```

**What You Implement Manually:**

The `???` (Scala) and `TODO()` (Kotlin) placeholders are where **you write the business logic**. For example:

```kotlin
override suspend fun save(bestand: BestandCreateDocument) = withContext(Dispatchers.IO) {
  val document = Document().apply {
    append("_id", bestand.id.value)
    append("quantity", bestand.quantity)
    append("warehouse", bestand.warehouse)
  }
  bestandDocumentCollection.insertOne(document)
}

override suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument> = 
  withContext(Dispatchers.IO) {
    val filter = Filters.`in`("_id", ids.map { it.value })
    bestandDocumentCollection.find(filter)
      .toList()
      .map { doc ->
        BestandCreateDocument(
          id = ArtikelId(doc.getString("_id")),
          quantity = doc.getInteger("quantity"),
          warehouse = doc.getString("warehouse")
        )
      }
  }
```

**Key Point:** The grammar tool generates the **architectural skeleton** (class structure, interfaces, type-safe signatures) but **you implement the business logic** (MongoDB queries, error handling, data transformation).

### Example 5: Type Tags

**Abstract Model:**
```
TypeTag: BestandTag
TaggedType: MongoCollection<Document> tagged BestandTag
```

**Scala Rendering:**
```scala
trait BestandTag
type TaggedBestandCollection = MongoCollection[Document] @@ BestandTag
```

**Kotlin Rendering:**
```kotlin
// Kotlin uses inline value classes for type safety
@JvmInline
value class BestandCollection(val collection: MongoCollection<Document>)
```

---

## Validation Examples

The grammar enforces these validation rules across all languages:

### ✓ Valid - Port Implemented by Adapter
```
Port: BestandRepository (domain layer)
Adapter: MongoBestandRepository (ports layer)
✓ Adapter implements Port
✓ Adapter in correct layer
✓ Port uses only domain types
```

### ✗ Invalid - Service in Port Layer
```
Service: MongoBestandService
Location: ports/persistence/
✗ Services must be in application layer
```

### ✗ Invalid - Port Depends on Infrastructure
```
Port: BestandRepository
Method: save(doc: MongoDocument): Effect[Unit]
✗ Port method uses infrastructure type (MongoDocument)
✗ Should use domain type (BestandCreateDocument)
```

### ✗ Invalid - Wrong Dependency Direction
```
Domain: BestandRepository imports MongoBestandRepository
✗ Domain cannot depend on Ports
✗ Dependency arrow points wrong direction
```

---

## Scala-to-Kotlin Migration Plan

### Migration Strategy Overview

The grammar enables safe, incremental migration by:
1. Parsing Scala code into abstract model
2. Validating architectural constraints
3. Generating equivalent Kotlin code
4. Validating Kotlin code against same constraints
5. Ensuring behavioral equivalence

### Phase 1: Dual Grammar Support

**Goal**: Support both Scala and Kotlin in the same grammar

**Steps**:
1. Extend grammar parser to handle both languages
2. Create validators that work on abstract model
3. Ensure architectural rules are language-agnostic
4. Build bidirectional parsers (Scala ↔ Abstract ↔ Kotlin)

**Deliverables**:
```
┌──────────┐         ┌──────────────┐         ┌──────────┐
│  Scala   │ ──────> │   Abstract   │ ──────> │  Kotlin  │
│  Source  │         │    Model     │         │  Source  │
└──────────┘         └──────────────┘         └──────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │  Validation  │
                     │    Rules     │
                     └──────────────┘
```

**Timeline**: 2-3 weeks
**Risk**: Low - purely additive work

---

### Phase 2: Type System Mapping

**Goal**: Define precise mappings between Scala and Kotlin types

**Mappings**:

| Concept | Scala | Kotlin |
|---------|-------|--------|
| Value Object | `case class X(v: T) extends AnyVal` | `@JvmInline value class X(val v: T)` |
| Entity | `case class X(...)` | `data class X(...)` |
| Sum Type | `sealed trait X` | `sealed interface X` |
| Enumeration | `enum X` | `enum class X` |
| Port | `trait X` | `interface X` |
| Effect | `IO[A]` | `suspend fun` or `Effect<A>` |
| Tagged Type | `Type @@ Tag` | `value class Tagged(val value: Type)` |
| Optional | `Option[A]` | `A?` |
| Collection | `List[A]` | `List<A>` |
| Module | `trait XModule` | `object XModule` |

**Effect System Migration**:

```scala
// Scala with cats-effect
def save(doc: Document): IO[Unit]
def getById(id: Id): IO[Option[Document]]
```

```kotlin
// Kotlin Option 1: Coroutines (recommended)
suspend fun save(doc: Document)
suspend fun getById(id: Id): Document?

// Kotlin Option 2: Arrow Effect
fun save(doc: Document): Effect<Unit>
fun getById(id: Id): Effect<Document?>
```

**Recommendation**: Use Kotlin coroutines (`suspend fun`) for simplicity and ecosystem support.

**Timeline**: 1-2 weeks
**Risk**: Medium - effect system differences require careful handling

---

### Phase 3: Incremental Migration

**Goal**: Migrate one module at a time while maintaining system functionality

**Migration Order**:

```
1. Domain Models (simplest, no dependencies)
   ├── Value Objects (ArtikelId, PreisV2Id, etc.)
   ├── Entities (BestandCreateDocument, etc.)
   └── Enumerations

2. Domain Ports (interfaces only, no implementations)
   ├── BestandRepository
   ├── MessageRepository
   ├── PreisV2Repository
   └── Other repositories

3. Adapters (implementations)
   ├── Persistence adapters (MongoDB)
   ├── Messaging adapters (Kafka)
   └── REST adapters (HTTP)

4. Application Services
   ├── Service orchestration logic
   └── Business logic

5. Dependency Injection
   ├── Module definitions
   └── Wiring
```

**Interop Strategy**:

Kotlin has excellent Java interop, allowing gradual migration:

```kotlin
// Kotlin code can call Scala code
val scalaRepository: ScalaBestandRepository = ...
scalaRepository.save(document) // Works via Java interop

// Scala code can call Kotlin code
val kotlinRepository: KotlinBestandRepository = ...
kotlinRepository.save(document) // Works via Java interop
```

**Per-Module Migration Steps**:

1. **Parse** existing Scala code → Abstract model
2. **Validate** abstract model against architectural rules
3. **Generate** Kotlin code from abstract model
4. **Compile** both Scala and Kotlin versions
5. **Test** behavioral equivalence
6. **Deploy** with feature flag (if needed)
7. **Remove** Scala version once stable

**Timeline**: 8-12 weeks (depends on codebase size)
**Risk**: Medium - requires careful testing at each step

---

### Phase 4: Code Generation Tooling

**Goal**: Automate migration with grammar-based code generator

**Tool Architecture**:

```
┌───────────────────────────��─────────────────────┐
│           Grammar-Based Code Generator          │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌──────────┐      ┌──────────────┐            │
│  │  Scala   │ ───> │   Parser     │            │
│  │  Parser  │      │   (Scalameta)│            │
│  └──────────┘      └───────┬──────┘            │
│                            │                    │
│  ┌──────────┐      ┌───────▼──────┐            │
│  │  Kotlin  │ ───> │   Abstract   │            │
│  │  Parser  │      │    Model     │            │
│  └──────────┘      │     (IR)     │            │
│                    └───────┬──────┘            │
│                            │                    │
│                    ┌───────▼──────┐            │
│                    │  Validation  │            │
│                    │    Engine    │            │
│                    └───────┬──────┘            │
│                            │                    │
│           ┌────────────────┴────────────────┐  │
│           │                                  │  │
│    ┌──────▼──────┐                  ┌───────▼──────┐
│    │   Scala     │                  │   Kotlin     │
│    │  Renderer   │                  │  Renderer    │
│    └─────────────┘                  └──────────────┘
│                                                 │
└─────────────────────────────────────────────────┘
```

**CLI Tool Interface**:

```bash
# Parse Scala code and validate architecture
./arch-tool validate --source src/main/scala

# Generate Kotlin from Scala
./arch-tool migrate --input src/main/scala/domain/repository/BestandRepository.scala \
                    --output src/main/kotlin/domain/repository/BestandRepository.kt \
                    --target kotlin

# Generate Scala from abstract model (for testing)
./arch-tool generate --model domain-model.yaml --target scala

# Validate both implementations
./arch-tool validate --scala src/main/scala --kotlin src/main/kotlin

# Generate architecture diagrams
./arch-tool diagram --output architecture.mermaid
```

**DSL Option (Alternative to parsing)**:

Instead of parsing Scala, define architecture in YAML:

```yaml
# domain/repository/bestand.yaml
port:
  name: BestandRepository
  package: com.breuninger.domain.repository
  methods:
    - name: save
      params:
        - name: bestand
          type: BestandCreateDocument
      returns: Effect[Unit]
    
    - name: getByIds
      params:
        - name: ids
          type: List[ArtikelId]
      returns: Effect[List[BestandCreateDocument]]

models:
  - name: ArtikelId
    type: value_object
    fields:
      - name: value
        type: String
  
  - name: BestandCreateDocument
    type: entity
    fields:
      - name: id
        type: ArtikelId
      - name: quantity
        type: Int
      - name: warehouse
        type: String
```

Then generate both Scala and Kotlin:

```bash
./arch-tool generate --model domain/repository/bestand.yaml \
                     --targets scala,kotlin
```

**Timeline**: 4-6 weeks
**Risk**: Medium - tooling complexity

---

### Phase 5: Testing and Validation

**Goal**: Ensure architectural and behavioral equivalence

**Validation Layers**:

1. **Architectural Validation**
   - Same ports in both languages
   - Same adapters implementing same ports
   - Same dependency directions
   - Same layer boundaries

2. **Type-Level Validation**
   - Domain models equivalent
   - Method signatures equivalent
   - Effect types compatible

3. **Behavioral Validation**
   - Property-based testing
   - Dual-implementation testing
   - Integration tests with both versions

**Example Architecture Test** (using ArchUnit for Kotlin):

```kotlin
@Test
fun `all repository interfaces must be in domain package`() {
    classes()
        .that().areInterfaces()
        .and().haveSimpleNameEndingWith("Repository")
        .should().resideInPackage("..domain.repository..")
        .check(kotlinClasses)
}

@Test
fun `domain layer must not depend on infrastructure`() {
    noClasses()
        .that().resideInPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..mongo..", "..kafka..", "..http..")
        .check(kotlinClasses)
}

@Test
fun `all adapters must implement exactly one port`() {
    classes()
        .that().resideInPackage("..ports..")
        .and().areNotInterfaces()
        .should(ImplementExactlyOnePort())
        .check(kotlinClasses)
}
```

**Behavioral Equivalence Testing**:

```kotlin
class MigrationEquivalenceTest {
    @Test
    fun `Scala and Kotlin implementations behave identically`() {
        val scalaRepo: BestandRepository = ScalaMongoBestandRepository(mongoCollection)
        val kotlinRepo: BestandRepository = KotlinMongoBestandRepository(mongoCollection)
        
        val testDoc = BestandCreateDocument(
            id = ArtikelId("123"),
            quantity = 10,
            warehouse = "MAIN"
        )
        
        // Test save
        scalaRepo.save(testDoc).unsafeRunSync()
        kotlinRepo.save(testDoc) // suspend function
        
        // Test retrieve
        val scalaResult = scalaRepo.getByIds(listOf(ArtikelId("123"))).unsafeRunSync()
        val kotlinResult = kotlinRepo.getByIds(listOf(ArtikelId("123")))
        
        assertEquals(scalaResult, kotlinResult)
    }
}
```

**Timeline**: 2-3 weeks (ongoing during migration)
**Risk**: Low - critical for safe migration

---

### Phase 6: Dependency and Build Migration

**Goal**: Migrate build system and dependencies

**Build System Migration**:

| Aspect | Scala (SBT) | Kotlin (Gradle) |
|--------|-------------|-----------------|
| Build Tool | `build.sbt` | `build.gradle.kts` |
| Compiler | `scalac` | `kotlinc` |
| Effect Library | cats-effect | kotlinx-coroutines or Arrow |
| MongoDB Driver | mongo-scala-driver | mongo-java-driver (Kotlin) |
| Kafka Client | kafka-scala | kafka-clients (Kotlin) |
| HTTP Framework | http4s | ktor or http4k |
| Test Framework | ScalaTest | JUnit 5 + Kotest |
| Serialization | circe | kotlinx-serialization |

**Dependency Migration Example**:

```scala
// build.sbt (Scala)
libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "3.5.0",
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.9.0",
  "org.apache.kafka" %% "kafka" % "3.4.0",
  "org.http4s" %% "http4s-dsl" % "0.23.18"
)
```

```kotlin
// build.gradle.kts (Kotlin)
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.0")
    implementation("org.apache.kafka:kafka-clients:3.6.0")
    implementation("io.ktor:ktor-server-core:2.3.5")
    
    // Alternative: Arrow for functional programming
    // implementation("io.arrow-kt:arrow-core:1.2.0")
    // implementation("io.arrow-kt:arrow-fx-coroutines:1.2.0")
}
```

**Timeline**: 1-2 weeks
**Risk**: Low - straightforward dependency mapping

---

### Migration Risk Assessment

| Phase | Risk Level | Mitigation Strategy |
|-------|-----------|---------------------|
| Phase 1: Dual Grammar | Low | Additive work, no production impact |
| Phase 2: Type Mapping | Medium | Extensive testing, property-based tests |
| Phase 3: Incremental Migration | Medium | Module-by-module, feature flags, rollback plan |
| Phase 4: Code Generation | Medium | Manual review of generated code, comprehensive tests |
| Phase 5: Testing | Low | Critical for safety, invest heavily here |
| Phase 6: Build Migration | Low | Standard practice, well-documented |

---

### Success Criteria

The migration is successful when:

1. ✓ All architectural rules validated in both languages
2. ✓ All ports have equivalent Kotlin implementations
3. ✓ All adapters migrated and tested
4. ✓ Behavioral equivalence tests pass 100%
5. ✓ Performance metrics within acceptable range
6. ✓ Build and deployment pipelines functional
7. ✓ Documentation updated
8. ✓ Team trained on Kotlin codebase

---

### Rollback Strategy

If migration encounters critical issues:

1. **Immediate Rollback**: Feature flags allow instant switch to Scala
2. **Gradual Rollback**: Migrate failing modules back to Scala
3. **Parallel Running**: Keep both versions running, compare outputs
4. **Data Integrity**: Ensure no data corruption during rollback

---

## Grammar-Based Code Generation

### Architecture-as-Code DSL

Define architecture once, generate multiple targets:

```yaml
# architecture.yaml
system:
  name: produkt-assembler
  layers:
    - domain
    - application
    - ports

domain:
  ports:
    - name: BestandRepository
      package: com.breuninger.domain.repository
      methods:
        - name: save
          params:
            - name: bestand
              type: BestandCreateDocument
          returns: Effect[Unit]
  
  models:
    - name: ArtikelId
      type: value_object
      package: com.breuninger.domain.model
      fields:
        - name: value
          type: String
```

**Generate Scala**:
```bash
./arch-tool generate --model architecture.yaml --target scala --output src/main/scala
```

**Generate Kotlin**:
```bash
./arch-tool generate --model architecture.yaml --target kotlin --output src/main/kotlin
```

**Generate Tests**:
```bash
./arch-tool generate --model architecture.yaml --target kotlin-tests --output src/test/kotlin
```

**Generate Documentation**:
```bash
./arch-tool generate --model architecture.yaml --target mermaid --output docs/architecture.md
```

---

## Benefits of Grammar-Based Migration

### 1. Safety
- Architectural rules enforced mechanically
- Type-level validation catches errors early
- Behavioral equivalence tests prevent regressions

### 2. Speed
- Automated code generation reduces manual work
- Parallel migration of independent modules
- Consistent patterns across codebase

### 3. Maintainability
- Single source of truth (abstract model)
- Changes propagate to all targets
- Documentation auto-generated

### 4. Flexibility
- Support multiple target languages
- Easy to add new patterns
- Incremental adoption possible

---

## Tools and Technologies

### For Grammar Implementation

**Option 1: ANTLR + Custom Renderers**
- Define grammar in ANTLR
- Parse Scala/Kotlin into AST
- Transform to abstract model
- Render to target language

**Option 2: Scalameta + Kotlin Poet**
- Use Scalameta to parse Scala
- Use Kotlin Poet to generate Kotlin
- Build transformation layer

**Option 3: Domain-Specific Language**
- Define architecture in YAML/JSON
- Skip parsing, generate directly
- Simpler, more maintainable

**Recommendation**: Start with Option 3 (DSL), migrate to Option 2 for production.

### For Validation

**ArchUnit (Kotlin)**
- Runtime architecture tests
- Express rules in code
- Integrates with JUnit

**Konsist (Kotlin)**
- Kotlin-specific architecture testing
- More idiomatic for Kotlin

**Custom Validator**
- Grammar-based validation
- Highest precision

---

## Next Steps

### Immediate Actions (Week 1-2)

1. **Define Type Mappings**
   - Create complete Scala ↔ Kotlin type table
   - Identify edge cases
   - Document decisions

2. **Build Proof-of-Concept**
   - Migrate one simple repository (e.g., `BestandRepository`)
   - Validate architectural rules
   - Test behavioral equivalence

3. **Set Up Tooling**
   - Install Kotlin toolchain
   - Configure Gradle build
   - Set up architecture testing framework (ArchUnit or Konsist)

4. **Team Alignment**
   - Review migration plan with team
   - Identify concerns and risks
   - Assign responsibilities

### Short-term (Month 1-2)

1. **Migrate Domain Models**
   - All value objects
   - All entities
   - All enumerations

2. **Migrate Domain Ports**
   - All repository interfaces
   - Validate architectural constraints

3. **Build Code Generator MVP**
   - Simple DSL → Kotlin generator
   - Automated tests

### Medium-term (Month 3-4)

1. **Migrate Adapters**
   - Persistence adapters
   - Messaging adapters
   - REST adapters

2. **Migrate Application Layer**
   - Services
   - Business logic

3. **Enhance Code Generator**
   - Scala parser
   - Bidirectional generation

### Long-term (Month 5-6)

1. **Production Deployment**
   - Gradual rollout
   - Monitoring and metrics
   - Performance validation

2. **Remove Scala Code**
   - Archive Scala implementation
   - Full Kotlin codebase

3. **Documentation and Training**
   - Update all documentation
   - Team training on Kotlin
   - Architecture governance

---

## Conclusion

The grammar-based approach to Scala-Kotlin migration provides:

1. **Architectural Safety** - Rules enforced mechanically
2. **Migration Safety** - Incremental, testable, reversible
3. **Code Quality** - Consistent, validated, documented
4. **Future Flexibility** - Support for additional languages

The key insight is that **architecture transcends language**. By modeling architecture abstractly, we can migrate between languages while preserving the fundamental design.

This approach transforms migration from a risky, manual process into a systematic, validated, automated one.
