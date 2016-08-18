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
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetadata;
import org.gradle.platform.base.Binary;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.LibraryBinarySpec;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetadata.newResolvedLibraryMetadata;

public class NativeLocalLibraryMetaDataAdapter implements LocalLibraryMetaDataAdapter {
    @Override
    public LocalComponentMetadata createLocalComponentMetaData(Binary selectedBinary, String projectPath, boolean toAssembly) {
        if (selectedBinary instanceof NativeLibraryBinarySpec) {
            return createForNativeLibrary((NativeLibraryBinarySpec) selectedBinary);
        }
        throw new RuntimeException("Can't create metadata for binary: " + selectedBinary);
    }

    private static LocalComponentMetadata createForNativeLibrary(NativeLibraryBinarySpec sharedLib) {
        LibraryBinaryIdentifier id = createComponentId(sharedLib);
        DefaultLibraryLocalComponentMetadata metadata = createComponentMetadata(id);

        // TODO:DAZ Don't use PublishArtifact and PublishArtifactLocalArtifactMetadata here
        NativeLibraryBinary libraryBinary = (NativeLibraryBinary) sharedLib;
        for (File headerDir : libraryBinary.getHeaderDirs()) {
            PublishArtifact headerDirArtifact = new LibraryPublishArtifact("header", headerDir);
            metadata.addArtifact("compile", new PublishArtifactLocalArtifactMetadata(id, sharedLib.getDisplayName(), headerDirArtifact));
        }

        for (File linkFile : libraryBinary.getLinkFiles()) {
            PublishArtifact linkFileArtifact = new LibraryPublishArtifact("link-file", linkFile);
            metadata.addArtifact("link", new PublishArtifactLocalArtifactMetadata(id, sharedLib.getDisplayName(), linkFileArtifact));
        }

        for (File runtimeFile : libraryBinary.getRuntimeFiles()) {
            PublishArtifact runtimeFileArtifact = new LibraryPublishArtifact("runtime-file", runtimeFile);
            metadata.addArtifact("run", new PublishArtifactLocalArtifactMetadata(id, sharedLib.getDisplayName(), runtimeFileArtifact));
        }

        return metadata;
    }

    private static LibraryBinaryIdentifier createComponentId(LibraryBinarySpec staticLib) {
        String projectPath = staticLib.getProjectPath();
        return new DefaultLibraryBinaryIdentifier(projectPath, staticLib.getLibrary().getName(), "staticLibrary");
    }

    private static DefaultLibraryLocalComponentMetadata createComponentMetadata(LibraryBinaryIdentifier id) {
        List<String> usages = Lists.newArrayList("compile", "link", "run");
        Map<String, TaskDependency> configurations = Maps.newLinkedHashMap();
        for (String usage : usages) {
            configurations.put(usage, new DefaultTaskDependency());
        }
        return newResolvedLibraryMetadata(id, configurations, Collections.<String, Iterable<DependencySpec>>emptyMap(), null);
    }
}
