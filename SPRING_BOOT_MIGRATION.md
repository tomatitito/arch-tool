# Spring Boot Integration for Kotlin Migration

## Overview

Migrating from Scala to **Kotlin with Spring Boot** introduces additional considerations beyond the basic language migration. Spring Boot brings:

- **Dependency Injection** - Spring's DI container replaces custom DI modules
- **Web Framework** - Spring MVC/WebFlux replaces http4s
- **Data Access** - Spring Data MongoDB replaces raw MongoDB Scala driver
- **Configuration** - Spring Boot application.yml replaces Scala config
- **Component Model** - Spring stereotypes (@Service, @Repository, @Controller)

This document extends the migration plan to incorporate Spring Boot patterns.

---

## Updated Type Mappings for Spring Boot

### Scala → Kotlin + Spring Boot

| Concept | Scala | Kotlin + Spring Boot |
|---------|-------|---------------------|
| Port (Repository) | `trait XRepository` | `interface XRepository` (no Spring annotation needed) |
| Adapter (Persistence) | `class MongoXRepository extends XRepository` | `@Repository class MongoXRepository : XRepository` |
| Adapter (REST) | `class XRoutes extends HttpRoutes` | `@RestController class XController` |
| Service | `class XService(repo: Repository)` | `@Service class XService(val repo: Repository)` |
| Effect | `IO[A]` | `suspend fun` (Spring WebFlux supports coroutines) |
| DI Module | `trait XModule extends Module` | Spring auto-wiring (no explicit module) |
| Configuration | Scala case class config | `@ConfigurationProperties` data class |
| Entry Point | `object Main extends IOApp` | `@SpringBootApplication class Application` |

---

## Updated Abstract Grammar for Spring Boot

### Domain Layer (Unchanged)

Ports remain pure interfaces:

```kotlin
// domain/repository/BestandRepository.kt
package com.breuninger.domain.repository

interface BestandRepository {
  suspend fun save(bestand: BestandCreateDocument)
  suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>
  suspend fun deleteBatch(bestaende: List<BestandDeleteDocument>)
}
```

**No Spring annotations on ports** - keep domain pure.

### Ports Layer (Spring Annotations)

Adapters use Spring stereotypes:

```kotlin
// ports/persistence/bestand/MongoBestandRepository.kt
package com.breuninger.ports.persistence.bestand

import com.breuninger.domain.repository.BestandRepository
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Repository

@Repository
class MongoBestandRepository(
  private val mongoTemplate: ReactiveMongoTemplate
) : BestandRepository {
  
  override suspend fun save(bestand: BestandCreateDocument) {
    mongoTemplate.save(bestand).awaitSingle()
  }
  
  override suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument> {
    return mongoTemplate.find(
      Query.query(Criteria.where("_id").`in`(ids)),
      BestandCreateDocument::class.java
    ).collectList().awaitSingle()
  }
  
  override suspend fun deleteBatch(bestaende: List<BestandDeleteDocument>) {
    val ids = bestaende.map { it.id }
    mongoTemplate.remove(
      Query.query(Criteria.where("_id").`in`(ids)),
      BestandCreateDocument::class.java
    ).awaitSingle()
  }
}
```

**Key differences from non-Spring version:**
- `@Repository` annotation for Spring component scanning
- `ReactiveMongoTemplate` instead of `MongoCollection` (Spring Data abstraction)
- `.awaitSingle()` extension functions for coroutine integration

### Application Layer (Spring Services)

Services use `@Service` annotation:

```kotlin
// application/service/BestandAssemblerService.kt
package com.breuninger.application.service

import com.breuninger.domain.repository.BestandRepository
import org.springframework.stereotype.Service

@Service
class BestandAssemblerService(
  private val bestandRepository: BestandRepository,
  private val stammdatenRepository: ProduktStammdatenRepository
) {
  
  suspend fun assembleBestand(artikelId: ArtikelId): AssembledBestand {
    val bestand = bestandRepository.getByIds(listOf(artikelId)).firstOrNull()
      ?: throw NotFoundException("Bestand not found for $artikelId")
    
    val stammdaten = stammdatenRepository.getById(artikelId)
      ?: throw NotFoundException("Stammdaten not found for $artikelId")
    
    return AssembledBestand(bestand, stammdaten)
  }
}
```

**Key differences:**
- `@Service` annotation for Spring component scanning
- Constructor injection (Spring auto-wires dependencies)
- No explicit DI module needed

