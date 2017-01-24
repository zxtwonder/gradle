/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.StartParameter;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.process.daemon.WorkerDaemonService;
import org.gradle.process.internal.daemon.DefaultSessionWorkerDaemonManager;
import org.gradle.process.internal.daemon.DefaultWorkerDaemonService;
import org.gradle.process.internal.daemon.WorkerDaemonManager;
import org.gradle.process.internal.daemon.WorkerDaemonClientsManager;
import org.gradle.process.internal.daemon.DefaultWorkerDaemonManager;
import org.gradle.process.internal.daemon.DefaultWorkerDaemonStarter;
import org.gradle.process.internal.daemon.SessionWorkerDaemonManager;
import org.gradle.process.internal.daemon.WorkerDaemonStarter;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.worker.WorkerProcessFactory;

public class WorkersServices implements PluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalScopeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionScopeServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class GlobalScopeServices {
        WorkerDaemonClientsManager createWorkerDaemonClientsManager() {
            return new WorkerDaemonClientsManager();
        }

        WorkerDaemonManager createWorkerDaemonManager(WorkerDaemonClientsManager workerDaemonClientsManager,
                                                      MemoryManager memoryManager) {
            return new DefaultWorkerDaemonManager(workerDaemonClientsManager, memoryManager);
        }
    }

    private static class BuildSessionScopeServices {
        WorkerDaemonStarter createWorkerDaemonStarter(WorkerProcessFactory workerProcessFactory,
                                                      StartParameter startParameter) {
            return new DefaultWorkerDaemonStarter(workerProcessFactory, startParameter);
        }

        WorkerDaemonService createWorkerDaemonService(SessionWorkerDaemonManager sessionWorkerDaemonManager,
                                                      FileResolver fileResolver) {
            return new DefaultWorkerDaemonService(sessionWorkerDaemonManager, fileResolver);
        }

        SessionWorkerDaemonManager createWorkerDaemonFactory(WorkerDaemonManager workerDaemonManager, BuildOperationWorkerRegistry buildOperationWorkerRegistry, WorkerDaemonStarter workerDaemonStarter) {
            return new DefaultSessionWorkerDaemonManager(workerDaemonManager, buildOperationWorkerRegistry, workerDaemonStarter);
        }
    }
}
