package org.dynamisengine.event;

import org.dynamisengine.core.event.EngineEvent;
import org.dynamisengine.core.event.EventListener;
import org.dynamisengine.core.event.EventPriority;

record ListenerEntry<T extends EngineEvent>(
    long subscriptionId, EventListener<T> listener, EventPriority priority) {}
