package org.dynamis.event;

/**
 * Immutable snapshot of event bus runtime metrics.
 *
 * @param totalPublished total events published
 * @param totalDelivered total listener deliveries completed
 * @param totalDeadLetters total events with no listeners
 * @param averageDispatchNanos average dispatch duration in nanoseconds
 * @param activeSubscriptions number of active subscriptions
 */
public record BusMetrics(
    long totalPublished,
    long totalDelivered,
    long totalDeadLetters,
    double averageDispatchNanos,
    int activeSubscriptions) {
  public static final BusMetrics EMPTY = new BusMetrics(0L, 0L, 0L, 0.0, 0);

  public static BusMetrics of(
      long totalPublished,
      long totalDelivered,
      long totalDeadLetters,
      double averageDispatchNanos,
      int activeSubscriptions) {
    return new BusMetrics(
        totalPublished,
        totalDelivered,
        totalDeadLetters,
        averageDispatchNanos,
        activeSubscriptions);
  }
}