### REST Layer (Spring Controllers)

REST adapters become Spring controllers:

```kotlin
// ports/rest/BestandController.kt
package com.breuninger.ports.rest

import com.breuninger.application.service.BestandAssemblerService
import com.breuninger.domain.model.ArtikelId
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/bestand")
class BestandController(
  private val bestandService: BestandAssemblerService
) {
  
  @GetMapping("/{artikelId}")
  suspend fun getBestand(@PathVariable artikelId: String): AssembledBestandDto {
    val assembled = bestandService.assembleBestand(ArtikelId(artikelId))
    return AssembledBestandDto.fromDomain(assembled)
  }
  
  @PostMapping
  suspend fun createBestand(@RequestBody request: CreateBestandRequest) {
    // ... implementation
  }
}
```

**Key differences from http4s:**
- `@RestController` instead of extending `HttpRoutes`
- Spring MVC annotations (`@GetMapping`, `@PostMapping`, etc.)
- Path parameters with `@PathVariable`
- Request bodies with `@RequestBody`
- Automatic JSON serialization/deserialization

---

## Updated Grammar Model for Spring Boot

### Extended Abstract Model

Add Spring-specific concepts to our IR:

```scala
// model/SpringAnnotation.scala
package com.breuninger.arch.model

sealed trait SpringAnnotation
object SpringAnnotation {
  case object Repository extends SpringAnnotation
  case object Service extends SpringAnnotation
  case object RestController extends SpringAnnotation
  case object Component extends SpringAnnotation
  
  case class RequestMapping(path: String) extends SpringAnnotation
  case class GetMapping(path: String) extends SpringAnnotation
  case class PostMapping(path: String) extends SpringAnnotation
  case class PutMapping(path: String) extends SpringAnnotation
  case class DeleteMapping(path: String) extends SpringAnnotation
  
  case object PathVariable extends SpringAnnotation
  case object RequestBody extends SpringAnnotation
  case object RequestParam extends SpringAnnotation
}

// Update Adapter model
case class Adapter(
  name: String,
  packageName: String,
  implementedPort: Port,
  dependencies: List[Dependency],
  springAnnotations: List[SpringAnnotation]  // NEW
)

// Update Method model for REST endpoints
case class Method(
  name: String,
  parameters: List[Parameter],
  returnType: Type,
  httpMapping: Option[HttpMapping] = None  // NEW
)

case class HttpMapping(
  method: HttpMethod,
  path: String
)

sealed trait HttpMethod
object HttpMethod {
  case object GET extends HttpMethod
  case object POST extends HttpMethod
  case object PUT extends HttpMethod
  case object DELETE extends HttpMethod
  case object PATCH extends HttpMethod
}
```

### Updated Renderer for Spring Boot

