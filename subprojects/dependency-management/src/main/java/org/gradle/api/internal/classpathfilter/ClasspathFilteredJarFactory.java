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

package org.gradle.api.internal.classpathfilter;

import org.gradle.api.Action;
import org.gradle.api.internal.cache.GeneratedGradleJarCache;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarType;
import org.gradle.configuration.ImportsReader;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;

public class ClasspathFilteredJarFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeShadedJarFactory.class);

    private final GeneratedGradleJarCache cache;
    private final ProgressLoggerFactory progressLoggerFactory;

    public ClasspathFilteredJarFactory(GeneratedGradleJarCache cache, ProgressLoggerFactory progressLoggerFactory) {
        this.cache = cache;
        this.progressLoggerFactory = progressLoggerFactory;
    }

    public File get(final RuntimeShadedJarType type, final Collection<? extends File> classpath, final ClasspathFilter classpathFilter) {
        final File jarFile = cache.get(type.getIdentifier(), new Action<File>() {
            @Override
            public void execute(File file) {
                ClasspathFilteredJarCreator creator = new ClasspathFilteredJarCreator(progressLoggerFactory, classpathFilter);
                creator.create(file, classpath);
            }
        });
        LOGGER.debug("Using Gradle runtime shaded JAR file: {}", jarFile);
        return jarFile;
    }
}
