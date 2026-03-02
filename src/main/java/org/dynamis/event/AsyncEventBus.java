package org.dynamis.event;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dynamis.core.event.EngineEvent;
import org.dynamis.core.event.EventListener;
import org.dynamis.core.event.EventPriority;
import org.dynamis.core.event.EventSubscription;
import org.dynamis.core.logging.DynamisLogger;

/**
 * Event bus that dispatches events asynchronously on a fixed thread pool.
 */
public final class AsyncEventBus implements EventBus {
  private static final DynamisLogger LOGGER = DynamisLogger.get(AsyncEventBus.class);

  private final SynchronousEventBus delegate;
  private final ExecutorService executor;
  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public AsyncEventBus(int threadPoolSize) {
    this(threadPoolSize, event -> {});
  }

  public AsyncEventBus(int threadPoolSize, DeadLetterHandler deadLetterHandler) {
    if (threadPoolSize < 1) {
      throw new IllegalArgumentException(
          "Thread pool size must be at least 1, got: " + threadPoolSize);
    }
    if (deadLetterHandler == null) {
      throw new IllegalArgumentException("deadLetterHandler must not be null");
    }

    this.delegate = new SynchronousEventBus(deadLetterHandler);
    this.executor = Executors.newFixedThreadPool(threadPoolSize);
  }

  @Override
  public <T extends EngineEvent> EventSubscription subscribe(
      Class<T> eventType, EventListener<T> listener) {
    return delegate.subscribe(eventType, listener);
  }

  @Override
  public <T extends EngineEvent> EventSubscription subscribe(
      Class<T> eventType, EventListener<T> listener, EventPriority priority) {
    return delegate.subscribe(eventType, listener, priority);
  }

  /**
   * Publishes asynchronously by submitting dispatch to the executor and returning immediately.
   *
   * <p>Listener exceptions occur on worker threads and do not propagate to the publish caller.
   */
  @Override
  public void publish(EngineEvent event) {
    Objects.requireNonNull(event, "event");
    if (shutdown.get()) {
      throw new IllegalStateException("EventBus has been shut down");
    }
    Future<?> dispatch = executor.submit(() -> delegate.publish(event));
    if (dispatch == null) {
      throw new IllegalStateException("Failed to submit event dispatch task");
    }
  }

  @Override
  public void publishAsync(EngineEvent event) {
    publish(event);
  }

  @Override
  public void unsubscribe(EventSubscription subscription) {
    delegate.unsubscribe(subscription);
  }

  @Override
  public int subscriberCount(Class<? extends EngineEvent> eventType) {
    return delegate.subscriberCount(eventType);
  }

  @Override
  public BusMetrics metrics() {
    return delegate.metrics();
  }

  @Override
  public void shutdown() {
    if (!shutdown.compareAndSet(false, true)) {
      return;
    }

    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        LOGGER.warn("AsyncEventBus executor did not terminate in 5 seconds; forced shutdown.");
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      LOGGER.warn("Interrupted while awaiting AsyncEventBus executor termination.", e);
      Thread.currentThread().interrupt();
    }
  }
}
