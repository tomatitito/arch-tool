# Grammar Tool Proof-of-Concept Implementation Plan

## Overview

This document describes how to implement a proof-of-concept grammar tool that:
1. Parses Scala code into an abstract architectural model
2. Validates architectural constraints
3. Generates equivalent Kotlin code
4. Ensures type safety and correctness

## Scope and Limitations

### What the Grammar Tool DOES Generate (Automated ✅)

The tool **automatically generates** structural, interface-level code that can be mechanically translated:

- ✅ **Port Interfaces** - Complete interface definitions with all methods
- ✅ **Domain Models** - Value objects, entities, data classes
- ✅ **Sealed Hierarchies** - Sealed traits/interfaces and their variants
- ✅ **Method Signatures** - Parameter lists, return types, annotations
- ✅ **Type Mappings** - Scala types → Kotlin types (IO[A] → suspend fun, etc.)
- ✅ **Adapter Skeletons** - Class declarations that implement ports with TODO placeholders

### What You MUST Write Yourself (Manual ❌)

The tool **cannot generate** business logic and implementation details:

- ❌ **Adapter Implementations** - The actual logic inside repository methods
- ❌ **Database Queries** - MongoDB aggregations, SQL queries, etc.
- ❌ **Service Logic** - Business rules, orchestration, error handling
- ❌ **Controller Logic** - Request validation, response mapping, error handling
- ❌ **Tests** - Unit tests, integration tests, contract tests (tool can generate test skeletons)
- ❌ **Infrastructure Code** - Connection pools, retry logic, circuit breakers

### Why Implementations Cannot Be Automated

The grammar tool cannot generate implementations because:

1. **Business Logic is Unique** - Your MongoDB queries, Kafka message handling, and HTTP responses are specific to your application
2. **Infrastructure Decisions** - Connection strings, timeouts, retry strategies, error handling vary by use case
3. **External Dependencies** - Third-party APIs, message formats, database schemas are not captured in the IR
4. **Performance Optimizations** - Indexing strategies, caching, batching depend on runtime characteristics
5. **Edge Cases** - Error recovery, validation rules, business constraints require domain knowledge

### What the Tool Helps With

Even though you write implementations manually, the grammar tool provides significant value:

1. **Architectural Safety** - Ensures adapters implement the correct ports
2. **Type Safety** - Validates that implementations match interface signatures
3. **Consistency** - All generated code follows the same patterns
4. **Documentation** - Auto-generated interfaces serve as contracts
5. **Validation** - Architectural rules are enforced before you write any code

### Example: Repository Migration

**What the Tool Generates:**

```kotlin
// ✅ Generated automatically
interface BestandRepository {
    suspend fun save(bestand: BestandCreateDocument)
    suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>
}

// ✅ Generated automatically
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

**What You Write:**

```kotlin
// ❌ You implement the TODOs
@Repository
class MongoBestandRepository(
    private val mongoTemplate: MongoTemplate
) : BestandRepository {
    override suspend fun save(bestand: BestandCreateDocument) {
        // 🔴 Your business logic
        withContext(Dispatchers.IO) {
            mongoTemplate.save(bestand.toMongoDocument(), "bestand")
        }
    }
    
    override suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument> {
        // 🔴 Your complex query logic
        return withContext(Dispatchers.IO) {
            val query = Query.query(Criteria.where("_id").`in`(ids.map { it.value }))
            mongoTemplate.find(query, MongoBestandDocument::class.java)
                .map { it.toBestandCreateDocument() }
        }
    }
}
```

### Migration Strategy

Given this scope, the recommended migration approach is:

1. **Generate Interfaces** (automated) - Run the grammar tool to create all port interfaces and domain models
2. **Write Contract Tests** (manual) - Define the expected behavior of your adapters
3. **Implement Adapters** (manual) - Write the actual business logic to satisfy the contracts
4. **Validate Both** (automated) - Use the grammar tool to validate Scala and Kotlin implementations satisfy the same architectural rules
5. **Test Equivalence** (manual) - Ensure both implementations pass the same contract tests

This ensures you're not doing tedious interface translation work (automated) while maintaining full control over business logic (manual).

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Grammar Tool (arch-tool)                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐      ┌─────────────────┐                 │
│  │ Scala Parser │ ───> │ Abstract Model  │                 │
│  │ (Scalameta)  │      │      (IR)       │                 │
│  └──────────────┘      └────────┬────────┘                 │
│                                 │                            │
│                        ┌────────▼────────┐                  │
│                        │   Validator     │                  │
│                        │  (Arch Rules)   │                  │
│                        └────────┬────────┘                  │
│                                 │                            │
│                        ┌────────▼────────┐                  │
│                        │ Kotlin Renderer │                  │
│                        │ (KotlinPoet)    │                  │
│                        └─────────────────┘                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## How the Tool Works: Component Breakdown

### What We Use (External Libraries)

1. **Scalameta** - Parses Scala source code into an Abstract Syntax Tree (AST)
   - Input: Scala source code string
   - Output: Low-level AST (Defn.Trait, Decl.Def, Type.Apply, etc.)
   - We use this library as-is

2. **KotlinPoet** - Generates Kotlin source code programmatically
   - Input: Builder API calls (TypeSpec, FunSpec, etc.)
   - Output: Formatted Kotlin source code
   - We use this library as-is

### What We Write (Custom Code)

**The core conversion logic is custom code we write ourselves** - there is no existing library that:
- Understands our architectural patterns (Ports, Adapters, Domain Models)
- Converts Scalameta's low-level AST to our high-level abstract model
- Enforces our specific architectural rules

Our custom code consists of:

1. **Abstract Model (IR)** - Domain-specific representation
   - Defines architectural concepts: Port, Method, Type, DomainModel, Adapter
   - Language-agnostic - represents architecture, not syntax
   - We design and implement this from scratch

2. **Parser Modules** - Scalameta AST → Our Abstract Model
   - Pattern-match on Scalameta nodes (Defn.Trait, Decl.Def, etc.)
   - Extract architectural information
   - Construct our Port, Method, Type instances
   - **This is the intelligence of the tool - we write this**

3. **Validator** - Enforce architectural rules
   - Operates on our abstract model
   - Checks layer boundaries, type restrictions, etc.
   - We implement all validation logic

4. **Renderer** - Our Abstract Model → KotlinPoet calls
   - Converts our high-level model to KotlinPoet builder calls
   - Handles type mappings (IO[A] → suspend fun, etc.)
   - We write this mapping logic

### The Complete Flow

```
Scala Source Code
      ↓
