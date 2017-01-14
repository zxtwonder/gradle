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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.internal.AsyncCacheAccessContext;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.util.ArrayList;
import java.util.List;

public class TaskExecutionListSerializer implements Serializer<ImmutableList<TaskExecutionSnapshot>> {
    private static final String CONTEXT_KEY_FOR_CLASSLOADER = AsyncCacheAccessContext.createKey(TaskExecutionListSerializer.class, "classLoader");
    private final StringInterner stringInterner;

    public TaskExecutionListSerializer(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
    }

    public ImmutableList<TaskExecutionSnapshot> read(Decoder decoder) throws Exception {
        byte count = decoder.readByte();
        List<TaskExecutionSnapshot> executions = new ArrayList<TaskExecutionSnapshot>(count);
        TaskExecutionSnapshotSerializer executionSerializer = new TaskExecutionSnapshotSerializer(getClassLoader(), stringInterner);
        for (int i = 0; i < count; i++) {
            TaskExecutionSnapshot exec = executionSerializer.read(decoder);
            executions.add(exec);
        }
        return ImmutableList.copyOf(executions);
    }

    public void write(Encoder encoder, ImmutableList<TaskExecutionSnapshot> value) throws Exception {
        int size = value.size();
        encoder.writeByte((byte) size);
        TaskExecutionSnapshotSerializer executionSerializer = new TaskExecutionSnapshotSerializer(getClassLoader(), stringInterner);
        for (TaskExecutionSnapshot execution : value) {
            executionSerializer.write(encoder, execution);
        }
    }

    public ClassLoader getClassLoader() {
        AsyncCacheAccessContext context = AsyncCacheAccessContext.current();
        if (context != null) {
            return context.get(CONTEXT_KEY_FOR_CLASSLOADER, ClassLoader.class);
        } else {
            return getClass().getClassLoader();
        }
    }

    public void setClassLoader(ClassLoader classLoader) {
        AsyncCacheAccessContext context = AsyncCacheAccessContext.current();
        if (context != null) {
            context.put(CONTEXT_KEY_FOR_CLASSLOADER, classLoader);
        }
    }
}
