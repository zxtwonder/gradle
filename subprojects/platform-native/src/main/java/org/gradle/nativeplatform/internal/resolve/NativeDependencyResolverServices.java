/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.resolve.DefaultProjectModelResolver;
import org.gradle.api.internal.resolve.NativeLocalLibraryMetaDataAdapter;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.nativeplatform.NativeDependencySet;

public class NativeDependencyResolverServices {

    public ProjectModelResolver createProjectLocator(ProjectRegistry<ProjectInternal> projectRegistry, DependencyMetaDataProvider metaDataProvider) {
        String currentProjectPath = metaDataProvider.getModule().getProjectPath();
        return new CurrentProjectModelResolver(currentProjectPath, new DefaultProjectModelResolver(projectRegistry));
    }

    public NativeDependencyResolver createResolver(org.gradle.api.internal.resolve.NativeDependencyResolver realNativeResolver, FileCollectionFactory fileCollectionFactory) {
        NativeDependencyResolver resolver = new NativeDependencyResolverAdapter(realNativeResolver);
        resolver = new RequirementParsingNativeDependencyResolver(resolver);
        resolver = new SourceSetNativeDependencyResolver(resolver, fileCollectionFactory);
        return new InputHandlingNativeDependencyResolver(resolver);
    }

    private static class NativeDependencyResolverAdapter implements NativeDependencyResolver {
        private final org.gradle.api.internal.resolve.NativeDependencyResolver realNativeResolver;

        private NativeDependencyResolverAdapter(org.gradle.api.internal.resolve.NativeDependencyResolver realNativeResolver) {
            this.realNativeResolver = realNativeResolver;
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
            return realNativeResolver.resolveFiles(resolution.getTarget(), requirementResolution.getRequirement().getProjectPath(), requirementResolution.getRequirement().getLibraryName(), requirementResolution.getRequirement().getLinkage(), usage);
        }
    }
}