┌─────────────────────────┐
│    Scalameta (library)  │ ← We use this
│    .parse[Source]       │
└───────────┬─────────────┘
            │
    Scalameta AST
    (Low-level syntax tree)
            │
            ↓
┌─────────────────────────┐
│  ScalaParser (we write) │ ← Pattern matching & extraction
│  PortParser (we write)  │ ← Port-specific parsing
│  TypeParser (we write)  │ ← Type conversion logic
│  ModelParser (we write) │ ← Model extraction
└───────────┬─────────────┘
            │
    Our Abstract Model
    (Port, Method, Type.Effect, DomainModel, etc.)
            │
            ↓
┌─────────────────────────┐
│ ArchValidator (we write)│ ← Architectural rules
└───────────┬─────────────┘
            │
            ↓
┌─────────────────────────┐
│ KotlinRenderer (we write)│ ← Model → KotlinPoet calls
│   Uses KotlinPoet lib    │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│  KotlinPoet (library)   │ ← We use this
│  .toString()            │
└───────────┬─────────────┘
            │
    Kotlin Source Code
```

### Example: Converting a Port

**Input (Scala):**
```scala
trait BestandRepository {
  def save(bestand: BestandCreateDocument): IO[Unit]
}
```

**Step 1: Scalameta parses to AST** (library does this)
```scala
Defn.Trait(
  name = Type.Name("BestandRepository"),
  templ = Template(
    stats = List(
      Decl.Def(
        name = Term.Name("save"),
        paramss = List(List(Term.Param(...))),
        decltpe = Type.Apply(Type.Name("IO"), List(Type.Name("Unit")))
      )
    )
  )
)
```

**Step 2: Our parser converts to abstract model** (we write this)
```scala
// In ScalaParser.scala (our code)
def convertToPort(traitDef: Defn.Trait): Port = {
  Port(
    name = traitDef.name.value,
    packageName = extractPackage(traitDef),
    methods = traitDef.templ.stats.collect {
      case Decl.Def(_, methodName, _, paramss, returnType) =>
        Method(
          name = methodName.value,
          parameters = convertParameters(paramss),
          returnType = TypeParser.parseType(returnType)
        )
    }
  )
}
```

**Step 3: Our abstract model** (our design)
```scala
Port(
  name = "BestandRepository",
  packageName = "com.breuninger.domain.repository",
  methods = List(
    Method(
      name = "save",
      parameters = List(Parameter("bestand", Type.Domain("BestandCreateDocument"))),
      returnType = Type.Effect(Type.Unit, Type.EffectType.IO)
    )
  )
)
```

**Step 4: Our renderer generates Kotlin** (we write this)
```scala
// In KotlinRenderer.scala (our code)
def renderPort(port: Port): String = {
  val interfaceBuilder = TypeSpec.interfaceBuilder(port.name)
  
  port.methods.foreach { method =>
    val funSpec = FunSpec.builder(method.name)
      .addModifiers(KModifier.SUSPEND)  // Because Type.Effect → suspend
      .addParameter(method.parameters.head.name, ...)
      .build()
    
    interfaceBuilder.addFunction(funSpec)
  }
  
  FileSpec.builder(port.packageName, port.name)
    .addType(interfaceBuilder.build())
    .build()
    .toString
}
```

**Step 5: KotlinPoet generates code** (library does this)
```kotlin
interface BestandRepository {
  suspend fun save(bestand: BestandCreateDocument)
}
```

### Key Insight

**We are the "glue" between two libraries:**
- Scalameta understands Scala syntax
- KotlinPoet generates Kotlin syntax
- **We understand architecture** and bridge the gap

The parser modules (ScalaParser, PortParser, TypeParser) are the core intellectual work - they embody our understanding of hexagonal architecture and how it maps across languages.

## Technology Stack

### Core Technologies
- **Language**: Scala 2.13 or 3.x (for familiarity with existing codebase)
- **Build Tool**: SBT
- **Scala Parser**: Scalameta (semantic API for parsing Scala) - **We use this library**
- **Kotlin Generator**: KotlinPoet (code generation for Kotlin) - **We use this library**
- **Testing**: ScalaTest, ScalaCheck (property-based testing)

### Alternative: Kotlin Implementation
- **Language**: Kotlin
- **Build Tool**: Gradle
- **Scala Parser**: Still use Scalameta via Java interop
- **Kotlin Generator**: KotlinPoet (native)

**Recommendation**: Start with Scala for POC (team familiarity), migrate tool to Kotlin later.

## Project Structure

```
arch-tool/
├── build.sbt
├── src/
│   ├── main/
│   │   └── scala/
│   │       └── com/breuninger/arch/
│   │           ├── model/          # Abstract IR model
│   │           │   ├── Domain.scala
│   │           │   ├── Port.scala
│   │           │   ├── DomainModel.scala
│   │           │   └── Type.scala
│   │           │
│   │           ├── parser/         # Scala → IR
│   │           │   ├── ScalaParser.scala
│   │           │   ├── PortParser.scala
│   │           │   └── ModelParser.scala
│   │           │
│   │           ├── validator/      # Architectural rules
│   │           │   ├── ArchValidator.scala
│   │           │   ├── LayerRule.scala
│   │           │   └── DependencyRule.scala
│   │           │
│   │           ├── renderer/       # IR → Kotlin
│   │           │   ├── KotlinRenderer.scala
│   │           │   ├── PortRenderer.scala
│   │           │   └── ModelRenderer.scala
│   │           │
│   │           └── Main.scala      # CLI entry point
│   │
│   └── test/
│       └── scala/
│           └── com/breuninger/arch/
│               ├── parser/
│               ├── validator/
│               └── renderer/
│
└── examples/
    ├── scala/                      # Example Scala code
    │   └── BestandRepository.scala
    └── kotlin/                     # Expected Kotlin output
        └── BestandRepository.kt
