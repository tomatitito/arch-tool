# ScalaParser Implementation Plan

## Goal
Implement the ScalaParser using Scalameta to parse Scala source files into the IR (Intermediate Representation), enabling migration of Scala code to Kotlin. The immediate target is successfully parsing BestandRepository.scala.

## Overview

Replace the stub `StubScalaParser` with a real `ScalametaParser` implementation that converts Scala AST to IR types.

**Current State:**
- CLI infrastructure complete
- IR model fully defined
- Comprehensive test suite (589 lines + 522 edge cases + 481 converter tests)
- Parser returns "not implemented" stub

**Target State:**
- ScalametaParser converts Scala source to IR
- BestandRepository.scala fully parses
- Existing test suite passes

## Architecture

### Component Structure

```
ScalametaParser (main orchestrator)
├── TypeConverter (Scalameta Type → IR Type)
├── DomainModelConverter (Defn.Class → ValueObject/Entity/SealedHierarchy)
├── PortConverter (Defn.Trait → Port)
├── MethodConverter (Decl.Def → Method)
├── LocationExtractor (Position → Location)
└── AnnotationConverter (Mod.Annot → Annotation) [Phase 7]
```

### Key Type Mappings

| Scala Type | IR Type | Notes |
|------------|---------|-------|
| `IO[A]` | `A` with `isSuspend=true` | Effect unwrapped, suspend flag set |
| `Option[A]` | `NullableType(A)` | Maps to Kotlin `A?` |
| `List[A]` | `ListType(A)` | Recursive for nested types |
| `String`, `Int`, etc. | `StringType`, `IntType` | Direct primitive mapping |
| Domain types | `NamedType(qualifiedName)` | Custom types preserved |

### Domain Model Detection

| Scala Pattern | IR Type | Detection Logic |
|---------------|---------|-----------------|
| `case class X(v: T) extends AnyVal` | `ValueObject` | Case class + AnyVal parent |
| `case class X(...)` | `Entity` | Case class without AnyVal |
| `sealed trait X` + subtypes | `SealedHierarchy` | Sealed modifier + collect subtypes |
| `trait X { def m(): T }` | `Port` | Trait with method declarations |

## Implementation Phases

### Phase 1: Foundation (START HERE)

**Files to Create:**
- `modules/parser/src/main/scala/com/breuninger/arch/parser/ScalametaParser.scala`
- `modules/parser/src/main/scala/com/breuninger/arch/parser/TypeConverter.scala`
- `modules/parser/src/main/scala/com/breuninger/arch/parser/LocationExtractor.scala`

**Tasks:**
1. Implement `TypeConverter` with primitive types (String, Int, Long, Double, Float, Boolean, Unit)
2. Implement `TypeConverter` for simple named types
3. Implement `LocationExtractor` to extract source positions
4. Create `ScalametaParser` skeleton:
   - `parseFile(path)` → read file → `parseString(content)`
   - `parseString(source)` → `source.parse[Source]` → handle errors → return empty `ParseResult`

**Test Validation:**
```bash
sbt "parser/testOnly *ScalametaParserSpec -- -z \"parse a simple\""
```

### Phase 2: Simple Domain Models

**Files to Create:**
- `modules/parser/src/main/scala/com/breuninger/arch/parser/DomainModelConverter.scala`

**Tasks:**
1. Detect ValueObject: `case class` extending `AnyVal`
2. Detect Entity: `case class` NOT extending `AnyVal`
3. Extract properties from constructor parameters: `cls.ctor.paramClauses`
4. Convert each property: name, type, documentation

**Implementation Pattern:**
```scala
def isValueObject(cls: Defn.Class): Boolean = {
  val isCaseClass = cls.mods.exists { case Mod.Case() => true; case _ => false }
  val extendsAnyVal = cls.templ.inits.exists {
    case Init(Type.Name("AnyVal"), _, _) => true
    case _ => false
  }
  isCaseClass && extendsAnyVal
}
```

