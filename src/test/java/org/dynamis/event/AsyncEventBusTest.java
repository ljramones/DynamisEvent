package org.dynamis.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.dynamis.core.event.EngineEvent;
import org.dynamis.core.event.EventSubscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AsyncEventBusTest {
  record TestEvent(String message) implements EngineEvent {}

  private AsyncEventBus bus;

  @AfterEach
  void tearDown() {
    if (bus != null) {
      bus.shutdown();
    }
  }

  @Test
  void publishReturnsBeforeListenerInvocation() throws InterruptedException {
    bus = new AsyncEventBus(2);
    CountDownLatch listenerCanFinish = new CountDownLatch(1);
    CountDownLatch listenerDone = new CountDownLatch(1);
    bus.subscribe(
        TestEvent.class,
        event -> {
          try {
            listenerCanFinish.await(2, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          listenerDone.countDown();
        });

    bus.publish(new TestEvent("async"));
    assertEquals(1L, listenerDone.getCount());

    listenerCanFinish.countDown();
    assertTrue(listenerDone.await(2, TimeUnit.SECONDS));
  }

  @Test
  void allPublishedEventsAreEventuallyDelivered() throws InterruptedException {
    bus = new AsyncEventBus(4);
    CountDownLatch delivered = new CountDownLatch(100);
    bus.subscribe(TestEvent.class, event -> delivered.countDown());

    for (int i = 0; i < 100; i++) {
      bus.publish(new TestEvent("event-" + i));
    }

    assertTrue(delivered.await(5, TimeUnit.SECONDS));
    assertEquals(0L, delivered.getCount());
  }

  @Test
  void publishAsyncBehavesLikePublish() throws InterruptedException {
    bus = new AsyncEventBus(2);
    CountDownLatch listenerCanFinish = new CountDownLatch(1);
    CountDownLatch listenerDone = new CountDownLatch(1);
    bus.subscribe(
        TestEvent.class,
        event -> {
          try {
            listenerCanFinish.await(2, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          listenerDone.countDown();
        });

    bus.publishAsync(new TestEvent("async"));
    assertEquals(1L, listenerDone.getCount());

    listenerCanFinish.countDown();
    assertTrue(listenerDone.await(2, TimeUnit.SECONDS));
  }

  @Test
  void listenerRegisteredBeforePublishReceivesEvent() throws InterruptedException {
    bus = new AsyncEventBus(2);
    CountDownLatch delivered = new CountDownLatch(1);
    bus.subscribe(TestEvent.class, event -> delivered.countDown());

    bus.publish(new TestEvent("hello"));

    assertTrue(delivered.await(2, TimeUnit.SECONDS));
  }

  @Test
  void unsubscribePreventsFurtherEventDelivery() throws InterruptedException {
    bus = new AsyncEventBus(2);
    CountDownLatch delivered = new CountDownLatch(1);
    EventSubscription subscription = bus.subscribe(TestEvent.class, event -> delivered.countDown());

    subscription.cancel();
    bus.publish(new TestEvent("should-not-deliver"));

    assertFalse(delivered.await(100, TimeUnit.MILLISECONDS));
  }

  @Test
  void subscriberCountReflectsSubscribeAndUnsubscribe() {
    bus = new AsyncEventBus(2);
    assertEquals(0, bus.subscriberCount(TestEvent.class));

    EventSubscription subscription = bus.subscribe(TestEvent.class, event -> {});
    assertEquals(1, bus.subscriberCount(TestEvent.class));

    bus.unsubscribe(subscription);
    assertEquals(0, bus.subscriberCount(TestEvent.class));
  }

  @Test
  void publishAfterShutdownThrowsIllegalStateException() {
    bus = new AsyncEventBus(2);
    bus.shutdown();

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> bus.publish(new TestEvent("fail")));
    assertEquals("EventBus has been shut down", exception.getMessage());
  }

  @Test
  void shutdownIsIdempotent() {
    bus = new AsyncEventBus(2);

    assertDoesNotThrow(bus::shutdown);
    assertDoesNotThrow(bus::shutdown);
  }

  @Test
  void constructorRejectsZeroThreadPoolSize() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new AsyncEventBus(0));
    assertEquals("Thread pool size must be at least 1, got: 0", exception.getMessage());
  }

  @Test
  void constructorRejectsNullDeadLetterHandler() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new AsyncEventBus(1, null));
    assertEquals("deadLetterHandler must not be null", exception.getMessage());
  }

  @Test
  void metricsReturnsNonNullSnapshot() {
    bus = new AsyncEventBus(2);
    AtomicInteger delivered = new AtomicInteger(0);
    CountDownLatch done = new CountDownLatch(1);
    bus.subscribe(
        TestEvent.class,
        event -> {
          delivered.incrementAndGet();
          done.countDown();
        });

    bus.publish(new TestEvent("metrics"));
    assertTrue(await(done, 2, TimeUnit.SECONDS));
    assertEquals(1, delivered.get());
    assertNotNull(bus.metrics());
  }

  private static boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
    try {
      return latch.await(timeout, unit);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