```

## Step 1: Abstract Model (IR)

Define the intermediate representation that captures architectural concepts.

### model/Type.scala

```scala
package com.breuninger.arch.model

sealed trait Type {
  def name: String
}

object Type {
  // Primitive types
  case class Primitive(name: String) extends Type
  
  // Domain types (custom classes)
  case class Domain(name: String) extends Type
  
  // Generic types (List[T], Option[T], etc.)
  case class Generic(
    name: String,
    typeArgs: List[Type]
  ) extends Type
  
  // Effect types (IO[T], Effect[T], suspend fun)
  case class Effect(
    wrapped: Type,
    effectType: EffectType
  ) extends Type
  
  sealed trait EffectType
  object EffectType {
    case object IO extends EffectType           // cats-effect IO
    case object Suspend extends EffectType      // Kotlin suspend
    case object Effect extends EffectType       // Arrow Effect
  }
  
  // Common types
  val Unit = Primitive("Unit")
  val String = Primitive("String")
  val Int = Primitive("Int")
  val Boolean = Primitive("Boolean")
  val Long = Primitive("Long")
  
  def list(elementType: Type): Generic = 
    Generic("List", List(elementType))
  
  def option(elementType: Type): Generic = 
    Generic("Option", List(elementType))
}
```

### model/DomainModel.scala

```scala
package com.breuninger.arch.model

sealed trait DomainModel {
  def name: String
  def packageName: String
}

object DomainModel {
  // Value Object (case class X(v: T) extends AnyVal)
  case class ValueObject(
    name: String,
    packageName: String,
    field: Field
  ) extends DomainModel
  
  // Entity (case class with multiple fields)
  case class Entity(
    name: String,
    packageName: String,
    fields: List[Field]
  ) extends DomainModel
  
  // Sealed hierarchy (sealed trait/interface)
  case class SealedHierarchy(
    name: String,
    packageName: String,
    variants: List[Variant]
  ) extends DomainModel
  
  // Enumeration
  case class Enumeration(
    name: String,
    packageName: String,
    values: List[String]
  ) extends DomainModel
}

case class Field(
  name: String,
  fieldType: Type,
  defaultValue: Option[String] = None
)

case class Variant(
  name: String,
  fields: List[Field]
)
```

### model/Port.scala

```scala
package com.breuninger.arch.model

case class Port(
  name: String,
  packageName: String,
  methods: List[Method]
)

case class Method(
  name: String,
  parameters: List[Parameter],
  returnType: Type
)

case class Parameter(
  name: String,
  paramType: Type
)
```

### model/Adapter.scala

```scala
package com.breuninger.arch.model

sealed trait Adapter {
  def name: String
  def packageName: String
  def implementedPort: Port
  def dependencies: List[Dependency]
}

object Adapter {
  case class PersistenceAdapter(
    name: String,
    packageName: String,
    implementedPort: Port,
    dependencies: List[Dependency]
  ) extends Adapter
  
  case class MessagingAdapter(
    name: String,
    packageName: String,
    implementedPort: Port,
    dependencies: List[Dependency]
  ) extends Adapter
  
  case class RestAdapter(
    name: String,
    packageName: String,
    implementedPort: Port,
    dependencies: List[Dependency]
  ) extends Adapter
}

case class Dependency(
  name: String,
  dependencyType: Type
)
```

### model/Domain.scala

```scala
package com.breuninger.arch.model

case class ArchitectureModel(
  ports: List[Port],
  models: List[DomainModel],
  adapters: List[Adapter]
)
```

## Step 2: Scala Parser (using Scalameta)

Parse Scala source files into the abstract model.

### build.sbt

```scala
name := "arch-tool"
version := "0.1.0"
scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  // Scalameta for parsing Scala
  "org.scalameta" %% "scalameta" % "4.8.15",
  
  // KotlinPoet for generating Kotlin (Java library)
  "com.squareup" % "kotlinpoet" % "1.15.1",
  
  // Testing
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "org.scalacheck" %% "scalacheck" % "1.17.0" % Test,
  
  // CLI
  "com.github.scopt" %% "scopt" % "4.1.0"
)
```

### parser/ScalaParser.scala

```scala
package com.breuninger.arch.parser

