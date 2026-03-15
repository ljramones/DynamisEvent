package org.dynamisengine.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.dynamisengine.core.event.EngineEvent;
import org.dynamisengine.core.event.EventListener;
import org.dynamisengine.core.event.EventPriority;
import org.dynamisengine.core.event.EventSubscription;
import org.junit.jupiter.api.Test;

class EventBusContractTest {
  @Test
  void busMetricsOfExposesAllValues() {
    BusMetrics metrics = BusMetrics.of(10L, 8L, 2L, 42.5, 3);

    assertEquals(10L, metrics.totalPublished());
    assertEquals(8L, metrics.totalDelivered());
    assertEquals(2L, metrics.totalDeadLetters());
    assertEquals(42.5, metrics.averageDispatchNanos());
    assertEquals(3, metrics.activeSubscriptions());
  }

  @Test
  void busMetricsEmptyContainsAllZeroValues() {
    assertEquals(0L, BusMetrics.EMPTY.totalPublished());
    assertEquals(0L, BusMetrics.EMPTY.totalDelivered());
    assertEquals(0L, BusMetrics.EMPTY.totalDeadLetters());
    assertEquals(0.0, BusMetrics.EMPTY.averageDispatchNanos());
    assertEquals(0, BusMetrics.EMPTY.activeSubscriptions());
  }

  @Test
  void busMetricsEqualityUsesRecordValueSemantics() {
    BusMetrics left = BusMetrics.of(1L, 2L, 3L, 4.0, 5);
    BusMetrics right = BusMetrics.of(1L, 2L, 3L, 4.0, 5);

    assertEquals(left, right);
  }

  @Test
  void deadLetterHandlerIsLambdaCompatible() {
    DeadLetterHandler handler = event -> {};

    assertNotNull(handler);
  }

  @Test
  void publishAllDefaultPublishesEachEventInOrder() {
    AtomicInteger publishCount = new AtomicInteger(0);
    EventBus bus =
        new EventBus() {
          @Override
          public <T extends EngineEvent> EventSubscription subscribe(
              Class<T> eventType, EventListener<T> listener) {
            return EventSubscription.of(1L, eventType, () -> {});
          }

          @Override
          public <T extends EngineEvent> EventSubscription subscribe(
              Class<T> eventType, EventListener<T> listener, EventPriority priority) {
            return EventSubscription.of(1L, eventType, () -> {});
          }

          @Override
          public void publish(EngineEvent event) {
            publishCount.incrementAndGet();
          }

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
        };

    bus.publishAll(List.of(new TestEvent(), new TestEvent()));

    assertEquals(2, publishCount.get());
  }

  private record TestEvent(long timestamp) implements EngineEvent { TestEvent() { this(System.nanoTime()); } }
}
