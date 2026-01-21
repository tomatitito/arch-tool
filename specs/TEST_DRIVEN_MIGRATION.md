# Test-Driven Migration Strategy

## Overview

A test-driven approach to the Scala → Kotlin + Spring Boot migration provides:

- **Safety**: Each step is validated before proceeding
- **Confidence**: Behavioral equivalence is proven, not assumed
- **Regression Prevention**: Existing functionality is preserved
- **Documentation**: Tests document the expected behavior
- **Incremental Progress**: Small, verified steps instead of big-bang migration

## Migration Testing Pyramid

```
                    ┌─────────────────────┐
                    │  End-to-End Tests   │  ← Full system behavior
                    │  (Contract Tests)   │
                    └─────────────────────┘
                  ┌───────────────────────────┐
                  │   Integration Tests       │  ← Spring Boot slice tests
                  │   (@SpringBootTest)       │
                  └───────────────────────────┘
              ┌─────────────────────────────────────┐
              │      Component Tests                │  ← Repository, Service tests
              │      (Testcontainers)               │
              └─────────────────────────────────────┘
          ┌─────────────────────────────────────────────┐
          │           Unit Tests                        │  ← Domain model, pure logic
          │           (Kotest, JUnit 5)                 │
          └─────────────────────────────────────────────┘
      ┌───────────────────────────────────────────────────────┐
      │         Grammar Tool Tests                            │  ← Parser, renderer validation
      │         (Property-based, Golden tests)                │
      └───────────────────────────────────────────────────────┘
```

---

## Phase 1: Grammar Tool Test-Driven Development

Build the grammar tool using TDD to ensure correct parsing and generation.

### Step 1.1: Test the Abstract Model

**Test First:**
```scala
// arch-tool/src/test/scala/com/breuninger/arch/model/TypeSpec.scala
class TypeSpec extends AnyFlatSpec with Matchers {
  
  "Type.Effect" should "represent IO[Unit] correctly" in {
    val effect = Type.Effect(Type.Unit, Type.EffectType.IO)
    
    effect.wrapped shouldBe Type.Unit
    effect.effectType shouldBe Type.EffectType.IO
  }
  
  "Type.Generic" should "represent List[String] correctly" in {
    val listType = Type.Generic("List", List(Type.Primitive("String")))
    
    listType.name shouldBe "List"
    listType.typeArgs should have size 1
    listType.typeArgs.head shouldBe Type.Primitive("String")
  }
}
```

**Then Implement:**
```scala
// arch-tool/src/main/scala/com/breuninger/arch/model/Type.scala
sealed trait Type {
  def name: String
}

object Type {
  case class Effect(wrapped: Type, effectType: EffectType) extends Type {
    def name: String = s"Effect[${wrapped.name}]"
  }
  // ... implementation
}
```

### Step 1.2: Test the Scala Parser

**Golden Test Approach:**

```scala
// arch-tool/src/test/scala/com/breuninger/arch/parser/ScalaParserSpec.scala
class ScalaParserSpec extends AnyFlatSpec with Matchers {
  
  "ScalaParser" should "parse simple trait to Port" in {
    val scalaCode = """
      package com.breuninger.domain.repository
      
      import cats.effect.IO
      
      trait BestandRepository {
        def save(bestand: BestandCreateDocument): IO[Unit]
      }
    """
    
    val result = ScalaParser.parseFile(scalaCode)
    
    result shouldBe a[Right[_, _]]
    val model = result.toOption.get
    
    model.ports should have size 1
    val port = model.ports.head
    
    port.name shouldBe "BestandRepository"
    port.packageName shouldBe "com.breuninger.domain.repository"
    port.methods should have size 1
    
    val method = port.methods.head
    method.name shouldBe "save"
    method.parameters should have size 1
    method.returnType shouldBe Type.Effect(Type.Unit, Type.EffectType.IO)
  }
  
  it should "parse case class to ValueObject" in {
    val scalaCode = """
      case class ArtikelId(value: String) extends AnyVal
    """
    
    val result = ScalaParser.parseFile(scalaCode)
    
    result shouldBe a[Right[_, _]]
    val model = result.toOption.get
    
    model.models should have size 1
    model.models.head shouldBe a[DomainModel.ValueObject]
    
    val valueObject = model.models.head.asInstanceOf[DomainModel.ValueObject]
    valueObject.name shouldBe "ArtikelId"
    valueObject.field.name shouldBe "value"
    valueObject.field.fieldType shouldBe Type.Primitive("String")
  }
  
  it should "parse sealed trait hierarchy" in {
    val scalaCode = """
      sealed trait Result
      case class Success(value: String) extends Result
      case class Failure(error: String) extends Result
    """
    
    val result = ScalaParser.parseFile(scalaCode)
    
    result shouldBe a[Right[_, _]]
    val model = result.toOption.get
    
    val sealed = model.models.find(_.name == "Result").get
    sealed shouldBe a[DomainModel.SealedHierarchy]
    
    val hierarchy = sealed.asInstanceOf[DomainModel.SealedHierarchy]
    hierarchy.variants should have size 2
  }
}
```

