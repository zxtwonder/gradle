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

import com.google.common.collect.Lists;
import org.gradle.internal.logging.events.BatchOutputEventListener;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.LogEventType;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GroupedBuildOperationRenderer extends BatchOutputEventListener {

    private static final int SCHEDULER_INITIAL_DELAY_MS = 100;
    private static final int SCHEDULER_CHECK_PERIOD_MS = 5000;
    private final BatchOutputEventListener listener;
    private final ScheduledExecutorService executor;
    private final Object lock = new Object();
    private final Map<OperationIdentifier, List<OutputEvent>> groupedTaskBuildOperations = new HashMap<OperationIdentifier, List<OutputEvent>>();
    private final List<OperationIdentifier> currentlyRendered = new ArrayList<OperationIdentifier>();

    public GroupedBuildOperationRenderer(BatchOutputEventListener listener) {
        this.listener = listener;
        executor = Executors.newSingleThreadScheduledExecutor();
        scheduleTimedEventForwarding();
    }

    private void scheduleTimedEventForwarding() {
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                synchronized (lock) {
                    for (Map.Entry<OperationIdentifier, List<OutputEvent>> groupedEvents : groupedTaskBuildOperations.entrySet()) {
                        List<OutputEvent> originalOutputEvents = groupedEvents.getValue();

                        if (currentlyRendered.contains(groupedEvents.getKey())) {
                            List<OutputEvent> outputEventsWithoutHeader = originalOutputEvents.subList(1, originalOutputEvents.size());
                            forwardBatchedEvents(outputEventsWithoutHeader);
                        } else {
                            forwardBatchedEvents(originalOutputEvents);
                        }

                        for (int i = 1; i <= groupedEvents.getValue().size(); i++) {
                            originalOutputEvents.remove(i);
                        }

                        currentlyRendered.add(groupedEvents.getKey());
                    }
                }
            }
        }, SCHEDULER_INITIAL_DELAY_MS, SCHEDULER_CHECK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    Map<OperationIdentifier, List<OutputEvent>> getGroupedTaskBuildOperations() {
        return groupedTaskBuildOperations;
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

                    if (currentlyRendered.contains(operationId)) {
                        List<OutputEvent> outputEventsWithoutHeader = outputEvents.subList(1, outputEvents.size());
                        forwardBatchedEvents(outputEventsWithoutHeader);
                    } else {
                        forwardBatchedEvents(outputEvents);
                    }

                    forwardEvent(event);
                    groupedTaskBuildOperations.remove(operationId);
                    currentlyRendered.remove(operationId);
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
                currentlyRendered.clear();
            }
        }
    }

    private boolean isTaskExecutionProgressStartEvent(ProgressStartEvent event) {
        return event.getLogEventType() == LogEventType.TASK_EXECUTION;
    }

    private void forwardEvent(OutputEvent event) {
        listener.onOutput(event);
    }

    private void forwardBatchedEvents(Iterable<OutputEvent> events) {
        listener.onOutput(events);
    }
}
