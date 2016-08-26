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

import com.google.common.collect.Maps;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.MissingLocalArtifactMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetadata;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.platform.base.Binary;
import org.gradle.platform.base.DependencySpec;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetadata.newResolvedLibraryMetadata;

public class NativeLocalLibraryMetaDataAdapter implements LocalLibraryMetaDataAdapter {

    public static final String COMPILE = "compile";
    public static final String LINK = "link";
    public static final String RUN = "run";

    @Override
    public LocalComponentMetadata createLocalComponentMetaData(Binary selectedBinary, String projectPath, boolean toAssembly) {

        if (selectedBinary instanceof NativeLibraryBinary) {
            return createForNativeLibrary((NativeLibraryBinary) selectedBinary, projectPath);
        }
        throw new RuntimeException("Can't create metadata for binary: " + selectedBinary);
    }

    private static LocalComponentMetadata createForNativeLibrary(NativeLibraryBinary library, String projectPath) {
        LibraryBinaryIdentifier id = library.getId();
        String displayName = library.getDisplayName();
        DefaultLibraryLocalComponentMetadata metadata = createComponentMetadata(id, library, projectPath);

        addArtifactsToConfiguration(metadata, COMPILE, library.getHeaderDirs(), new FileToArtifactTransformer("header", id, displayName));
        addArtifactsToConfiguration(metadata, LINK, library.getLinkFiles(), new FileToArtifactTransformer("link-file", id, displayName));
        addArtifactsToConfiguration(metadata, RUN, library.getRuntimeFiles(), new FileToArtifactTransformer("runtime-file", id, displayName));

        return metadata;
    }

    private static void addArtifactsToConfiguration(DefaultLibraryLocalComponentMetadata metadata, String configurationName, FileCollection files, Transformer<LocalComponentArtifactMetadata, File> converter) {
        for (File file : files) {
            metadata.addArtifact(configurationName, converter.transform(file));
        }
    }

    private static class FileToArtifactTransformer implements Transformer<LocalComponentArtifactMetadata, File> {
        private final String type;
        private final LibraryBinaryIdentifier id;
        private final String displayName;

        private FileToArtifactTransformer(String type, LibraryBinaryIdentifier id, String displayName) {
            this.type = type;
            this.id = id;
            this.displayName = displayName;
        }

        @Override
        public LocalComponentArtifactMetadata transform(File file) {
            if (file != null) {
                PublishArtifact artifact = new LibraryPublishArtifact(type, file);
                return new PublishArtifactLocalArtifactMetadata(id, displayName, artifact);
            } else {
                return new MissingLocalArtifactMetadata(id, displayName, new DefaultIvyArtifactName(displayName, type, null));
            }
        }
    }

    private static DefaultLibraryLocalComponentMetadata createComponentMetadata(LibraryBinaryIdentifier id, NativeLibraryBinary library, String projectPath) {
        // TODO:DAZ Should wire task dependencies to artifacts, not configurations.
        Map<String, TaskDependency> configurations = Maps.newLinkedHashMap();
        configurations.put(COMPILE, new DefaultTaskDependency().add(library.getHeaderDirs()));
        configurations.put(LINK, new DefaultTaskDependency().add(library.getLinkFiles()));
        configurations.put(RUN, new DefaultTaskDependency().add(library.getRuntimeFiles()));

        // TODO:DAZ For transitive dependency resolution, include dependencies from lib
        Map<String, Iterable<DependencySpec>> dependencies;
        dependencies = Collections.emptyMap();
        return newResolvedLibraryMetadata(id, configurations, dependencies, projectPath);
    }
}
