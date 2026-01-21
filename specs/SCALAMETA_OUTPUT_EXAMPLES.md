# Scalameta Parser Output Examples

## What is Scalameta?

Scalameta is a library for metaprogramming in Scala that provides:
- **Parsing**: Convert Scala source code into an Abstract Syntax Tree (AST)
- **Semantic Analysis**: Resolve types, symbols, and references
- **Tree Manipulation**: Transform and generate Scala code

## Output: Abstract Syntax Tree (AST)

When you parse Scala code with Scalameta, you get a **tree structure** representing the code's syntax.

---

## Example 1: Simple Case Class

### Input Scala Code

```scala
case class ArtikelId(value: String) extends AnyVal
```

### Scalameta AST Output

```scala
Defn.Class(
  mods = List(Mod.Case()),                    // case modifier
  name = Type.Name("ArtikelId"),              // class name
  tparams = List(),                           // no type parameters
  ctor = Ctor.Primary(                        // primary constructor
    mods = List(),
    name = Name(""),
    paramss = List(
      List(
        Term.Param(                           // constructor parameter
          mods = List(),
          name = Term.Name("value"),          // parameter name
          decltpe = Some(Type.Name("String")), // parameter type
          default = None                      // no default value
        )
      )
    )
  ),
  templ = Template(                           // class body
    early = List(),
    inits = List(
      Init(
        tpe = Type.Name("AnyVal"),            // extends AnyVal
        name = Name(""),
        argss = List()
      )
    ),
    self = Self(Name(""), None),
    stats = List()                            // empty body
  )
)
```

### Tree Structure Visualization

```
Defn.Class
├── mods: [Mod.Case]
├── name: Type.Name("ArtikelId")
├── tparams: []
├── ctor: Ctor.Primary
│   └── paramss: [[Term.Param("value", Type.Name("String"))]]
└── templ: Template
    ├── inits: [Init(Type.Name("AnyVal"))]
    └── stats: []
```

---

## Example 2: Repository Trait

### Input Scala Code

```scala
package com.breuninger.domain.repository

import cats.effect.IO

trait BestandRepository {
  def save(bestand: BestandCreateDocument): IO[Unit]
  def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]]
}
```

### Scalameta AST Output

```scala
Source(
  stats = List(
    // Package declaration
    Pkg(
      ref = Term.Select(
        qual = Term.Select(
          qual = Term.Name("com"),
          name = Term.Name("breuninger")
        ),
        name = Term.Name("domain")
      ),
      stats = List(
        // Import statement
        Import(
          importers = List(
            Importer(
              ref = Term.Select(
                qual = Term.Select(
                  qual = Term.Name("cats"),
                  name = Term.Name("effect")
                ),
                name = Term.Name("IO")
              ),
              importees = List(Importee.Name(Name("IO")))
            )
          )
        ),
        
        // Trait definition
        Defn.Trait(
          mods = List(),                              // no modifiers
          name = Type.Name("BestandRepository"),      // trait name
          tparams = List(),                           // no type parameters
          ctor = Ctor.Primary(
            mods = List(),
            name = Name(""),
            paramss = List()
          ),
          templ = Template(
            early = List(),
            inits = List(),
            self = Self(Name(""), None),
            stats = List(
              // Method: save
              Decl.Def(
                mods = List(),
                name = Term.Name("save"),             // method name
                tparams = List(),
                paramss = List(
                  List(
                    Term.Param(
                      mods = List(),
                      name = Term.Name("bestand"),    // parameter name
                      decltpe = Some(
                        Type.Name("BestandCreateDocument")  // parameter type
                      ),
                      default = None
                    )
                  )
                ),
                decltpe = Type.Apply(                 // return type: IO[Unit]
                  tpe = Type.Name("IO"),
                  args = List(Type.Name("Unit"))
                )
              ),
              
              // Method: getByIds
              Decl.Def(
                mods = List(),
                name = Term.Name("getByIds"),
                tparams = List(),
                paramss = List(
                  List(
                    Term.Param(
                      mods = List(),
                      name = Term.Name("ids"),
                      decltpe = Some(
                        Type.Apply(                   // List[ArtikelId]
                          tpe = Type.Name("List"),
                          args = List(Type.Name("ArtikelId"))
                        )
                      ),
                      default = None
                    )
                  )
                ),
                decltpe = Type.Apply(                 // IO[List[BestandCreateDocument]]
                  tpe = Type.Name("IO"),
                  args = List(
                    Type.Apply(
                      tpe = Type.Name("List"),
                      args = List(Type.Name("BestandCreateDocument"))
                    )
                  )
                )
              )
            )
          )
        )
      )
    )
  )
)
```

### Tree Structure Visualization

```
Source
└── Pkg("com.breuninger.domain.repository")
    ├── Import("cats.effect.IO")
    └── Defn.Trait("BestandRepository")
        └── Template
            ├── Decl.Def("save")
            │   ├── paramss: [[Term.Param("bestand", "BestandCreateDocument")]]
            │   └── decltpe: Type.Apply("IO", [Type.Name("Unit")])
            └── Decl.Def("getByIds")
                ├── paramss: [[Term.Param("ids", Type.Apply("List", ["ArtikelId"]))]]
                └── decltpe: Type.Apply("IO", [Type.Apply("List", ["BestandCreateDocument"])])
```

