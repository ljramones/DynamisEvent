package org.dynamisengine.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.dynamisengine.core.event.EngineEvent;
import org.dynamisengine.core.event.EventListener;
import org.dynamisengine.core.event.EventPriority;
import org.dynamisengine.core.event.EventSubscription;

/**
 * Event bus that dispatches events on the caller thread.
 */
public final class SynchronousEventBus implements EventBus {
  private final ConcurrentHashMap<Class<? extends EngineEvent>, CopyOnWriteArrayList<ListenerEntry<?>>>
      listeners = new ConcurrentHashMap<>();
  private final AtomicLong subscriptionIdCounter = new AtomicLong(1L);
  private final AtomicLong totalPublished = new AtomicLong(0L);
  private final AtomicLong totalDelivered = new AtomicLong(0L);
  private final AtomicLong totalDeadLetters = new AtomicLong(0L);
  private final AtomicLong totalDispatchNanos = new AtomicLong(0L);
  private final AtomicLong dispatchCount = new AtomicLong(0L);
  private final DeadLetterHandler deadLetterHandler;

  public SynchronousEventBus() {
    this(event -> {});
  }

  public SynchronousEventBus(DeadLetterHandler deadLetterHandler) {
    this.deadLetterHandler = Objects.requireNonNull(deadLetterHandler, "deadLetterHandler");
  }

  @Override
  public <T extends EngineEvent> EventSubscription subscribe(
      Class<T> eventType, EventListener<T> listener) {
    return subscribe(eventType, listener, EventPriority.NORMAL);
  }

  @Override
  public <T extends EngineEvent> EventSubscription subscribe(
      Class<T> eventType, EventListener<T> listener, EventPriority priority) {
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(listener, "listener");
    Objects.requireNonNull(priority, "priority");

    long subscriptionId = subscriptionIdCounter.getAndIncrement();
    ListenerEntry<T> entry = new ListenerEntry<>(subscriptionId, listener, priority);
    listeners.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>()).add(entry);

    return EventSubscription.of(
        subscriptionId, eventType, () -> removeListener(eventType, subscriptionId));
  }

  private void removeListener(Class<? extends EngineEvent> eventType, long subscriptionId) {
    CopyOnWriteArrayList<ListenerEntry<?>> list = listeners.get(eventType);
    if (list == null) {
      return;
    }

    list.removeIf(entry -> entry.subscriptionId() == subscriptionId);
    if (list.isEmpty()) {
      listeners.remove(eventType, list);
    }
  }

  /**
   * Publishes an event synchronously to listeners registered for the exact event type.
   *
   * <p>Listener exceptions are not swallowed and will propagate to the caller.
   */
  @Override
  public void publish(EngineEvent event) {
    Objects.requireNonNull(event, "event");
    totalPublished.incrementAndGet();

    CopyOnWriteArrayList<ListenerEntry<?>> liveList = listeners.get(event.getClass());
    if (liveList == null || liveList.isEmpty()) {
      totalDeadLetters.incrementAndGet();
      deadLetterHandler.handle(event);
      return;
    }

    ArrayList<ListenerEntry<?>> sorted = new ArrayList<>(liveList);
    sorted.sort(Comparator.comparingInt(entry -> entry.priority().ordinal()));

    long start = System.nanoTime();
    for (ListenerEntry<?> entry : sorted) {
      dispatchToListener(entry, event);
      totalDelivered.incrementAndGet();
    }
    totalDispatchNanos.addAndGet(System.nanoTime() - start);
    dispatchCount.incrementAndGet();
  }

  @SuppressWarnings("unchecked")
  private void dispatchToListener(ListenerEntry<?> entry, EngineEvent event) {
    // Safe by construction: listener and eventType are coupled at registration time.
    EventListener<EngineEvent> listener = (EventListener<EngineEvent>) entry.listener();
    listener.onEvent(event);
  }

  @Override
  public void unsubscribe(EventSubscription subscription) {
    Objects.requireNonNull(subscription, "subscription");
    subscription.cancel();
  }

  @Override
  public int subscriberCount(Class<? extends EngineEvent> eventType) {
    Objects.requireNonNull(eventType, "eventType");
    CopyOnWriteArrayList<ListenerEntry<?>> list = listeners.get(eventType);
    return list == null ? 0 : list.size();
  }

  @Override
  public BusMetrics metrics() {
    long count = dispatchCount.get();
    double averageDispatchNanos =
        count == 0L ? 0.0 : (double) totalDispatchNanos.get() / (double) count;
    int activeSubscriptions = listeners.values().stream().mapToInt(CopyOnWriteArrayList::size).sum();
    return BusMetrics.of(
        totalPublished.get(),
        totalDelivered.get(),
        totalDeadLetters.get(),
        averageDispatchNanos,
        activeSubscriptions);
  }
}
