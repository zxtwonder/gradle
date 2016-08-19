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
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.internal.prebuilt.PrebuiltLibraryBinaryLocator;

import java.util.ArrayList;
import java.util.List;

public class NativeDependencyResolverServices {

    public ProjectModelResolver createProjectLocator(ProjectRegistry<ProjectInternal> projectRegistry, DependencyMetaDataProvider metaDataProvider) {
        String currentProjectPath = metaDataProvider.getModule().getProjectPath();
        return new CurrentProjectModelResolver(currentProjectPath, new DefaultProjectModelResolver(projectRegistry));
    }

    // TODO: SLG Look up prebuilt libraries with the real resolver.
    public LibraryBinaryLocator createLibraryBinaryLocator(ProjectModelResolver projectModelResolver) {
        List<LibraryBinaryLocator> locators = new ArrayList<LibraryBinaryLocator>();
        locators.add(new PrebuiltLibraryBinaryLocator(projectModelResolver));
        return new ChainedLibraryBinaryLocator(locators);
    }

    public NativeDependencyResolver createResolver(org.gradle.api.internal.resolve.NativeDependencyResolver realNativeResolver, LibraryBinaryLocator locator, FileCollectionFactory fileCollectionFactory) {
        NativeDependencyResolver resolver = new LibraryNativeDependencyResolver(locator); // TODO: Remove this resolver (once prebuilt libraries work)
        resolver = new ApiRequirementNativeDependencyResolver(resolver, fileCollectionFactory); // TODO: Remove this resolver
        resolver = new NativeDependencyResolverAdapter(resolver, realNativeResolver, fileCollectionFactory);
        resolver = new RequirementParsingNativeDependencyResolver(resolver);
        resolver = new SourceSetNativeDependencyResolver(resolver, fileCollectionFactory);
        return new InputHandlingNativeDependencyResolver(resolver);
    }

    private static class NativeDependencyResolverAdapter implements NativeDependencyResolver {
        private final NativeDependencyResolver delegate;
        private final org.gradle.api.internal.resolve.NativeDependencyResolver realNativeResolver;
        private final FileCollectionFactory fileCollectionFactory;

        private NativeDependencyResolverAdapter(NativeDependencyResolver delegate, org.gradle.api.internal.resolve.NativeDependencyResolver realNativeResolver, FileCollectionFactory fileCollectionFactory) {
            this.delegate = delegate;
            this.realNativeResolver = realNativeResolver;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public void resolve(NativeBinaryResolveResult resolution) {
            for (NativeBinaryRequirementResolveResult requirementResolution : resolution.getPendingResolutions()) {
                // TODO: Clean-up usages
                // TODO: Resolve these lazily?
                try {
                    final FileCollection compile = resolve(resolution, requirementResolution, "compile");
                    final FileCollection link;
                    final FileCollection runtime;

                    if (isApiLinkage(requirementResolution)) {
                        link = fileCollectionFactory.empty("api link");
                        runtime = fileCollectionFactory.empty("api runtime");
                    } else {
                        link = resolve(resolution, requirementResolution, "link");
                        runtime = resolve(resolution, requirementResolution, "run");
                    }

                    NativeDependencySet dependencySet = new NativeDependencySet() {
                        @Override
                        public FileCollection getIncludeRoots() {
                            return compile;
                        }

                        @Override
                        public FileCollection getLinkFiles() {
                            return link;
                        }

                        @Override
                        public FileCollection getRuntimeFiles() {
                            return runtime;
                        }
                    };
                    requirementResolution.setNativeDependencySet(dependencySet);
                } catch (LibraryResolveException e) {
                    // TODO: Remove this when prebuilt libraries come out of the real resolver.
                    delegate.resolve(resolution);
                }
            }
        }

        private boolean isApiLinkage(NativeBinaryRequirementResolveResult requirementResolution) {
            return "api".equals(requirementResolution.getRequirement().getLinkage());
        }

        private FileCollection resolve(NativeBinaryResolveResult resolution, NativeBinaryRequirementResolveResult requirementResolution, String usage) {
            return realNativeResolver.resolveFiles(resolution.getTarget(), requirementResolution.getRequirement().getProjectPath(), requirementResolution.getRequirement().getLibraryName(), requirementResolution.getRequirement().getLinkage(), usage);
        }
    }
}
