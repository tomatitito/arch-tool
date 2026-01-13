# IR Module - Intermediate Representation

The IR (Intermediate Representation) module provides a language-agnostic abstract model for representing architectural concepts. It serves as the bridge between Scala code parsing (using Scalameta) and Kotlin code generation (using KotlinPoet).

## Purpose

The IR decouples the parsing and rendering phases:
- **Parser → IR**: Scalameta parses Scala code into the IR model
- **IR → Renderer**: KotlinPoet generates Kotlin code from the IR model

This separation allows:
- Independent development of parser and renderer
- Easy addition of new source/target languages
- Validation and transformation of architectural concepts
- Clear contract between modules

## Core Components

### Type System (`Type.scala`)

Represents all types in a language-agnostic way:

- **Primitive Types**: `IntType`, `LongType`, `DoubleType`, `FloatType`, `BooleanType`, `StringType`, `UnitType`
- **Named Types**: References to classes/interfaces with optional generic type arguments
- **Type Parameters**: Generic type variables (e.g., `T`, `A`, `B`)
- **Nullable Types**: Optional/nullable wrappers (`Option[T]` in Scala, `T?` in Kotlin)
- **Collection Types**: `ListType`, `SetType`, `MapType`
- **Function Types**: Higher-order function signatures

All types support nullability operations:
```scala
val nullable = Type.IntType.makeNullable  // NullableType(IntType)
val nonNull = nullable.makeNonNullable    // IntType
```

### Methods and Parameters (`Method.scala`)

Represents method signatures:

- **Parameter**: Name, type, optional default value
- **Method**: Name, type parameters, parameters, return type, visibility, modifiers
- **Visibility**: `Public`, `Protected`, `Private`, `Internal`
- **Annotation**: Annotation metadata with arguments

Supports:
- Generic methods with type parameters
- Abstract vs concrete methods
- Suspend functions (for Kotlin coroutines)
- Documentation comments

### Domain Models (`DomainModel.scala`)

Represents domain concepts:

#### Value Objects
Immutable data holders (case class in Scala, data class in Kotlin):
```scala
DomainModel.ValueObject(
  name = "User",
  packageName = "com.example.domain",
  properties = List(
    Property("id", Type.StringType),
    Property("name", Type.StringType)
  )
)
```

#### Entities
Objects with identity, potentially mutable:
```scala
DomainModel.Entity(
  name = "Order",
  packageName = "com.example.domain",
  properties = List(
    Property("id", Type.StringType, isVal = true),
    Property("status", Type.StringType, isVal = false)
  )
)
```

#### Sealed Hierarchies
Closed set of subtypes (sealed trait in Scala, sealed class in Kotlin):
```scala
DomainModel.SealedHierarchy(
  name = "Result",
  typeParameters = List(Type.TypeParameter("T")),
  subtypes = List(
    SealedSubtype("Success", List(Property("value", Type.TypeParameter("T")))),
    SealedSubtype("Failure", List(Property("error", Type.StringType)))
  )
)
```

#### Enums
Simple named value enumerations:
```scala
DomainModel.Enum(
  name = "OrderStatus",
  values = List(
    EnumValue("PENDING"),
    EnumValue("CONFIRMED"),
    EnumValue("SHIPPED")
  )
)
```

### Ports and Implementations (`Port.scala`)

Represents architectural boundaries and contracts:

#### Port Interface
Abstract contract defining a boundary (trait in Scala, interface in Kotlin):
```scala
Port(
  name = "UserRepository",
  packageName = "com.example.ports",
  methods = List(
    Method("findById", List(Parameter("id", Type.StringType)),
           Type.NullableType(Type.NamedType("User"))),
    Method("save", List(Parameter("user", Type.NamedType("User"))), Type.UnitType)
  ),
  portType = PortType.Repository
)
```

Port types:
- `Repository`: Data access interfaces
- `Service`: Application service interfaces
- `UseCase`: Domain use case interfaces
- `EventHandler`: Event processing interfaces
- `Generic`: General purpose interfaces

#### Port Implementation
Concrete adapter implementing a port:
```scala
PortImplementation(
  name = "InMemoryUserRepository",
  packageName = "com.example.adapters",
  implementedPort = Type.NamedType("com.example.ports.UserRepository"),
  constructorParameters = List(Parameter("cache", Type.NamedType("Cache")))
)
```

### Modules and Projects (`Module.scala`)

Represents organizational structure:

#### Module
Cohesive unit of code (package/namespace):
```scala
Module(
  name = "domain",
  packageName = "com.example.domain",
  domainModels = List(...),
  ports = List(...),
  portImplementations = List(...),
  subModules = List(...)
)
```

Provides navigation:
- `findDomainModel(name)`: Find domain model by name
- `findPort(name)`: Find port by name
- `allTypes`: Get all qualified type names recursively

#### Project
Complete codebase representation:
```scala
Project(
  name = "my-project",
  rootPackage = "com.example",
  modules = List(...),
  metadata = Map("version" -> "1.0.0")
)
```

Provides aggregation:
- `allModules`: All modules recursively
- `allDomainModels`: All domain models across modules
- `allPorts`: All ports across modules
- `allPortImplementations`: All implementations across modules

## Testing

Comprehensive test suite included:
- `TypeSpec.scala`: Type system tests
- `DomainModelSpec.scala`: Domain model tests
- `PortSpec.scala`: Port and method tests
- `ModuleSpec.scala`: Module and project tests

Run tests with:
```bash
sbt "project ir" test
```

## Usage Example

Creating a complete IR model:

```scala
import com.breuninger.arch.ir._

// Define a domain model
val user = DomainModel.ValueObject(
  name = "User",
  packageName = "com.example.domain",
  properties = List(
    Property("id", Type.StringType),
    Property("name", Type.StringType),
    Property("email", Type.StringType)
  )
)

// Define a port interface
val userRepo = Port(
  name = "UserRepository",
  packageName = "com.example.ports",
  methods = List(
    Method("findById", List(Parameter("id", Type.StringType)),
           Type.NullableType(Type.NamedType("com.example.domain.User"))),
    Method("save", List(Parameter("user", Type.NamedType("com.example.domain.User"))),
           Type.UnitType)
  ),
  portType = PortType.Repository
)

// Organize in a module
val domainModule = Module(
  name = "domain",
  packageName = "com.example.domain",
  domainModels = List(user),
  ports = List(userRepo)
)

// Create project
val project = Project(
  name = "user-service",
  rootPackage = "com.example",
  modules = List(domainModule)
)
```

## Design Principles

1. **Language Agnostic**: No Scala or Kotlin specific constructs in the IR
2. **Immutable**: All IR types are immutable case classes
3. **Type Safe**: Leverages Scala's type system for correctness
4. **Composable**: Small, focused types that compose into larger structures
5. **Documentation First**: All types support optional documentation
6. **Testable**: Pure data structures, easy to test

## Next Steps

The IR module is complete and ready for:
1. **Parser module** (`arch-tool-iah`): Implement Scalameta parser to produce IR
2. **Renderer module** (`arch-tool-2wv`): Implement KotlinPoet renderer to consume IR
3. **Validator module** (`arch-tool-gd5`): Implement architectural validation rules on IR
