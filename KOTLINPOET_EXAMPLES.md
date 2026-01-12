# KotlinPoet Examples and Guide

## What is KotlinPoet?

**KotlinPoet** is a Java/Kotlin library for **generating Kotlin source code programmatically**.

Instead of concatenating strings to build Kotlin code, KotlinPoet provides a type-safe, fluent API for constructing Kotlin files, classes, functions, properties, and more.

### Key Features
- **Type-safe code generation** - No string concatenation
- **Automatic formatting** - Proper indentation and spacing
- **Import management** - Automatically adds and organizes imports
- **Annotation support** - Add annotations like `@JvmInline`, `@Serializable`
- **Kotlin-specific features** - Suspend functions, data classes, sealed classes, etc.

### Similar Tools
- **JavaPoet** - For generating Java code (KotlinPoet is based on it)
- **Roslyn** - For generating C# code
- **Scalameta** - For generating Scala code (though primarily used for parsing)

---

## How KotlinPoet Works

### Traditional String Concatenation (Bad)

```scala
def generateKotlinClass(name: String, fields: List[(String, String)]): String = {
  val fieldDecls = fields.map { case (fieldName, fieldType) =>
    s"  val $fieldName: $fieldType"
  }.mkString(",\n")
  
  s"""
  |data class $name(
  |$fieldDecls
  |)
  """.stripMargin
}
```

**Problems:**
- Error-prone (typos, missing commas, wrong indentation)
- No validation
- Hard to maintain
- No import management

### KotlinPoet (Good)

```scala
import com.squareup.kotlinpoet._

def generateKotlinClass(name: String, fields: List[(String, String)]): String = {
  val classBuilder = TypeSpec.classBuilder(name)
    .addModifiers(KModifier.DATA)
  
  val constructorBuilder = FunSpec.constructorBuilder()
  
  fields.foreach { case (fieldName, fieldType) =>
    constructorBuilder.addParameter(fieldName, ClassName.bestGuess(fieldType))
    classBuilder.addProperty(
      PropertySpec.builder(fieldName, ClassName.bestGuess(fieldType))
        .initializer(fieldName)
        .build()
    )
  }
  
  classBuilder.primaryConstructor(constructorBuilder.build())
  
  val fileSpec = FileSpec.builder("com.example", name)
    .addType(classBuilder.build())
    .build()
  
  fileSpec.toString
}
```

**Advantages:**
- Type-safe
- Validated at compile time
- Proper formatting guaranteed
- Automatic import management

---

## Core KotlinPoet Components

### 1. FileSpec - Represents a .kt file

```scala
import com.squareup.kotlinpoet._

// Create a Kotlin file
val fileSpec = FileSpec.builder("com.breuninger.domain", "BestandRepository")
  .addType(/* TypeSpec */)
  .addFunction(/* FunSpec */)
  .addProperty(/* PropertySpec */)
  .addImport("kotlin.collections", "List")
  .build()

// Generate Kotlin code
val kotlinCode: String = fileSpec.toString

// Write to file
fileSpec.writeTo(System.out)
fileSpec.writeTo(Paths.get("src/main/kotlin"))
```

**Output:**
```kotlin
package com.breuninger.domain

import kotlin.collections.List

// Generated classes, functions, properties...
```

---

### 2. TypeSpec - Represents a type (class, interface, object)

#### Interface

```scala
val interfaceSpec = TypeSpec.interfaceBuilder("BestandRepository")
  .addFunction(
    FunSpec.builder("save")
      .addModifiers(KModifier.SUSPEND)
      .addParameter("bestand", ClassName.bestGuess("BestandCreateDocument"))
      .build()
  )
  .build()
```

**Output:**
```kotlin
interface BestandRepository {
  suspend fun save(bestand: BestandCreateDocument)
}
```

#### Data Class

```scala
val dataClassSpec = TypeSpec.classBuilder("ArtikelId")
  .addModifiers(KModifier.DATA)
  .primaryConstructor(
    FunSpec.constructorBuilder()
      .addParameter("value", String::class)
      .build()
  )
  .addProperty(
    PropertySpec.builder("value", String::class)
      .initializer("value")
      .build()
  )
  .build()
```

**Output:**
```kotlin
data class ArtikelId(val value: String)
```

#### Inline Value Class