**Test Validation:**
```bash
sbt "parser/testOnly *ParserConverterSpec -- -z \"ValueObject\""
sbt "parser/testOnly *ParserConverterSpec -- -z \"Entity\""
```

### Phase 3: Collection Types & Generics

**Files to Modify:**
- `TypeConverter.scala` (extend with collection types)

**Tasks:**
1. Add `List[A]` → `ListType(convertType(A))`
2. Add `Set[A]` → `SetType(convertType(A))`
3. Add `Map[K,V]` → `MapType(convertType(K), convertType(V))`
4. Add `Option[A]` → `NullableType(convertType(A))`
5. Handle nested generics recursively: `List[Option[String]]`

**Implementation Pattern:**
```scala
def convertType(metaType: scala.meta.Type): Type = metaType match {
  case Type.Name("String") => Type.StringType
  case Type.Name("Int") => Type.IntType
  // ... other primitives ...

  case Type.Apply(Type.Name("Option"), args) =>
    Type.NullableType(convertType(args.head))

  case Type.Apply(Type.Name("List"), args) =>
    Type.ListType(convertType(args.head))

  case Type.Apply(Type.Name("IO" | "Future"), args) =>
    convertType(args.head)  // Unwrap effect type

  case Type.Apply(tpe, args) =>
    Type.NamedType(tpe.syntax, args.map(convertType))

  case Type.Name(name) =>
    Type.NamedType(qualifiedName = name)
}
```

**Test Validation:**
```bash
sbt "parser/testOnly *ScalametaParserSpec -- -z \"type extraction\""
```

### Phase 4: Port Interfaces (CRITICAL - BestandRepository Target)

**Files to Create:**
- `modules/parser/src/main/scala/com/breuninger/arch/parser/MethodConverter.scala`
- `modules/parser/src/main/scala/com/breuninger/arch/parser/PortConverter.scala`

**Tasks:**
1. Implement `MethodConverter`:
   - Extract method name: `declDef.name.value`
   - Extract parameters: `declDef.paramClauses.headOption` (first param list only, ignore implicits)
   - Extract return type: `declDef.decltpe`
   - Detect `IO[A]` or `Future[A]` → set `isSuspend = true`, unwrap to `A`

2. Implement `PortConverter`:
   - Convert `Defn.Trait` to `Port`
   - Extract all `Decl.Def` from trait body
   - Convert each using `MethodConverter`
   - Infer `portType` from trait name pattern (e.g., "Repository" → `PortType.Repository`)

**Critical Logic - IO[A] Handling:**
```scala
def convertMethod(declDef: Decl.Def): Method = {
  val (isSuspend, returnType) = declDef.decltpe match {
    case Type.Apply(Type.Name("IO"), args) =>
      (true, TypeConverter.convertType(args.head))
    case Type.Apply(Type.Name("Future"), args) =>
      (true, TypeConverter.convertType(args.head))
    case other =>
      (false, TypeConverter.convertType(other))
  }

  Method(
    name = declDef.name.value,
    parameters = declDef.paramClauses.headOption.getOrElse(Nil).map(convertParameter),
    returnType = returnType,
    isSuspend = isSuspend,
    isAbstract = true,
    visibility = Visibility.Public
  )
}
```

**Test Validation:**
```bash
# This is the key milestone - BestandRepository should fully parse
sbt "cli/run parse /Volumes/sourcecode/meta-breuninger/entdecken/reco/produkt-assembler/src/main/scala/com/breuninger/entdecken/domain/repository/BestandRepository.scala --verbose"

sbt "parser/testOnly *ParserConverterSpec -- -z \"Port\""
sbt "parser/testOnly *ScalametaParserSpec -- -z \"port\""
```

**Success Criteria for Phase 4:**
BestandRepository.scala should parse to:
- 2 domain models (ArtikelId as ValueObject, BestandCreateDocument as Entity)
- 1 port (BestandRepository) with 4 methods (saveBatch, save, getByIds, deleteBatch)
- All methods have `isSuspend = true`
- Type mapping: `IO[Unit]` → `UnitType` with suspend, `List[ArtikelId]` → `ListType(NamedType("ArtikelId"))`