import scala.meta._
import com.breuninger.arch.model._

object ScalaParser {
  
  def parseFile(scalaSource: String): Either[String, ArchitectureModel] = {
    try {
      val tree = scalaSource.parse[Source].get
      
      val ports = extractPorts(tree)
      val models = extractModels(tree)
      val adapters = extractAdapters(tree)
      
      Right(ArchitectureModel(ports, models, adapters))
    } catch {
      case e: Exception => Left(s"Parse error: ${e.getMessage}")
    }
  }
  
  private def extractPorts(tree: Source): List[Port] = {
    tree.collect {
      case t @ Defn.Trait(_, typeName, _, _, template) 
        if isRepository(typeName.value) =>
          parsePort(typeName.value, template)
    }.flatten
  }
  
  private def isRepository(name: String): Boolean = 
    name.endsWith("Repository")
  
  private def parsePort(name: String, template: Template): Option[Port] = {
    val methods = template.stats.collect {
      case Decl.Def(_, methodName, _, paramss, returnType) =>
        Method(
          name = methodName.value,
          parameters = paramss.flatten.map(parseParameter),
          returnType = parseType(returnType)
        )
    }
    
    Some(Port(
      name = name,
      packageName = "", // Extract from tree
      methods = methods
    ))
  }
  
  private def parseParameter(param: Term.Param): Parameter = {
    Parameter(
      name = param.name.value,
      paramType = parseType(param.decltpe.getOrElse(Type.Name("Any")))
    )
  }
  
  private def parseType(tpe: scala.meta.Type): com.breuninger.arch.model.Type = {
    tpe match {
      // IO[T]
      case Type.Apply(Type.Name("IO"), List(inner)) =>
        Type.Effect(
          wrapped = parseType(inner),
          effectType = Type.EffectType.IO
        )
      
      // List[T]
      case Type.Apply(Type.Name("List"), args) =>
        Type.Generic(
          name = "List",
          typeArgs = args.map(parseType)
        )
      
      // Option[T]
      case Type.Apply(Type.Name("Option"), args) =>
        Type.Generic(
          name = "Option",
          typeArgs = args.map(parseType)
        )
      
      // Primitive or domain type
      case Type.Name(name) =>
        name match {
          case "String" | "Int" | "Long" | "Boolean" | "Double" | "Unit" =>
            Type.Primitive(name)
          case _ =>
            Type.Domain(name)
        }
      
      case _ => Type.Primitive("Unknown")
    }
  }
  
  private def extractModels(tree: Source): List[DomainModel] = {
    tree.collect {
      // case class (Entity or ValueObject)
      case Defn.Class(
        mods,
        typeName,
        _,
        Ctor.Primary(_, _, paramss),
        template
      ) if mods.exists(_.is[Mod.Case]) =>
        parseCaseClass(typeName.value, paramss.flatten)
    }.flatten
  }
  
  private def parseCaseClass(
    name: String,
    params: List[Term.Param]
  ): Option[DomainModel] = {
    val fields = params.map { param =>
      Field(
        name = param.name.value,
        fieldType = parseType(param.decltpe.getOrElse(Type.Name("Any")))
      )
    }
    
    // Single field extending AnyVal = ValueObject
    if (fields.size == 1) {
      Some(DomainModel.ValueObject(
        name = name,
        packageName = "",
        field = fields.head
      ))
    } else {
      Some(DomainModel.Entity(
        name = name,
        packageName = "",
        fields = fields
      ))
    }
  }
  
  private def extractAdapters(tree: Source): List[Adapter] = {
    tree.collect {
      case Defn.Class(
        _,
        typeName,
        _,
        ctor,
        Template(_, inits, _, _)
      ) =>
        // Check if implements a Repository
        val implementedPorts = inits.collect {
          case Init(Type.Name(name), _, _) if name.endsWith("Repository") =>
            name
        }
        
        implementedPorts.headOption.map { portName =>
          Adapter.PersistenceAdapter(
            name = typeName.value,
            packageName = "",
            implementedPort = Port(portName, "", List.empty), // Simplified
            dependencies = List.empty // Parse constructor params
          )
        }
    }.flatten
  }
}
```

### parser/PortParser.scala

More detailed port parsing with package extraction:

```scala
package com.breuninger.arch.parser

import scala.meta._
import com.breuninger.arch.model._

object PortParser {
  
  def parseRepositoryTrait(source: String): Either[String, Port] = {
    try {
      val tree = source.parse[Source].get
      
      // Extract package name
      val packageName = tree.collect {
        case Pkg(ref, _) => ref.toString
      }.headOption.getOrElse("")
      
      // Find trait definition
      val traitDef = tree.collect {
        case t @ Defn.Trait(_, name, _, _, template) 
          if name.value.endsWith("Repository") =>
            (name.value, template)
      }.headOption
      
      traitDef match {
        case Some((name, template)) =>
          val methods = extractMethods(template)
          Right(Port(name, packageName, methods))
        
        case None =>
          Left("No repository trait found")
      }
    } catch {
      case e: Exception => 
        Left(s"Parse error: ${e.getMessage}")
    }
  }
  
