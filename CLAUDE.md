# DynamisEvent Architecture Notes

## Purpose
DynamisEvent provides runtime event bus implementations for the Dynamis ecosystem while remaining behind DynamisCore contracts.

## Dependency Rule
- Compile dependency is limited to one artifact: `org.dynamis:dynamis-core:1.0.0`.
- No additional runtime dependencies are allowed.
- Test-only dependencies (for example JUnit) are permitted under `test` scope.
- One-time developer setup: run `mvn install` in the sibling `DynamisCore` repository before
  building DynamisEvent so `dynamis-core:1.0.0` is available in the local Maven cache.

## Core Design Decision
- Subsystems depend on `EngineEvent`, `EventListener`, `EventSubscription`, and `EventPriority` from DynamisCore only.
- Subsystems do not import concrete classes from DynamisEvent.
- `DynamisWorldEngine` is responsible for selecting and wiring the concrete bus implementation (`SynchronousEventBus`, `AsyncEventBus`, or `NoOpEventBus`) through `EventBusBuilder`.
- Dispatch is exact-type only — a listener registered for a supertype will not receive subtype
  events. This is a conscious design decision for performance and predictability.
- `SynchronousEventBus` listener exceptions propagate to the caller of `publish()`.
- `AsyncEventBus` listener exceptions execute on worker threads and do not propagate to the
  caller of `publish()`/`publishAsync()`.
- `EventBusBuilder` is single-use by design; a second `build()` call throws
  `IllegalStateException("EventBusBuilder has already been built")`.
- `EventBusBuilder` includes an explicit `threadPoolSize` null guard in the async build path to
  prevent impossible-state assumptions and satisfy SpotBugs null-safety analysis.

## Planned Package Layout
- `src/main/java/org/dynamis/event` for bus interfaces and implementations.
- `src/test/java/org/dynamis/event` for implementation tests.
- Subsystem-style tests should interact through `EventBus` and `EventBusBuilder`, not concrete bus types.

## Class Inventory
### Public API
- `EventBus`: central event bus contract used by engine wiring and subsystem callers.
- `BusMetrics`: immutable metrics snapshot for published, delivered, dead-letter, latency, and
  active subscription counts.
- `DeadLetterHandler`: functional callback for unhandled events.
- `EventBusBuilder`: single-use builder for selecting synchronous/async mode and dead-letter
  behavior.

### Implementations
- `SynchronousEventBus`: exact-type, caller-thread dispatch with priority ordering and atomic
  metrics tracking.
- `AsyncEventBus`: fixed-thread-pool dispatch that delegates subscription and delivery behavior to
  `SynchronousEventBus`.
- `NoOpEventBus`: do-nothing implementation for tests or disabled-dispatch contexts.

### Internal Implementation Detail
- `ListenerEntry`: package-private listener registration record containing subscription ID,
  listener instance, and priority.
