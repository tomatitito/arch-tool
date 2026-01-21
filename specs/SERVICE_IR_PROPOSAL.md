# Application/Service Layer IR Representation Proposal

**Issue**: at-13w
**Date**: 2026-01-13
**Author**: arch_tool_crew

## Summary

The ARCHITECTURE_GRAMMAR.md defines Application and Service as separate architectural concepts (lines 118-121), but the current IR (Intermediate Representation) only includes Port, PortImplementation, and PortType. This document investigates the gap and proposes an IR design for representing application services.

## Current State Analysis

### 1. Grammar Definition

From ARCHITECTURE_GRAMMAR.md:118-121:

```ebnf
(* ===== APPLICATION LAYER ===== *)
Application ::= Service*
Service ::= Identifier PortDependency+
PortDependency ::= Port
```

This defines:
- **Application**: A collection of Services
- **Service**: Has an identifier (name) and depends on one or more Ports
- **PortDependency**: A reference to a Port interface

From ARCHITECTURE_GRAMMAR.md:77-78:
> ### 3. Application Layer - Use Cases and Orchestration
> - Services that orchestrate domain logic using repository ports

### 2. Current IR Structure

From PLAN_GRAMMAR_POC.md:563-572 (`model/Domain.scala`):

```scala
case class ArchitectureModel(
  ports: List[Port],
  models: List[DomainModel],
  adapters: List[Adapter]
)
```

**Missing**:
- No `Service` representation
- No `Application` representation
- No way to capture service dependencies on ports

### 3. Supporting Evidence

**Layer validation exists** (PLAN_GRAMMAR_POC.md:1217-1222):
```scala
sealed trait Layer
object Layer {
  case object Domain extends Layer
  case object Application extends Layer  // ← Layer exists
  case object Ports extends Layer
}
```

**Validation rules reference services** (ARCHITECTURE_GRAMMAR.md:455-460):
```
✗ Invalid - Service in Port Layer
Service: MongoBestandService
Location: ports/persistence/
✗ Services must be in application layer
```

## Gap Analysis

### What's Missing

| Concept | Grammar | Current IR | Status |
|---------|---------|------------|--------|
| Port | ✅ Defined | ✅ `Port` case class | ✅ Complete |
| DomainModel | ✅ Defined | ✅ `DomainModel` sealed trait | ✅ Complete |
| Adapter | ✅ Defined | ✅ `Adapter` sealed trait | ✅ Complete |
| **Service** | ✅ Defined | ❌ Missing | 🔴 Gap |
| **Application** | ✅ Defined | ❌ Missing | 🔴 Gap |

### Implications

Without Service representation in the IR:

1. **Cannot parse** application services from Scala codebase
2. **Cannot validate** that services are in the correct layer
3. **Cannot generate** service class skeletons in Kotlin
4. **Cannot enforce** dependency rules (services can only depend on ports)
5. **Incomplete architecture model** - missing entire layer

## Investigation: Service Patterns in Practice

### What is an Application Service?

**Characteristics**:
- Lives in `application/` package layer
- Orchestrates domain logic
- Depends on Port interfaces (repositories)
- Contains business logic (use cases)
- No direct dependencies on infrastructure (MongoDB, Kafka, etc.)

### Example Service (Hypothetical Scala)

```scala
package com.breuninger.application.service

import com.breuninger.domain.repository.BestandRepository
import com.breuninger.domain.repository.MessageRepository
import cats.effect.IO

class BestandService(
  bestandRepository: BestandRepository,
  messageRepository: MessageRepository
) {
  def createBestand(data: BestandCreateDocument): IO[Unit] = {
    for {
      _ <- bestandRepository.save(data)
      _ <- messageRepository.publish(BestandCreatedEvent(data.id))
    } yield ()
  }

  def getBestaende(ids: List[ArtikelId]): IO[List[BestandCreateDocument]] = {
    bestandRepository.getByIds(ids)
  }
}
```

