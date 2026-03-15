package org.dynamisengine.event;

import java.util.Collection;
import org.dynamisengine.core.event.EngineEvent;
import org.dynamisengine.core.event.EventListener;
import org.dynamisengine.core.event.EventPriority;
import org.dynamisengine.core.event.EventSubscription;

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