**Property-Based Testing:**

```scala
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class ScalaParserPropertySpec extends AnyPropSpec with Matchers {
  
  property("parsing a valid trait always produces a Port") {
    forAll(validTraitGen) { traitCode =>
      val result = ScalaParser.parseFile(traitCode)
      result.isRight && result.toOption.get.ports.nonEmpty
    }
  }
  
  lazy val validTraitGen: Gen[String] = for {
    name <- Gen.alphaStr.map(_.capitalize)
    method <- methodGen
  } yield s"""
    trait ${name}Repository {
      $method
    }
  """
  
  lazy val methodGen: Gen[String] = for {
    methodName <- Gen.alphaStr
    paramName <- Gen.alphaStr
    paramType <- Gen.oneOf("String", "Int", "Document")
  } yield s"def $methodName($paramName: $paramType): IO[Unit]"
}
```

### Step 1.3: Test the Kotlin Renderer

**Snapshot/Golden Testing:**

```scala
class KotlinRendererSpec extends AnyFlatSpec with Matchers {
  
  "KotlinRenderer" should "generate correct interface from Port" in {
    val port = Port(
      name = "BestandRepository",
      packageName = "com.breuninger.domain.repository",
      methods = List(
        Method(
          name = "save",
          parameters = List(
            Parameter("bestand", Type.Domain("BestandCreateDocument"))
          ),
          returnType = Type.Effect(Type.Unit, Type.EffectType.IO)
        )
      )
    )
    
    val kotlinCode = KotlinRenderer.renderPort(port)
    
    // Golden test: compare with expected output
    val expected = readGoldenFile("BestandRepository.kt")
    kotlinCode shouldBe expected
  }
  
  it should "generate correct data class from Entity" in {
    val entity = DomainModel.Entity(
      name = "BestandCreateDocument",
      packageName = "com.breuninger.domain.model",
      fields = List(
        Field("id", Type.Domain("ArtikelId")),
        Field("quantity", Type.Primitive("Int")),
        Field("warehouse", Type.Primitive("String"))
      )
    )
    
    val kotlinCode = ModelRenderer.renderEntity(entity)
    
    kotlinCode should include("data class BestandCreateDocument")
    kotlinCode should include("val id: ArtikelId")
    kotlinCode should include("val quantity: Int")
    kotlinCode should include("val warehouse: String")
  }
  
  it should "add @Repository annotation for persistence adapter" in {
    val adapter = PersistenceAdapter(
      name = "MongoBestandRepository",
      packageName = "com.breuninger.ports.persistence",
      implementedPort = Port("BestandRepository", "", List.empty),
      dependencies = List.empty,
      springAnnotations = List(SpringAnnotation.Repository)
    )
    
    val kotlinCode = SpringKotlinRenderer.renderRepository(adapter)
    
    kotlinCode should include("@Repository")
    kotlinCode should include("class MongoBestandRepository")
    kotlinCode should include(": BestandRepository")
  }
}
```

### Step 1.4: Round-Trip Testing

Ensure parsing and rendering are inverse operations:

