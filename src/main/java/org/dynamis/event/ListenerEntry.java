package org.dynamis.event;

import org.dynamis.core.event.EngineEvent;
import org.dynamis.core.event.EventListener;
import org.dynamis.core.event.EventPriority;

record ListenerEntry<T extends EngineEvent>(
    long subscriptionId, EventListener<T> listener, EventPriority priority) {}