### Phase 5: Sealed Hierarchies

**Files to Modify:**
- `DomainModelConverter.scala` (add sealed hierarchy logic)

**Tasks:**
1. Detect sealed traits: `trt.mods.exists { case Mod.Sealed() => true }`
2. Find all classes/objects extending the sealed trait
3. Group into `SealedHierarchy` with `SealedSubtype` list
4. Handle case objects (subtypes with no properties)

**Implementation Strategy:**
Two-pass approach:
1. First pass: collect all sealed trait names
2. Second pass: for each sealed trait, find all `Defn.Class` and `Defn.Object` where `templ.inits` references the trait

**Test Validation:**
```bash
sbt "parser/testOnly *ParserConverterSpec -- -z \"SealedHierarchy\""
```

### Phase 6: Package & Import Handling

**Files to Modify:**
- `ScalametaParser.scala` (add package extraction)

**Tasks:**
1. Extract package name from `Pkg` node: `pkg.ref.syntax`
2. Pass package name to all converters
3. Extract imports (store but no processing needed for now)

**Implementation Pattern:**
```scala
source.stats.collect {
  case pkg: Pkg =>
    val packageName = pkg.ref.syntax
    val models = extractDomainModels(pkg.stats, packageName)
    val ports = extractPorts(pkg.stats, packageName)
    ParseResult(models, ports)
}
```

**Test Validation:**
```bash
sbt "parser/testOnly *ScalametaParserSpec -- -z \"package\""
```

### Phase 7: Annotations & Edge Cases (OPTIONAL)

**Files to Create:**
- `modules/parser/src/main/scala/com/breuninger/arch/parser/AnnotationConverter.scala`

**Tasks:**
1. Extract annotations from `mods`: `cls.mods.collect { case Mod.Annot(annot) => annot }`
2. Convert to IR `Annotation(name, parameters)`
3. Handle visibility modifiers (private, protected)
4. Extract Scaladoc comments

**Test Validation:**
```bash
sbt "parser/testOnly *ParserEdgeCasesSpec"
```

### Phase 8: Error Handling (OPTIONAL)

**Files to Modify:**
- All converters (add try-catch and detailed error messages)

**Tasks:**
1. Wrap conversions in try-catch
2. Add location information to all errors
3. Add warnings for ignored constructs (implicit parameters, etc.)

## Critical Files

### Files to Create (New)
1. `modules/parser/src/main/scala/com/breuninger/arch/parser/ScalametaParser.scala` - Main parser orchestrator
2. `modules/parser/src/main/scala/com/breuninger/arch/parser/TypeConverter.scala` - Core type conversion
3. `modules/parser/src/main/scala/com/breuninger/arch/parser/LocationExtractor.scala` - Error location tracking
4. `modules/parser/src/main/scala/com/breuninger/arch/parser/DomainModelConverter.scala` - Domain model conversion
5. `modules/parser/src/main/scala/com/breuninger/arch/parser/MethodConverter.scala` - Method signature conversion
6. `modules/parser/src/main/scala/com/breuninger/arch/parser/PortConverter.scala` - Port/trait conversion
7. `modules/parser/src/main/scala/com/breuninger/arch/parser/AnnotationConverter.scala` - Annotation conversion (Phase 7)

### Files to Modify (Existing)
8. `modules/cli/src/main/scala/com/breuninger/arch/cli/DefaultMigrationPipeline.scala` - Replace `StubScalaParser` with `ScalametaParser` in `createStub()` method

### Reference Files (Read Only)
9. `specs/SCALAMETA_OUTPUT_EXAMPLES.md` - Scalameta usage guide
10. `modules/parser/src/test/scala/com/breuninger/arch/parser/ScalametaParserSpec.scala` - Test expectations
11. `modules/ir/src/main/scala/com/breuninger/arch/ir/` - IR type definitions

## End-to-End Verification

### Step 1: Compile
```bash
sbt compile
```

