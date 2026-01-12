# Grammar Tool Scope: What's Automated vs. Manual

## Quick Reference

### ✅ Fully Automated (100%)

The grammar tool generates complete, production-ready code for:

1. **Port Interfaces**
   ```kotlin
   interface BestandRepository {
       suspend fun save(bestand: BestandCreateDocument)
       suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>
   }
   ```

2. **Domain Models**
   ```kotlin
   @JvmInline
   value class ArtikelId(val value: String)
   
   data class BestandCreateDocument(
       val id: ArtikelId,
       val quantity: Int,
       val warehouse: String
   )
   ```

3. **Sealed Hierarchies**
   ```kotlin
   sealed interface Result
   data class Success(val value: String) : Result
   data class Failure(val error: String) : Result
   ```

4. **Type Mappings**
   - Scala `IO[A]` → Kotlin `suspend fun`
   - Scala `Option[A]` → Kotlin `A?`
   - Scala `List[A]` → Kotlin `List<A>`
   - Value objects with `@JvmInline`

### 🟡 Skeleton Only (Structure Generated, Logic Manual)

The tool generates the class structure but you implement the methods:

1. **Adapter Implementations**
   ```kotlin
   // ✅ Generated
   @Repository
   class MongoBestandRepository(
       private val mongoTemplate: MongoTemplate
   ) : BestandRepository {
       
       // ❌ You implement
       override suspend fun save(bestand: BestandCreateDocument) {
           TODO("Your MongoDB save logic here")
       }
       
       // ❌ You implement
       override suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument> {
           TODO("Your MongoDB query logic here")
       }
   }
   ```

2. **Service Classes**
   ```kotlin
   // ✅ Generated
   @Service
   class BestandAssemblerService(
       private val bestandRepository: BestandRepository,
       private val stammdatenRepository: StammdatenRepository
   ) {
       // ❌ You implement
       suspend fun assembleBestand(id: ArtikelId): AssembledBestand {
           TODO("Your orchestration logic here")
       }
   }
   ```

3. **REST Controllers**
   ```kotlin
   // ✅ Generated
   @RestController
   @RequestMapping("/api/bestand")
   class BestandController(
       private val bestandService: BestandAssemblerService
   ) {
       // ❌ You implement
       @GetMapping("/{id}")
       suspend fun getBestand(@PathVariable id: String): ResponseEntity<AssembledBestand> {
           TODO("Your request handling logic here")
       }
   }
   ```

### ❌ Fully Manual (0% Generated)

You implement these yourself:

1. **Business Logic**
   - Database queries and updates
   - Data transformations
   - Error handling and validation
   - Retry logic and circuit breakers

2. **Infrastructure Code**
   - Connection pools
   - Kafka producers/consumers
   - HTTP client configurations
   - Caching strategies

3. **Tests**
   - Unit tests
   - Integration tests
   - Contract tests
   - Property-based tests

## Why This Division?

### What the Tool Can Do

The grammar tool operates on **structural information** available in the code:
- Interface definitions
- Type signatures
- Class hierarchies
- Dependency relationships

It can translate these **mechanically** because they follow predictable patterns.

### What the Tool Cannot Do

The tool cannot generate **implementation logic** because:
- **Business rules are unique** - Your validation logic is specific to your domain
- **Infrastructure varies** - MongoDB query patterns, Kafka serialization, HTTP error handling
- **Optimizations differ** - Indexing strategies, caching, batch sizes
- **Edge cases matter** - How you handle failures, retries, timeouts

These require **domain knowledge** and **design decisions** that cannot be inferred from interfaces alone.

## The Value Proposition

Even though implementations are manual, the grammar tool provides **massive value**:

### Time Savings

| Task | Without Tool | With Tool | Time Saved |
|------|-------------|-----------|------------|
| Port interfaces | 2 hours manual translation | 2 seconds generated | ~2 hours |
| Domain models | 4 hours manual translation | 5 seconds generated | ~4 hours |
| Adapter skeletons | 3 hours manual writing | 10 seconds generated | ~3 hours |
| Type mappings | 2 hours + debugging | Automatic + validated | ~2 hours |
| **Total per module** | **~11 hours** | **~1 hour** | **~10 hours** |