```scala
class RoundTripSpec extends AnyFlatSpec with Matchers {
  
  "Parser and Renderer" should "be inverses for simple cases" in {
    val originalScala = """
      package com.breuninger.domain.repository
      
      trait BestandRepository {
        def save(bestand: BestandCreateDocument): IO[Unit]
      }
    """
    
    // Parse Scala → Abstract Model
    val model = ScalaParser.parseFile(originalScala).toOption.get
    
    // Render to Kotlin
    val kotlinCode = KotlinRenderer.renderPort(model.ports.head)
    
    // Parse Kotlin back (if we had a Kotlin parser)
    // For now, check semantic equivalence
    kotlinCode should include("interface BestandRepository")
    kotlinCode should include("suspend fun save")
    kotlinCode should include("bestand: BestandCreateDocument")
  }
  
  it should "preserve all method signatures" in {
    forAll(portGen) { port =>
      val kotlinCode = KotlinRenderer.renderPort(port)
      
      port.methods.forall { method =>
        kotlinCode.contains(s"fun ${method.name}")
      }
    }
  }
}
```

---

## Phase 2: Domain Model Migration (Test-First)

Migrate domain models with unit tests leading the way.

### Step 2.1: Write Tests for Domain Models (Kotlin)

**Before writing Kotlin code:**

```kotlin
// src/test/kotlin/com/breuninger/domain/model/ArtikelIdTest.kt
class ArtikelIdTest {
    
    @Test
    fun `should create ArtikelId with value`() {
        val id = ArtikelId("123")
        
        assertThat(id.value).isEqualTo("123")
    }
    
    @Test
    fun `two ArtikelIds with same value should be equal`() {
        val id1 = ArtikelId("123")
        val id2 = ArtikelId("123")
        
        assertThat(id1).isEqualTo(id2)
    }
    
    @Test
    fun `ArtikelId should have proper toString`() {
        val id = ArtikelId("123")
        
        assertThat(id.toString()).contains("123")
    }
}
```

### Step 2.2: Generate Kotlin Code with Grammar Tool

```bash
./arch-tool migrate \
  -i src/main/scala/domain/model/ArtikelId.scala \
  -o src/main/kotlin/domain/model/ArtikelId.kt
```

### Step 2.3: Run Tests to Verify

```bash
./gradlew test --tests ArtikelIdTest
```

Tests should pass immediately if grammar tool is correct!

### Step 2.4: Property-Based Tests for Domain Models

```kotlin
// Use Kotest property testing
class ArtikelIdPropertyTest : StringSpec({
    
    "ArtikelId equality is reflexive" {
        checkAll<String> { value ->
            val id = ArtikelId(value)
            id shouldBe id
        }
    }
    
    "ArtikelId equality is symmetric" {
        checkAll<String> { value ->
            val id1 = ArtikelId(value)
            val id2 = ArtikelId(value)
            
            (id1 == id2) shouldBe (id2 == id1)
        }
    }
    
    "ArtikelId hashCode is consistent with equals" {
        checkAll<String> { value ->
            val id1 = ArtikelId(value)
            val id2 = ArtikelId(value)
            
            if (id1 == id2) {
                id1.hashCode() shouldBe id2.hashCode()
            }
        }
    }
})
```

---

## Phase 3: Repository Migration (Contract Tests)

Use contract tests to ensure Scala and Kotlin repositories behave identically.

### Step 3.1: Define Repository Contract Tests

```kotlin
// src/test/kotlin/com/breuninger/domain/repository/BestandRepositoryContract.kt

/**
 * Contract test that both Scala and Kotlin implementations must pass.
 * This ensures behavioral equivalence during migration.
 */
abstract class BestandRepositoryContract {
    
    abstract fun createRepository(): BestandRepository
    
    @Test
    fun `save should persist document`() = runBlocking {
        val repo = createRepository()
        val bestand = BestandCreateDocument(
            id = ArtikelId("123"),
            quantity = 10,
            warehouse = "MAIN"
        )
        
        repo.save(bestand)
        
        val retrieved = repo.getByIds(listOf(ArtikelId("123")))
        assertThat(retrieved).hasSize(1)
        assertThat(retrieved.first()).isEqualTo(bestand)
    }
    
    @Test
    fun `getByIds should return empty list for non-existent IDs`() = runBlocking {
        val repo = createRepository()
        
        val result = repo.getByIds(listOf(ArtikelId("nonexistent")))
        
        assertThat(result).isEmpty()
    }
    
    @Test
    fun `getByIds should return multiple documents`() = runBlocking {
        val repo = createRepository()
        val bestand1 = BestandCreateDocument(ArtikelId("1"), 10, "MAIN")
        val bestand2 = BestandCreateDocument(ArtikelId("2"), 20, "BACKUP")
        
        repo.save(bestand1)
        repo.save(bestand2)
        
        val result = repo.getByIds(listOf(ArtikelId("1"), ArtikelId("2")))
        
        assertThat(result).hasSize(2)
        assertThat(result).containsExactlyInAnyOrder(bestand1, bestand2)
    }
    
    @Test
    fun `deleteBatch should remove documents`() = runBlocking {
        val repo = createRepository()
        val bestand = BestandCreateDocument(ArtikelId("123"), 10, "MAIN")
        
        repo.save(bestand)
        repo.deleteBatch(listOf(BestandDeleteDocument(ArtikelId("123"))))
        
        val result = repo.getByIds(listOf(ArtikelId("123")))
        assertThat(result).isEmpty()
    }
}
```

