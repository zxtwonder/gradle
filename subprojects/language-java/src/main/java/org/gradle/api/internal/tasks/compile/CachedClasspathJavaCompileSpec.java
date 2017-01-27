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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.DefaultClassPath;

import java.io.File;
import java.io.Serializable;
import java.util.List;

@SuppressWarnings("deprecation")
public class CachedClasspathJavaCompileSpec implements JavaCompileSpec, Serializable {
    private final List<File> compileClasspath;
    private final JavaCompileSpec delegate;

    CachedClasspathJavaCompileSpec(JavaCompileSpec delegate, CachedClasspathTransformer cachedClasspathTransformer) {
        this.delegate = delegate;
        this.compileClasspath = cachedClasspathTransformer.transform(DefaultClassPath.of(delegate.getCompileClasspath())).getAsFiles();
    }

    public File getWorkingDir() {
        return delegate.getWorkingDir();
    }

    public void setWorkingDir(File workingDir) {
        delegate.setWorkingDir(workingDir);
    }

    public File getDestinationDir() {
        return delegate.getDestinationDir();
    }

    public void setDestinationDir(File destinationDir) {
        delegate.setDestinationDir(destinationDir);
    }

    public File getTempDir() {
        return delegate.getTempDir();
    }

    public void setTempDir(File tempDir) {
        delegate.setTempDir(tempDir);
    }

    public FileCollection getSource() {
        return delegate.getSource();
    }

    public void setSource(FileCollection source) {
        delegate.setSource(source);
    }

    public List<File> getCompileClasspath() {
        return compileClasspath;
    }

    public void setCompileClasspath(List<File> classpath) {
        throw new UnsupportedOperationException();
    }

    public Iterable<File> getClasspath() {
        return delegate.getClasspath();
    }

    public void setClasspath(Iterable<File> classpath) {
        delegate.setClasspath(classpath);
    }

    public String getSourceCompatibility() {
        return delegate.getSourceCompatibility();
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        delegate.setSourceCompatibility(sourceCompatibility);
    }

    public String getTargetCompatibility() {
        return delegate.getTargetCompatibility();
    }

    public void setTargetCompatibility(String targetCompatibility) {
        delegate.setTargetCompatibility(targetCompatibility);
    }

    public CompileOptions getCompileOptions() {
        return delegate.getCompileOptions();
    }

    public File getDependencyCacheDir() {
        return delegate.getDependencyCacheDir();
    }

    public void setDependencyCacheDir(File dependencyCacheDir) {
        delegate.setDependencyCacheDir(dependencyCacheDir);
    }

    public List<File> getAnnotationProcessorPath() {
        return delegate.getAnnotationProcessorPath();
    }

    public void setAnnotationProcessorPath(List<File> annotationProcessorPath) {
        delegate.setAnnotationProcessorPath(annotationProcessorPath);
    }
}