### Service vs. Adapter - Key Differences

| Aspect | Service (Application Layer) | Adapter (Ports Layer) |
|--------|----------------------------|----------------------|
| **Purpose** | Orchestrate use cases | Implement port interfaces |
| **Package** | `application/service/` | `ports/persistence/`, etc. |
| **Dependencies** | Depends on Ports | Depends on Infrastructure |
| **Implements** | Nothing (standalone class) | Implements Port interface |
| **Business Logic** | Orchestration, workflows | Data access, messaging |
| **Example** | `BestandService` | `MongoBestandRepository` |

**Key Insight**: Services *use* ports, Adapters *implement* ports.

## Proposed IR Design

### Option 1: Add Service to ArchitectureModel (Recommended)

```scala
// model/Service.scala
package com.breuninger.arch.model

case class Service(
  name: String,
  packageName: String,
  portDependencies: List[PortDependency],
  methods: List[Method]  // Optional: service methods
)

case class PortDependency(
  portName: String,      // Name of the port interface
  parameterName: String  // Name of the constructor parameter
)

// model/Domain.scala
case class ArchitectureModel(
  ports: List[Port],
  models: List[DomainModel],
  adapters: List[Adapter],
  services: List[Service]  // ← NEW: Application services
)
```

**Pros**:
- Simple extension to existing model
- Mirrors grammar structure
- Easy to validate services list

**Cons**:
- Flat structure (no explicit Application container)

### Option 2: Explicit Application Container

```scala
// model/Application.scala
package com.breuninger.arch.model

case class Application(
  services: List[Service]
)

// model/Domain.scala
case class ArchitectureModel(
  domain: Domain,        // ← Wrap existing domain concepts
  application: Application,
  adapters: List[Adapter]
)

case class Domain(
  ports: List[Port],
  models: List[DomainModel]
)
```

**Pros**:
- Matches grammar exactly: `System ::= Domain Application Ports`
- More explicit layer separation
- Better for future extensions (e.g., application events)

**Cons**:
- More refactoring required
- Breaking change to existing IR

### Option 3: Minimal - Service as Method Container Only

```scala
case class Service(
  name: String,
  packageName: String,
  methods: List[Method]
)
```

**Pros**:
- Minimal change
- Focuses on generation needs

**Cons**:
- Loses dependency information
- Cannot validate port dependencies
- Incomplete architectural representation

## Recommendation: Option 1

**Choose Option 1** because:
1. **Minimal disruption**: Extends existing model without breaking changes
2. **Complete information**: Captures port dependencies for validation
3. **Grammar alignment**: `Application ::= Service*` → `services: List[Service]`
4. **Practical**: Sufficient for parsing, validation, and generation

**Deferred**: Option 2 can be a future refactoring if we need stronger layer separation.

## Implementation Plan

### Step 1: Define Service Model

Add to `model/Service.scala`:

```scala
package com.breuninger.arch.model

case class Service(
  name: String,
  packageName: String,
  portDependencies: List[PortDependency],
  methods: List[Method] = List.empty  // Optional, for skeleton generation
)

case class PortDependency(
  portName: String,       // e.g., "BestandRepository"
  parameterName: String   // e.g., "bestandRepository"
)
```

### Step 2: Update ArchitectureModel

Modify `model/Domain.scala`:

```scala
case class ArchitectureModel(
  ports: List[Port],
  models: List[DomainModel],
  adapters: List[Adapter],
  services: List[Service]  // NEW
)
```

### Step 3: Parser - Extract Services from Scala

Add to `parser/ServiceParser.scala`:

