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
import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Nullable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.Cast;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.StaticLibraryBinary;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.platform.base.Binary;
import org.gradle.platform.base.VariantComponent;

import java.util.Collection;
import java.util.Set;

public class NativeVariantChooser implements VariantSelector {
    private final Flavor flavor;
    private final NativePlatform platform;
    private final BuildType buildType;
    private final FileCollectionFactory fileCollectionFactory;

    public NativeVariantChooser(Flavor flavor, NativePlatform platform, BuildType buildType, FileCollectionFactory fileCollectionFactory) {
        this.flavor = flavor;
        this.platform = platform;
        this.buildType = buildType;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public Collection<? extends Binary> selectVariants(VariantComponent componentSpec, @Nullable String linkage) {
        Class<? extends NativeLibraryBinary> type = getTypeForLinkage(linkage);
        Collection<NativeLibraryBinary> candidateBinaries = Lists.newArrayList();
        for (Binary binary : componentSpec.getVariants()) {
            // TODO: SG - Remove this special handling of API linkages
            if (isApiLinkage(linkage)) {
                if (SharedLibraryBinary.class.isInstance(binary)) {
                    candidateBinaries.add(new ApiLibraryBinary(Cast.cast(SharedLibraryBinary.class, binary), fileCollectionFactory));
                }
            } else {
                if (type.isInstance(binary)) {
                    candidateBinaries.add(type.cast(binary));
                }
            }
        }
        return resolve(candidateBinaries, flavor, platform, buildType);
    }

    private Class<? extends NativeLibraryBinary> getTypeForLinkage(String linkage) {
        if ("static".equals(linkage)) {
            return StaticLibraryBinary.class;
        }
        if ("shared".equals(linkage) || linkage == null) {
            return SharedLibraryBinary.class;
        }
        if (isApiLinkage(linkage)) {
            return ApiLibraryBinary.class;
        }
        throw new InvalidUserDataException("Not a valid linkage: " + linkage);
    }

    private boolean isApiLinkage(@Nullable String linkage) {
        return "api".equals(linkage);
    }

    private Collection<NativeLibraryBinary> resolve(Collection<? extends NativeLibraryBinary> candidates, Flavor flavor, NativePlatform platform, BuildType buildType) {
        Set<NativeLibraryBinary> matches = Sets.newLinkedHashSet();
        for (NativeLibraryBinary candidate : candidates) {
            if (flavor != null && !flavor.getName().equals(candidate.getFlavor().getName())) {
                continue;
            }
            if (platform != null && !platform.getName().equals(candidate.getTargetPlatform().getName())) {
                continue;
            }
            if (buildType != null && !buildType.getName().equals(candidate.getBuildType().getName())) {
                continue;
            }

            matches.add(candidate);
        }
        return matches;
    }


    // TODO: SG-Eventually expose this as an actual binary
    private static class ApiLibraryBinary implements NativeLibraryBinary {

        private final SharedLibraryBinary sharedLibraryBinary;
        private final FileCollectionFactory fileCollectionFactory;

        private ApiLibraryBinary(SharedLibraryBinary sharedLibraryBinary, FileCollectionFactory fileCollectionFactory) {
            this.sharedLibraryBinary = sharedLibraryBinary;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public FileCollection getHeaderDirs() {
            return sharedLibraryBinary.getHeaderDirs();
        }

        @Override
        public FileCollection getLinkFiles() {
            return fileCollectionFactory.empty("api link files");
        }

        @Override
        public FileCollection getRuntimeFiles() {
            return fileCollectionFactory.empty("api runtime files");
        }

        @Override
        public String getDisplayName() {
            return "API " + sharedLibraryBinary.getDisplayName();
        }

        @Override
        public Flavor getFlavor() {
            return sharedLibraryBinary.getFlavor();
        }

        @Override
        public NativePlatform getTargetPlatform() {
            return sharedLibraryBinary.getTargetPlatform();
        }

        @Override
        public BuildType getBuildType() {
            return sharedLibraryBinary.getBuildType();
        }
    }
}
