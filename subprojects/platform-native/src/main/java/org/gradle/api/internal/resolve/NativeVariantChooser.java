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

    public NativeVariantChooser(Flavor flavor, NativePlatform platform, BuildType buildType) {
        this.flavor = flavor;
        this.platform = platform;
        this.buildType = buildType;
    }

    @Override
    public Collection<? extends Binary> selectVariants(VariantComponent componentSpec, @Nullable String linkage) {
        Class<? extends NativeLibraryBinary> type = getTypeForLinkage(linkage);
        Collection<NativeLibraryBinary> candidateBinaries = Lists.newArrayList();
        for (Binary binary : componentSpec.getVariants()) {
            if (type.isInstance(binary)) {
                candidateBinaries.add(type.cast(binary));
            }
        }
        return resolve(candidateBinaries, flavor, platform, buildType);
    }

    // TODO:DAZ Needs to handle Prebuilt libraries too (with their different type hierarchy...)
    private Class<? extends NativeLibraryBinary> getTypeForLinkage(String linkage) {
        if ("static".equals(linkage)) {
            return StaticLibraryBinary.class;
        }
        if ("shared".equals(linkage) || linkage == null) {
            return SharedLibraryBinary.class;
        }
        if ("api".equals(linkage)) {
            return SharedLibraryBinary.class; // TODO: SG Not correct
        }
        throw new InvalidUserDataException("Not a valid linkage: " + linkage);
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

}