```scala
// renderer/SpringKotlinRenderer.scala
package com.breuninger.arch.renderer

import com.breuninger.arch.model._
import com.squareup.kotlinpoet._

object SpringKotlinRenderer {
  
  def renderRepository(adapter: PersistenceAdapter): String = {
    val classBuilder = TypeSpec.classBuilder(adapter.name)
      .addAnnotation(ClassName.bestGuess("org.springframework.stereotype.Repository"))
      .addSuperinterface(ClassName.bestGuess(adapter.implementedPort.name))
    
    // Add constructor with dependencies
    val constructorBuilder = FunSpec.constructorBuilder()
    adapter.dependencies.foreach { dep =>
      constructorBuilder.addParameter(
        ParameterSpec.builder(dep.name, renderTypeName(dep.dependencyType))
          .addModifiers(KModifier.PRIVATE)
          .build()
      )
    }
    classBuilder.primaryConstructor(constructorBuilder.build())
    
    // Add method implementations
    adapter.implementedPort.methods.foreach { method =>
      val funSpec = renderRepositoryMethod(method)
      classBuilder.addFunction(funSpec)
    }
    
    val fileSpec = FileSpec.builder(adapter.packageName, adapter.name)
      .addType(classBuilder.build())
      .build()
    
    fileSpec.toString
  }
  
  def renderService(service: Service): String = {
    val classBuilder = TypeSpec.classBuilder(service.name)
      .addAnnotation(ClassName.bestGuess("org.springframework.stereotype.Service"))
    
    // Constructor with repository dependencies
    val constructorBuilder = FunSpec.constructorBuilder()
    service.dependencies.foreach { dep =>
      constructorBuilder.addParameter(
        ParameterSpec.builder(dep.name, ClassName.bestGuess(dep.typeName))
          .addModifiers(KModifier.PRIVATE)
          .build()
      )
    }
    classBuilder.primaryConstructor(constructorBuilder.build())
    
    // Add service methods
    service.methods.foreach { method =>
      classBuilder.addFunction(renderServiceMethod(method))
    }
    
    val fileSpec = FileSpec.builder(service.packageName, service.name)
      .addType(classBuilder.build())
      .build()
    
    fileSpec.toString
  }
  
  def renderController(adapter: RestAdapter): String = {
    val classBuilder = TypeSpec.classBuilder(adapter.name)
      .addAnnotation(ClassName.bestGuess("org.springframework.web.bind.annotation.RestController"))
    
    // Add @RequestMapping if base path exists
    adapter.basePath.foreach { path =>
      classBuilder.addAnnotation(
        AnnotationSpec.builder(ClassName.bestGuess("org.springframework.web.bind.annotation.RequestMapping"))
          .addMember("%S", path)
          .build()
      )
    }
    
    // Constructor with service dependencies
    val constructorBuilder = FunSpec.constructorBuilder()
    adapter.dependencies.foreach { dep =>
      constructorBuilder.addParameter(
        ParameterSpec.builder(dep.name, ClassName.bestGuess(dep.typeName))
          .addModifiers(KModifier.PRIVATE)
          .build()
      )
    }
    classBuilder.primaryConstructor(constructorBuilder.build())
    
    // Add controller methods with HTTP mappings
    adapter.methods.foreach { method =>
      classBuilder.addFunction(renderControllerMethod(method))
    }
    
    val fileSpec = FileSpec.builder(adapter.packageName, adapter.name)
      .addType(classBuilder.build())
      .build()
    
    fileSpec.toString
  }
  
  private def renderControllerMethod(method: Method): FunSpec = {
    val funBuilder = FunSpec.builder(method.name)
      .addModifiers(KModifier.SUSPEND)
    
    // Add HTTP mapping annotation
    method.httpMapping.foreach { mapping =>
      val annotationClass = mapping.method match {
        case HttpMethod.GET => "org.springframework.web.bind.annotation.GetMapping"
        case HttpMethod.POST => "org.springframework.web.bind.annotation.PostMapping"
        case HttpMethod.PUT => "org.springframework.web.bind.annotation.PutMapping"
        case HttpMethod.DELETE => "org.springframework.web.bind.annotation.DeleteMapping"
        case HttpMethod.PATCH => "org.springframework.web.bind.annotation.PatchMapping"
      }
      
      funBuilder.addAnnotation(
        AnnotationSpec.builder(ClassName.bestGuess(annotationClass))
          .addMember("%S", mapping.path)
          .build()
      )
    }
    
    // Add parameters with appropriate annotations
    method.parameters.foreach { param =>
      val paramBuilder = ParameterSpec.builder(param.name, renderTypeName(param.paramType))
      
      param.annotation match {
        case Some(ParameterAnnotation.PathVariable) =>
          paramBuilder.addAnnotation(
            ClassName.bestGuess("org.springframework.web.bind.annotation.PathVariable")
          )
        case Some(ParameterAnnotation.RequestBody) =>
          paramBuilder.addAnnotation(
            ClassName.bestGuess("org.springframework.web.bind.annotation.RequestBody")
          )
        case Some(ParameterAnnotation.RequestParam) =>
          paramBuilder.addAnnotation(
            ClassName.bestGuess("org.springframework.web.bind.annotation.RequestParam")
          )
        case None => ()
      }
      
      funBuilder.addParameter(paramBuilder.build())
    }
    
    // Return type
    method.returnType match {
      case Type.Unit => () // No return type
      case other => funBuilder.returns(renderTypeName(other))
    }
    
    funBuilder.build()
  }
}
```

---

## Dependency Mapping

### Scala Dependencies → Spring Boot Kotlin

| Scala Dependency | Spring Boot Kotlin Equivalent |
|------------------|------------------------------|
| cats-effect | kotlinx-coroutines-reactor |
| http4s | spring-boot-starter-webflux |
| mongo-scala-driver | spring-boot-starter-data-mongodb-reactive |
| circe | jackson-module-kotlin |
| kafka-clients | spring-kafka |
| logback | spring-boot-starter-logging |
| pureconfig | spring-boot-configuration-processor |

