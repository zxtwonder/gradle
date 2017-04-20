/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.logging.console;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.internal.logging.events.BatchOutputEventListener;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.LoggingType;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GroupedBuildOperationRenderer extends BatchOutputEventListener {

    static final int SCHEDULER_INITIAL_DELAY_MS = 100;
    static final int SCHEDULER_CHECK_PERIOD_MS = 5000;
    private final BatchOutputEventListener listener;
    private final ScheduledExecutorService executor;
    private final Object lock = new Object();
    private final Map<OperationIdentifier, List<OutputEvent>> groupedTaskBuildOperations = new LinkedHashMap<OperationIdentifier, List<OutputEvent>>();
    private final RenderState renderState = new RenderState();

    public GroupedBuildOperationRenderer(BatchOutputEventListener listener) {
        this(listener, true);
    }

    GroupedBuildOperationRenderer(BatchOutputEventListener listener, boolean enableScheduler) {
        this.listener = listener;
        executor = Executors.newSingleThreadScheduledExecutor();

        if (enableScheduler) {
            scheduleTimedEventForwarding();
        }
    }

    private void scheduleTimedEventForwarding() {
        executor.scheduleAtFixedRate(new ForwardingOutputEventRunnable(), SCHEDULER_INITIAL_DELAY_MS, SCHEDULER_CHECK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOutput(OutputEvent event) {
        synchronized (lock) {
            if (event instanceof ProgressStartEvent) {
                ProgressStartEvent startEvent = (ProgressStartEvent) event;
                OperationIdentifier operationId = startEvent.getOperationId();

                if (isTaskExecutionProgressStartEvent(startEvent)) {
                    groupedTaskBuildOperations.put(operationId, Lists.newArrayList(event));
                } else {
                    forwardEvent(event);
                }
            } else if (event instanceof ProgressCompleteEvent) {
                ProgressCompleteEvent progressCompleteEvent = (ProgressCompleteEvent) event;
                OperationIdentifier operationId = progressCompleteEvent.getOperationId();

                if (groupedTaskBuildOperations.containsKey(operationId)) {
                    List<OutputEvent> outputEvents = groupedTaskBuildOperations.get(operationId);

                    if (renderState.isCurrentlyRendered(operationId)) {
                        List<OutputEvent> outputEventsWithoutHeader = outputEvents.subList(1, outputEvents.size());
                        forwardBatchedEvents(outputEventsWithoutHeader);
                    } else {
                        forwardBatchedEvents(outputEvents);
                    }

                    forwardEvent(event);
                    groupedTaskBuildOperations.remove(operationId);
                    renderState.clearCurrentlyRendered();
                } else {
                    forwardEvent(event);
                }
            } else if (event instanceof RenderableOutputEvent) {
                RenderableOutputEvent renderableOutputEvent = (RenderableOutputEvent) event;
                OperationIdentifier operationId = renderableOutputEvent.getOperationId();

                if (groupedTaskBuildOperations.containsKey(operationId)) {
                    List<OutputEvent> outputEvents = groupedTaskBuildOperations.get(operationId);
                    outputEvents.add(event);
                } else {
                    forwardEvent(event);
                }
            } else if (event instanceof EndOutputEvent) {
                forwardEvent(event);
                executor.shutdown();
                groupedTaskBuildOperations.clear();
                renderState.clearCurrentlyRendered();
            }
        }
    }

    private boolean isTaskExecutionProgressStartEvent(ProgressStartEvent event) {
        return LoggingType.TASK_EXECUTION == event.getLoggingType();
    }

    private void forwardEvent(OutputEvent event) {
        listener.onOutput(event);
    }

    private void forwardBatchedEvents(Iterable<OutputEvent> events) {
        listener.onOutput(events);
    }

    private class ForwardingOutputEventRunnable implements Runnable {

        @Override
        public void run() {
            synchronized (lock) {
                for (Map.Entry<OperationIdentifier, List<OutputEvent>> groupedEvents : groupedTaskBuildOperations.entrySet()) {
                    forwardOutputEvents(groupedEvents);
                }

                setRenderState();
            }
        }

        private void forwardOutputEvents(Map.Entry<OperationIdentifier, List<OutputEvent>> groupedEvents) {
            List<OutputEvent> originalOutputEvents = groupedEvents.getValue();
            List<OutputEvent> outputEventsWithoutHeader = getOutputEventsWithoutHeader(originalOutputEvents);
            List<OutputEvent> forwardedOutputEvents = renderState.isCurrentlyRendered(groupedEvents.getKey()) ? outputEventsWithoutHeader : originalOutputEvents;
            forwardBatchedEvents(forwardedOutputEvents);
            outputEventsWithoutHeader.clear();
        }

        private List<OutputEvent> getOutputEventsWithoutHeader(List<OutputEvent> outputEvents) {
            return outputEvents.subList(1, outputEvents.size());
        }

        private void setRenderState() {
            if (!groupedTaskBuildOperations.isEmpty()) {
                renderState.setCurrentlyRendered(Iterables.getLast(groupedTaskBuildOperations.keySet()));
            }
        }
    }

    private static class RenderState {

        private OperationIdentifier currentlyRendered;

        public OperationIdentifier getCurrentlyRendered() {
            return currentlyRendered;
        }

        public void setCurrentlyRendered(OperationIdentifier operationId) {
            currentlyRendered = operationId;
        }

        public boolean isCurrentlyRendered(OperationIdentifier operationId) {
            return currentlyRendered != null && currentlyRendered.equals(operationId);
        }

        public void clearCurrentlyRendered() {
            currentlyRendered = null;
        }
    }

    Map<OperationIdentifier, List<OutputEvent>> getGroupedTaskBuildOperations() {
        return groupedTaskBuildOperations;
    }

    RenderState getRenderState() {
        return renderState;
    }
}
