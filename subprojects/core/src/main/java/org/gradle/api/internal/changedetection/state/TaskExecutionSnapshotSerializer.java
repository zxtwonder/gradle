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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.Map;

class TaskExecutionSnapshotSerializer implements Serializer<TaskExecutionSnapshot> {
    private final InputPropertiesSerializer inputPropertiesSerializer;
    private final StringInterner stringInterner;

    TaskExecutionSnapshotSerializer(ClassLoader classLoader, StringInterner stringInterner) {
        this.inputPropertiesSerializer = new InputPropertiesSerializer(classLoader);
        this.stringInterner = stringInterner;
    }

    public TaskExecutionSnapshot read(Decoder decoder) throws Exception {
        ImmutableSortedMap<String, Long> inputFilesSnapshotIds = readSnapshotIds(decoder);
        ImmutableSortedMap<String, Long> outputFilesSnapshotIds = readSnapshotIds(decoder);
        Long discoveredFilesSnapshotId = decoder.readLong();
        String taskClass = decoder.readString();
        HashCode taskClassLoaderHash = null;
        if (decoder.readBoolean()) {
            taskClassLoaderHash = HashCode.fromBytes(decoder.readBinary());
        }
        HashCode taskActionsClassLoaderHash = null;
        if (decoder.readBoolean()) {
            taskActionsClassLoaderHash = HashCode.fromBytes(decoder.readBinary());
        }

        int cacheableOutputPropertiesCount = decoder.readSmallInt();
        ImmutableSet.Builder<String> cacheableOutputPropertiesBuilder = ImmutableSet.builder();
        for (int j = 0; j < cacheableOutputPropertiesCount; j++) {
            cacheableOutputPropertiesBuilder.add(decoder.readString());
        }
        ImmutableSet<String> cacheableOutputProperties = cacheableOutputPropertiesBuilder.build();

        int outputFilesCount = decoder.readSmallInt();
        ImmutableSet.Builder<String> declaredOutputFilePathsBuilder = ImmutableSet.builder();
        for (int j = 0; j < outputFilesCount; j++) {
            declaredOutputFilePathsBuilder.add(stringInterner.intern(decoder.readString()));
        }
        ImmutableSet<String> declaredOutputFilePaths = declaredOutputFilePathsBuilder.build();

        boolean hasInputProperties = decoder.readBoolean();
        Map<String, Object> inputProperties;
        if (hasInputProperties) {
            inputProperties = inputPropertiesSerializer.read(decoder);
        } else {
            inputProperties = ImmutableMap.of();
        }
        return new TaskExecutionSnapshot(
            taskClass,
            cacheableOutputProperties,
            declaredOutputFilePaths,
            taskClassLoaderHash,
            taskActionsClassLoaderHash,
            inputProperties,
            inputFilesSnapshotIds,
            discoveredFilesSnapshotId,
            outputFilesSnapshotIds
        );
    }

    public void write(Encoder encoder, TaskExecutionSnapshot execution) throws Exception {
        writeSnapshotIds(encoder, execution.getInputFilesSnapshotIds());
        writeSnapshotIds(encoder, execution.getOutputFilesSnapshotIds());
        encoder.writeLong(execution.getDiscoveredFilesSnapshotId());
        encoder.writeString(execution.getTaskClass());
        HashCode classLoaderHash = execution.getTaskClassLoaderHash();
        if (classLoaderHash == null) {
            encoder.writeBoolean(false);
        } else {
            encoder.writeBoolean(true);
            encoder.writeBinary(classLoaderHash.asBytes());
        }
        HashCode actionsClassLoaderHash = execution.getTaskActionsClassLoaderHash();
        if (actionsClassLoaderHash == null) {
            encoder.writeBoolean(false);
        } else {
            encoder.writeBoolean(true);
            encoder.writeBinary(actionsClassLoaderHash.asBytes());
        }
        encoder.writeSmallInt(execution.getCacheableOutputProperties().size());
        for (String outputFile : execution.getCacheableOutputProperties()) {
            encoder.writeString(outputFile);
        }
        encoder.writeSmallInt(execution.getDeclaredOutputFilePaths().size());
        for (String outputFile : execution.getDeclaredOutputFilePaths()) {
            encoder.writeString(outputFile);
        }
        if (execution.getInputProperties() == null || execution.getInputProperties().isEmpty()) {
            encoder.writeBoolean(false);
        } else {
            encoder.writeBoolean(true);
            inputPropertiesSerializer.write(encoder, execution.getInputProperties());
        }
    }

    private static ImmutableSortedMap<String, Long> readSnapshotIds(Decoder decoder) throws IOException {
        int count = decoder.readSmallInt();
        ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
        for (int snapshotIdx = 0; snapshotIdx < count; snapshotIdx++) {
            String property = decoder.readString();
            long id = decoder.readLong();
            builder.put(property, id);
        }
        return builder.build();
    }

    private static void writeSnapshotIds(Encoder encoder, Map<String, Long> ids) throws IOException {
        encoder.writeSmallInt(ids.size());
        for (Map.Entry<String, Long> entry : ids.entrySet()) {
            encoder.writeString(entry.getKey());
            encoder.writeLong(entry.getValue());
        }
    }
}
