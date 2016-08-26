/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.internal.services;

import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DelegatingComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.resolve.ChainLocalLibraryResolver;
import org.gradle.api.internal.resolve.DefaultLocalLibraryResolver;
import org.gradle.api.internal.resolve.LibraryResolutionErrorMessageBuilder;
import org.gradle.api.internal.resolve.LocalLibraryDependencyResolver;
import org.gradle.api.internal.resolve.LocalLibraryMetaDataAdapter;
import org.gradle.api.internal.resolve.NativeComponentResolveContext;
import org.gradle.api.internal.resolve.NativeLibraryResolutionErrorMessageBuilder;
import org.gradle.api.internal.resolve.NativeLocalLibraryMetaDataAdapter;
import org.gradle.api.internal.resolve.NativeVariantSelector;
import org.gradle.api.internal.resolve.PrebuiltLibraryResolver;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.api.internal.resolve.VariantSelector;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.nativeplatform.NativeBinary;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinaryRenderer;
import org.gradle.nativeplatform.internal.NativeExecutableBinaryRenderer;
import org.gradle.nativeplatform.internal.NativePlatformResolver;
import org.gradle.nativeplatform.internal.SharedLibraryBinaryRenderer;
import org.gradle.nativeplatform.internal.StaticLibraryBinaryRenderer;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolverServices;
import org.gradle.nativeplatform.platform.internal.NativePlatforms;
import org.gradle.nativeplatform.toolchain.internal.gcc.version.CompilerMetaDataProviderFactory;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultVisualStudioLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultWindowsSdkLocator;

public class NativeBinaryServices implements PluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(NativeBinaryRenderer.class);
        registration.add(SharedLibraryBinaryRenderer.class);
        registration.add(StaticLibraryBinaryRenderer.class);
        registration.add(NativeExecutableBinaryRenderer.class);
        registration.add(NativePlatforms.class);
        registration.add(NativePlatformResolver.class);
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(DefaultVisualStudioLocator.class);
        registration.add(DefaultWindowsSdkLocator.class);
        registration.add(CompilerMetaDataProviderFactory.class);
        registration.add(NativeLibraryDependencyResolverFactory.class);
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new NativeDependencyResolverServices());
    }


    public static class NativeLibraryDependencyResolverFactory implements ResolverProviderFactory {
        private final ProjectModelResolver projectModelResolver;
        private final FileCollectionFactory fileCollectionFactory;

        public NativeLibraryDependencyResolverFactory(ProjectModelResolver projectModelResolver, FileCollectionFactory fileCollectionFactory) {
            this.projectModelResolver = projectModelResolver;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public boolean canCreate(ResolveContext context) {
            return context instanceof NativeComponentResolveContext;
        }

        @Override
        public ComponentResolvers create(ResolveContext context) {
            NativeBinarySpec binarySpec = ((NativeComponentResolveContext) context).getBinarySpec();
            VariantSelector variantSelector = new NativeVariantSelector(binarySpec.getFlavor(), binarySpec.getTargetPlatform(), binarySpec.getBuildType(), fileCollectionFactory);
            LocalLibraryMetaDataAdapter libraryMetaDataAdapter = new NativeLocalLibraryMetaDataAdapter();
            LibraryResolutionErrorMessageBuilder errorMessageBuilder = new NativeLibraryResolutionErrorMessageBuilder();
            LocalLibraryDependencyResolver delegate =
                    new LocalLibraryDependencyResolver(
                            NativeBinary.class,
                            projectModelResolver,
                            new ChainLocalLibraryResolver(new DefaultLocalLibraryResolver(), new PrebuiltLibraryResolver()),
                            variantSelector,
                            libraryMetaDataAdapter,
                        errorMessageBuilder
                    );
            return DelegatingComponentResolvers.of(delegate);
        }
    }

}