### Step 2: Run Unit Tests
```bash
# Run all parser tests
sbt "parser/test"

# Run specific test suites
sbt "parser/testOnly com.breuninger.arch.parser.ScalametaParserSpec"
sbt "parser/testOnly com.breuninger.arch.parser.ParserConverterSpec"
```

### Step 3: Test CLI Parse Command
```bash
# Parse BestandRepository (should show domain models and ports)
sbt "cli/run parse /Volumes/sourcecode/meta-breuninger/entdecken/reco/produkt-assembler/src/main/scala/com/breuninger/entdecken/domain/repository/BestandRepository.scala --verbose"
```

**Expected Output:**
```
=== Parse Result ===

Domain Models: 2
  - ArtikelId (ValueObject)
  - BestandCreateDocument (Entity)

Ports: 1
  - BestandRepository

=== Detailed IR ===

ValueObject(name=ArtikelId, properties=[Property(name=value, propertyType=StringType)])
Entity(name=BestandCreateDocument, properties=[...])
Port(name=BestandRepository, methods=[
  Method(name=saveBatch, returnType=UnitType, isSuspend=true),
  Method(name=save, returnType=UnitType, isSuspend=true),
  Method(name=getByIds, returnType=ListType(NamedType(BestandCreateDocument)), isSuspend=true),
  Method(name=deleteBatch, returnType=UnitType, isSuspend=true)
])
```

### Step 4: Test CLI Migrate Command
```bash
# Migrate BestandRepository to Kotlin
sbt "cli/run migrate \
  /Volumes/sourcecode/meta-breuninger/entdecken/reco/produkt-assembler/src/main/scala/com/breuninger/entdecken/domain/repository/BestandRepository.scala \
  test-output/kotlin/com/breuninger/entdecken/domain/repository/BestandRepository.kt"
```

**Expected Output:**
```
=== Migration Complete ===

Input:  /Volumes/sourcecode/.../BestandRepository.scala
Output: test-output/kotlin/.../BestandRepository.kt
Domain models migrated: 2
Ports migrated: 1
```

### Step 5: Verify Generated Kotlin (After Renderer is Implemented)
```bash
cat test-output/kotlin/com/breuninger/entdecken/domain/repository/BestandRepository.kt
```

Should contain:
- `@JvmInline value class ArtikelId(val value: String)`
- `data class BestandCreateDocument(...)`
- `interface BestandRepository { suspend fun save(...) }`

## Dependencies

**Already Configured in build.sbt:**
- `org.scalameta:scalameta_2.13:4.14.4`
- IR module dependency

**No additional dependencies needed.**

## Key Design Decisions

1. **IO[A] Handling**: Unwrap to `A`, set `isSuspend = true` on Method (matches Kotlin suspend functions)
2. **Option[A]**: Map to `NullableType(A)` (becomes `A?` in Kotlin)
3. **Implicit Parameters**: Ignore (second parameter list), focus on first parameter list only
4. **Sealed Hierarchies**: Two-pass approach (collect sealed traits, then find all subtypes)
5. **Error Strategy**: Fail fast on parse errors, provide precise locations

## Success Criteria

### Minimum Viable Parser (Phases 1-4)
- Parses BestandRepository.scala completely
- Converts 2 domain models (ValueObject, Entity)
- Converts 1 port with 4 methods
- Handles IO[A] → suspend correctly
- Handles List[A] types

### Full Parser (Phases 1-8)
- Passes all ScalametaParserSpec tests (589 lines)
- Passes all ParserConverterSpec tests (481 lines)
- Passes all ParserEdgeCasesSpec tests (522 lines)
- Handles sealed hierarchies
- Handles packages and imports
- Handles annotations
- Provides detailed error messages

## Implementation Order

**Recommended path to working BestandRepository migration:**

1. Phase 1 (Foundation)
2. Phase 2 (Domain Models)
3. Phase 3 (Collections)
4. Phase 4 (Ports)
   - **MILESTONE**: BestandRepository fully parses
5. Phase 6 (Packages)
6. Phase 5 (Sealed Hierarchies)
7. Phases 7-8 (Polish)
