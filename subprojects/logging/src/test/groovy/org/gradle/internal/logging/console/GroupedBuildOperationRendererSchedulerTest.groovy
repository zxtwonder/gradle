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

package org.gradle.internal.logging.console

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.BatchOutputEventListener
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.LoggingType
import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import spock.lang.Specification

class GroupedBuildOperationRendererSchedulerTest extends Specification {

    public static final OperationIdentifier DEFAULT_OPERATION_ID = new OperationIdentifier(123)
    public static final String TASK_EXECUTION_CATEGORY = 'class org.gradle.internal.buildevents.TaskExecutionLogger'
    def listener = Mock(BatchOutputEventListener)
    def renderer = new GroupedBuildOperationRenderer(listener, true)

    def "scheduler forwards batched events of a single operation ID before progress end event is received"() {
        given:
        def progressStartEvent = createDefaultProgressStartEvent()
        def logEvent = createDefaultLogEvent()

        when:
        renderer.onOutput(progressStartEvent)
        renderer.onOutput(logEvent)

        then:
        0 * listener.onOutput(_)
        renderer.groupedTaskBuildOperations.size() == 1
        def batchedEvents = renderer.groupedTaskBuildOperations.get(DEFAULT_OPERATION_ID)
        batchedEvents.size() == 2
        batchedEvents.get(0) == progressStartEvent
        batchedEvents.get(1) == logEvent
        !renderer.renderState.currentlyRendered

        when:
        waitForSchedulerExecution()

        then:
        1 * listener.onOutput([progressStartEvent, logEvent])
        renderer.groupedTaskBuildOperations.size() == 1
        batchedEvents.size() == 1
        batchedEvents.get(0) == progressStartEvent
        renderer.renderState.isCurrentlyRendered(DEFAULT_OPERATION_ID)

        when:
        renderer.onOutput(logEvent)
        renderer.onOutput(logEvent)

        then:
        0 * listener.onOutput(_)
        renderer.groupedTaskBuildOperations.size() == 1
        batchedEvents.size() == 3
        batchedEvents.get(0) == progressStartEvent
        batchedEvents.get(1) == logEvent
        batchedEvents.get(2) == logEvent
        renderer.renderState.isCurrentlyRendered(DEFAULT_OPERATION_ID)

        when:
        waitForSchedulerExecution()

        then:
        1 * listener.onOutput([logEvent, logEvent])
        renderer.groupedTaskBuildOperations.size() == 1
        batchedEvents.size() == 1
        batchedEvents.get(0) == progressStartEvent
        renderer.renderState.isCurrentlyRendered(DEFAULT_OPERATION_ID)

        when:
        waitForSchedulerExecution()

        then:
        0 * listener.onOutput(_)
        renderer.groupedTaskBuildOperations.size() == 1
        batchedEvents.size() == 1
        batchedEvents.get(0) == progressStartEvent
        renderer.renderState.isCurrentlyRendered(DEFAULT_OPERATION_ID)
    }

    def "scheduler forwards batched events of a multiple operation IDs before progress end event is received"() {
        given:
        def progressStartEvent1 = createDefaultProgressStartEvent()
        def logEvent1a = createLogEvent( DEFAULT_OPERATION_ID, '1a')
        def logEvent1b = createLogEvent( DEFAULT_OPERATION_ID, '1b')
        def operationId = new OperationIdentifier(456)
        def progressStartEvent2 = createProgressStartEvent(operationId, LoggingType.TASK_EXECUTION, ':assemble')
        def logEvent2a = createLogEvent(operationId, '2a')
        def logEvent2b = createLogEvent(operationId, '2b')

        when:
        renderer.onOutput(progressStartEvent1)
        renderer.onOutput(logEvent1a)
        renderer.onOutput(progressStartEvent2)
        renderer.onOutput(logEvent1b)
        renderer.onOutput(logEvent2a)
        renderer.onOutput(logEvent2b)

        then:
        0 * listener.onOutput(_)
        renderer.groupedTaskBuildOperations.size() == 2
        def batchedEvents1 = renderer.groupedTaskBuildOperations.get(DEFAULT_OPERATION_ID)
        batchedEvents1.size() == 3
        batchedEvents1.get(0) == progressStartEvent1
        batchedEvents1.get(1) == logEvent1a
        batchedEvents1.get(2) == logEvent1b
        def batchedEvents2 = renderer.groupedTaskBuildOperations.get(operationId)
        batchedEvents2.size() == 3
        batchedEvents2.get(0) == progressStartEvent2
        batchedEvents2.get(1) == logEvent2a
        batchedEvents2.get(2) == logEvent2b
        !renderer.renderState.currentlyRendered

        when:
        waitForSchedulerExecution()

        then:
        1 * listener.onOutput([progressStartEvent1, logEvent1a, logEvent1b])
        1 * listener.onOutput([progressStartEvent2, logEvent2a, logEvent2b])
        renderer.groupedTaskBuildOperations.size() == 2
        batchedEvents1.size() == 1
        batchedEvents1.get(0) == progressStartEvent1
        batchedEvents2.size() == 1
        batchedEvents2.get(0) == progressStartEvent2
        renderer.renderState.isCurrentlyRendered(operationId)

        when:
        renderer.onOutput(logEvent1a)
        renderer.onOutput(logEvent1b)
        waitForSchedulerExecution()

        then:
        1 * listener.onOutput([progressStartEvent1, logEvent1a, logEvent1b])
        0 * listener.onOutput([progressStartEvent2])
        renderer.groupedTaskBuildOperations.size() == 2
        batchedEvents1.size() == 1
        batchedEvents1.get(0) == progressStartEvent1
        batchedEvents2.size() == 1
        batchedEvents2.get(0) == progressStartEvent2
        renderer.renderState.isCurrentlyRendered(operationId)
    }