### Step 3.2: Test Scala Implementation Against Contract

```kotlin
// src/test/kotlin/com/breuninger/ports/persistence/ScalaBestandRepositoryContractTest.kt

@Testcontainers
class ScalaBestandRepositoryContractTest : BestandRepositoryContract() {
    
    @Container
    val mongoContainer = MongoDBContainer("mongo:6.0")
    
    override fun createRepository(): BestandRepository {
        // Wrap Scala repository to implement Kotlin interface
        val scalaRepo = ScalaMongoBestandRepository(
            // ... Scala repository setup
        )
        
        return object : BestandRepository {
            override suspend fun save(bestand: BestandCreateDocument) {
                scalaRepo.save(bestand).unsafeRunSync()
            }
            
            override suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument> {
                return scalaRepo.getByIds(ids.asScala).unsafeRunSync().asKotlin
            }
            
            override suspend fun deleteBatch(bestaende: List<BestandDeleteDocument>) {
                scalaRepo.deleteBatch(bestaende.asScala).unsafeRunSync()
            }
        }
    }
}
```

### Step 3.3: Test Kotlin Implementation Against Same Contract

```kotlin
// src/test/kotlin/com/breuninger/ports/persistence/KotlinBestandRepositoryContractTest.kt

@SpringBootTest
@Testcontainers
class KotlinBestandRepositoryContractTest : BestandRepositoryContract() {
    
    @Container
    val mongoContainer = MongoDBContainer("mongo:6.0")
    
    @Autowired
    lateinit var mongoTemplate: ReactiveMongoTemplate
    
    override fun createRepository(): BestandRepository {
        return MongoBestandRepository(mongoTemplate)
    }
}
```

### Step 3.4: Both Tests Must Pass!

```bash
# Test Scala implementation
./sbt "testOnly *ScalaBestandRepositoryContractTest"

# Test Kotlin implementation
./gradlew test --tests KotlinBestandRepositoryContractTest

# Both should pass with identical behavior!
```

---

## Phase 4: Service Layer Migration (Behavior Tests)

### Step 4.1: Write Behavior Tests First

```kotlin
// src/test/kotlin/com/breuninger/application/service/BestandAssemblerServiceTest.kt

class BestandAssemblerServiceTest {
    
    private lateinit var bestandRepository: BestandRepository
    private lateinit var stammdatenRepository: ProduktStammdatenRepository
    private lateinit var service: BestandAssemblerService
    
    @BeforeEach
    fun setup() {
        bestandRepository = mockk()
        stammdatenRepository = mockk()
        service = BestandAssemblerService(bestandRepository, stammdatenRepository)
    }
    
    @Test
    fun `should assemble bestand with stammdaten`() = runBlocking {
        // Given
        val artikelId = ArtikelId("123")
        val bestand = BestandCreateDocument(artikelId, 10, "MAIN")
        val stammdaten = ProduktStammdaten(artikelId, "Product 123", "Category")
        
        coEvery { bestandRepository.getByIds(listOf(artikelId)) } returns listOf(bestand)
        coEvery { stammdatenRepository.getById(artikelId) } returns stammdaten
        
        // When
        val result = service.assembleBestand(artikelId)
        
        // Then
        assertThat(result.bestand).isEqualTo(bestand)
        assertThat(result.stammdaten).isEqualTo(stammdaten)
        
        coVerify(exactly = 1) { bestandRepository.getByIds(listOf(artikelId)) }
        coVerify(exactly = 1) { stammdatenRepository.getById(artikelId) }
    }
    
    @Test
    fun `should throw NotFoundException when bestand not found`() = runBlocking {
        // Given
        val artikelId = ArtikelId("nonexistent")
        coEvery { bestandRepository.getByIds(listOf(artikelId)) } returns emptyList()
        
        // When/Then
        assertThrows<NotFoundException> {
            service.assembleBestand(artikelId)
        }
    }
    
    @Test
    fun `should throw NotFoundException when stammdaten not found`() = runBlocking {
        // Given
        val artikelId = ArtikelId("123")
        val bestand = BestandCreateDocument(artikelId, 10, "MAIN")
        
        coEvery { bestandRepository.getByIds(listOf(artikelId)) } returns listOf(bestand)
        coEvery { stammdatenRepository.getById(artikelId) } returns null
        
        // When/Then
        assertThrows<NotFoundException> {
            service.assembleBestand(artikelId)
        }
    }
}
```

