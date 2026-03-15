package org.dynamisengine.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.dynamisengine.core.event.EngineEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EventBusBuilderTest {
  record TestEvent(String message, long timestamp) implements EngineEvent { TestEvent(String message) { this(message, System.nanoTime()); } }

  private final List<EventBus> builtBuses = new ArrayList<>();

  @AfterEach
  void tearDown() {
    for (EventBus bus : builtBuses) {
      bus.shutdown();
    }
    builtBuses.clear();
  }

  @Test
  void buildDefaultsToSynchronousBus() {
    EventBus bus = track(EventBusBuilder.create().build());

    assertInstanceOf(SynchronousEventBus.class, bus);
  }

  @Test
  void synchronousModeBuildsSynchronousBus() {
    EventBus bus = track(EventBusBuilder.create().synchronous().build());

    assertInstanceOf(SynchronousEventBus.class, bus);
  }

  @Test
  void asyncModeBuildsAsyncBus() {
    EventBus bus = track(EventBusBuilder.create().async(4).build());

    assertInstanceOf(AsyncEventBus.class, bus);
  }

  @Test
  void switchingFromSynchronousToAsyncThrows() {
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> EventBusBuilder.create().synchronous().async(4));

    assertEquals("Bus mode already set to synchronous", exception.getMessage());
  }

  @Test
  void switchingFromAsyncToSynchronousThrows() {
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> EventBusBuilder.create().async(4).synchronous());

    assertEquals("Bus mode already set to async", exception.getMessage());
  }

  @Test
  void asyncRejectsZeroThreadPoolSize() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> EventBusBuilder.create().async(0));

    assertEquals("Thread pool size must be at least 1, got: 0", exception.getMessage());
  }

  @Test
  void asyncRejectsNegativeThreadPoolSize() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> EventBusBuilder.create().async(-1));

    assertEquals("Thread pool size must be at least 1, got: -1", exception.getMessage());
  }

  @Test
  void nullDeadLetterHandlerIsRejected() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> EventBusBuilder.create().onDeadLetter(null));

    assertEquals("Dead letter handler must not be null", exception.getMessage());
  }

  @Test
  void deadLetterHandlerIsWiredIntoBuiltSynchronousBus() {
    AtomicInteger deadLetters = new AtomicInteger(0);
    EventBus bus =
        track(EventBusBuilder.create().synchronous().onDeadLetter(event -> deadLetters.incrementAndGet()).build());

    bus.publish(new TestEvent("unhandled"));

    assertEquals(1, deadLetters.get());
  }

  @Test
  void builtSynchronousBusIsFunctional() {
    EventBus bus = track(EventBusBuilder.create().synchronous().build());
    AtomicInteger received = new AtomicInteger(0);
    bus.subscribe(TestEvent.class, event -> received.incrementAndGet());

    bus.publish(new TestEvent("hello"));

    assertEquals(1, received.get());
  }

  @Test
  void builtAsyncBusIsFunctional() throws InterruptedException {
    EventBus bus = track(EventBusBuilder.create().async(2).build());
    CountDownLatch delivered = new CountDownLatch(1);
    bus.subscribe(TestEvent.class, event -> delivered.countDown());

    bus.publish(new TestEvent("hello"));

    assertTrue(delivered.await(2, TimeUnit.SECONDS));
  }

  @Test
  void builderCannotBuildTwice() {
    EventBusBuilder builder = EventBusBuilder.create().synchronous();
    track(builder.build());

    IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
    assertEquals("EventBusBuilder has already been built", exception.getMessage());
  }

  private EventBus track(EventBus bus) {
    builtBuses.add(bus);
    return bus;
  }
}