### Example build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.4"
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
    
    // Jackson for JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Kafka
    implementation("org.springframework.kafka:spring-kafka")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
}
```

---

## Application Structure

### Spring Boot Project Layout

```
src/
├── main/
│   ├── kotlin/
│   │   └── com/breuninger/
│   │       ├── Application.kt                    # Spring Boot entry point
│   │       ├── domain/
│   │       │   ├── model/                        # Domain models (no Spring)
│   │       │   │   ├── ArtikelId.kt
│   │       │   │   └── BestandCreateDocument.kt
│   │       │   └── repository/                   # Ports (no Spring)
│   │       │       └── BestandRepository.kt
│   │       ├── application/
│   │       │   ├── service/                      # @Service classes
│   │       │   │   └── BestandAssemblerService.kt
│   │       │   └── config/                       # Spring configuration
│   │       │       └── MongoConfig.kt
│   │       └── ports/
│   │           ├── persistence/                  # @Repository classes
│   │           │   └── bestand/
│   │           │       └── MongoBestandRepository.kt
│   │           ├── kafka/                        # @Component for Kafka
│   │           │   └── BestandMessageProducer.kt
│   │           └── rest/                         # @RestController classes
│   │               └── BestandController.kt
│   │
│   └── resources/
│       ├── application.yml                       # Spring Boot config
│       └── application-test.yml
│
└── test/
    └── kotlin/
        └── com/breuninger/
            ├── integration/                      # @SpringBootTest
            └── unit/
```

### Application Entry Point

```kotlin
// Application.kt
package com.breuninger

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ProduktAssemblerApplication

fun main(args: Array<String>) {
    runApplication<ProduktAssemblerApplication>(*args)
}
```

### Configuration

```yaml
# application.yml
spring:
  application:
    name: produkt-assembler
  
  data:
    mongodb:
      uri: mongodb://localhost:27017/produkt-assembler
      database: produkt-assembler
  
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: produkt-assembler
      auto-offset-reset: earliest
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8080

logging:
  level:
    com.breuninger: DEBUG
    org.springframework.data.mongodb: DEBUG
```

---

## Architectural Rules for Spring Boot

### Updated Validation Rules

Our grammar validator needs additional rules for Spring Boot:

```scala
// validator/SpringArchValidator.scala
package com.breuninger.arch.validator

object SpringArchValidator {
  
  // Rule: Domain layer must not have Spring annotations
  def validateDomainLayerPure(model: ArchitectureModel): List[ValidationError] = {
    model.ports.filter { port =>
      port.springAnnotations.nonEmpty
    }.map { port =>
      ValidationError.DomainLayerHasSpringAnnotation(port)
    }
  }
  
  // Rule: All adapters must have appropriate Spring stereotype
  def validateAdaptersHaveStereotype(adapters: List[Adapter]): List[ValidationError] = {
    adapters.filter { adapter =>
      !hasValidStereotype(adapter)
    }.map { adapter =>
      ValidationError.AdapterMissingSpringStereotype(adapter)
    }
  }
  
  private def hasValidStereotype(adapter: Adapter): Boolean = {
    adapter.springAnnotations.exists {
      case SpringAnnotation.Repository => true
      case SpringAnnotation.Service => true
      case SpringAnnotation.RestController => true
      case SpringAnnotation.Component => true
      case _ => false
    }
  }
  
  // Rule: REST controllers must have HTTP mappings
  def validateControllersHaveMappings(
    controllers: List[RestAdapter]
  ): List[ValidationError] = {
    controllers.flatMap { controller =>
      controller.methods.filter(_.httpMapping.isEmpty).map { method =>
        ValidationError.ControllerMethodMissingHttpMapping(controller, method)
      }
    }
  }
  
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
}
```

---

## Migration Examples

### Example 1: Repository Migration

**Scala (with custom DI):**
```scala
// Scala
package com.breuninger.ports.persistence.bestand

import cats.effect.IO
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.Document

class MongoBestandRepository(
  bestandCollection: MongoCollection[Document] @@ BestandTag
) extends BestandRepository {
  
  override def save(bestand: BestandCreateDocument): IO[Unit] = {
    IO.fromFuture(IO(
      bestandCollection.insertOne(toBson(bestand)).toFuture()
    )).void
  }
}
```

**Kotlin + Spring Boot:**
```kotlin
// Kotlin + Spring Boot
package com.breuninger.ports.persistence.bestand

import com.breuninger.domain.repository.BestandRepository
import com.breuninger.domain.model.*
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactive.awaitSingle