    def "currently rendered output events for an operation ID can alternate after receiving complete event"() {
        given:
        def progressStartEvent1 = createDefaultProgressStartEvent()
        def logEvent1a = createLogEvent( DEFAULT_OPERATION_ID, '1a')
        def logEvent1b = createLogEvent( DEFAULT_OPERATION_ID, '1b')
        def operationId = new OperationIdentifier(456)
        def progressStartEvent2 = createProgressStartEvent(operationId, LoggingType.TASK_EXECUTION, ':assemble')
        def logEvent2a = createLogEvent(operationId, '2a')
        def logEvent2b = createLogEvent(operationId, '2b')
        def progressCompleteEvent1 = createDefaultProgressCompleteEvent()
        def progressCompleteEvent2 = createProgressCompleteEvent(operationId)

        when:
        renderer.onOutput(progressStartEvent1)
        renderer.onOutput(logEvent1a)
        renderer.onOutput(progressStartEvent2)
        renderer.onOutput(logEvent1b)
        renderer.onOutput(logEvent2a)
        renderer.onOutput(logEvent2b)
        waitForSchedulerExecution()

        then:
        1 * listener.onOutput([progressStartEvent1, logEvent1a, logEvent1b])
        1 * listener.onOutput([progressStartEvent2, logEvent2a, logEvent2b])
        renderer.groupedTaskBuildOperations.size() == 2
        def batchedEvents1 = renderer.groupedTaskBuildOperations.get(DEFAULT_OPERATION_ID)
        batchedEvents1.size() == 1
        batchedEvents1.get(0) == progressStartEvent1
        def batchedEvents2 = renderer.groupedTaskBuildOperations.get(operationId)
        batchedEvents2.size() == 1
        batchedEvents2.get(0) == progressStartEvent2
        renderer.renderState.isCurrentlyRendered(operationId)

        when:
        renderer.onOutput(progressCompleteEvent1)

        then:
        1 * listener.onOutput(progressCompleteEvent1)
        renderer.groupedTaskBuildOperations.size() == 1
        batchedEvents1.size() == 1
        batchedEvents1.get(0) == progressStartEvent1
        renderer.renderState.isCurrentlyRendered(operationId)

        when:
        waitForSchedulerExecution()

        then:
        0 * listener.onOutput(_)
        renderer.groupedTaskBuildOperations.size() == 1
        batchedEvents1.size() == 1
        batchedEvents1.get(0) == progressStartEvent1
        renderer.renderState.isCurrentlyRendered(operationId)

        when:
        renderer.onOutput(progressCompleteEvent2)

        then:
        1 * listener.onOutput(progressCompleteEvent2)
        renderer.groupedTaskBuildOperations.isEmpty()
        !renderer.renderState.currentlyRendered
    }

    static void waitForSchedulerExecution() {
        Thread.sleep(GroupedBuildOperationRenderer.SCHEDULER_CHECK_PERIOD_MS + 200)
    }

    static ProgressStartEvent createDefaultProgressStartEvent() {
        return createProgressStartEvent(DEFAULT_OPERATION_ID, LoggingType.TASK_EXECUTION, ':compileJava')
    }

    static ProgressCompleteEvent createDefaultProgressCompleteEvent() {
        return createProgressCompleteEvent(DEFAULT_OPERATION_ID)
    }

    static LogEvent createDefaultLogEvent() {
        return createLogEvent(DEFAULT_OPERATION_ID, 'complete')
    }

    static StyledTextOutputEvent createDefaultStyledTextOutputEvent() {
        return new StyledTextOutputEvent(new Date().time, TASK_EXECUTION_CATEGORY, DEFAULT_OPERATION_ID, 'text')
    }

    static ProgressStartEvent createProgressStartEvent(OperationIdentifier operationId, LoggingType loggingType, String loggingHeader) {
        return new ProgressStartEvent(operationId, null, new Date().time, TASK_EXECUTION_CATEGORY, 'some task', null, loggingType, loggingHeader, null)
    }

    static LogEvent createLogEvent(OperationIdentifier operationId, String message) {
        return new LogEvent(new Date().time, TASK_EXECUTION_CATEGORY, LogLevel.LIFECYCLE, operationId, message, null)
    }

    static ProgressCompleteEvent createProgressCompleteEvent(OperationIdentifier operationId) {
        return new ProgressCompleteEvent(operationId, new Date().time, TASK_EXECUTION_CATEGORY, 'some task', 'complete')
    }
}