```scala
val valueClassSpec = TypeSpec.classBuilder("ArtikelId")
  .addAnnotation(ClassName.bestGuess("kotlin.jvm.JvmInline"))
  .addModifiers(KModifier.VALUE)
  .primaryConstructor(
    FunSpec.constructorBuilder()
      .addParameter("value", String::class)
      .build()
  )
  .addProperty(
    PropertySpec.builder("value", String::class)
      .initializer("value")
      .build()
  )
  .build()
```

**Output:**
```kotlin
@JvmInline
value class ArtikelId(val value: String)
```

#### Sealed Interface

```scala
val sealedInterfaceSpec = TypeSpec.interfaceBuilder("Result")
  .addModifiers(KModifier.SEALED)
  .build()

val successClass = TypeSpec.classBuilder("Success")
  .addModifiers(KModifier.DATA)
  .addSuperinterface(ClassName.bestGuess("Result"))
  .primaryConstructor(
    FunSpec.constructorBuilder()
      .addParameter("value", String::class)
      .build()
  )
  .addProperty(
    PropertySpec.builder("value", String::class)
      .initializer("value")
      .build()
  )
  .build()

val fileSpec = FileSpec.builder("com.example", "Result")
  .addType(sealedInterfaceSpec)
  .addType(successClass)
  .build()
```

**Output:**
```kotlin
sealed interface Result

data class Success(val value: String) : Result
```

---

### 3. FunSpec - Represents a function

#### Simple Function

```scala
val funSpec = FunSpec.builder("greet")
  .addParameter("name", String::class)
  .returns(String::class)
  .addStatement("return \"Hello, \$name!\"")
  .build()
```

**Output:**
```kotlin
fun greet(name: String): String {
  return "Hello, $name!"
}
```

#### Suspend Function

```scala
val suspendFunSpec = FunSpec.builder("save")
  .addModifiers(KModifier.SUSPEND)
  .addParameter("document", ClassName.bestGuess("Document"))
  .build()
```

**Output:**
```kotlin
suspend fun save(document: Document)
```

#### Function with Multiple Parameters

```scala
val funSpec = FunSpec.builder("getByIds")
  .addModifiers(KModifier.SUSPEND)
  .addParameter("ids", 
    ParameterizedTypeName.get(
      ClassName.bestGuess("kotlin.collections.List"),
      ClassName.bestGuess("ArtikelId")
    )
  )
  .returns(
    ParameterizedTypeName.get(
      ClassName.bestGuess("kotlin.collections.List"),
      ClassName.bestGuess("BestandCreateDocument")
    )
  )
  .build()
```

**Output:**
```kotlin
suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>
```

#### Extension Function

```scala
val extensionFun = FunSpec.builder("isEmpty")
  .receiver(String::class)
  .returns(Boolean::class)
  .addStatement("return this.length == 0")
  .build()
```

**Output:**
```kotlin
fun String.isEmpty(): Boolean {
  return this.length == 0
}
```

---

### 4. PropertySpec - Represents a property

#### Immutable Property

```scala
val propertySpec = PropertySpec.builder("name", String::class)
  .initializer("\"default\"")
  .build()
```

**Output:**
```kotlin
val name: String = "default"
```

#### Mutable Property

```scala
val mutablePropertySpec = PropertySpec.builder("count", Int::class)
  .mutable(true)
  .initializer("0")
  .build()
```

**Output:**
```kotlin
var count: Int = 0
```

#### Property with Getter/Setter

```scala
val propertyWithGetter = PropertySpec.builder("fullName", String::class)
  .getter(
    FunSpec.getterBuilder()
      .addStatement("return \"\$firstName \$lastName\"")
      .build()
  )
  .build()
```

**Output:**
```kotlin
val fullName: String
  get() = "$firstName $lastName"
```

---

### 5. ParameterSpec - Represents a parameter

```scala
val param = ParameterSpec.builder("bestand", ClassName.bestGuess("BestandCreateDocument"))
  .build()

val paramWithDefault = ParameterSpec.builder("retry", Boolean::class)
  .defaultValue("true")
  .build()
```

**Output:**
```kotlin
bestand: BestandCreateDocument
retry: Boolean = true
```

---

### 6. TypeName - Represents a type reference

#### Simple Types

```scala
// Primitive types
val stringType = ClassName.bestGuess("kotlin.String")
val intType = ClassName.bestGuess("kotlin.Int")

// Using Kotlin reflection
val stringType2 = String::class.asTypeName()
val intType2 = Int::class.asTypeName()
```

