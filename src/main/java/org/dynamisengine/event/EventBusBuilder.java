package org.dynamisengine.event;

/**
 * Builder for creating configured EventBus instances.
 */
public final class EventBusBuilder {
  private Boolean synchronous;
  private Integer threadPoolSize;
  private DeadLetterHandler deadLetterHandler;
  private boolean built;

  private EventBusBuilder() {}

  public static EventBusBuilder create() {
    return new EventBusBuilder();
  }

  public EventBusBuilder synchronous() {
    if (Boolean.FALSE.equals(synchronous)) {
      throw new IllegalStateException("Bus mode already set to async");
    }
    synchronous = Boolean.TRUE;
    return this;
  }

  public EventBusBuilder async(int threadPoolSize) {
    if (threadPoolSize < 1) {
      throw new IllegalArgumentException(
          "Thread pool size must be at least 1, got: " + threadPoolSize);
    }
    if (Boolean.TRUE.equals(synchronous)) {
      throw new IllegalStateException("Bus mode already set to synchronous");
    }

    synchronous = Boolean.FALSE;
    this.threadPoolSize = threadPoolSize;
    return this;
  }

  public EventBusBuilder onDeadLetter(DeadLetterHandler handler) {
    if (handler == null) {
      throw new IllegalArgumentException("Dead letter handler must not be null");
    }
    deadLetterHandler = handler;
    return this;
  }

  public EventBus build() {
    if (built) {
      throw new IllegalStateException("EventBusBuilder has already been built");
    }
    built = true;

    DeadLetterHandler handler = deadLetterHandler != null ? deadLetterHandler : event -> {};
    if (synchronous == null || Boolean.TRUE.equals(synchronous)) {
      return new SynchronousEventBus(handler);
    }
    if (threadPoolSize == null) {
      throw new IllegalStateException("Async mode requires a thread pool size");
    }
    return new AsyncEventBus(threadPoolSize, handler);
  }
}