### Step 4.2: Implement Service (Generated from Grammar Tool)

```bash
./arch-tool migrate \
  -i src/main/scala/application/service/BestandAssemblerService.scala \
  -o src/main/kotlin/application/service/BestandAssemblerService.kt \
  --spring-boot
```

### Step 4.3: Run Tests

```bash
./gradlew test --tests BestandAssemblerServiceTest
```

---

## Phase 5: Integration Testing (Spring Boot Slices)

### Step 5.1: Repository Integration Tests

```kotlin
@DataMongoTest
@Testcontainers
class MongoBestandRepositoryIntegrationTest {
    
    @Container
    val mongoContainer = MongoDBContainer("mongo:6.0")
        .withExposedPorts(27017)
    
    @Autowired
    lateinit var mongoTemplate: ReactiveMongoTemplate
    
    private lateinit var repository: MongoBestandRepository
    
    @BeforeEach
    fun setup() {
        repository = MongoBestandRepository(mongoTemplate)
    }
    
    @Test
    fun `should save and retrieve bestand`() = runBlocking {
        val bestand = BestandCreateDocument(
            id = ArtikelId("123"),
            quantity = 10,
            warehouse = "MAIN"
        )
        
        repository.save(bestand)
        
        val retrieved = repository.getByIds(listOf(ArtikelId("123")))
        
        assertThat(retrieved).hasSize(1)
        assertThat(retrieved.first()).isEqualTo(bestand)
    }
}
```

### Step 5.2: Controller Integration Tests

```kotlin
@WebFluxTest(BestandController::class)
class BestandControllerIntegrationTest {
    
    @Autowired
    lateinit var webTestClient: WebTestClient
    
    @MockBean
    lateinit var bestandService: BestandAssemblerService
    
    @Test
    fun `GET bestand by ID should return assembled bestand`() {
        // Given
        val artikelId = "123"
        val assembled = AssembledBestand(/* ... */)
        
        coEvery { bestandService.assembleBestand(ArtikelId(artikelId)) } returns assembled
        
        // When/Then
        webTestClient.get()
            .uri("/api/bestand/$artikelId")
            .exchange()
            .expectStatus().isOk
            .expectBody<AssembledBestandDto>()
            .consumeWith { response ->
                assertThat(response.responseBody).isNotNull
                assertThat(response.responseBody?.id).isEqualTo(artikelId)
            }
    }
    
    @Test
    fun `GET bestand with non-existent ID should return 404`() {
        // Given
        val artikelId = "nonexistent"
        coEvery { bestandService.assembleBestand(ArtikelId(artikelId)) } throws 
            NotFoundException("Not found")
        
        // When/Then
        webTestClient.get()
            .uri("/api/bestand/$artikelId")
            .exchange()
            .expectStatus().isNotFound
    }
}
```

---

## Phase 6: End-to-End Testing

### Step 6.1: Dual-Running Test

