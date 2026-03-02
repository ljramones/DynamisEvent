package org.dynamis.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamis.core.event.EngineEvent;
import org.dynamis.core.event.EventPriority;
import org.dynamis.core.event.EventSubscription;
import org.junit.jupiter.api.Test;

class NoOpEventBusTest {
  record TestEvent(String message) implements EngineEvent {}

  @Test
  void subscribeReturnsNonNullSubscription() {
    NoOpEventBus bus = new NoOpEventBus();

    EventSubscription subscription = bus.subscribe(TestEvent.class, event -> {});

    assertNotNull(subscription);
  }

  @Test
  void subscribeWithPriorityReturnsNonNullSubscription() {
    NoOpEventBus bus = new NoOpEventBus();

    EventSubscription subscription =
        bus.subscribe(TestEvent.class, event -> {}, EventPriority.NORMAL);

    assertNotNull(subscription);
  }

  @Test
  void subscriptionIdIsPositive() {
    NoOpEventBus bus = new NoOpEventBus();

    EventSubscription subscription = bus.subscribe(TestEvent.class, event -> {});

    assertTrue(subscription.subscriptionId() > 0L);
  }

  @Test
  void eachSubscriptionUsesDistinctId() {
    NoOpEventBus bus = new NoOpEventBus();

    EventSubscription first = bus.subscribe(TestEvent.class, event -> {});
    EventSubscription second = bus.subscribe(TestEvent.class, event -> {});

    assertNotEquals(first.subscriptionId(), second.subscriptionId());
  }

  @Test
  void cancelOnReturnedSubscriptionDoesNotThrow() {
    NoOpEventBus bus = new NoOpEventBus();
    EventSubscription subscription = bus.subscribe(TestEvent.class, event -> {});

    assertDoesNotThrow(subscription::cancel);
  }

  @Test
  void publishDoesNotThrow() {
    NoOpEventBus bus = new NoOpEventBus();

    assertDoesNotThrow(() -> bus.publish(new TestEvent("hello")));
  }

  @Test
  void publishAllDoesNotThrow() {
    NoOpEventBus bus = new NoOpEventBus();

    assertDoesNotThrow(
        () ->
            bus.publishAll(
                List.of(new TestEvent("one"), new TestEvent("two"), new TestEvent("three"))));
  }

  @Test
  void publishAsyncDoesNotThrow() {
    NoOpEventBus bus = new NoOpEventBus();

    assertDoesNotThrow(() -> bus.publishAsync(new TestEvent("async")));
  }

  @Test
  void subscriberCountAlwaysReturnsZero() {
    NoOpEventBus bus = new NoOpEventBus();

    assertEquals(0, bus.subscriberCount(TestEvent.class));
    assertEquals(0, bus.subscriberCount(AnotherTestEvent.class));
  }

  @Test
  void metricsReturnsEmptySnapshot() {
    NoOpEventBus bus = new NoOpEventBus();

    assertEquals(BusMetrics.EMPTY, bus.metrics());
  }

  @Test
  void shutdownDoesNotThrow() {
    NoOpEventBus bus = new NoOpEventBus();

    assertDoesNotThrow(bus::shutdown);
  }

  private record AnotherTestEvent() implements EngineEvent {}
}
