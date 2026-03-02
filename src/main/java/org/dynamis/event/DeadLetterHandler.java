package org.dynamis.event;

import org.dynamis.core.event.EngineEvent;

/**
 * Handles events that were published without any matching subscribers.
 */
@FunctionalInterface
public interface DeadLetterHandler {
  void handle(EngineEvent event);
}
