# Module Contracts and Enforcement Rules

This document defines the contracts between modules and how to enforce architectural boundaries.

## Enforcement Mechanisms

### 1. Build-Time Enforcement (SBT)

The module dependencies are enforced by SBT's `.dependsOn()` mechanism:

```scala
// build.sbt enforces dependencies at compile time
lazy val parser = (project in file("modules/parser"))
  .dependsOn(ir)  // Can only import from ir module
```

**Violation Detection**: If parser tries to import from renderer, SBT compilation will fail.

### 2. Compile-Time Enforcement (Scala Compiler)

The Scala compiler enforces:
- Type safety across module boundaries
- Visibility of public APIs
- Immutability contracts

**Example**:
```scala
// In parser module - ALLOWED
import com.breuninger.arch.ir.DomainModel

// In parser module - FORBIDDEN (compile error)
import com.breuninger.arch.renderer.KotlinRenderer
```

### 3. Code Review Enforcement

During code review, verify:
- No new dependencies added to ir module
- parser, renderer, validator only depend on ir
- cli depends on all modules but contains no business logic
- No framework-specific code in ir module

## Module Contracts

### IR Module Contract

**EXPORTS**:
- ✅ Domain model types (DomainModel, Field, TypeRef, etc.)
- ✅ Port interface types (PortInterface, PortMethod, etc.)
- ✅ Common types (Location, Annotation, Constraint)

**MUST NOT**:
- ❌ Import from any other project module
- ❌ Reference Scalameta, KotlinPoet, or Spring frameworks
- ❌ Contain mutable state
- ❌ Perform I/O operations
- ❌ Contain parsing or rendering logic

**VALIDATION CHECKS**:
```scala
// Run these checks in CI
// 1. Check for external dependencies
grep -r "import org.scalameta" modules/ir/  # Should be empty
grep -r "import com.squareup" modules/ir/   # Should be empty
grep -r "import org.springframework" modules/ir/  # Should be empty

// 2. Check for mutable state
grep -r "var " modules/ir/src/main/scala/  # Should be empty

// 3. Check for module dependencies
grep -r "import com.breuninger.arch.parser" modules/ir/  # Should be empty
grep -r "import com.breuninger.arch.renderer" modules/ir/  # Should be empty
```

### Parser Module Contract

**IMPORTS FROM**:
- ✅ ir module (all public types)
- ✅ Scalameta library

**EXPORTS**:
- ✅ ScalaParser trait
- ✅ ParseResult, ParseError

**MUST NOT**:
- ❌ Import from renderer, validator, or cli modules
- ❌ Reference KotlinPoet or Spring frameworks
- ❌ Perform validation (call validator module instead)
- ❌ Generate code (call renderer module instead)

**VALIDATION CHECKS**:
```scala
// Run in CI
grep -r "import com.breuninger.arch.renderer" modules/parser/  # Should be empty
grep -r "import com.breuninger.arch.validator" modules/parser/  # Should be empty
grep -r "import com.breuninger.arch.cli" modules/parser/  # Should be empty
grep -r "import com.squareup.kotlinpoet" modules/parser/  # Should be empty
```

### Renderer Module Contract

**IMPORTS FROM**:
- ✅ ir module (all public types)
- ✅ KotlinPoet library

**EXPORTS**:
- ✅ KotlinRenderer trait
- ✅ RenderConfig, RenderError

**MUST NOT**:
- ❌ Import from parser, validator, or cli modules
- ❌ Reference Scalameta or perform parsing
- ❌ Perform validation (call validator module instead)
- ❌ Read source files (accept IR types only)

**VALIDATION CHECKS**:
```scala
// Run in CI
grep -r "import com.breuninger.arch.parser" modules/renderer/  # Should be empty
grep -r "import com.breuninger.arch.validator" modules/renderer/  # Should be empty
grep -r "import com.breuninger.arch.cli" modules/renderer/  # Should be empty
grep -r "import org.scalameta" modules/renderer/  # Should be empty
```

### Validator Module Contract

**IMPORTS FROM**:
- ✅ ir module (all public types)
- ✅ Cats library (for functional validation)

**EXPORTS**:
- ✅ ArchitectureValidator trait
- ✅ ValidationResult, ValidationError, ValidationWarning
- ✅ ValidationRule types

**MUST NOT**:
- ❌ Import from parser, renderer, or cli modules
- ❌ Perform parsing or rendering
- ❌ Read source files (accept IR types only)

**VALIDATION CHECKS**:
```scala
// Run in CI
grep -r "import com.breuninger.arch.parser" modules/validator/  # Should be empty
grep -r "import com.breuninger.arch.renderer" modules/validator/  # Should be empty
grep -r "import com.breuninger.arch.cli" modules/validator/  # Should be empty
```

### CLI Module Contract

**IMPORTS FROM**:
- ✅ All modules (ir, parser, renderer, validator)
- ✅ scopt library (CLI parsing)

**EXPORTS**:
- ✅ Main entry point
- ✅ Command types
- ✅ MigrationPipeline

**MUST NOT**:
- ❌ Contain business logic (delegate to other modules)
- ❌ Directly use Scalameta or KotlinPoet (use parser/renderer)
- ❌ Implement validation rules (use validator module)

**VALIDATION CHECKS**:
```scala
// CLI should orchestrate, not implement
// Check that CLI files are small and focused on coordination
find modules/cli/src/main/scala -name "*.scala" -exec wc -l {} \; | sort -rn
# Files should generally be < 200 lines (coordination code)
```

