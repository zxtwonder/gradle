/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.initialization;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Task;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.execution.BuildExecuter;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.includedbuild.internal.IncludedBuildControllers;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultGradleLauncher implements GradleLauncher {

    private enum Stage {
        Load, Configure, Build
    }

    private final InitScriptHandler initScriptHandler;
    private final SettingsLoader settingsLoader;
    private final BuildConfigurer buildConfigurer;
    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final ModelConfigurationListener modelConfigurationListener;
    private final BuildCompletionListener buildCompletionListener;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildConfigurationActionExecuter buildConfigurationActionExecuter;
    private final BuildExecuter buildExecuter;
    private final BuildScopeServices buildServices;
    private final List<?> servicesToStop;
    private GradleInternal gradle;
    private Stage stage;

    private final GradleBuildController controller;

    public DefaultGradleLauncher(GradleInternal gradle, InitScriptHandler initScriptHandler, SettingsLoader settingsLoader,
                                 BuildConfigurer buildConfigurer, ExceptionAnalyser exceptionAnalyser,
                                 BuildListener buildListener, ModelConfigurationListener modelConfigurationListener,
                                 BuildCompletionListener buildCompletionListener, BuildOperationExecutor operationExecutor,
                                 BuildConfigurationActionExecuter buildConfigurationActionExecuter, BuildExecuter buildExecuter,
                                 BuildScopeServices buildServices, List<?> servicesToStop) {
        this.gradle = gradle;
        this.initScriptHandler = initScriptHandler;
        this.settingsLoader = settingsLoader;
        this.buildConfigurer = buildConfigurer;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.modelConfigurationListener = modelConfigurationListener;
        this.buildOperationExecutor = operationExecutor;
        this.buildConfigurationActionExecuter = buildConfigurationActionExecuter;
        this.buildExecuter = buildExecuter;
        this.buildCompletionListener = buildCompletionListener;
        this.buildServices = buildServices;
        this.servicesToStop = servicesToStop;
        controller = new GradleBuildController(gradle);
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        controller.buildStarted();
        return controller.getLoadedSettings();
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        controller.buildStarted();
        return controller.getConfiguredBuild();
    }

    public void runTasks(Iterable<String> taskNames) {
        controller.buildStarted();
        controller.runTasks(taskNames);
    }

    @Override
    public BuildResult run() {
        return doBuild(Stage.Build);
    }

    @Override
    public BuildResult getBuildAnalysis() {
        return doBuild(Stage.Configure);
    }

    @Override
    public BuildResult load() throws ReportedException {
        return doBuild(Stage.Load);
    }

    private BuildResult doBuild(final Stage upTo) {
        // TODO:pm Move this to RunAsBuildOperationBuildActionRunner when BuildOperationWorkerRegistry scope is changed
        final AtomicReference<BuildResult> buildResult = new AtomicReference<BuildResult>();
        WorkerLeaseService workerLeaseService = buildServices.get(WorkerLeaseService.class);
        workerLeaseService.withLocks(Collections.singleton(workerLeaseService.getWorkerLease()), new Runnable() {
            @Override
            public void run() {
                Throwable failure = null;
                try {
                    buildListener.buildStarted(gradle);
                    doBuildStages(upTo);
                } catch (Throwable t) {
                    failure = exceptionAnalyser.transform(t);
                }
                buildResult.set(new BuildResult(upTo.name(), gradle, failure));
                buildListener.buildFinished(buildResult.get());
                if (failure != null) {
                    throw new ReportedException(failure);
                }
            }
        });
        return buildResult.get();
    }


    private void doBuildStages(Stage upTo) {
        if (stage == Stage.Build) {
            throw new IllegalStateException("Cannot build with GradleLauncher multiple times");
        }

        if (stage == null) {
            controller.getLoadedSettings();

            stage = Stage.Load;
        }

        if (upTo == Stage.Load) {
            return;
        }

        if (stage == Stage.Load) {
            buildOperationExecutor.run(new ConfigureBuild());
            stage = Stage.Configure;
        }

        if (upTo == Stage.Configure) {
            return;
        }

        // After this point, the GradleLauncher cannot be reused
        stage = Stage.Build;

        buildOperationExecutor.run(new CalculateTaskGraph());

        if (!gradle.getIncludedBuilds().isEmpty()) {
            IncludedBuildControllers buildControllers = gradle.getServices().get(IncludedBuildControllers.class);
            buildControllers.populateTaskGraphs();
            buildControllers.startTaskExecution();
        }

        buildOperationExecutor.run(new ExecuteTasks());
    }

    /**
     * <p>Adds a listener to this build instance. The listener is notified of events which occur during the execution of the build. See {@link org.gradle.api.invocation.Gradle#addListener(Object)} for
     * supported listener types.</p>
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addListener(Object listener) {
        gradle.addListener(listener);
    }

    public void stop() {
        try {
            CompositeStoppable.stoppable(buildServices).add(servicesToStop).stop();
        } finally {
            buildCompletionListener.completed();
        }
    }

    private class ConfigureBuild implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            controller.getConfiguredBuild();

            if (!isConfigureOnDemand()) {
                projectsEvaluated();
            }

            modelConfigurationListener.onConfigure(gradle);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(contextualize("Configure build"));
        }
    }

    private class CalculateTaskGraph implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext buildOperationContext) {
            buildConfigurationActionExecuter.select(gradle);

            if (isConfigureOnDemand()) {
                projectsEvaluated();
            }

            final TaskGraphExecuter taskGraph = gradle.getTaskGraph();
            buildOperationContext.setResult(new CalculateTaskGraphBuildOperationType.Result() {
                @Override
                public List<String> getRequestedTaskPaths() {
                    return toTaskPaths(taskGraph.getRequestedTasks());
                }

                @Override
                public List<String> getExcludedTaskPaths() {
                    return toTaskPaths(taskGraph.getFilteredTasks());
                }

                private List<String> toTaskPaths(Set<Task> tasks) {
                    return ImmutableSortedSet.copyOf(Collections2.transform(tasks, new Function<Task, String>() {
                        @Override
                        public String apply(Task task) {
                            return task.getPath();
                        }
                    })).asList();
                }
            });
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(contextualize("Calculate task graph"))
                .details(new CalculateTaskGraphBuildOperationType.Details() {
                });
        }
    }

    private class ExecuteTasks implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            buildExecuter.execute(gradle);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(contextualize("Run tasks"));
        }
    }


    private boolean isConfigureOnDemand() {
        return gradle.getStartParameter().isConfigureOnDemand();
    }

    private void projectsEvaluated() {
        buildListener.projectsEvaluated(gradle);
    }

    private String contextualize(String descriptor) {
        if (isNestedBuild()) {
            return descriptor + " (" + gradle.getIdentityPath() + ")";
        }
        return descriptor;
    }

    private boolean isNestedBuild() {
        return gradle.getParent() != null;
    }

    private class GradleBuildController {
        private final GradleInternal gradle;
        private boolean started;
        private SettingsInternal settings;
        private GradleInternal configuredBuild;

        private GradleBuildController(GradleInternal gradle) {
            this.gradle = gradle;
        }

        private void buildStarted() {
            if (!started) {
                buildListener.buildStarted(gradle);
                started = true;
            }
        }

        public SettingsInternal getLoadedSettings() {
            if (settings == null) {
                // Evaluate init scripts
                initScriptHandler.executeScripts(gradle);

                // Build `buildSrc`, load settings.gradle, and construct composite (if appropriate)
                settings = settingsLoader.findAndLoadSettings(gradle);
            }
            return settings;
        }

        public GradleInternal getConfiguredBuild() {
            getLoadedSettings();
            if (configuredBuild == null) {
                configuredBuild = gradle;
                buildConfigurer.configure(configuredBuild);
            }
            return configuredBuild;
        }

        public void runTasks(final Iterable<String> taskNames) {
            // TODO:DAZ Find a way to avoid a separate worker lease here. Preferably a single shared pool of worker threads.
            WorkerLeaseService workerLeaseService = buildServices.get(WorkerLeaseService.class);
            workerLeaseService.withLocks(Collections.singleton(workerLeaseService.getWorkerLease()), new Runnable() {
                @Override
                public void run() {
                    Throwable failure = null;
                    try {
                        // TODO:DAZ Should be able to assert here that build is already configured...
                        getConfiguredBuild();
                        projectsEvaluated();
                        modelConfigurationListener.onConfigure(gradle);

                        gradle.getStartParameter().setTaskNames(taskNames);
                        buildConfigurationActionExecuter.select(gradle);

                        buildExecuter.execute(gradle);
                    } catch (Throwable t) {
                        failure = exceptionAnalyser.transform(t);
                    }

                    buildListener.buildFinished(new BuildResult(gradle, failure));
                }
            });
        }
    }
}