```scala
package com.breuninger.arch.parser

import scala.meta._
import com.breuninger.arch.model._

object ServiceParser {

  def parseService(source: String): Either[String, Service] = {
    try {
      val tree = source.parse[Source].get

      // Extract package name
      val packageName = tree.collect {
        case Pkg(ref, _) => ref.toString
      }.headOption.getOrElse("")

      // Find class definition
      val classDef = tree.collect {
        case c @ Defn.Class(_, name, _, ctor, template)
          if isService(name.value) =>
            (name.value, ctor, template)
      }.headOption

      classDef match {
        case Some((name, ctor, template)) =>
          val dependencies = extractPortDependencies(ctor)
          val methods = extractMethods(template)

          Right(Service(
            name = name,
            packageName = packageName,
            portDependencies = dependencies,
            methods = methods
          ))

        case None =>
          Left("No service class found")
      }
    } catch {
      case e: Exception => Left(s"Parse error: ${e.getMessage}")
    }
  }

  private def isService(name: String): Boolean =
    name.endsWith("Service")

  private def extractPortDependencies(
    ctor: Ctor.Primary
  ): List[PortDependency] = {
    ctor.paramss.flatten.collect {
      case Term.Param(_, paramName, Some(Type.Name(typeName)), _)
        if typeName.endsWith("Repository") =>
          PortDependency(
            portName = typeName,
            parameterName = paramName.value
          )
    }
  }

  private def extractMethods(template: Template): List[Method] = {
    template.stats.collect {
      case Defn.Def(_, methodName, _, paramss, Some(returnType), _) =>
        Method(
          name = methodName.value,
          parameters = paramss.flatten.map { param =>
            Parameter(
              name = param.name.value,
              paramType = TypeParser.parseType(
                param.decltpe.getOrElse(Type.Name("Any"))
              )
            )
          },
          returnType = TypeParser.parseType(returnType)
        )
    }
  }
}
```

### Step 4: Validator - Enforce Service Rules

Add to `validator/ServiceValidator.scala`:

```scala
package com.breuninger.arch.validator

import com.breuninger.arch.model._

object ServiceValidator {

  def validateServices(
    services: List[Service],
    ports: List[Port]
  ): List[ValidationError] = {
    List(
      validateServicesInApplicationPackage(services),
      validatePortDependenciesExist(services, ports),
      validateServicesOnlyDependOnPorts(services)
    ).flatten
  }

  private def validateServicesInApplicationPackage(
    services: List[Service]
  ): List[ValidationError] = {
    services.filter { service =>
      !service.packageName.contains("application")
    }.map { service =>
      ValidationError.ServiceNotInApplicationPackage(service)
    }
  }

  private def validatePortDependenciesExist(
    services: List[Service],
    ports: List[Port]
  ): List[ValidationError] = {
    val portNames = ports.map(_.name).toSet

    services.flatMap { service =>
      service.portDependencies.collect {
        case dep if !portNames.contains(dep.portName) =>
          ValidationError.ServiceDependsOnNonExistentPort(service, dep.portName)
      }
    }
  }

  private def validateServicesOnlyDependOnPorts(
    services: List[Service]
  ): List[ValidationError] = {
    // Check that service constructor parameters are either:
    // 1. Port interfaces (end with Repository, etc.)
    // 2. Other services
    // 3. NOT adapters or infrastructure types

    services.flatMap { service =>
      service.portDependencies.collect {
        case dep if isInfrastructureType(dep.portName) =>
          ValidationError.ServiceDependsOnInfrastructure(service, dep.portName)
      }
    }
  }

  private def isInfrastructureType(typeName: String): Boolean = {
    val infraTypes = Set(
      "MongoCollection", "MongoTemplate",
      "KafkaProducer", "KafkaConsumer",
      "RestTemplate", "WebClient"
    )
    infraTypes.contains(typeName)
  }
}

// Add to ValidationError
object ValidationError {
  case class ServiceNotInApplicationPackage(service: Service) extends ValidationError {
    def message = s"Service ${service.name} must be in application package, found in ${service.packageName}"
  }

  case class ServiceDependsOnNonExistentPort(service: Service, portName: String) extends ValidationError {
    def message = s"Service ${service.name} depends on non-existent port $portName"
  }

  case class ServiceDependsOnInfrastructure(service: Service, infraType: String) extends ValidationError {
    def message = s"Service ${service.name} depends on infrastructure type $infraType (should depend on ports)"
  }
}
```