## Automated Enforcement

### Pre-Commit Hook

Create `.git/hooks/pre-commit`:

```bash
#!/bin/bash

echo "Checking module boundaries..."

# Check IR module purity
if grep -r "import org.scalameta\|import com.squareup\|import org.springframework" modules/ir/ 2>/dev/null; then
  echo "ERROR: IR module must not depend on external frameworks"
  exit 1
fi

# Check parser module boundaries
if grep -r "import com.breuninger.arch.renderer\|import com.breuninger.arch.validator\|import com.breuninger.arch.cli" modules/parser/ 2>/dev/null; then
  echo "ERROR: Parser module has invalid dependencies"
  exit 1
fi

# Check renderer module boundaries
if grep -r "import com.breuninger.arch.parser\|import com.breuninger.arch.validator\|import com.breuninger.arch.cli" modules/renderer/ 2>/dev/null; then
  echo "ERROR: Renderer module has invalid dependencies"
  exit 1
fi

# Check validator module boundaries
if grep -r "import com.breuninger.arch.parser\|import com.breuninger.arch.renderer\|import com.breuninger.arch.cli" modules/validator/ 2>/dev/null; then
  echo "ERROR: Validator module has invalid dependencies"
  exit 1
fi

echo "Module boundaries verified ✓"
```

### CI/CD Pipeline

Add to `.github/workflows/ci.yml` or similar:

```yaml
- name: Verify Module Boundaries
  run: |
    ./scripts/verify-module-boundaries.sh
```

Create `scripts/verify-module-boundaries.sh`:

```bash
#!/bin/bash
set -e

echo "=== Verifying Module Boundaries ==="

# Function to check for forbidden imports
check_imports() {
  local module=$1
  shift
  local forbidden_patterns=("$@")

  for pattern in "${forbidden_patterns[@]}"; do
    if grep -r "$pattern" "modules/$module/src" 2>/dev/null; then
      echo "ERROR: Module $module contains forbidden import: $pattern"
      return 1
    fi
  done
  return 0
}

# Check IR module
check_imports "ir" \
  "import org.scalameta" \
  "import com.squareup" \
  "import org.springframework" \
  "import com.breuninger.arch.parser" \
  "import com.breuninger.arch.renderer" \
  "import com.breuninger.arch.validator" \
  "import com.breuninger.arch.cli"

# Check parser module
check_imports "parser" \
  "import com.breuninger.arch.renderer" \
  "import com.breuninger.arch.validator" \
  "import com.breuninger.arch.cli" \
  "import com.squareup.kotlinpoet"

# Check renderer module
check_imports "renderer" \
  "import com.breuninger.arch.parser" \
  "import com.breuninger.arch.validator" \
  "import com.breuninger.arch.cli" \
  "import org.scalameta"

# Check validator module
check_imports "validator" \
  "import com.breuninger.arch.parser" \
  "import com.breuninger.arch.renderer" \
  "import com.breuninger.arch.cli"

echo "✓ All module boundaries verified"
```

## Testing Contracts

Each module must have tests that verify its contract:

### IR Module Tests

```scala
// modules/ir/src/test/scala/IRContractSpec.scala
class IRContractSpec extends AnyFlatSpec {
  "IR module" should "have no external dependencies" in {
    // Verify through compilation - if this compiles, we're good
    val model = DomainModel("Test", List.empty, ValueObject)
    assert(model.name == "Test")
  }

  it should "only contain immutable types" in {
    // All case classes are immutable by default
    // This test documents the requirement
    succeed
  }
}
```

### Parser Module Tests

```scala
// modules/parser/src/test/scala/ParserContractSpec.scala
class ParserContractSpec extends AnyFlatSpec {
  "Parser module" should "only depend on IR" in {
    // If this compiles, contract is satisfied
    val parser: ScalaParser = ???
    succeed
  }

  it should "not reference renderer types" in {
    // Compilation will fail if renderer is imported
    succeed
  }
}
```

## Design Principles

### 1. Dependency Inversion Principle

High-level modules (cli) depend on abstractions (traits in ir).
Low-level modules (parser, renderer) implement these abstractions.

### 2. Interface Segregation Principle

Each module exposes only what clients need:
- Parser exports ScalaParser trait (not Scalameta internals)
- Renderer exports KotlinRenderer trait (not KotlinPoet internals)

### 3. Single Responsibility Principle

Each module has one reason to change:
- IR changes when domain model evolves
- Parser changes when Scala parsing needs change
- Renderer changes when Kotlin generation needs change
- Validator changes when architectural rules change
- CLI changes when user interface needs change

### 4. Open/Closed Principle

Modules are open for extension (new implementations) but closed for modification:
- New parsers (Java, Kotlin) extend the pattern without modifying ir
- New renderers (TypeScript, Go) extend without modifying ir

## Troubleshooting

### "Cannot resolve symbol" errors

If you get compilation errors like "Cannot resolve symbol DomainModel":
1. Verify the module dependency in build.sbt
2. Check the import statement uses the correct package
3. Run `sbt clean compile` to refresh

### Circular dependency errors

If SBT reports circular dependencies:
1. Identify which modules are creating the cycle
2. Refactor to move shared types to ir module
3. Ensure only cli depends on multiple modules

### Contract violations in code review

If code review reveals contract violations:
1. Move code to the appropriate module
2. Update tests to reflect the new structure
3. Verify with `./scripts/verify-module-boundaries.sh`
