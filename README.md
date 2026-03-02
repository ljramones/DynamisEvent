# DynamisEvent

The event bus for the Dynamis game engine ecosystem. DynamisEvent enables subsystems to publish and subscribe to engine events without direct dependencies on each other — DynamisPhysics publishes collision events without knowing who listens, DynamisScripting subscribes to state changes without polling, DynamisAI reacts to world events without coupling to their source.

DynamisEvent implements the contracts defined in DynamisCore (`EngineEvent`, `EventListener`, `EventSubscription`, `EventPriority`) and provides the runtime bus that DynamisWorldEngine wires together at startup.

---

## Architecture

```
DynamisCore (contracts)
    ↓
DynamisEvent (implementations)
    ↓
DynamisWorldEngine (wires the bus)
    ↓
All other subsystems (publish and subscribe via DynamisCore contracts only)
```

Subsystems that publish or subscribe depend only on DynamisCore — they never depend on DynamisEvent directly. Only DynamisWorldEngine depends on DynamisEvent to construct and configure the bus. This means the bus implementation can be swapped (synchronous in tests, async in production) without touching any subsystem.

---

## Core Interfaces

**`EventBus`** — the central dispatcher interface. Subsystems interact with the bus through this contract.

```java
public interface EventBus {
    <T extends EngineEvent> EventSubscription subscribe(Class<T> eventType, EventListener<T> listener);
    <T extends EngineEvent> EventSubscription subscribe(Class<T> eventType, EventListener<T> listener, EventPriority priority);
    void publish(EngineEvent event);
    void publishAsync(EngineEvent event);
    void unsubscribe(EventSubscription subscription);
    int subscriberCount(Class<? extends EngineEvent> eventType);
    BusMetrics metrics();
}
```

---

## Implementations

**`SynchronousEventBus`** — dispatches events on the calling thread. Listeners are invoked in `EventPriority` order before `publish()` returns. Used during testing and for events that require guaranteed ordering within a tick.

**`AsyncEventBus`** — dispatches events on a managed thread pool. `publish()` returns immediately; listeners are invoked asynchronously. Used for non-critical notifications where dispatch latency matters.

**`NoOpEventBus`** — accepts all publishes and subscriptions silently. Used in unit tests for subsystems that require a bus but don't need real dispatch behavior.

---

## Dead Letter Handling

Events that have no registered listeners are routed to the dead letter handler. The default handler logs at DEBUG level. A custom handler can be registered via `EventBusBuilder`:

```java
EventBus bus = EventBusBuilder.create()
    .synchronous()
    .onDeadLetter(event -> log.warn("Unhandled event: " + event.getClass().getSimpleName()))
    .build();
```

---

## Bus Metrics

`BusMetrics` provides runtime visibility into bus health:

- `long totalPublished()` — total events published since startup
- `long totalDelivered()` — total listener invocations completed
- `long totalDeadLetters()` — events with no listener
- `double averageDispatchNanos()` — rolling average dispatch latency
- `int activeSubscriptions()` — current live subscription count

Metrics are published to DynamisDebug when available.

---

## Usage

### Defining an event

```java
// In any Dynamis component — depends on DynamisCore only
public record CollisionEvent(EntityId entityA, EntityId entityB, double impactForce)
    implements EngineEvent {

    @Override
    public EventPriority priority() {
        return EventPriority.HIGH;
    }
}
```

### Subscribing

```java
// Depends on DynamisCore only
EventSubscription sub = bus.subscribe(CollisionEvent.class, event -> {
    log.info("Collision: " + event.entityA() + " hit " + event.entityB());
});
```

### Publishing

```java
// Depends on DynamisCore only
bus.publish(new CollisionEvent(entityA, entityB, force));
```

### Unsubscribing

```java
sub.cancel();
// or
bus.unsubscribe(sub);
```

---

## EventBusBuilder

```java
// Synchronous bus for testing
EventBus testBus = EventBusBuilder.create()
    .synchronous()
    .build();

// Async bus for production
EventBus productionBus = EventBusBuilder.create()
    .async(threadPoolSize)
    .onDeadLetter(handler)
    .build();
```

---

## Dependencies

| Dependency | Scope |
|---|---|
| `org.dynamis:dynamis-core` | compile |

No other runtime dependencies.

---

## Requirements

- Java 25+

---

## License

Apache 2.0 — see [LICENSE](LICENSE) for details.
