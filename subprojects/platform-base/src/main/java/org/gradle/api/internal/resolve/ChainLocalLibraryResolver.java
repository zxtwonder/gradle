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

import org.gradle.api.Action;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.VariantComponent;
import org.gradle.util.CollectionUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

public class ChainLocalLibraryResolver implements LocalLibraryResolver {
    private final Collection<LocalLibraryResolver> resolvers;

    public ChainLocalLibraryResolver(LocalLibraryResolver... resolvers) {
        this.resolvers = Arrays.asList(resolvers);
    }

    @Override
    public Collection<VariantComponent> resolveCandidates(final ModelRegistry projectModel, final String libraryName) {
        return CollectionUtils.inject(new LinkedList<VariantComponent>(), resolvers, new Action<CollectionUtils.InjectionStep<LinkedList<VariantComponent>, LocalLibraryResolver>>() {
            @Override
            public void execute(CollectionUtils.InjectionStep<LinkedList<VariantComponent>, LocalLibraryResolver> step) {
                step.getTarget().addAll(step.getItem().resolveCandidates(projectModel, libraryName));
            }
        });
    }
}
