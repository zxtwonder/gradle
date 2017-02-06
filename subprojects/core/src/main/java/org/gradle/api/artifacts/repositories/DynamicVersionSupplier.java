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
package org.gradle.api.artifacts.repositories;

import org.gradle.api.artifacts.ModuleVersionSelector;

/**
 * A dynamic version supplier is responsible for supplying a list of versions for a given
 * dependency, represented as (group, name, version). If it can provide a version (or a list
 * of versions), then it is expected to call {@link VersionListingBuilder#listed(String)}. It
 * can be used to implement custom resolution rules (for a version token) or to make dependency
 * resolution faster when an artifact repository doesn't support listing.
 *
 * @since 4.0
 */
public interface DynamicVersionSupplier {
    /**
     * Supply, if possible, one or more version number for a version selector. The selector version
     * typically has a dynamic version number such as <i>latest.release</i> or <i>latest.integration</i>.
     * @param selector the dependency selector
     * @param resultBuilder the builder to be called whenever a version is supplied
     */
    void supply(ModuleVersionSelector selector, VersionListingBuilder resultBuilder);
}