### Step 5: Renderer - Generate Service Skeletons

Add to `renderer/ServiceRenderer.scala`:

```scala
package com.breuninger.arch.renderer

import com.breuninger.arch.model._
import com.squareup.kotlinpoet._

object ServiceRenderer {

  def renderService(service: Service): String = {
    val classBuilder = TypeSpec.classBuilder(service.name)
      .addAnnotation(
        ClassName.bestGuess("org.springframework.stereotype.Service")
      )

    // Add constructor with port dependencies
    val constructorBuilder = FunSpec.constructorBuilder()

    service.portDependencies.foreach { dep =>
      val portType = ClassName.bestGuess(dep.portName)

      constructorBuilder.addParameter(
        ParameterSpec.builder(
          dep.parameterName,
          portType
        ).build()
      )

      // Add as private property
      classBuilder.addProperty(
        PropertySpec.builder(
          dep.parameterName,
          portType
        )
        .addModifiers(KModifier.PRIVATE)
        .initializer(dep.parameterName)
        .build()
      )
    }

    classBuilder.primaryConstructor(constructorBuilder.build())

    // Add method skeletons (if provided)
    service.methods.foreach { method =>
      val funSpec = renderServiceMethod(method)
      classBuilder.addFunction(funSpec)
    }

    val fileSpec = FileSpec.builder(service.packageName, service.name)
      .addType(classBuilder.build())
      .build()

    fileSpec.toString
  }

  private def renderServiceMethod(method: Method): FunSpec = {
    val funBuilder = FunSpec.builder(method.name)

    // Add parameters
    method.parameters.foreach { param =>
      funBuilder.addParameter(
        param.name,
        KotlinRenderer.renderTypeName(param.paramType)
      )
    }

    // Handle return type
    method.returnType match {
      case Type.Effect(wrapped, Type.EffectType.IO | Type.EffectType.Suspend) =>
        funBuilder.addModifiers(KModifier.SUSPEND)
        wrapped match {
          case Type.Unit => ()
          case other => funBuilder.returns(KotlinRenderer.renderTypeName(other))
        }

      case other =>
        funBuilder.returns(KotlinRenderer.renderTypeName(other))
    }

    // Add TODO body
    funBuilder.addCode("TODO(\"Implement service logic\")\n")

    funBuilder.build()
  }
}
```

### Step 6: Example Output

**Input Scala**:
```scala
package com.breuninger.application.service

import com.breuninger.domain.repository.BestandRepository
import cats.effect.IO

class BestandService(
  bestandRepository: BestandRepository
) {
  def createBestand(data: BestandCreateDocument): IO[Unit] = ???
}
```

**Generated Kotlin**:
```kotlin
package com.breuninger.application.service

import org.springframework.stereotype.Service

@Service
class BestandService(
    private val bestandRepository: BestandRepository
) {
    suspend fun createBestand(data: BestandCreateDocument) {
        TODO("Implement service logic")
    }
}
```

## Validation Rules

### Rule 1: Services in Application Package
```
service.packageName.contains("application")
```

### Rule 2: Services Only Depend on Ports
```
∀ dep ∈ service.portDependencies:
  dep.portName ∈ {port.name | port ∈ ports}
```

### Rule 3: Services Don't Depend on Infrastructure
```
∀ dep ∈ service.portDependencies:
  dep.portName ∉ {MongoCollection, KafkaProducer, ...}
```

### Rule 4: Layer Dependency Direction
```
Application → Domain ← Ports
Services depend on Ports (not vice versa)
```

## Testing Strategy

### Unit Tests

