# Architecture Tool Specifications

A grammar-based tool for migrating Scala microservices to Kotlin + Spring Boot while preserving hexagonal architecture patterns.

## Quick Start

**New to arch-tool?** Start here:
- [Tool Scope Summary](TOOL_SCOPE_SUMMARY.md) - What's automated vs. manual, time savings, quick reference

## Core Architecture

**Fundamental concepts and design:**

- [Architecture Overview](ARCHITECTURE.md) - Module structure, boundaries, and responsibilities
- [Architecture Grammar](ARCHITECTURE_GRAMMAR.md) - Formal EBNF grammar specification and language-agnostic patterns
- [Module Contracts](MODULE_CONTRACTS.md) - Enforcement rules and boundaries between modules
- [Service IR Proposal](SERVICE_IR_PROPOSAL.md) - Application/Service layer intermediate representation design

## Implementation

**Building the tool:**

- [Grammar POC Plan](PLAN_GRAMMAR_POC.md) - Step-by-step POC implementation with IR design, parser, and renderer

## Framework Integration

**Target platform specifics:**

- [Spring Boot Migration](SPRING_BOOT_MIGRATION.md) - Spring annotations, dependency injection, WebFlux integration

## Testing & Quality

**Ensuring correctness:**

- [Test-Driven Migration](TEST_DRIVEN_MIGRATION.md) - Contract tests, integration tests, property-based testing, CI/CD

## API Reference

**Library documentation:**

- [KotlinPoet Examples](KOTLINPOET_EXAMPLES.md) - Code generation API reference
- [Scalameta Output Examples](SCALAMETA_OUTPUT_EXAMPLES.md) - Parser AST structure and output format

## Architecture Diagrams

**Visual representations:**

- [Application Service Audit](APPLICATION_SERVICE_AUDIT.md) - Service layer analysis

---

## Document Organization

```
Core Concepts
├─ TOOL_SCOPE_SUMMARY.md          Quick reference
├─ ARCHITECTURE.md                 Module structure
├─ ARCHITECTURE_GRAMMAR.md         Formal grammar (EBNF)
└─ MODULE_CONTRACTS.md             Enforcement rules

Design & Planning
├─ SERVICE_IR_PROPOSAL.md          Service layer design
└─ PLAN_GRAMMAR_POC.md             Implementation plan

Integration & Testing
├─ SPRING_BOOT_MIGRATION.md        Framework integration
└─ TEST_DRIVEN_MIGRATION.md        Quality assurance

Reference
├─ KOTLINPOET_EXAMPLES.md          Code generation API
└─ SCALAMETA_OUTPUT_EXAMPLES.md    Parser reference
```

## Reading Path

**For understanding the approach:**
1. TOOL_SCOPE_SUMMARY.md (5 min)
2. ARCHITECTURE_GRAMMAR.md (15 min)
3. ARCHITECTURE.md (10 min)

**For implementation:**
1. PLAN_GRAMMAR_POC.md (30 min)
2. MODULE_CONTRACTS.md (10 min)
3. SERVICE_IR_PROPOSAL.md (15 min)

**For specific integrations:**
- Spring Boot → SPRING_BOOT_MIGRATION.md
- Testing → TEST_DRIVEN_MIGRATION.md
- API reference → KOTLINPOET_EXAMPLES.md, SCALAMETA_OUTPUT_EXAMPLES.md