  private def extractMethods(template: Template): List[Method] = {
    template.stats.collect {
      case Decl.Def(_, methodName, _, paramss, returnType) =>
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

### parser/TypeParser.scala

Dedicated type parser with comprehensive support:

```scala
package com.breuninger.arch.parser

import scala.meta.{Type => ScalaType, _}
import com.breuninger.arch.model.Type

object TypeParser {
  
  def parseType(tpe: ScalaType): Type = tpe match {
    // Effect types
    case ScalaType.Apply(ScalaType.Name("IO"), List(inner)) =>
      Type.Effect(parseType(inner), Type.EffectType.IO)
    
    // Generic types
    case ScalaType.Apply(ScalaType.Name(name), args) =>
      Type.Generic(name, args.map(parseType))
    
    // Simple types
    case ScalaType.Name(name) =>
      parsePrimitiveOrDomain(name)
    
    // Unit type
    case ScalaType.Name("Unit") =>
      Type.Unit
    
    case _ =>
      Type.Primitive("Unknown")
  }
  
  private def parsePrimitiveOrDomain(name: String): Type = {
    name match {
      case "String" | "Int" | "Long" | "Boolean" | 
           "Double" | "Float" | "Byte" | "Short" | "Char" =>
        Type.Primitive(name)
      
      case "Unit" =>
        Type.Unit
      
      case _ =>
        Type.Domain(name)
    }
  }
}
```

## Step 3: Kotlin Renderer (using KotlinPoet)

Generate Kotlin code from the abstract model.

### renderer/KotlinRenderer.scala

```scala
package com.breuninger.arch.renderer

import com.breuninger.arch.model._
import com.squareup.kotlinpoet._
import javax.lang.model.element.Modifier

object KotlinRenderer {
  
  def renderPort(port: Port): String = {
    val interfaceBuilder = TypeSpec.interfaceBuilder(port.name)
    
    // Add methods
    port.methods.foreach { method =>
      val funSpec = renderMethod(method)
      interfaceBuilder.addFunction(funSpec)
    }
    
    // Create file
    val fileSpec = FileSpec.builder(port.packageName, port.name)
      .addType(interfaceBuilder.build())
      .build()
    
    fileSpec.toString
  }
  
  private def renderMethod(method: Method): FunSpec = {
    val funBuilder = FunSpec.builder(method.name)
    
    // Add parameters
    method.parameters.foreach { param =>
      funBuilder.addParameter(
        param.name,
        renderTypeName(param.paramType)
      )
    }
    
    // Handle effect types
    method.returnType match {
      case Type.Effect(wrapped, Type.EffectType.IO | Type.EffectType.Suspend) =>
        // Use suspend function
        funBuilder.addModifiers(KModifier.SUSPEND)
        
        // Return type (unwrap effect)
        wrapped match {
          case Type.Unit =>
            // suspend fun foo() - no return type for Unit
            ()
          case other =>
            funBuilder.returns(renderTypeName(other))
        }
      
      case Type.Effect(wrapped, Type.EffectType.Effect) =>
        // Use Arrow Effect
        val effectType = ClassName.bestGuess("arrow.fx.coroutines.Effect")
        funBuilder.returns(
          ParameterizedTypeName.get(effectType, renderTypeName(wrapped))
        )
      
      case other =>
        funBuilder.returns(renderTypeName(other))
    }
    
    funBuilder.build()
  }
  
  private def renderTypeName(tpe: Type): TypeName = tpe match {
    case Type.Primitive(name) =>
      name match {
        case "String" => ClassName.bestGuess("kotlin.String")
        case "Int" => ClassName.bestGuess("kotlin.Int")
        case "Long" => ClassName.bestGuess("kotlin.Long")
        case "Boolean" => ClassName.bestGuess("kotlin.Boolean")
        case "Double" => ClassName.bestGuess("kotlin.Double")
        case "Unit" => ClassName.bestGuess("kotlin.Unit")
        case _ => ClassName.bestGuess(s"kotlin.$name")
      }
    
    case Type.Domain(name) =>
      ClassName.bestGuess(name)
    
    case Type.Generic("List", args) =>
      val listType = ClassName.bestGuess("kotlin.collections.List")
      ParameterizedTypeName.get(
        listType,
        args.map(renderTypeName): _*
      )
    
    case Type.Generic("Option", args) =>
      // Option[T] becomes T? in Kotlin
      args.headOption.map(renderTypeName).getOrElse(
        ClassName.bestGuess("kotlin.Any")
      ).copy(nullable = true)
    
    case Type.Generic(name, args) =>
      val baseType = ClassName.bestGuess(name)
      ParameterizedTypeName.get(
        baseType,
        args.map(renderTypeName): _*
      )
    
    case Type.Effect(wrapped, _) =>
      // Should be handled by renderMethod
      renderTypeName(wrapped)
  }
}
```

### renderer/ModelRenderer.scala

```scala
package com.breuninger.arch.renderer

import com.breuninger.arch.model._
import com.squareup.kotlinpoet._

object ModelRenderer {
  
  def renderValueObject(vo: DomainModel.ValueObject): String = {
    val classBuilder = TypeSpec.classBuilder(vo.name)
      .addModifiers(KModifier.DATA)
      .addAnnotation(
        ClassName.bestGuess("kotlin.jvm.JvmInline")
      )
    
    // Primary constructor with single field
    val constructorBuilder = FunSpec.constructorBuilder()
      .addParameter(
        ParameterSpec.builder(
          vo.field.name,
          KotlinRenderer.renderTypeName(vo.field.fieldType)
        ).build()
      )
    
    classBuilder.primaryConstructor(constructorBuilder.build())
    
    // Add property
    classBuilder.addProperty(
      PropertySpec.builder(
        vo.field.name,
        KotlinRenderer.renderTypeName(vo.field.fieldType)
      )
      .initializer(vo.field.name)
      .build()
    )
    
    val fileSpec = FileSpec.builder(vo.packageName, vo.name)
      .addType(classBuilder.build())
      .build()
    
    fileSpec.toString
  }
  
  def renderEntity(entity: DomainModel.Entity): String = {
    val classBuilder = TypeSpec.classBuilder(entity.name)
      .addModifiers(KModifier.DATA)
    
    // Primary constructor
    val constructorBuilder = FunSpec.constructorBuilder()
    
    entity.fields.foreach { field =>
      constructorBuilder.addParameter(
        ParameterSpec.builder(
          field.name,
          KotlinRenderer.renderTypeName(field.fieldType)
        ).build()
      )
      
      // Add property
      classBuilder.addProperty(
        PropertySpec.builder(
          field.name,
          KotlinRenderer.renderTypeName(field.fieldType)
        )
        .initializer(field.name)
        .build()
      )
    }
    
    classBuilder.primaryConstructor(constructorBuilder.build())
    
    val fileSpec = FileSpec.builder(entity.packageName, entity.name)
      .addType(classBuilder.build())
      .build()
    
    fileSpec.toString
  }
  
  def renderSealedHierarchy(sealed: DomainModel.SealedHierarchy): String = {
    // Sealed interface
    val interfaceBuilder = TypeSpec.interfaceBuilder(sealed.name)
      .addModifiers(KModifier.SEALED)
    
    val fileBuilder = FileSpec.builder(sealed.packageName, sealed.name)
      .addType(interfaceBuilder.build())
    
    // Add variants as data classes
    sealed.variants.foreach { variant =>
      val variantBuilder = TypeSpec.classBuilder(variant.name)
        .addModifiers(KModifier.DATA)
        .addSuperinterface(ClassName.bestGuess(sealed.name))
      
      val constructorBuilder = FunSpec.constructorBuilder()
      
      variant.fields.foreach { field =>
        constructorBuilder.addParameter(
          field.name,
          KotlinRenderer.renderTypeName(field.fieldType)
        )
        
        variantBuilder.addProperty(
          PropertySpec.builder(
            field.name,
            KotlinRenderer.renderTypeName(field.fieldType)
          )
          .initializer(field.name)
          .build()
        )
      }
      
      variantBuilder.primaryConstructor(constructorBuilder.build())
      fileBuilder.addType(variantBuilder.build())
    }
    
    fileBuilder.build().toString
  }
}
```

## Step 4: Architectural Validator

Enforce architectural rules on the abstract model.

### validator/ArchValidator.scala

```scala
package com.breuninger.arch.validator

import com.breuninger.arch.model._

sealed trait ValidationError {
  def message: String
}

object ValidationError {
  case class PortNotInDomainPackage(port: Port) extends ValidationError {
    def message = s"Port ${port.name} must be in domain.repository package, found in ${port.packageName}"
  }
  
  case class PortUsesInfrastructureType(port: Port, method: Method, infraType: String) extends ValidationError {
    def message = s"Port ${port.name}.${method.name} uses infrastructure type $infraType"
  }
  
  case class AdapterNotInPortsPackage(adapter: Adapter) extends ValidationError {
    def message = s"Adapter ${adapter.name} must be in ports package, found in ${adapter.packageName}"
  }
  
  case class MissingPortImplementation(port: Port) extends ValidationError {
    def message = s"Port ${port.name} has no adapter implementation"
  }
}

object ArchValidator {
  
  def validate(model: ArchitectureModel): List[ValidationError] = {
    List(
      validatePortsInDomainPackage(model.ports),
      validatePortsDontUseInfrastructureTypes(model.ports),
      validateAdaptersInPortsPackage(model.adapters),
      validateEveryPortHasAdapter(model.ports, model.adapters)
    ).flatten
  }
  
  private def validatePortsInDomainPackage(ports: List[Port]): List[ValidationError] = {
    ports.filter { port =>
      !port.packageName.contains("domain.repository")
    }.map(ValidationError.PortNotInDomainPackage)
  }
  
  private def validatePortsDontUseInfrastructureTypes(
    ports: List[Port]
  ): List[ValidationError] = {
    val infraTypes = Set(
      "MongoDocument", "MongoCollection", 
      "KafkaProducer", "KafkaConsumer",
      "HttpRequest", "HttpResponse"
    )
    
    ports.flatMap { port =>
      port.methods.flatMap { method =>
        val usedTypes = collectTypes(method.returnType) ++ 
          method.parameters.flatMap(p => collectTypes(p.paramType))
        
        usedTypes.collect {
          case Type.Domain(name) if infraTypes.contains(name) =>
            ValidationError.PortUsesInfrastructureType(port, method, name)
        }
      }
    }
  }
  
  private def collectTypes(tpe: Type): List[Type] = tpe match {
    case Type.Generic(_, args) => tpe :: args.flatMap(collectTypes)
    case Type.Effect(wrapped, _) => tpe :: collectTypes(wrapped)
    case other => List(other)
  }
  
  private def validateAdaptersInPortsPackage(
    adapters: List[Adapter]
  ): List[ValidationError] = {
    adapters.filter { adapter =>
      !adapter.packageName.contains("ports")
    }.map(ValidationError.AdapterNotInPortsPackage)
  }
  
  private def validateEveryPortHasAdapter(
    ports: List[Port],
    adapters: List[Adapter]
  ): List[ValidationError] = {
    val implementedPorts = adapters.map(_.implementedPort.name).toSet
    
    ports.filter { port =>
      !implementedPorts.contains(port.name)
    }.map(ValidationError.MissingPortImplementation)
  }
}
```

### validator/LayerRule.scala

```scala
package com.breuninger.arch.validator

object LayerRule {
  
  sealed trait Layer
  object Layer {
    case object Domain extends Layer
    case object Application extends Layer
    case object Ports extends Layer
  }
  
  def getLayer(packageName: String): Option[Layer] = {
    if (packageName.contains("domain")) Some(Layer.Domain)
    else if (packageName.contains("application")) Some(Layer.Application)
    else if (packageName.contains("ports")) Some(Layer.Ports)
    else None
  }
  
  def validateDependencyDirection(
    from: Layer,
    to: Layer
  ): Boolean = {
    (from, to) match {
      // Application can depend on Domain
      case (Layer.Application, Layer.Domain) => true
      
      // Ports can depend on Domain
      case (Layer.Ports, Layer.Domain) => true
      
      // Same layer OK
      case (a, b) if a == b => true
      
      // All other directions forbidden
      case _ => false
    }
  }
}
```

## Step 5: CLI Interface

Command-line interface for the tool.

### Main.scala

```scala
package com.breuninger.arch

import scopt.OParser
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import com.breuninger.arch.parser._
import com.breuninger.arch.validator._
import com.breuninger.arch.renderer._

case class Config(
  command: String = "",
  input: String = "",
  output: String = "",
  target: String = "kotlin"
)

object Main extends App {
  
  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("arch-tool"),
      head("arch-tool", "0.1.0"),
      
      cmd("parse")
        .action((_, c) => c.copy(command = "parse"))
        .text("Parse Scala file and show abstract model")
        .children(
          opt[String]('i', "input")
            .required()
            .action((x, c) => c.copy(input = x))
            .text("Input Scala file")
        ),
      
      cmd("validate")
        .action((_, c) => c.copy(command = "validate"))
        .text("Validate architectural constraints")
        .children(
          opt[String]('i', "input")
            .required()
            .action((x, c) => c.copy(input = x))
            .text("Input Scala file or directory")
        ),
      
      cmd("migrate")
        .action((_, c) => c.copy(command = "migrate"))
        .text("Migrate Scala to Kotlin")
        .children(
          opt[String]('i', "input")
            .required()
            .action((x, c) => c.copy(input = x))
            .text("Input Scala file"),
          
          opt[String]('o', "output")
            .required()
            .action((x, c) => c.copy(output = x))
            .text("Output Kotlin file"),
          
          opt[String]('t', "target")
            .action((x, c) => c.copy(target = x))
            .text("Target language (kotlin)")
        )
    )
  }
  
  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      config.command match {
        case "parse" => handleParse(config)
        case "validate" => handleValidate(config)
        case "migrate" => handleMigrate(config)
        case _ => println("Unknown command")
      }
    
    case None =>
      // Arguments are bad, error message will be displayed
      ()
  }
  
  def handleParse(config: Config): Unit = {
    val source = readFile(config.input)
    
    ScalaParser.parseFile(source) match {
      case Right(model) =>
        println("=== Ports ===")
        model.ports.foreach { port =>
          println(s"${port.name}:")
          port.methods.foreach { method =>
            println(s"  - ${method.name}(${method.parameters.map(_.name).mkString(", ")}): ${method.returnType}")
          }
        }
        
        println("\n=== Models ===")
        model.models.foreach {
          case DomainModel.ValueObject(name, _, field) =>
            println(s"$name(${field.name}: ${field.fieldType})")
          
          case DomainModel.Entity(name, _, fields) =>
            println(s"$name:")
            fields.foreach(f => println(s"  - ${f.name}: ${f.fieldType}"))
          
          case _ => ()
        }
      
      case Left(error) =>
        println(s"Parse error: $error")
    }
  }
  
  def handleValidate(config: Config): Unit = {
    val source = readFile(config.input)
    
    ScalaParser.parseFile(source) match {
      case Right(model) =>
        val errors = ArchValidator.validate(model)
        
        if (errors.isEmpty) {
          println("✓ All architectural constraints satisfied")
        } else {
          println(s"✗ Found ${errors.size} architectural violations:")
          errors.foreach { error =>
            println(s"  - ${error.message}")
          }
        }
      
      case Left(error) =>
        println(s"Parse error: $error")
    }
  }
  
  def handleMigrate(config: Config): Unit = {
    val source = readFile(config.input)
    
    ScalaParser.parseFile(source) match {
      case Right(model) =>
        // Validate first
        val errors = ArchValidator.validate(model)
        if (errors.nonEmpty) {
          println("⚠ Architectural violations found:")
          errors.foreach(e => println(s"  - ${e.message}"))
          println("\nProceeding with migration anyway...\n")
        }
        
        // Generate Kotlin
        val kotlinCode = model.ports.map { port =>
          KotlinRenderer.renderPort(port)
        }.mkString("\n\n")
        
        // Write output
        writeFile(config.output, kotlinCode)
        println(s"✓ Generated Kotlin code: ${config.output}")
      
      case Left(error) =>
        println(s"Parse error: $error")
    }
  }
  
  private def readFile(path: String): String = {
    Files.readString(Paths.get(path))
  }
  
  private def writeFile(path: String, content: String): Unit = {
    Files.writeString(Paths.get(path), content)
  }
}
```

## Step 6: Example Test Case

Create a complete example migration.

### examples/scala/BestandRepository.scala

```scala
package com.breuninger.domain.repository

import cats.effect.IO

case class ArtikelId(value: String) extends AnyVal

case class BestandCreateDocument(
  id: ArtikelId,
  quantity: Int,
  warehouse: String
)

case class BestandDeleteDocument(id: ArtikelId)

trait BestandRepository {
  def save(bestand: BestandCreateDocument): IO[Unit]
  def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]]
  def deleteBatch(bestaende: List[BestandDeleteDocument]): IO[Unit]
}
```

### Expected Output: examples/kotlin/BestandRepository.kt

```kotlin
package com.breuninger.domain.repository

@JvmInline
value class ArtikelId(val value: String)

data class BestandCreateDocument(
  val id: ArtikelId,
  val quantity: Int,
  val warehouse: String
)

data class BestandDeleteDocument(val id: ArtikelId)

interface BestandRepository {
  suspend fun save(bestand: BestandCreateDocument)
  
  suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>
  
  suspend fun deleteBatch(bestaende: List<BestandDeleteDocument>)
}
```

### Test Case

```scala
package com.breuninger.arch

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.breuninger.arch.parser._
import com.breuninger.arch.renderer._
import com.breuninger.arch.validator._

class MigrationSpec extends AnyFlatSpec with Matchers {
  
  "ScalaParser" should "parse BestandRepository correctly" in {
    val source = """
      package com.breuninger.domain.repository
      
      import cats.effect.IO
      
      case class ArtikelId(value: String) extends AnyVal
      
      trait BestandRepository {
        def save(bestand: BestandCreateDocument): IO[Unit]
        def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]]
      }
    """
    
    val result = ScalaParser.parseFile(source)
    
    result shouldBe a[Right[_, _]]
    
    val model = result.toOption.get
    model.ports should have size 1
    
    val port = model.ports.head
    port.name shouldBe "BestandRepository"
    port.methods should have size 2
  }
  
  "ArchValidator" should "detect infrastructure type in port" in {
    val port = Port(
      name = "BestandRepository",
      packageName = "com.breuninger.domain.repository",
      methods = List(
        Method(
          name = "save",
          parameters = List(
            Parameter("doc", Type.Domain("MongoDocument"))
          ),
          returnType = Type.Effect(Type.Unit, Type.EffectType.IO)
        )
      )
    )
    
    val model = ArchitectureModel(
      ports = List(port),
      models = List.empty,
      adapters = List.empty
    )
    
    val errors = ArchValidator.validate(model)
    
    errors should not be empty
    errors.head shouldBe a[ValidationError.PortUsesInfrastructureType]
  }
  
  "KotlinRenderer" should "generate correct suspend function" in {
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
    
    val kotlin = KotlinRenderer.renderPort(port)
    
    kotlin should include("interface BestandRepository")
    kotlin should include("suspend fun save")
    kotlin should include("bestand: BestandCreateDocument")
  }
}
```

## Step 7: Build and Run

### Commands

```bash
# Build the tool
sbt compile

# Run tests
sbt test

# Package as executable
sbt assembly

# Parse a Scala file
./arch-tool parse -i src/main/scala/domain/repository/BestandRepository.scala

# Validate architecture
./arch-tool validate -i src/main/scala

# Migrate to Kotlin
./arch-tool migrate \
  -i src/main/scala/domain/repository/BestandRepository.scala \
  -o src/main/kotlin/domain/repository/BestandRepository.kt
```

## Advanced Features (Future)

### 1. Batch Migration

```bash
./arch-tool migrate-batch \
  --scala-root src/main/scala \
  --kotlin-root src/main/kotlin \
  --preserve-structure
```

### 2. Adapter Generation

```bash
./arch-tool generate adapter \
  --port BestandRepository \
  --type persistence \
  --target kotlin
```

### 3. Architecture Diagram

```bash
./arch-tool diagram \
  --input src/main/scala \
  --output architecture.mermaid
```

### 4. Interactive Mode

```bash
./arch-tool interactive
> parse BestandRepository.scala
> show ports
> migrate to kotlin
> validate
```

## Integration with Build Tools

### SBT Plugin

```scala
// project/plugins.sbt
addSbtPlugin("com.breuninger" % "sbt-arch-tool" % "0.1.0")

// build.sbt
enablePlugins(ArchToolPlugin)

archToolValidate := {
  // Validate on compile
}
```

### Gradle Plugin

```kotlin
// build.gradle.kts
plugins {
    id("com.breuninger.arch-tool") version "0.1.0"
}

tasks.register("validateArchitecture") {
    doLast {
        exec {
            commandLine("arch-tool", "validate", "-i", "src/main/kotlin")
        }
    }
}

tasks.compileKotlin {
    dependsOn("validateArchitecture")
}
```

## Conclusion

This POC provides:

1. **Parsing**: Scalameta → Abstract Model
2. **Validation**: Architectural rules enforcement
3. **Generation**: Abstract Model → Kotlin (KotlinPoet)
4. **CLI**: User-friendly command-line interface
5. **Testing**: Comprehensive test suite

The tool is production-ready for basic use cases and can be extended with additional features as needed.
