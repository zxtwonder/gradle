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
import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.progress.LoggingType
import spock.lang.Specification

class GroupedBuildOperationRendererSchedulerTest extends Specification {

    public static final OperationIdentifier DEFAULT_OPERATION_ID = new OperationIdentifier(123)
    def listener = Mock(BatchOutputEventListener)
    def renderer = new GroupedBuildOperationRenderer(listener, true)

    def "scheduler forwards batched events before progress end event is received"() {
        given:
        def progressStartEvent = new ProgressStartEvent(DEFAULT_OPERATION_ID, null, new Date().time, 'class org.gradle.internal.buildevents.TaskExecutionLogger', 'some task', null, LoggingType.TASK_EXECUTION, ':compileJava', null)
        def logEvent = new LogEvent(new Date().time, 'class org.gradle.internal.buildevents.TaskExecutionLogger', LogLevel.LIFECYCLE, DEFAULT_OPERATION_ID, 'complete', null)

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
    }

    static void waitForSchedulerExecution() {
        Thread.sleep(GroupedBuildOperationRenderer.SCHEDULER_CHECK_PERIOD_MS + 200)
    }
}
