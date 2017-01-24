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

package org.gradle.process.internal.daemon

import org.gradle.internal.operations.BuildOperationWorkerRegistry
import spock.lang.Specification


class DefaultSessionWorkerDaemonManagerTest extends Specification {
    def buildOperationWorkerRegistry = Mock(BuildOperationWorkerRegistry)
    def workerDaemonManager = Mock(WorkerDaemonManager)
    def workerDaemonStarter = Mock(WorkerDaemonStarter)
    def sessionWorkerDaemonManager = new DefaultSessionWorkerDaemonManager(workerDaemonManager, buildOperationWorkerRegistry, workerDaemonStarter)

    def "build operation is started and finished when client is executed"() {
        def operation = Mock(BuildOperationWorkerRegistry.Operation)
        def completion = Mock(BuildOperationWorkerRegistry.Completion)
        def workerDaemon = Mock(WorkerDaemon)

        when:
        sessionWorkerDaemonManager.getDaemon(Mock(WorkerDaemonProtocol).class, Mock(File), Mock(DaemonForkOptions)).execute(Mock(WorkerDaemonAction), Mock(WorkSpec))

        then:
        1 * workerDaemonManager.getDaemon(_, _, _, _) >> workerDaemon
        1 * workerDaemon.execute(_, _) >> new WorkerDaemonResult(true, null)
        1 * buildOperationWorkerRegistry.getCurrent() >> operation
        1 * operation.operationStart() >> completion
        1 * completion.operationFinish()
    }

    def "build operation is finished even if worker fails"() {
        def operation = Mock(BuildOperationWorkerRegistry.Operation)
        def completion = Mock(BuildOperationWorkerRegistry.Completion)
        def workerDaemon = Mock(WorkerDaemon)

        when:
        sessionWorkerDaemonManager.getDaemon(Mock(WorkerDaemonProtocol).class, Mock(File), Mock(DaemonForkOptions)).execute(Mock(WorkerDaemonAction), Mock(WorkSpec))

        then:
        thrown(RuntimeException)
        1 * workerDaemonManager.getDaemon(_, _, _, _) >> workerDaemon
        1 * workerDaemon.execute(_, _) >> { throw new RuntimeException() }
        1 * buildOperationWorkerRegistry.getCurrent() >> operation
        1 * operation.operationStart() >> completion
        1 * completion.operationFinish()
    }
}