### Quality Improvements

1. **Consistency** - All generated code follows the same patterns
2. **Correctness** - Type-safe migrations prevent subtle bugs
3. **Validation** - Architectural rules enforced automatically
4. **Documentation** - Generated code serves as up-to-date contracts

### Focus on What Matters

The tool eliminates the **tedious 30%** (interface translation, boilerplate) so you can focus on the **creative 70%** (business logic, optimizations, error handling).

## Migration Workflow

```
1. Run Grammar Tool
   ├── Parses Scala code
   ├── Validates architecture
   └── Generates Kotlin interfaces + skeletons
   
2. Review Generated Code
   ├── Check interfaces match expectations
   ├── Verify type mappings are correct
   └── Ensure architectural rules satisfied
   
3. Write Contract Tests (Manual)
   ├── Define expected behavior
   └── Test both Scala and Kotlin implementations
   
4. Implement Business Logic (Manual)
   ├── Fill in TODO placeholders
   ├── Write MongoDB queries
   ├── Add error handling
   └── Optimize performance
   
5. Validate Equivalence
   ├── Run contract tests on both implementations
   ├── Compare outputs
   └── Performance benchmarks
   
6. Deploy
   ├── Feature flags for gradual rollout
   └── Monitor metrics
```

## Example: Complete Migration

### Input (Scala)
```scala
// domain/repository/BestandRepository.scala
trait BestandRepository {
  def save(bestand: BestandCreateDocument): IO[Unit]
  def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]]
}

// domain/model/ArtikelId.scala
case class ArtikelId(value: String) extends AnyVal
```

### Generated (Kotlin)
```kotlin
// domain/repository/BestandRepository.kt - ✅ 100% generated
interface BestandRepository {
    suspend fun save(bestand: BestandCreateDocument)
    suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>
}

// domain/model/ArtikelId.kt - ✅ 100% generated
@JvmInline
value class ArtikelId(val value: String)

// ports/persistence/MongoBestandRepository.kt - 🟡 Skeleton generated
@Repository
class MongoBestandRepository(
    private val mongoTemplate: MongoTemplate
) : BestandRepository {
    override suspend fun save(bestand: BestandCreateDocument) {
        TODO("Implement MongoDB save")
    }
    
    override suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument> {
        TODO("Implement MongoDB query")
    }
}
```

### Implemented (Kotlin)
```kotlin
// You write this - ❌ Manual implementation
@Repository
class MongoBestandRepository(
    private val mongoTemplate: MongoTemplate
) : BestandRepository {
    
    override suspend fun save(bestand: BestandCreateDocument) = withContext(Dispatchers.IO) {
        val document = Document().apply {
            append("_id", bestand.id.value)
            append("quantity", bestand.quantity)
            append("warehouse", bestand.warehouse)
        }
        mongoTemplate.save(document, "bestand")
    }
    
    override suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument> = 
        withContext(Dispatchers.IO) {
            val query = Query.query(Criteria.where("_id").`in`(ids.map { it.value }))
            mongoTemplate.find(query, Document::class.java, "bestand")
                .map { doc ->
                    BestandCreateDocument(
                        id = ArtikelId(doc.getString("_id")),
                        quantity = doc.getInteger("quantity"),
                        warehouse = doc.getString("warehouse")
                    )
                }
        }
}
```

## Summary

| Aspect | Automated | Manual | Benefit |
|--------|-----------|--------|---------|
| **Interfaces** | ✅ Yes | - | No tedious translation |
| **Models** | ✅ Yes | - | Consistent patterns |
| **Type safety** | ✅ Yes | - | Catch errors at compile time |
| **Architecture validation** | ✅ Yes | - | Enforce design rules |
| **Business logic** | - | ❌ Yes | Full control over implementation |
| **Optimizations** | - | ❌ Yes | Tailor to your needs |
| **Error handling** | - | ❌ Yes | Handle edge cases properly |

**The grammar tool is a force multiplier, not a magic wand.** It automates the mechanical parts of migration so you can focus on the parts that require human expertise and creativity.
