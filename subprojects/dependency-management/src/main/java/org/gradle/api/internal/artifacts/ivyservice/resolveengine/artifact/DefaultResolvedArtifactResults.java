/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.specs.Spec;

import java.util.BitSet;
import java.util.Collection;
import java.util.Set;

import static com.google.common.collect.Sets.newLinkedHashSet;

public class DefaultResolvedArtifactResults implements VisitedArtifactsResults {
    private final ArtifactSet[] artifactsById;
    private final BitSet buildableArtifacts;

    public DefaultResolvedArtifactResults(ArtifactSet[] artifactsById, BitSet buildableArtifacts) {
        this.artifactsById = artifactsById;
        this.buildableArtifacts = buildableArtifacts;
    }

    @Override
    public SelectedArtifactResults select(Spec<? super ComponentIdentifier> componentFilter, Transformer<HasAttributes, Collection<? extends HasAttributes>> selector) {
        Set<ResolvedArtifactSet> allArtifactSets = newLinkedHashSet();
        final ResolvedArtifactSet[] resolvedArtifactsById = new ResolvedArtifactSet[artifactsById.length];

        for (int i = 0; i < artifactsById.length; i++) {
            ArtifactSet artifactSet = artifactsById[i];
            if (artifactSet != null) {
                if (!componentFilter.isSatisfiedBy(artifactSet.getComponentIdentifier())) {
                    continue;
                }
                Set<? extends ResolvedVariant> variants = artifactSet.getVariants();
                ResolvedVariant selected = (ResolvedVariant) selector.transform(variants);
                ResolvedArtifactSet resolvedArtifacts;
                if (selected == null) {
                    resolvedArtifacts = ResolvedArtifactSet.EMPTY;
                } else {
                    resolvedArtifacts = selected.getArtifacts();
                    if (!buildableArtifacts.get(artifactSet.getId())) {
                        resolvedArtifacts = NoBuildDependenciesArtifactSet.of(resolvedArtifacts);
                    }
                    allArtifactSets.add(resolvedArtifacts);
                }
                resolvedArtifactsById[i] = resolvedArtifacts;
            }
        }

        return new DefaultSelectedArtifactResults(CompositeArtifactSet.of(allArtifactSets), resolvedArtifactsById);
    }

    private static class DefaultSelectedArtifactResults implements SelectedArtifactResults {
        private final ResolvedArtifactSet allArtifacts;
        private final ResolvedArtifactSet[] resolvedArtifactsById;

        DefaultSelectedArtifactResults(ResolvedArtifactSet allArtifacts, ResolvedArtifactSet[] resolvedArtifactsById) {
            this.allArtifacts = allArtifacts;
            this.resolvedArtifactsById = resolvedArtifactsById;
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return allArtifacts;
        }

        @Override
        public ResolvedArtifactSet getArtifacts(int id) {
            return resolvedArtifactsById[id];
        }
    }
}
