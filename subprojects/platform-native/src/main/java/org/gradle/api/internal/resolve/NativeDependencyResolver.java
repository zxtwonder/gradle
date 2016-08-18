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

package org.gradle.api.internal.resolve;

import com.google.common.collect.Lists;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.internal.DefaultLibraryBinaryDependencySpec;
import org.gradle.platform.base.internal.DefaultProjectDependencySpec;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.gradle.util.CollectionUtils.collect;

public class NativeDependencyResolver {
    private final GlobalDependencyResolutionRules globalRules = GlobalDependencyResolutionRules.NO_OP;
    private final List<ResolutionAwareRepository> remoteRepositories = Lists.newArrayList();
    private final ArtifactDependencyResolver dependencyResolver;

    public NativeDependencyResolver(ArtifactDependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    public Set<File> resolveFiles(String project, String component, String variant, String usage) {
        ResolveResult resolveResult = doResolve(project, component, variant, usage);
        Set<ResolvedArtifact> artifacts = resolveResult.artifactResults.getArtifacts();

        failOnUnresolvedDependency(resolveResult.notFound);

        return collect(artifacts, new org.gradle.api.Transformer<File, ResolvedArtifact>() {
            @Override
            public File transform(ResolvedArtifact resolvedArtifact) {
                return resolvedArtifact.getFile();
            }
        });
    }

    private ResolveResult doResolve(String project, String library, @Nullable String variant, String usage) {
        LibraryBinaryIdentifier libraryId = new DefaultLibraryBinaryIdentifier(":foo", "bar", "baz");
        DependencySpec dep;
        if (variant == null) {
            dep = new DefaultProjectDependencySpec(library, project);
        } else {
            dep = new DefaultLibraryBinaryDependencySpec(project, library, variant);
        }
        NativeComponentResolveContext context = new NativeComponentResolveContext(libraryId, Collections.singleton(dep), usage, "foo");

        ResolveResult result = new ResolveResult();
        dependencyResolver.resolve(context, remoteRepositories, globalRules, result, result);
        return result;
    }

    private void failOnUnresolvedDependency(List<Throwable> notFound) {
        if (!notFound.isEmpty()) {
            throw new LibraryResolveException("Could not resolve all dependencies", notFound);
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
