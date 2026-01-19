package com.breuninger.arch

/**
 * IR (Intermediate Representation) module
 *
 * Core abstract model for representing architectural concepts in a language-agnostic way.
 * This module defines the data structures that represent:
 * - Domain models (value objects, entities, sealed hierarchies)
 * - Port interfaces (repository, service contracts)
 * - Application services (orchestration, use cases)
 * - Adapters (infrastructure implementations)
 * - Type signatures and mappings
 *
 * The IR serves as a bridge between:
 * - Scala code parsing (Scalameta) → IR
 * - IR → Kotlin code generation (KotlinPoet)
 *
 * Main components:
 * - [[Type]]: Type system (primitives, named types, generics, collections)
 * - [[Method]]: Method signatures with parameters and return types
 * - [[DomainModel]]: Domain concepts (value objects, entities, sealed hierarchies, enums)
 * - [[Port]]: Interface contracts for architectural boundaries
 * - [[Service]]: Application services with port dependencies
 * - [[Adapter]]: Infrastructure implementations of ports
 * - [[Module]]: Package/module organization
 * - [[Project]]: Complete codebase representation
 */
package object ir {
  // All IR types are defined in their respective files:
  // - Type.scala: Type representations
  // - Method.scala: Method and parameter representations
  // - DomainModel.scala: Domain model representations
  // - Port.scala: Port interface representations
  // - Module.scala: Module and project structure
}