---

## Example 3: Sealed Trait Hierarchy

### Input Scala Code

```scala
sealed trait Result
case class Success(value: String) extends Result
case class Failure(error: String) extends Result
```

### Scalameta AST Output

```scala
Source(
  stats = List(
    // Sealed trait
    Defn.Trait(
      mods = List(Mod.Sealed()),                    // sealed modifier
      name = Type.Name("Result"),
      tparams = List(),
      ctor = Ctor.Primary(mods = List(), name = Name(""), paramss = List()),
      templ = Template(
        early = List(),
        inits = List(),
        self = Self(Name(""), None),
        stats = List()
      )
    ),
    
    // Success case class
    Defn.Class(
      mods = List(Mod.Case()),
      name = Type.Name("Success"),
      tparams = List(),
      ctor = Ctor.Primary(
        mods = List(),
        name = Name(""),
        paramss = List(
          List(
            Term.Param(
              mods = List(),
              name = Term.Name("value"),
              decltpe = Some(Type.Name("String")),
              default = None
            )
          )
        )
      ),
      templ = Template(
        early = List(),
        inits = List(
          Init(Type.Name("Result"), Name(""), List())  // extends Result
        ),
        self = Self(Name(""), None),
        stats = List()
      )
    ),
    
    // Failure case class
    Defn.Class(
      mods = List(Mod.Case()),
      name = Type.Name("Failure"),
      tparams = List(),
      ctor = Ctor.Primary(
        mods = List(),
        name = Name(""),
        paramss = List(
          List(
            Term.Param(
              mods = List(),
              name = Term.Name("error"),
              decltpe = Some(Type.Name("String")),
              default = None
            )
          )
        )
      ),
      templ = Template(
        early = List(),
        inits = List(
          Init(Type.Name("Result"), Name(""), List())
        ),
        self = Self(Name(""), None),
        stats = List()
      )
    )
  )
)
```

---

## How to Use Scalameta Parser

### Basic Parsing

```scala
import scala.meta._

// Parse a string of Scala code
val code = """
  case class ArtikelId(value: String) extends AnyVal
"""

// Parse into AST
val tree: Source = code.parse[Source].get

// tree is now a Scalameta AST
println(tree)
println(tree.structure)  // Shows detailed structure
```

### Pattern Matching on AST

```scala
import scala.meta._

val code = """
  trait BestandRepository {
    def save(doc: Document): IO[Unit]
  }
""".parse[Source].get

// Pattern match to extract information
code.collect {
  case Defn.Trait(_, name, _, _, template) =>
    println(s"Found trait: ${name}")
    
    template.stats.collect {
      case Decl.Def(_, methodName, _, paramss, returnType) =>
        println(s"  Method: ${methodName}")
        println(s"  Return type: ${returnType}")
    }
}
```

### Output:
```
Found trait: BestandRepository
  Method: save
  Return type: IO[Unit]
```

---

## Key Scalameta Tree Node Types

### Definitions (Defn)

| Type | Description | Example |
|------|-------------|---------|
| `Defn.Class` | Class definition | `class Foo` or `case class Foo` |
| `Defn.Trait` | Trait definition | `trait Bar` |
| `Defn.Object` | Object definition | `object Baz` |
| `Defn.Def` | Method with implementation | `def foo = 42` |
| `Defn.Val` | Value definition | `val x = 10` |
| `Defn.Var` | Variable definition | `var y = 20` |

### Declarations (Decl)

| Type | Description | Example |
|------|-------------|---------|
| `Decl.Def` | Abstract method (no body) | `def save(): IO[Unit]` |
| `Decl.Val` | Abstract value | `val name: String` |

### Types

| Type | Description | Example |
|------|-------------|---------|
| `Type.Name` | Simple type name | `String`, `Int` |
| `Type.Apply` | Generic type application | `List[String]`, `IO[Unit]` |
| `Type.Select` | Selected type | `cats.effect.IO` |
| `Type.Function` | Function type | `A => B` |

### Terms

| Type | Description | Example |
|------|-------------|---------|
| `Term.Name` | Variable/method name | `foo`, `bar` |
| `Term.Param` | Parameter definition | `x: Int` |
| `Term.Apply` | Function application | `foo(42)` |
| `Term.Select` | Field/method selection | `obj.field` |

### Modifiers (Mod)

| Type | Description | Example |
|------|-------------|---------|
| `Mod.Case` | Case modifier | `case class` |
| `Mod.Sealed` | Sealed modifier | `sealed trait` |
| `Mod.Private` | Private modifier | `private def` |
| `Mod.Abstract` | Abstract modifier | `abstract class` |

---

## Real-World Example: Extracting Port Information

### Code to Parse

```scala
package com.breuninger.domain.repository

import cats.effect.IO

trait BestandRepository {
  def save(bestand: BestandCreateDocument): IO[Unit]
  def getByIds(ids: List[ArtikelId]): IO[List[BestandCreateDocument]]
  def delete(id: ArtikelId): IO[Boolean]
}
```