Run both Scala and Kotlin applications side-by-side, compare outputs:

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DualRunningE2ETest {
    
    @LocalServerPort
    var kotlinPort: Int = 0
    
    @Container
    val scalaApp = GenericContainer("produkt-assembler-scala:latest")
        .withExposedPorts(8080)
    
    @Container
    val mongoContainer = MongoDBContainer("mongo:6.0")
    
    @Autowired
    lateinit var webClient: WebClient.Builder
    
    @Test
    fun `Kotlin and Scala apps should return identical responses`() = runBlocking {
        val artikelId = "123"
        
        // Seed database with test data
        seedDatabase()
        
        // Call Scala app
        val scalaResponse = webClient.build()
            .get()
            .uri("http://localhost:${scalaApp.getMappedPort(8080)}/api/bestand/$artikelId")
            .retrieve()
            .bodyToMono(String::class.java)
            .awaitSingle()
        
        // Call Kotlin app
        val kotlinResponse = webClient.build()
            .get()
            .uri("http://localhost:$kotlinPort/api/bestand/$artikelId")
            .retrieve()
            .bodyToMono(String::class.java)
            .awaitSingle()
        
        // Responses should be identical (ignoring formatting)
        val scalaJson = objectMapper.readTree(scalaResponse)
        val kotlinJson = objectMapper.readTree(kotlinResponse)
        
        assertThat(kotlinJson).isEqualTo(scalaJson)
    }
}
```

### Step 6.2: Shadow Traffic Testing

In production, send traffic to both versions and compare:

```kotlin
@Component
class ShadowTrafficFilter : WebFilter {
    
    @Autowired
    lateinit var scalaClient: WebClient
    
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // Clone request
        val request = exchange.request
        
        // Send to Scala app asynchronously (don't wait for response)
        GlobalScope.launch {
            try {
                scalaClient.method(request.method)
                    .uri(request.uri)
                    .headers { it.addAll(request.headers) }
                    .bodyValue(request.body)
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .subscribe { scalaResponse ->
                        // Compare with Kotlin response (logged elsewhere)
                        compareResponses(request.uri, scalaResponse)
                    }
            } catch (e: Exception) {
                logger.warn("Shadow traffic failed", e)
            }
        }
        
        // Continue with Kotlin app
        return chain.filter(exchange)
    }
}
```

---

## Test Organization

### Test Structure

```
src/test/kotlin/
├── com/breuninger/
│   ├── grammar/                           # Grammar tool tests
│   │   ├── parser/
│   │   │   ├── ScalaParserTest.kt
│   │   │   └── TypeParserPropertyTest.kt
│   │   ├── renderer/
│   │   │   ├── KotlinRendererTest.kt
│   │   │   └── SpringRendererTest.kt
│   │   └── validator/
│   │       └── ArchValidatorTest.kt
│   │
│   ├── domain/                            # Unit tests (fast)
│   │   ├── model/
│   │   │   ├── ArtikelIdTest.kt
│   │   │   └── BestandCreateDocumentTest.kt
│   │   └── repository/
│   │       └── BestandRepositoryContract.kt    # Contract test
│   │
│   ├── application/                       # Service tests (mocked)
│   │   └── service/
│   │       └── BestandAssemblerServiceTest.kt
│   │
│   ├── ports/
│   │   ├── persistence/                   # Integration tests
│   │   │   ├── ScalaBestandRepositoryContractTest.kt
│   │   │   ├── KotlinBestandRepositoryContractTest.kt
│   │   │   └── MongoBestandRepositoryIntegrationTest.kt
│   │   └── rest/                          # Controller tests
│   │       └── BestandControllerIntegrationTest.kt
│   │
│   └── e2e/                               # End-to-end tests
│       ├── DualRunningE2ETest.kt
│       └── ShadowTrafficTest.kt
```

### Test Execution Order

```bash
# 1. Grammar tool tests (must pass first)
./gradlew :arch-tool:test

# 2. Domain unit tests (fast)
./gradlew test --tests com.breuninger.domain.*

# 3. Service tests (mocked, fast)
./gradlew test --tests com.breuninger.application.*

# 4. Integration tests (slower, use Testcontainers)
./gradlew integrationTest

# 5. Contract tests (Scala vs Kotlin)
./gradlew contractTest

# 6. E2E tests (slowest)
./gradlew e2eTest
```

---

## Continuous Integration

### GitHub Actions Workflow

```yaml
# .github/workflows/migration.yml
name: Migration Tests

on: [push, pull_request]

