# Claude Instructions

## Building and Testing

This project uses **sbt** (Scala Build Tool) with Scala 2.13. It's a multi-module project with modules: `ir`, `parser`, `renderer`, `validator`, and `cli`.

### Compile

```bash
# Compile all modules
sbt compile

# Compile a specific module
sbt "parser/compile"
```

### Run Tests

```bash
# Run all tests across all modules
sbt test

# Run tests for a specific module
sbt "parser/test"
sbt "ir/test"
sbt "validator/test"
```

### Quality Gate

Before pushing, ensure all tests pass:

```bash
sbt test
```
