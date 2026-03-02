package org.dynamis.event;

import java.util.Collection;
import org.dynamis.core.event.EngineEvent;
import org.dynamis.core.event.EventListener;
import org.dynamis.core.event.EventPriority;
import org.dynamis.core.event.EventSubscription;

/**
 * Core event bus contract for publishing and subscribing to engine events.
 */
public interface EventBus {
  <T extends EngineEvent> EventSubscription subscribe(Class<T> eventType, EventListener<T> listener);

  <T extends EngineEvent> EventSubscription subscribe(
      Class<T> eventType, EventListener<T> listener, EventPriority priority);

  void publish(EngineEvent event);

  default void publishAsync(EngineEvent event) {
    publish(event);
  }

  void unsubscribe(EventSubscription subscription);

  int subscriberCount(Class<? extends EngineEvent> eventType);

  BusMetrics metrics();

  default void publishAll(Collection<? extends EngineEvent> events) {
    for (EngineEvent event : events) {
      publish(event);
    }
  }

  default void shutdown() {
    // No-op by default.
  }
}