#### Generic Types

```scala
// List<String>
val listOfStrings = ParameterizedTypeName.get(
  ClassName.bestGuess("kotlin.collections.List"),
  String::class.asTypeName()
)

// Map<String, Int>
val mapType = ParameterizedTypeName.get(
  ClassName.bestGuess("kotlin.collections.Map"),
  String::class.asTypeName(),
  Int::class.asTypeName()
)

// List<List<String>>
val nestedList = ParameterizedTypeName.get(
  ClassName.bestGuess("kotlin.collections.List"),
  ParameterizedTypeName.get(
    ClassName.bestGuess("kotlin.collections.List"),
    String::class.asTypeName()
  )
)
```

#### Nullable Types

```scala
val nullableString = String::class.asTypeName().copy(nullable = true)
```

**Output:**
```kotlin
String?
```

---

## Complete Example: Repository Interface

### Goal
Generate this Kotlin interface:

```kotlin
package com.breuninger.domain.repository

interface BestandRepository {
  suspend fun save(bestand: BestandCreateDocument)
  
  suspend fun getByIds(ids: List<ArtikelId>): List<BestandCreateDocument>
  
  suspend fun deleteBatch(bestaende: List<BestandDeleteDocument>)
}
```

### KotlinPoet Code

```scala
import com.squareup.kotlinpoet._
import scala.jdk.CollectionConverters._

object RepositoryGenerator {
  
  def generateRepository(): String = {
    // Create interface
    val interfaceBuilder = TypeSpec.interfaceBuilder("BestandRepository")
    
    // Method 1: save
    val saveMethod = FunSpec.builder("save")
      .addModifiers(KModifier.SUSPEND)
      .addParameter("bestand", ClassName.bestGuess("BestandCreateDocument"))
      .build()
    
    interfaceBuilder.addFunction(saveMethod)
    
    // Method 2: getByIds
    val listOfArtikelId = ParameterizedTypeName.get(
      ClassName.bestGuess("kotlin.collections.List"),
      ClassName.bestGuess("ArtikelId")
    )
    
    val listOfBestandCreateDocument = ParameterizedTypeName.get(
      ClassName.bestGuess("kotlin.collections.List"),
      ClassName.bestGuess("BestandCreateDocument")
    )
    
    val getByIdsMethod = FunSpec.builder("getByIds")
      .addModifiers(KModifier.SUSPEND)
      .addParameter("ids", listOfArtikelId)
      .returns(listOfBestandCreateDocument)
      .build()
    
    interfaceBuilder.addFunction(getByIdsMethod)
    
    // Method 3: deleteBatch
    val listOfBestandDeleteDocument = ParameterizedTypeName.get(
      ClassName.bestGuess("kotlin.collections.List"),
      ClassName.bestGuess("BestandDeleteDocument")
    )
    
    val deleteBatchMethod = FunSpec.builder("deleteBatch")
      .addModifiers(KModifier.SUSPEND)
      .addParameter("bestaende", listOfBestandDeleteDocument)
      .build()
    
    interfaceBuilder.addFunction(deleteBatchMethod)
    
    // Create file
    val fileSpec = FileSpec.builder("com.breuninger.domain.repository", "BestandRepository")
      .addType(interfaceBuilder.build())
      .build()
    
    fileSpec.toString
  }
}

// Usage
val kotlinCode = RepositoryGenerator.generateRepository()
println(kotlinCode)
```

---

## Complete Example: Domain Models

### Goal
Generate these Kotlin classes:

```kotlin
@JvmInline
value class ArtikelId(val value: String)

data class BestandCreateDocument(
  val id: ArtikelId,
  val quantity: Int,
  val warehouse: String
)
```

### KotlinPoet Code

