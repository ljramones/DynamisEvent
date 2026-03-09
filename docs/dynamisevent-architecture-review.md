# DynamisEvent Architecture Boundary Ratification Review

Date: 2026-03-09

## Intent and Scope

This is a **boundary-ratification review** for DynamisEvent based on current repository code and docs.

This pass does not refactor code or rename APIs. It establishes strict ownership/dependency boundaries for DynamisEvent and clarifies what should be deferred to higher orchestration layers.

## 1) Repo Overview (Grounded)

Repository shape:

- Single-module Java 25 JAR (`pom.xml`, packaging `jar`)
- Main package: `org.dynamis.event`
- Public event bus contracts and implementations currently coexist in the same package:
  - `EventBus`
  - `EventBusBuilder`
  - `SynchronousEventBus`
  - `AsyncEventBus`
  - `NoOpEventBus`
  - `DeadLetterHandler`
  - `BusMetrics`
- Internal listener node type:
  - `ListenerEntry` (package-private)

Dependencies:

- Compile dependency: `org.dynamis:dynamis-core:1.0.0`
- No direct dependencies on world/session/scene/renderer modules.

Behavior visible in tests:

- Synchronous dispatch preserves priority ordering and caller-thread execution.
- Asynchronous dispatch uses a fixed thread pool, with publish returning before listener completion.
- Dead-letter handling exists and is pluggable.
- Bus metrics are tracked.
- No-op bus exists for test/muted dispatch contexts.

## 2) Strict Ownership Statement

### 2.1 What DynamisEvent should exclusively own

DynamisEvent should own **generic runtime event transport mechanics** for the ecosystem:

- listener registration and unregistration lifecycle
- event dispatch mechanics (sync/async variants)
- generic dispatch ordering/priority semantics
- dead-letter handling hook
- bus-level observability metrics (`BusMetrics`)
- implementation selection/build-time wiring surface (`EventBusBuilder`)

### 2.2 What is appropriate for this subsystem

Appropriate concerns include:

- subscription identity and cancellation semantics
- delivery guarantees scoped to bus mode (sync vs async)
- thread-safety for subscribe/unsubscribe/publish operations
- mode-neutral API contract (`EventBus`)

### 2.3 What DynamisEvent must never own

DynamisEvent must not own:

- world/session/gameplay authority
- scene graph routing policy
- ECS ownership or component state mutation policy
- scripting/AI decision policy
- renderer-specific event semantics
- persistence/replay/state authority
- application orchestration policy (startup/tick/shutdown ordering across subsystems)

Those belong to higher layers (WorldEngine, Session, Scripting, SceneGraph, LightEngine, ECS).

## 3) Dependency Rules

### 3.1 Allowed dependencies for DynamisEvent

- DynamisCore event/lifecycle/logging contracts (as currently used)
- JDK concurrency/util collections primitives

### 3.2 Forbidden dependencies for DynamisEvent

- `DynamisSession`, `DynamisWorldEngine`, `DynamisSceneGraph`, `DynamisScripting`, `DynamisECS`, `DynamisLightEngine`
- Feature/runtime modules (`DynamisAI`, `DynamisPhysics`, `DynamisUI`, etc.)
- backend-specific runtime dependencies (GPU/window/audio/rendering)

### 3.3 Who may depend on DynamisEvent

- Integration/orchestration layers that instantiate/configure a bus (notably WorldEngine)
- Potential host/runtime composition modules

Preferred boundary posture:

- Most feature/runtime modules should target **DynamisCore event contracts** and receive an `EventBus` abstraction via composition/injection.
- Avoid direct dependency from every subsystem to concrete bus implementations.

## 4) Public vs Internal Boundary Assessment

### 4.1 Canonical public boundary (current)

Current public surface is effectively everything public in `org.dynamis.event`, including concrete implementations.

This includes both:

- stable contract types (`EventBus`, `DeadLetterHandler`, `BusMetrics`)
- concrete runtime classes (`SynchronousEventBus`, `AsyncEventBus`, `NoOpEventBus`, `EventBusBuilder`)

### 4.2 Internal implementation areas

- `ListenerEntry` is package-private and appropriately internal.

### 4.3 Boundary concern

Because this is a single package/module without explicit API/internal partitioning, concrete implementation classes are easy for downstream code to bind to directly.

This is not a functional bug, but it is a boundary-hardening concern: it can freeze implementation details as de facto public API.

## 5) Policy Leakage / Overlap Findings

### 5.1 Major clean boundaries confirmed

- No world/session/scene/scripting/ECS policy logic in code.
- Dispatch concerns are generic and subsystem-neutral.
- No persistence/replay or canonical state ownership in this repo.
- No direct coupling to rendering or feature modules.

### 5.2 Overlap / leakage risks to watch

1. **WorldEngine overlap risk (future integration phase)**
- If WorldEngine starts embedding dispatch policy beyond configuration (e.g., per-subsystem routing semantics), boundary can blur.

2. **Session/Scripting overlap risk (future)**
- Event replay, persistence, or authoritative state deltas must not be added here; those are higher-layer responsibilities.

3. **SceneGraph/ECS overlap risk (future)**
- Spatial/entity-scoped routing policies should remain outside DynamisEvent unless expressed as generic filters with no domain semantics.

4. **API surface broadness**
- Public concrete classes in same package may encourage direct implementation coupling in downstream repos.

## 6) Ratification Result

**Ratified with constraints**.

Why:

- Current implementation is focused on generic dispatch mechanics and is largely clean.
- No significant policy leakage is present now.
- Constraints are needed to prevent future drift and to keep concrete implementation exposure from becoming accidental long-term API lock-in.

## 7) Strict Boundary Rules to Carry Forward

1. Keep DynamisEvent focused on transport semantics, not domain policy.
2. Keep dependency floor at Core + JDK only.
3. Do not introduce world/session/ECS/scene-specific routing semantics.
4. Do not add replay/persistence/state-authority logic to bus layer.
5. Prefer that higher layers depend on `EventBus` abstraction, not concrete bus classes.

## 8) Recommended Next Step

Next deep review should be **DynamisECS**.

Reason:

- ECS is the next major potential authority/ownership boundary hotspot after Core/Event.
- Clarifying ECS ownership before SceneGraph and LightEngine reduces downstream integration ambiguity.