```scala
class ServiceParserSpec extends AnyFlatSpec with Matchers {

  "ServiceParser" should "parse BestandService correctly" in {
    val source = """
      package com.breuninger.application.service

      class BestandService(
        bestandRepository: BestandRepository,
        messageRepository: MessageRepository
      ) {
        def createBestand(data: BestandCreateDocument): IO[Unit] = ???
      }
    """

    val result = ServiceParser.parseService(source)

    result shouldBe a[Right[_, _]]
    val service = result.toOption.get

    service.name shouldBe "BestandService"
    service.packageName shouldBe "com.breuninger.application.service"
    service.portDependencies should have size 2
    service.portDependencies.map(_.portName) should contain allOf (
      "BestandRepository",
      "MessageRepository"
    )
  }
}

class ServiceValidatorSpec extends AnyFlatSpec with Matchers {

  "ServiceValidator" should "detect service in wrong package" in {
    val service = Service(
      name = "BestandService",
      packageName = "com.breuninger.ports.service",  // Wrong!
      portDependencies = List.empty
    )

    val errors = ServiceValidator.validateServicesInApplicationPackage(List(service))

    errors should not be empty
    errors.head shouldBe a[ValidationError.ServiceNotInApplicationPackage]
  }

  it should "detect dependency on non-existent port" in {
    val service = Service(
      name = "BestandService",
      packageName = "com.breuninger.application.service",
      portDependencies = List(
        PortDependency("NonExistentRepository", "repo")
      )
    )

    val ports = List.empty[Port]

    val errors = ServiceValidator.validatePortDependenciesExist(List(service), ports)

    errors should not be empty
  }
}
```

## Migration Impact

### Backward Compatibility

**Breaking Change**: Yes
- `ArchitectureModel` constructor signature changes
- Existing code must add `services = List.empty` parameter

**Migration Path**:
```scala
// Old
ArchitectureModel(ports, models, adapters)

// New
ArchitectureModel(ports, models, adapters, services = List.empty)
```

### Rollout Plan

1. **Phase 1**: Add Service model, keep services optional
2. **Phase 2**: Update parser to extract services
3. **Phase 3**: Add service validation
4. **Phase 4**: Add service rendering
5. **Phase 5**: Make services required (non-empty validation)

## Alternatives Considered

### Alternative 1: Treat Services as Special Adapters

**Idea**: Make Service a subtype of Adapter

```scala
sealed trait Adapter
case class ServiceAdapter(...) extends Adapter
case class PersistenceAdapter(...) extends Adapter
```

**Rejected because**:
- Services don't implement ports (adapters do)
- Conceptually wrong layer (services are Application, adapters are Ports)
- Violates grammar definition

### Alternative 2: Ignore Services

**Idea**: Don't model services in IR, only generate ports and adapters

**Rejected because**:
- Grammar explicitly defines Application layer
- Cannot validate layer boundaries
- Incomplete architecture representation
- Misses opportunity for service skeleton generation

## Conclusion

### Summary

1. **Gap Confirmed**: Application/Service layer is defined in grammar but missing from IR
2. **Design Proposed**: Extend `ArchitectureModel` with `services: List[Service]`
3. **Service Model**: Captures name, package, port dependencies, and optional methods
4. **Implementation Path**: Parser → Validator → Renderer
5. **Validation Rules**: Package location, port dependencies, no infrastructure deps

### Recommendation

**Implement Option 1**:
- Add `Service` case class with port dependencies
- Extend `ArchitectureModel` with services field
- Implement parsing, validation, and rendering for services
- This completes the IR representation of all three layers: Domain, Application, Ports

### Next Steps

1. Get approval on Service model design
2. Implement Service and PortDependency case classes
3. Update ArchitectureModel
4. Implement ServiceParser
5. Implement ServiceValidator
6. Implement ServiceRenderer
7. Add comprehensive tests
8. Update documentation with service examples

---

**References**:
- ARCHITECTURE_GRAMMAR.md:118-121 (Application/Service grammar)
- PLAN_GRAMMAR_POC.md:563-572 (Current ArchitectureModel)
- PLAN_GRAMMAR_POC.md:1217-1249 (Layer validation)
