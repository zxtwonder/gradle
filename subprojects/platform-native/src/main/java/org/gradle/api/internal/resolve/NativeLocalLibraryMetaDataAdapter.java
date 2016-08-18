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

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetadata;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.platform.base.Binary;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.DependencySpec;

import java.util.Collections;
import java.util.Map;

import static org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetadata.newResolvedLibraryMetadata;

public class NativeLocalLibraryMetaDataAdapter implements LocalLibraryMetaDataAdapter {
    @Override
    public LocalComponentMetadata createLocalComponentMetaData(Binary selectedBinary, String projectPath, boolean toAssembly) {
        if (selectedBinary instanceof StaticLibraryBinarySpec) {
            StaticLibraryBinarySpec binarySpec = (StaticLibraryBinarySpec) selectedBinary;
            return createForStaticLibrary(binarySpec);
        }

        if (selectedBinary instanceof SharedLibraryBinarySpec) {
            SharedLibraryBinarySpec binarySpec = (SharedLibraryBinarySpec) selectedBinary;
            return createForSharedLibrary(binarySpec);
        }
        throw new RuntimeException("Can't create metadata for binary: " + selectedBinary);
    }

    private static LocalComponentMetadata createForStaticLibrary(StaticLibraryBinarySpec staticLib) {
        String projectPath = staticLib.getProjectPath();
        ProjectComponentIdentifier componentIdentifier = DefaultProjectComponentIdentifier.newId(projectPath);
        LibraryBinaryIdentifier id = new DefaultLibraryBinaryIdentifier(projectPath, staticLib.getLibrary().getName(), "staticLibrary");
        Map<String, TaskDependency> configurations = Collections.singletonMap("default", (TaskDependency) new DefaultTaskDependency());
        DefaultLibraryLocalComponentMetadata metadata = newResolvedLibraryMetadata(id, configurations, Collections.<String, Iterable<DependencySpec>>emptyMap(), projectPath);

        PublishArtifact art = new LibraryPublishArtifact("staticLibrary", staticLib.getStaticLibraryFile());
        metadata.addArtifact("default", new PublishArtifactLocalArtifactMetadata(componentIdentifier, staticLib.getDisplayName(), art));
        return metadata;
    }

    private static LocalComponentMetadata createForSharedLibrary(SharedLibraryBinarySpec staticLib) {
        String projectPath = staticLib.getProjectPath();
        ProjectComponentIdentifier componentIdentifier = DefaultProjectComponentIdentifier.newId(projectPath);
        LibraryBinaryIdentifier id = new DefaultLibraryBinaryIdentifier(projectPath, staticLib.getLibrary().getName(), "sharedLibrary");
        Map<String, TaskDependency> configurations = Collections.singletonMap("default", (TaskDependency) new DefaultTaskDependency());
        DefaultLibraryLocalComponentMetadata metadata = newResolvedLibraryMetadata(id, configurations, Collections.<String, Iterable<DependencySpec>>emptyMap(), projectPath);

        PublishArtifact art = new LibraryPublishArtifact("sharedLibrary", staticLib.getSharedLibraryFile());
        metadata.addArtifact("default", new PublishArtifactLocalArtifactMetadata(componentIdentifier, staticLib.getDisplayName(), art));
        return metadata;
    }
}
