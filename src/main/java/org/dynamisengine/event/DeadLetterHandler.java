package org.dynamisengine.event;

import org.dynamisengine.core.event.EngineEvent;

/**
 * Handles events that were published without any matching subscribers.
 */
@FunctionalInterface
public interface DeadLetterHandler {
  void handle(EngineEvent event);
}