jobs:
  grammar-tool-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: coursier/setup-action@v1
        with:
          jvm: temurin:17
      - name: Run grammar tool tests
        run: |
          cd arch-tool
          sbt test

  kotlin-unit-tests:
    needs: grammar-tool-tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run Kotlin unit tests
        run: ./gradlew test --tests "com.breuninger.domain.*"

  integration-tests:
    needs: kotlin-unit-tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run integration tests
        run: ./gradlew integrationTest

  contract-tests:
    needs: integration-tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: coursier/setup-action@v1
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Build Scala app
        run: sbt compile
      - name: Run contract tests
        run: ./gradlew contractTest

  e2e-tests:
    needs: contract-tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Start services
        run: docker-compose up -d
      - name: Run E2E tests
        run: ./gradlew e2eTest
      - name: Shutdown services
        run: docker-compose down
```

---

## Test-Driven Migration Workflow

### Daily Workflow

```bash
# Morning: Check status
./scripts/migration-status.sh

# 1. Pick a component to migrate (e.g., BestandRepository)
# 2. Write contract tests (if not exists)
# 3. Run contract tests against Scala implementation (should pass)
./gradlew test --tests ScalaBestandRepositoryContractTest

# 4. Generate Kotlin code with grammar tool
./arch-tool migrate \
  -i src/main/scala/ports/persistence/bestand/MongoBestandRepository.scala \
  -o src/main/kotlin/ports/persistence/bestand/MongoBestandRepository.kt \
  --spring-boot

# 5. Run contract tests against Kotlin implementation
./gradlew test --tests KotlinBestandRepositoryContractTest

# 6. Fix any issues until tests pass
# 7. Run integration tests
./gradlew integrationTest

# 8. Run full test suite
./gradlew test

# 9. Commit when all tests pass
git add .
git commit -m "Migrate MongoBestandRepository to Kotlin"

# 10. Push and verify CI
git push
```

---

## Metrics and Coverage

### Track Migration Progress

```kotlin
// scripts/migration-metrics.kt

data class MigrationMetrics(
    val totalComponents: Int,
    val migratedComponents: Int,
    val testsPassing: Int,
    val testsTotal: Int,
    val codeCoverage: Double
)

fun calculateMetrics(): MigrationMetrics {
    val scalaFiles = countFiles("src/main/scala")
    val kotlinFiles = countFiles("src/main/kotlin")
    
    val testResults = runTests()
    
    return MigrationMetrics(
        totalComponents = scalaFiles,
        migratedComponents = kotlinFiles,
        testsPassing = testResults.passing,
        testsTotal = testResults.total,
        codeCoverage = testResults.coverage
    )
}

fun printProgress(metrics: MigrationMetrics) {
    val percentMigrated = (metrics.migratedComponents.toDouble() / metrics.totalComponents) * 100
    val percentPassing = (metrics.testsPassing.toDouble() / metrics.testsTotal) * 100
    
    println("""
        Migration Progress:
        ==================
        Components: ${metrics.migratedComponents}/${metrics.totalComponents} (${percentMigrated.format(1)}%)
        Tests: ${metrics.testsPassing}/${metrics.testsTotal} (${percentPassing.format(1)}%)
        Coverage: ${metrics.codeCoverage.format(1)}%
        
        Status: ${if (percentPassing >= 95) "✓ Ready" else "⚠ In Progress"}
    """.trimIndent())
}
```

---

## Summary

### Test-Driven Migration Ensures:

1. **Grammar Tool Correctness** - Parser and renderer tested independently
2. **Behavioral Equivalence** - Contract tests prove Scala and Kotlin behave identically
3. **Regression Prevention** - Existing tests run on both versions
4. **Incremental Progress** - Small, verified steps
5. **Confidence** - Every component has passing tests before deployment

### Test Types by Phase:

| Phase | Test Type | Tool | Purpose |
|-------|-----------|------|---------|
| Grammar Tool | Unit, Property | ScalaTest, ScalaCheck | Verify parsing/rendering |
| Domain Models | Unit, Property | Kotest, JUnit 5 | Verify model behavior |
| Repositories | Contract, Integration | Testcontainers | Prove equivalence |
| Services | Unit (mocked) | MockK, Kotest | Verify business logic |
| Controllers | Integration | Spring WebFlux Test | Verify HTTP layer |
| E2E | Full system | Docker Compose | Verify complete system |

**Key Principle**: No code migrates to production without passing tests that also pass for the Scala version!
