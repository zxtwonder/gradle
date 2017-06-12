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

package org.gradle.composite.internal;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.includedbuild.IncludedBuild;
import org.gradle.includedbuild.internal.IncludedBuildController;
import org.gradle.includedbuild.internal.IncludedBuildControllers;
import org.gradle.includedbuild.internal.IncludedBuilds;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;

import java.util.Map;

class DefaultIncludedBuildControllers implements Stoppable, IncludedBuildControllers {
    private final Map<BuildIdentifier, IncludedBuildController> buildControllers = Maps.newHashMap();
    private final IncludedBuilds includedBuilds;
    private final BuildOperationExecutor buildOperationExecutor;
    private boolean taskExecutionStarted;

    DefaultIncludedBuildControllers(IncludedBuilds includedBuilds, BuildOperationExecutor buildOperationExecutor) {
        this.includedBuilds = includedBuilds;

        this.buildOperationExecutor = buildOperationExecutor;
    }

    public IncludedBuildController getBuildController(BuildIdentifier buildId) {
        IncludedBuildController buildController = buildControllers.get(buildId);
        if (buildController != null) {
            return buildController;
        }

        final IncludedBuild build = includedBuilds.getBuild(buildId.getName());
        final DefaultIncludedBuildController newBuildController = new DefaultIncludedBuildController(build);
        buildControllers.put(buildId, newBuildController);

        final RunnableBuildOperation operation = new RunnableBuildOperation() {
             @Override
             public void run(BuildOperationContext context) {
                 newBuildController.run();
             }

             @Override
             public BuildOperationDescriptor.Builder description() {
                 return BuildOperationDescriptor.displayName("Run tasks for build: " + build.getName());
             }
         };

         new Thread() {
             @Override
             public void run() {
                 buildOperationExecutor.run(operation);
             }
         }.start();

        // Required for build controllers created after initial start
        if (taskExecutionStarted) {
            newBuildController.startTaskExecution();
        }

        return newBuildController;
    }

    @Override
    public void populateTaskGraphs() {
        // TODO:DAZ Need to repeat until no tasks discovered.
        boolean tasksDiscovered = true;
        while (tasksDiscovered) {
            tasksDiscovered = false;
            for (IncludedBuildController buildController : buildControllers.values()) {
                if (buildController.populateTaskGraph()) {
                    tasksDiscovered = true;
                }
            }
        }
    }

    @Override
    public void startTaskExecution() {
        for (IncludedBuildController buildController : buildControllers.values()) {
            buildController.startTaskExecution();
        }
        taskExecutionStarted = true;
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(buildControllers.values()).stop();
    }
}
