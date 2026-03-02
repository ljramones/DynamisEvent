package org.dynamis.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.dynamis.core.event.EngineEvent;
import org.dynamis.core.event.EventPriority;
import org.dynamis.core.event.EventSubscription;
import org.junit.jupiter.api.Test;

class SynchronousEventBusTest {
  record TestEvent(String message) implements EngineEvent {}

  record OtherEvent(int value) implements EngineEvent {}

  record HighPriorityEvent() implements EngineEvent {
    @Override
    public EventPriority priority() {
      return EventPriority.CRITICAL;
    }
  }

  @Test
  void subscribeAndPublishDeliversEvent() {
    SynchronousEventBus bus = new SynchronousEventBus();
    AtomicInteger received = new AtomicInteger(0);
    List<String> messages = new ArrayList<>();
    bus.subscribe(
        TestEvent.class,
        event -> {
          messages.add(event.message());
          received.incrementAndGet();
        });

    bus.publish(new TestEvent("hello"));

    assertEquals(1, received.get());
    assertEquals(List.of("hello"), messages);
  }

  @Test
  void multipleListenersForSameEventTypeAllReceiveEvent() {
    SynchronousEventBus bus = new SynchronousEventBus();
    AtomicInteger received = new AtomicInteger(0);
    bus.subscribe(TestEvent.class, event -> received.incrementAndGet());
    bus.subscribe(TestEvent.class, event -> received.incrementAndGet());

    bus.publish(new TestEvent("event"));

    assertEquals(2, received.get());
  }

  @Test
  void listenerForDifferentTypeDoesNotReceiveUnrelatedEvent() {
    SynchronousEventBus bus = new SynchronousEventBus();
    AtomicInteger received = new AtomicInteger(0);
    bus.subscribe(OtherEvent.class, event -> received.incrementAndGet());

    bus.publish(new TestEvent("event"));

    assertEquals(0, received.get());
  }

  @Test
  void criticalPriorityListenerIsInvokedBeforeNormalPriorityListener() {
    SynchronousEventBus bus = new SynchronousEventBus();
    List<String> order = new ArrayList<>();
    bus.subscribe(TestEvent.class, event -> order.add("normal"), EventPriority.NORMAL);
    bus.subscribe(TestEvent.class, event -> order.add("critical"), EventPriority.CRITICAL);

    bus.publish(new TestEvent("ordered"));

    assertEquals(List.of("critical", "normal"), order);
  }

  @Test
  void unsubscribeViaCancelStopsFurtherDelivery() {
    SynchronousEventBus bus = new SynchronousEventBus();
    AtomicInteger received = new AtomicInteger(0);
    EventSubscription subscription = bus.subscribe(TestEvent.class, event -> received.incrementAndGet());
    bus.publish(new TestEvent("before"));

    subscription.cancel();
    bus.publish(new TestEvent("after"));

    assertEquals(1, received.get());
  }

  @Test
  void unsubscribeViaBusStopsFurtherDelivery() {
    SynchronousEventBus bus = new SynchronousEventBus();
    AtomicInteger received = new AtomicInteger(0);
    EventSubscription subscription = bus.subscribe(TestEvent.class, event -> received.incrementAndGet());
    bus.publish(new TestEvent("before"));

    bus.unsubscribe(subscription);
    bus.publish(new TestEvent("after"));

    assertEquals(1, received.get());
  }

  @Test
  void deadLetterHandlerIsInvokedForUnhandledEvent() {
    AtomicInteger deadLetters = new AtomicInteger(0);
    SynchronousEventBus bus = new SynchronousEventBus(event -> deadLetters.incrementAndGet());

    bus.publish(new OtherEvent(7));

    assertEquals(1, deadLetters.get());
  }

  @Test
  void defaultDeadLetterHandlerDoesNotThrow() {
    SynchronousEventBus bus = new SynchronousEventBus();

    assertDoesNotThrow(() -> bus.publish(new OtherEvent(5)));
  }

  @Test
  void metricsReflectPublishedDeliveredAndDeadLetterCounts() {
    SynchronousEventBus bus = new SynchronousEventBus();
    bus.subscribe(TestEvent.class, event -> {});
    bus.publish(new TestEvent("one"));
    bus.publish(new TestEvent("two"));
    bus.publish(new OtherEvent(1));

    BusMetrics metrics = bus.metrics();

    assertEquals(3L, metrics.totalPublished());
    assertEquals(2L, metrics.totalDelivered());
    assertEquals(1L, metrics.totalDeadLetters());
  }

  @Test
  void subscriberCountTracksSubscribeAndUnsubscribe() {
    SynchronousEventBus bus = new SynchronousEventBus();
    assertEquals(0, bus.subscriberCount(TestEvent.class));

    EventSubscription first = bus.subscribe(TestEvent.class, event -> {});
    EventSubscription second = bus.subscribe(TestEvent.class, event -> {});
    assertEquals(2, bus.subscriberCount(TestEvent.class));

    first.cancel();
    assertEquals(1, bus.subscriberCount(TestEvent.class));

    bus.unsubscribe(second);
    assertEquals(0, bus.subscriberCount(TestEvent.class));
  }

  @Test
  void publishAllDeliversAllEventsInCollection() {
    SynchronousEventBus bus = new SynchronousEventBus();
    AtomicInteger received = new AtomicInteger(0);
    bus.subscribe(TestEvent.class, event -> received.incrementAndGet());

    bus.publishAll(List.of(new TestEvent("a"), new TestEvent("b"), new TestEvent("c")));

    assertEquals(3, received.get());
  }

  @Test
  void concurrentPublishDeliversAllEvents() throws InterruptedException {
    SynchronousEventBus bus = new SynchronousEventBus();
    AtomicInteger received = new AtomicInteger(0);
    bus.subscribe(TestEvent.class, event -> received.incrementAndGet());

    int threadCount = 10;
    int perThreadPublishes = 100;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      Thread worker =
          new Thread(
              () -> {
                try {
                  startLatch.await();
                  for (int j = 0; j < perThreadPublishes; j++) {
                    bus.publish(new TestEvent("event"));
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  doneLatch.countDown();
                }
              });
      worker.start();
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(5, TimeUnit.SECONDS);

    assertTrue(completed);
    assertEquals(1000, received.get());
  }

  @Test
  void concurrentSubscribeAndUnsubscribeDoesNotThrow() throws InterruptedException {
    SynchronousEventBus bus = new SynchronousEventBus();
    ConcurrentLinkedQueue<EventSubscription> subscriptions = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(10);

    for (int i = 0; i < 5; i++) {
      Thread subscriber =
          new Thread(
              () -> {
                try {
                  startLatch.await();
                  for (int j = 0; j < 200; j++) {
                    subscriptions.add(bus.subscribe(TestEvent.class, event -> {}));
                  }
                } catch (Throwable t) {
                  failures.add(t);
                } finally {
                  doneLatch.countDown();
                }
              });
      subscriber.start();
    }

    for (int i = 0; i < 5; i++) {
      Thread unsubscriber =
          new Thread(
              () -> {
                try {
                  startLatch.await();
                  for (int j = 0; j < 200; j++) {
                    EventSubscription subscription = subscriptions.poll();
                    if (subscription != null) {
                      bus.unsubscribe(subscription);
                    }
                  }
                } catch (Throwable t) {
                  failures.add(t);
                } finally {
                  doneLatch.countDown();
                }
              });
      unsubscriber.start();
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(5, TimeUnit.SECONDS);

    assertTrue(completed);
    assertEquals(Collections.emptyList(), new ArrayList<>(failures));
  }
}
