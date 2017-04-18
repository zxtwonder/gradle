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
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.StyledTextOutputEvent
import spock.lang.Specification

class GroupedBuildOperationRendererTest extends Specification {

    public static final OperationIdentifier DEFAULT_OPERATION_ID = new OperationIdentifier(123)
    def listener = Mock(BatchOutputEventListener)
    def renderer = new GroupedBuildOperationRenderer(listener)

    def "end output events are forwarded and clear the queue"() {
        given:
        def event = new EndOutputEvent()

        when:
        renderer.onOutput(event)

        then:
        1 * listener.onOutput(event)
        renderer.groupedTaskBuildOperations.isEmpty()
    }

    def "output events that not considered to be a task-based event are forwarded"() {
        when:
        renderer.onOutput(event)

        then:
        1 * listener.onOutput(event)
        renderer.groupedTaskBuildOperations.isEmpty()

        where:
        event << [new ProgressStartEvent(DEFAULT_OPERATION_ID, null, new Date().time, 'task', 'some task', null, null, null),
                  new ProgressCompleteEvent(DEFAULT_OPERATION_ID, new Date().time, 'task', 'some task', 'complete'),
                  new LogEvent(new Date().time, 'task', LogLevel.LIFECYCLE, DEFAULT_OPERATION_ID, 'complete', null),
                  new StyledTextOutputEvent(new Date().time, 'task', DEFAULT_OPERATION_ID, 'text')]
    }

    def "batches task-based progress start event"() {
        given:
        def timestamp = new Date().time
        def loggingHeader = ':compileJava'
        def event = new ProgressStartEvent(DEFAULT_OPERATION_ID, null, timestamp, 'class org.gradle.internal.buildevents.TaskExecutionLogger', 'some task', null, loggingHeader, null)

        when:
        renderer.onOutput(event)

        then:
        0 * listener.onOutput(_)
        !renderer.groupedTaskBuildOperations.isEmpty()
        def batchedEvents = renderer.groupedTaskBuildOperations.get(DEFAULT_OPERATION_ID)
        batchedEvents.get(0) == event
    }

    def "batches and flushes different types of task-based events"() {
        given:
        def loggingHeader = ':compileJava'
        def progressStartEvent = new ProgressStartEvent(DEFAULT_OPERATION_ID, null, new Date().time, 'class org.gradle.internal.buildevents.TaskExecutionLogger', 'some task', null, loggingHeader, null)

        when:
        renderer.onOutput(progressStartEvent)

        then:
        0 * listener.onOutput(_)
        renderer.groupedTaskBuildOperations.size() == 1
        def batchedEvents = renderer.groupedTaskBuildOperations.get(DEFAULT_OPERATION_ID)
        batchedEvents.size() == 1
        batchedEvents.get(0) == progressStartEvent

        when:
        def logEvent = new LogEvent(new Date().time, 'class org.gradle.internal.buildevents.TaskExecutionLogger', LogLevel.LIFECYCLE, DEFAULT_OPERATION_ID, 'complete', null)
        renderer.onOutput(logEvent)

        then:
        0 * listener.onOutput(_)
        renderer.groupedTaskBuildOperations.size() == 1
        batchedEvents.size() == 2
        batchedEvents.get(0) == progressStartEvent
        batchedEvents.get(1) == logEvent

        when:
        def styledTextOutputEvent = new StyledTextOutputEvent(new Date().time, 'class org.gradle.internal.buildevents.TaskExecutionLogger', DEFAULT_OPERATION_ID, 'text')
        renderer.onOutput(styledTextOutputEvent)

        then:
        0 * listener.onOutput(_)
        renderer.groupedTaskBuildOperations.size() == 1
        batchedEvents.size() == 3
        batchedEvents.get(0) == progressStartEvent
        batchedEvents.get(1) == logEvent
        batchedEvents.get(2) == styledTextOutputEvent

        when:
        def progressCompleteEvent = new ProgressCompleteEvent(DEFAULT_OPERATION_ID, new Date().time, 'class org.gradle.internal.buildevents.TaskExecutionLogger', 'some task', 'complete')
        renderer.onOutput(progressCompleteEvent)

        then:
        1 * listener.onOutput(renderer.groupedTaskBuildOperations.get(DEFAULT_OPERATION_ID))
        renderer.groupedTaskBuildOperations.isEmpty()
    }

    def "can batch and flush different build operations"() {
        given:
        def loggingHeader1 = ':compileJava'
        def progressStartEvent1 = new ProgressStartEvent(DEFAULT_OPERATION_ID, null, new Date().time, 'class org.gradle.internal.buildevents.TaskExecutionLogger', 'some task', null, loggingHeader1, null)
        def operationIdentifier2 = new OperationIdentifier(456)
        def loggingHeader2 = ':compileJava'
        def progressStartEvent2 = new ProgressStartEvent(operationIdentifier2, null, new Date().time, 'class org.gradle.internal.buildevents.TaskExecutionLogger', 'some task', null, loggingHeader2, null)

        when:
        renderer.onOutput(progressStartEvent1)
        renderer.onOutput(progressStartEvent2)

        then:
        0 * listener.onOutput(_)
        renderer.groupedTaskBuildOperations.size() == 2
        def batchedEvents1 = renderer.groupedTaskBuildOperations.get(DEFAULT_OPERATION_ID)
        batchedEvents1.size() == 1
        batchedEvents1.get(0) == progressStartEvent1
        def batchedEvents2 = renderer.groupedTaskBuildOperations.get(operationIdentifier2)
        batchedEvents2.size() == 1
        batchedEvents2.get(0) == progressStartEvent2

        when:
        def progressCompleteEvent1 = new ProgressCompleteEvent(DEFAULT_OPERATION_ID, new Date().time, 'class org.gradle.internal.buildevents.TaskExecutionLogger', 'some task', 'complete')
        def progressCompleteEvent2 = new ProgressCompleteEvent(operationIdentifier2, new Date().time, 'class org.gradle.internal.buildevents.TaskExecutionLogger', 'some task', 'complete')
        renderer.onOutput(progressCompleteEvent1)
        renderer.onOutput(progressCompleteEvent2)

        then:
        1 * listener.onOutput(renderer.groupedTaskBuildOperations.get(DEFAULT_OPERATION_ID))
        1 * listener.onOutput(renderer.groupedTaskBuildOperations.get(operationIdentifier2))
        renderer.groupedTaskBuildOperations.isEmpty()
    }
}