```scala
import com.squareup.kotlinpoet._

object ModelGenerator {
  
  def generateArtikelId(): String = {
    val valueClass = TypeSpec.classBuilder("ArtikelId")
      .addAnnotation(ClassName.bestGuess("kotlin.jvm.JvmInline"))
      .addModifiers(KModifier.VALUE)
      .primaryConstructor(
        FunSpec.constructorBuilder()
          .addParameter("value", String::class)
          .build()
      )
      .addProperty(
        PropertySpec.builder("value", String::class)
          .initializer("value")
          .build()
      )
      .build()
    
    val fileSpec = FileSpec.builder("com.breuninger.domain.model", "ArtikelId")
      .addType(valueClass)
      .build()
    
    fileSpec.toString
  }
  
  def generateBestandCreateDocument(): String = {
    val dataClass = TypeSpec.classBuilder("BestandCreateDocument")
      .addModifiers(KModifier.DATA)
      .primaryConstructor(
        FunSpec.constructorBuilder()
          .addParameter("id", ClassName.bestGuess("ArtikelId"))
          .addParameter("quantity", Int::class)
          .addParameter("warehouse", String::class)
          .build()
      )
      .addProperty(
        PropertySpec.builder("id", ClassName.bestGuess("ArtikelId"))
          .initializer("id")
          .build()
      )
      .addProperty(
        PropertySpec.builder("quantity", Int::class)
          .initializer("quantity")
          .build()
      )
      .addProperty(
        PropertySpec.builder("warehouse", String::class)
          .initializer("warehouse")
          .build()
      )
      .build()
    
    val fileSpec = FileSpec.builder("com.breuninger.domain.model", "BestandCreateDocument")
      .addType(dataClass)
      .build()
    
    fileSpec.toString
  }
}
```

---

## Advanced Features

### 1. Annotations

```scala
val annotatedClass = TypeSpec.classBuilder("User")
  .addAnnotation(ClassName.bestGuess("kotlinx.serialization.Serializable"))
  .addAnnotation(
    AnnotationSpec.builder(ClassName.bestGuess("Entity"))
      .addMember("name = %S", "users")
      .build()
  )
  .build()
```

**Output:**
```kotlin
@Serializable
@Entity(name = "users")
class User
```

### 2. KDoc (Documentation)

```scala
val documentedFunction = FunSpec.builder("save")
  .addKdoc("Saves a document to the repository.\n\n")
  .addKdoc("@param document the document to save\n")
  .addKdoc("@return the saved document ID\n")
  .addParameter("document", ClassName.bestGuess("Document"))
  .returns(String::class)
  .build()
```

**Output:**
```kotlin
/**
 * Saves a document to the repository.
 *
 * @param document the document to save
 * @return the saved document ID
 */
fun save(document: Document): String
```

### 3. Control Flow (Statements)

```scala
val functionWithLogic = FunSpec.builder("processItems")
  .addParameter("items", 
    ParameterizedTypeName.get(
      ClassName.bestGuess("kotlin.collections.List"),
      String::class.asTypeName()
    )
  )
  .beginControlFlow("if (items.isEmpty())")
  .addStatement("return")
  .endControlFlow()
  .addStatement("")
  .beginControlFlow("items.forEach { item ->")
  .addStatement("println(item)")
  .endControlFlow()
  .build()
```

**Output:**
```kotlin
fun processItems(items: List<String>) {
  if (items.isEmpty()) {
    return
  }

  items.forEach { item ->
    println(item)
  }
}
```

### 4. Companion Objects

```scala
val classWithCompanion = TypeSpec.classBuilder("User")
  .addType(
    TypeSpec.companionObjectBuilder()
      .addFunction(
        FunSpec.builder("create")
          .returns(ClassName.bestGuess("User"))
          .addStatement("return User()")
          .build()
      )
      .build()
  )
  .build()
```

**Output:**
```kotlin
class User {
  companion object {
    fun create(): User {
      return User()
    }
  }
}
```

### 5. Type Aliases

```scala
val typeAlias = TypeAliasSpec.builder(
  "UserId",
  String::class.asTypeName()
).build()

val fileSpec = FileSpec.builder("com.example", "TypeAliases")
  .addTypeAlias(typeAlias)
  .build()
```

**Output:**
```kotlin
typealias UserId = String
```

---

## Integration with Our Grammar Tool

### How We Use KotlinPoet

