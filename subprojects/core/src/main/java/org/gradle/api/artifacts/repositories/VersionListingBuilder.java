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

import java.util.Collection;

/**
 * Interface for a builder of version listing.
 */
public interface VersionListingBuilder {
    /**
     * Lists a single version, meaning that for a requested version, a single version matches.
     * @param singleVersion the concrete version number corresponding to the searched version
     */
    void listed(String singleVersion);

    /**
     * Lists several versions, meaning that for a requested version, more than one versions are candidates.
     * @param versions the list of candidate versions
     */
    void listed(Collection<String> versions);
}