### Parser Implementation

```scala
import scala.meta._

def extractPortInfo(scalaCode: String): Unit = {
  val tree = scalaCode.parse[Source].get
  
  // Find all traits
  tree.collect {
    case Defn.Trait(mods, traitName, _, _, template) =>
      println(s"Trait: ${traitName.value}")
      
      // Extract methods
      template.stats.collect {
        case Decl.Def(_, methodName, _, paramss, returnType) =>
          println(s"  Method: ${methodName.value}")
          
          // Extract parameters
          paramss.flatten.foreach { param =>
            val paramName = param.name.value
            val paramType = param.decltpe.map(_.syntax).getOrElse("Unknown")
            println(s"    Param: $paramName: $paramType")
          }
          
          // Extract return type
          println(s"    Returns: ${returnType.syntax}")
      }
  }
}
```

### Output

```
Trait: BestandRepository
  Method: save
    Param: bestand: BestandCreateDocument
    Returns: IO[Unit]
  Method: getByIds
    Param: ids: List[ArtikelId]
    Returns: IO[List[BestandCreateDocument]]
  Method: delete
    Param: id: ArtikelId
    Returns: IO[Boolean]
```

---

## Converting Scalameta AST to Our Abstract Model

This is what our grammar tool does:

```scala
import scala.meta._
import com.breuninger.arch.model._

def convertToPort(tree: Defn.Trait): Port = {
  val name = tree.name.value
  
  val methods = tree.templ.stats.collect {
    case Decl.Def(_, methodName, _, paramss, returnType) =>
      Method(
        name = methodName.value,
        parameters = paramss.flatten.map { param =>
          Parameter(
            name = param.name.value,
            paramType = convertType(param.decltpe.get)
          )
        },
        returnType = convertType(returnType)
      )
  }
  
  Port(name, packageName = "", methods)
}

def convertType(metaType: scala.meta.Type): com.breuninger.arch.model.Type = {
  metaType match {
    case Type.Name("String") => Type.Primitive("String")
    case Type.Name("Int") => Type.Primitive("Int")
    case Type.Name("Unit") => Type.Unit
    
    case Type.Apply(Type.Name("IO"), args) =>
      Type.Effect(convertType(args.head), Type.EffectType.IO)
    
    case Type.Apply(Type.Name("List"), args) =>
      Type.Generic("List", args.map(convertType).toList)
    
    case Type.Name(name) =>
      Type.Domain(name)
    
    case _ => Type.Primitive("Unknown")
  }
}
```

---

## Scalameta vs Our Abstract Model

### Scalameta AST (Low-Level)
```scala
Decl.Def(
  name = Term.Name("save"),
  paramss = List(List(Term.Param(Term.Name("doc"), Type.Name("Document")))),
  decltpe = Type.Apply(Type.Name("IO"), List(Type.Name("Unit")))
)
```

### Our Abstract Model (High-Level)
```scala
Method(
  name = "save",
  parameters = List(Parameter("doc", Type.Domain("Document"))),
  returnType = Type.Effect(Type.Unit, Type.EffectType.IO)
)
```

**Key Difference:**
- **Scalameta AST** = Complete syntactic representation (every detail of syntax)
- **Our Model** = Semantic representation (architectural meaning)

---

## Inspecting Scalameta Output

### Method 1: Using `structure`

```scala
val code = "case class Foo(x: Int)".parse[Stat].get
println(code.structure)
```

**Output:**
```
Defn.Class(List(Mod.Case()), Type.Name("Foo"), List(), Ctor.Primary(List(), Name(""), List(List(Term.Param(List(), Term.Name("x"), Some(Type.Name("Int")), None)))), Template(List(), List(), Self(Name(""), None), List()))
```

### Method 2: Using Pretty Print

```scala
println(code.syntax)  // Pretty-printed Scala code
```

**Output:**
```scala
case class Foo(x: Int)
```

### Method 3: Interactive Exploration

```scala
import scala.meta._

val code = """
  trait Repo {
    def save(x: String): IO[Unit]
  }
""".parse[Source].get

// Explore interactively
code.collect { case t: Defn.Trait => t }.foreach { trait_ =>
  println(s"Trait name: ${trait_.name}")
  println(s"Modifiers: ${trait_.mods}")
  println(s"Template: ${trait_.templ}")
}
```

---

## Summary

**Scalameta Output = Abstract Syntax Tree (AST)**

The AST is a tree structure where:
- Each node represents a syntactic element (class, method, parameter, type, etc.)
- You traverse/pattern-match the tree to extract information
- Our grammar tool converts this low-level AST into our high-level architectural model

**Flow:**
```
Scala Source Code
      ↓ (Scalameta parse)
Scalameta AST (Defn.Trait, Decl.Def, Type.Apply, etc.)
      ↓ (Our parser)
Abstract Model (Port, Method, Type.Effect, etc.)
      ↓ (Validator)
Validation Results
      ↓ (Renderer)
Kotlin Source Code
```