@Repository
class MongoBestandRepository(
  private val mongoTemplate: ReactiveMongoTemplate
) : BestandRepository {
  
  override suspend fun save(bestand: BestandCreateDocument) {
    mongoTemplate.save(bestand).awaitSingle()
  }
}
```

**Key Changes:**
- `@Repository` annotation
- `ReactiveMongoTemplate` instead of `MongoCollection`
- `suspend fun` instead of `IO[A]`
- `.awaitSingle()` for coroutine integration

### Example 2: Service Migration

**Scala:**
```scala
// Scala
package com.breuninger.application.service

import cats.effect.IO

class BestandAssemblerService(
  bestandRepository: BestandRepository,
  stammdatenRepository: ProduktStammdatenRepository
) {
  
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
}
```

**Kotlin + Spring Boot:**
```kotlin
// Kotlin + Spring Boot
package com.breuninger.application.service

import com.breuninger.domain.repository.*
import com.breuninger.domain.model.*
import org.springframework.stereotype.Service

@Service
class BestandAssemblerService(
  private val bestandRepository: BestandRepository,
  private val stammdatenRepository: ProduktStammdatenRepository
) {
  
  suspend fun assembleBestand(artikelId: ArtikelId): AssembledBestand {
    val bestand = bestandRepository.getByIds(listOf(artikelId)).firstOrNull()
      ?: throw NotFoundException("Bestand not found for $artikelId")
    
    val stammdaten = stammdatenRepository.getById(artikelId)
      ?: throw NotFoundException("Stammdaten not found for $artikelId")
    
    return AssembledBestand(bestand, stammdaten)
  }
}
```

**Key Changes:**
- `@Service` annotation
- Constructor injection (Spring auto-wires)
- `suspend fun` instead of `IO[A]`
- Imperative style with `?:` elvis operator instead of flatMap

### Example 3: REST Controller Migration

**Scala (http4s):**
```scala
// Scala (http4s)
package com.breuninger.ports.rest

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._

class BestandRoutes(
  bestandService: BestandAssemblerService
) extends Http4sDsl[IO] {
  
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "api" / "bestand" / artikelId =>
      bestandService.assembleBestand(ArtikelId(artikelId))
        .flatMap(bestand => Ok(bestand.asJson))
        .handleErrorWith {
          case _: NotFoundException => NotFound()
          case e => InternalServerError(e.getMessage)
        }
  }
}
```

**Kotlin + Spring Boot:**
```kotlin
// Kotlin + Spring Boot
package com.breuninger.ports.rest

import com.breuninger.application.service.BestandAssemblerService
import com.breuninger.domain.model.ArtikelId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/bestand")
class BestandController(
  private val bestandService: BestandAssemblerService
) {
  
  @GetMapping("/{artikelId}")
  suspend fun getBestand(@PathVariable artikelId: String): AssembledBestandDto {
    return try {
      val assembled = bestandService.assembleBestand(ArtikelId(artikelId))
      AssembledBestandDto.fromDomain(assembled)
    } catch (e: NotFoundException) {
      throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }
  }
}
```

**Key Changes:**
- `@RestController` instead of `HttpRoutes`
- `@RequestMapping` for base path
- `@GetMapping` instead of pattern matching
- `@PathVariable` for path parameters
- Spring exception handling with `ResponseStatusException`

---

## Summary

### What Changes with Spring Boot

1. **Dependency Injection** - Spring auto-wiring replaces custom DI modules
2. **Web Framework** - Spring MVC annotations replace http4s DSL
3. **Data Access** - Spring Data templates replace raw drivers
4. **Effect System** - Kotlin coroutines with Spring reactive support
5. **Configuration** - YAML files replace Scala config
6. **Component Model** - Spring stereotypes (@Service, @Repository, @Controller)

### What Stays the Same

1. **Hexagonal Architecture** - Ports and adapters pattern preserved
2. **Domain Purity** - Domain layer has no framework dependencies
3. **Architectural Rules** - Layer boundaries still enforced
4. **Grammar Tool** - Still converts Scala AST → Abstract Model → Kotlin

### Grammar Tool Updates Needed

1. Add Spring annotation support to abstract model
2. Update renderer to generate Spring stereotypes
3. Add validator rules for Spring-specific constraints
4. Handle Spring Data patterns in persistence adapters
5. Generate Spring MVC annotations for REST controllers

The grammar-based approach still works - we just extend it to understand and generate Spring Boot patterns!