```scala
package com.breuninger.arch.renderer

import com.breuninger.arch.model._
import com.squareup.kotlinpoet._

object KotlinRenderer {
  
  // Convert our Port model to Kotlin interface
  def renderPort(port: Port): String = {
    val interfaceBuilder = TypeSpec.interfaceBuilder(port.name)
    
    port.methods.foreach { method =>
      val funSpec = renderMethod(method)
      interfaceBuilder.addFunction(funSpec)
    }
    
    val fileSpec = FileSpec.builder(port.packageName, port.name)
      .addType(interfaceBuilder.build())
      .build()
    
    fileSpec.toString
  }
  
  // Convert our Method model to Kotlin function
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
        funBuilder.addModifiers(KModifier.SUSPEND)
        
        wrapped match {
          case Type.Unit => () // No return type for Unit
          case other => funBuilder.returns(renderTypeName(other))
        }
      
      case other =>
        funBuilder.returns(renderTypeName(other))
    }
    
    funBuilder.build()
  }
  
  // Convert our Type model to KotlinPoet TypeName
  private def renderTypeName(tpe: Type): TypeName = tpe match {
    case Type.Primitive(name) =>
      name match {
        case "String" => String::class.asTypeName()
        case "Int" => Int::class.asTypeName()
        case "Long" => Long::class.asTypeName()
        case "Boolean" => Boolean::class.asTypeName()
        case _ => ClassName.bestGuess(s"kotlin.$name")
      }
    
    case Type.Domain(name) =>
      ClassName.bestGuess(name)
    
    case Type.Generic("List", args) =>
      ParameterizedTypeName.get(
        ClassName.bestGuess("kotlin.collections.List"),
        args.map(renderTypeName).toArray: _*
      )
    
    case Type.Generic("Option", args) =>
      // Option[T] becomes T? in Kotlin
      renderTypeName(args.head).copy(nullable = true)
    
    case _ => 
      ClassName.bestGuess("kotlin.Any")
  }
}
```

### Complete Flow

```
Our Abstract Model              KotlinPoet API              Kotlin Code
─────────────────              ───────────────             ───────────

Port(                    →     TypeSpec.interface    →     interface Repo {
  name = "Repo"                                              suspend fun save()
  methods = [                  FunSpec.builder              }
    Method(                      .addModifiers(SUSPEND)
      name = "save"              .build()
    )
  ]
)
```

---

## KotlinPoet vs String Templates

### String Template Approach (Fragile)

```scala
def generateKotlin(port: Port): String = {
  val methods = port.methods.map { m =>
    val params = m.parameters.map(p => s"${p.name}: ${p.paramType}").mkString(", ")
    s"  suspend fun ${m.name}($params)"
  }.mkString("\n")
  
  s"""
  |interface ${port.name} {
  |$methods
  |}
  """.stripMargin
}
```

**Problems:**
- Brittle (what if type name has spaces? generics?)
- No validation
- Hard to handle edge cases
- Manual indentation
- No import management

### KotlinPoet Approach (Robust)

```scala
def generateKotlin(port: Port): String = {
  val interfaceBuilder = TypeSpec.interfaceBuilder(port.name)
  
  port.methods.foreach { method =>
    val funSpec = FunSpec.builder(method.name)
      .addModifiers(KModifier.SUSPEND)
    
    method.parameters.foreach { param =>
      funSpec.addParameter(param.name, resolveType(param.paramType))
    }
    
    interfaceBuilder.addFunction(funSpec.build())
  }
  
  FileSpec.builder(port.packageName, port.name)
    .addType(interfaceBuilder.build())
    .build()
    .toString
}
```

**Advantages:**
- Type-safe
- Handles all edge cases
- Automatic formatting
- Automatic imports
- Maintainable

---

## Summary

**KotlinPoet = Kotlin Code Generator Library**

### Key Components

| Component | Purpose | Example |
|-----------|---------|---------|
| `FileSpec` | Represents a .kt file | Package, imports, top-level declarations |
| `TypeSpec` | Represents a type | Class, interface, object, enum |
| `FunSpec` | Represents a function | Methods, constructors, lambdas |
| `PropertySpec` | Represents a property | Fields, constants |
| `ParameterSpec` | Represents a parameter | Function/constructor parameters |
| `TypeName` | Represents a type reference | String, List<Int>, nullable types |

### Why We Use It

1. **Type Safety** - No string concatenation errors
2. **Validation** - Catches errors at compile time
3. **Formatting** - Perfect indentation and spacing
4. **Import Management** - Automatically handles imports
5. **Maintainability** - Easier to read and modify

### The Big Picture

```
Scalameta (Parse Scala)  →  Our Abstract Model  →  KotlinPoet (Generate Kotlin)
```

- **Scalameta** reads Scala and gives us an AST
- We convert the AST to **our abstract model**
- **KotlinPoet** converts our model to Kotlin code
