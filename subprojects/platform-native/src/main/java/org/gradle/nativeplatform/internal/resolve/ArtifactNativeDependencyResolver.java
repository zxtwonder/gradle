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

package org.gradle.nativeplatform.internal.resolve;

import com.google.common.collect.Lists;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.resolve.NativeComponentResolveContext;
import org.gradle.api.internal.resolve.NativeLocalLibraryMetaDataAdapter;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.internal.DefaultLibraryBinaryDependencySpec;
import org.gradle.platform.base.internal.DefaultProjectDependencySpec;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.gradle.util.CollectionUtils.collect;

class ArtifactNativeDependencyResolver implements NativeDependencyResolver {
    private final GlobalDependencyResolutionRules globalRules = GlobalDependencyResolutionRules.NO_OP;
    private final List<ResolutionAwareRepository> remoteRepositories = Lists.newArrayList();
    private final ArtifactDependencyResolver dependencyResolver;
    private final FileCollectionFactory fileCollectionFactory;

    ArtifactNativeDependencyResolver(ArtifactDependencyResolver dependencyResolver, FileCollectionFactory fileCollectionFactory) {
        this.dependencyResolver = dependencyResolver;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public void resolve(final NativeBinaryResolveResult resolution) {
        // TODO: Failures should propagate up
        for (final NativeBinaryRequirementResolveResult requirementResolution : resolution.getPendingResolutions()) {
            NativeDependencySet dependencySet = new NativeDependencySet() {
                @Override
                public FileCollection getIncludeRoots() {
                    return resolve(resolution, requirementResolution, NativeLocalLibraryMetaDataAdapter.COMPILE);
                }

                @Override
                public FileCollection getLinkFiles() {
                    return resolve(resolution, requirementResolution, NativeLocalLibraryMetaDataAdapter.LINK);
                }

                @Override
                public FileCollection getRuntimeFiles() {
                    return resolve(resolution, requirementResolution, NativeLocalLibraryMetaDataAdapter.RUN);
                }
            };
            requirementResolution.setNativeDependencySet(dependencySet);
        }
    }

    private FileCollection resolve(NativeBinaryResolveResult resolution, NativeBinaryRequirementResolveResult requirementResolution, String usage) {
        return resolveFiles(resolution.getTarget(), requirementResolution.getRequirement().getProjectPath(), requirementResolution.getRequirement().getLibraryName(), requirementResolution.getRequirement().getLinkage(), usage);
    }

    private FileCollection resolveFiles(NativeBinarySpec target, String project, String component, String variant, String usage) {
        ResolveResult resolveResult = doResolve(target, project, component, variant, usage);
        Set<ResolvedArtifact> artifacts = resolveResult.artifactResults.getArtifacts();

        failOnUnresolvedDependency(target, resolveResult.notFound);

        return fileCollectionFactory.create(resolveResult.taskDependency, new ValidatingFileSet(artifacts, String.format("%s dependencies of %s", usage, target.getDisplayName()), !NativeLocalLibraryMetaDataAdapter.COMPILE.equals(usage)));
    }

    private ResolveResult doResolve(NativeBinarySpec target, String project, String library, @Nullable String variant, String usage) {
        DependencySpec dep;
        // TODO: Fix-up the requirements so we always have the project set?
        String projectPath = project==null ? target.getProjectPath() : project;
        if (variant == null) {
            dep = new DefaultProjectDependencySpec(library, projectPath);
        } else {
            dep = new DefaultLibraryBinaryDependencySpec(projectPath, library, variant);
        }
        NativeComponentResolveContext context = new NativeComponentResolveContext(target, Collections.singleton(dep), usage, target.getDisplayName());

        ResolveResult result = new ResolveResult();
        dependencyResolver.resolve(context, remoteRepositories, globalRules, result, result);
        return result;
    }

    private void failOnUnresolvedDependency(NativeBinarySpec target, List<Throwable> notFound) {
        if (!notFound.isEmpty()) {
            throw new LibraryResolveException(String.format("Could not resolve all dependencies for %s in project '%s'.", target.getDisplayName(), target.getProjectPath()), notFound);
        }
    }

    private class ValidatingFileSet implements MinimalFileSet {
        private final Set<ResolvedArtifact> artifacts;
        private final String displayName;
        private final boolean validate;

        private ValidatingFileSet(Set<ResolvedArtifact> artifacts, String displayName, boolean validate) {
            this.artifacts = artifacts;
            this.displayName = displayName;
            this.validate = validate;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public Set<File> getFiles() {
            return collect(artifacts, new org.gradle.api.Transformer<File, ResolvedArtifact>() {
                @Override
                public File transform(ResolvedArtifact resolvedArtifact) {
                    File file = resolvedArtifact.getFile();
                    if (validate && (!file.exists() || !file.isFile())) {
                        throw new LibraryResolveException(String.format("Artifact %s does not exist for %s when resolving %s.", file.getAbsolutePath(), resolvedArtifact.getId(), displayName));
                    }
                    return resolvedArtifact.getFile();
                }
            });
        }
    }

    class ResolveResult implements DependencyGraphVisitor, DependencyArtifactsVisitor {
        public final DefaultTaskDependency taskDependency = new DefaultTaskDependency();
        public final List<Throwable> notFound = new LinkedList<Throwable>();
        public final DefaultResolvedArtifactResults artifactResults = new DefaultResolvedArtifactResults();

        @Override
        public void start(DependencyGraphNode root) {
        }

        @Override
        public void visitNode(DependencyGraphNode resolvedConfiguration) {
            ConfigurationMetadata configurationMetadata = resolvedConfiguration.getMetadata();
            if (configurationMetadata instanceof LocalConfigurationMetadata) {
                TaskDependency directBuildDependencies = ((LocalConfigurationMetadata) configurationMetadata).getDirectBuildDependencies();
                taskDependency.add(directBuildDependencies);
            }

            for (DependencyGraphEdge dependency : resolvedConfiguration.getOutgoingEdges()) {
                ModuleVersionResolveException failure = dependency.getFailure();
                if (failure != null) {
                    notFound.add(failure);
                }
            }
        }

        @Override
        public void visitEdge(DependencyGraphNode resolvedConfiguration) {
        }

        @Override
        public void finish(DependencyGraphNode root) {
        }

        @Override
        public void visitArtifacts(DependencyGraphNode parent, DependencyGraphNode child, ArtifactSet artifacts) {
            artifactResults.addArtifactSet(artifacts);
        }

        @Override
        public void finishArtifacts() {
            artifactResults.resolveNow();
        }
    }
}
