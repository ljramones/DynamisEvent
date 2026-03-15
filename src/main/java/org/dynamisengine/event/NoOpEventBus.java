package org.dynamisengine.event;

import java.util.concurrent.atomic.AtomicLong;
import org.dynamisengine.core.event.EngineEvent;
import org.dynamisengine.core.event.EventListener;
import org.dynamisengine.core.event.EventPriority;
import org.dynamisengine.core.event.EventSubscription;

/**
 * EventBus implementation that accepts calls without dispatching or storing any state.
 */
public final class NoOpEventBus implements EventBus {
  private final AtomicLong subscriptionIdCounter = new AtomicLong(1L);

  @Override
  public <T extends EngineEvent> EventSubscription subscribe(
      Class<T> eventType, EventListener<T> listener) {
    return EventSubscription.of(subscriptionIdCounter.getAndIncrement(), eventType, () -> {});
  }

  @Override
  public <T extends EngineEvent> EventSubscription subscribe(
      Class<T> eventType, EventListener<T> listener, EventPriority priority) {
    return EventSubscription.of(subscriptionIdCounter.getAndIncrement(), eventType, () -> {});
  }

  @Override
  public void publish(EngineEvent event) {}

  @Override
  public void unsubscribe(EventSubscription subscription) {}

  @Override
  public int subscriberCount(Class<? extends EngineEvent> eventType) {
    return 0;
  }

  @Override
  public BusMetrics metrics() {
    return BusMetrics.EMPTY;
  }

  @Override
  public void shutdown() {}
}
