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

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.installation.GradleRuntimeShadedJarDetector;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.progress.PercentageProgressFormatter;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ClasspathFilteredJarCreator {

    public static final int ADDITIONAL_PROGRESS_STEPS = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathFilteredJarCreator.class);
    private static final int BUFFER_SIZE = 8192;
    private static final String SERVICES_DIR_PREFIX = "META-INF/services/";
    private final ProgressLoggerFactory progressLoggerFactory;
    private final ClasspathFilter classpathFilter;

    public ClasspathFilteredJarCreator(ProgressLoggerFactory progressLoggerFactory, ClasspathFilter classpathFilter) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.classpathFilter = classpathFilter;
    }

    public void create(final File outputJar, final Iterable<? extends File> files) {
        LOGGER.info("Generating JAR file: " + outputJar.getAbsolutePath());
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(ClasspathFilteredJarCreator.class);
        progressLogger.setDescription("Gradle JARs generation");
        progressLogger.setLoggingHeader("Generating JAR file '" + outputJar.getName() + "'");
        progressLogger.started();

        try {
            System.out.println("CREATING FAT JAR!");
            createFatJar(outputJar, files, progressLogger);
        } finally {
            progressLogger.completed();
        }
    }

    private void createFatJar(final File outputJar, final Iterable<? extends File> files, final ProgressLogger progressLogger) {
        final File tmpFile = tempFileFor(outputJar);

        IoActions.withResource(openJarOutputStream(tmpFile), new ErroringAction<ZipOutputStream>() {
            @Override
            protected void doExecute(ZipOutputStream jarOutputStream) throws Exception {
                processFiles(jarOutputStream, files, new byte[BUFFER_SIZE], new HashSet<String>(), new LinkedHashMap<String, List<String>>(), progressLogger);
                jarOutputStream.finish();
            }
        });

        GFileUtils.moveFile(tmpFile, outputJar);
    }

    private File tempFileFor(File outputJar) {
        try {
            final File tmpFile = File.createTempFile(outputJar.getName(), ".tmp");
            tmpFile.deleteOnExit();
            return tmpFile;
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private ZipOutputStream openJarOutputStream(File outputJar) {
        try {
            ZipOutputStream outputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputJar), BUFFER_SIZE));
            outputStream.setLevel(0);
            return outputStream;
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void processFiles(ZipOutputStream outputStream, Iterable<? extends File> files, byte[] buffer, HashSet<String> seenPaths, Map<String, List<String>> services,
                              ProgressLogger progressLogger) throws Exception {
        PercentageProgressFormatter progressFormatter = new PercentageProgressFormatter("Generating", Iterables.size(files) + ADDITIONAL_PROGRESS_STEPS);

        for (File file : files) {
            progressLogger.progress(progressFormatter.getProgress());

            if (file.getName().endsWith(".jar")) {
                processJarFile(outputStream, file, buffer, seenPaths, services);
            } else {
                processDirectory(outputStream, file, buffer, seenPaths, services);
            }

            progressFormatter.increment();
        }

        writeServiceFiles(outputStream, services);
        progressLogger.progress(progressFormatter.incrementAndGetProgress());

        writeIdentifyingMarkerFile(outputStream);
        progressLogger.progress(progressFormatter.incrementAndGetProgress());
    }

    private void writeServiceFiles(ZipOutputStream outputStream, Map<String, List<String>> services) throws IOException {
        for (Map.Entry<String, List<String>> service : services.entrySet()) {
            String allProviders = Joiner.on("\n").join(service.getValue());
            writeEntry(outputStream, SERVICES_DIR_PREFIX + service.getKey(), allProviders.getBytes(Charsets.UTF_8));
        }
    }

    private void writeIdentifyingMarkerFile(ZipOutputStream outputStream) throws IOException {
        writeEntry(outputStream, GradleRuntimeShadedJarDetector.MARKER_FILENAME, new byte[0]);
    }

    private void processDirectory(final ZipOutputStream outputStream, File file, final byte[] buffer, final HashSet<String> seenPaths, final Map<String, List<String>> services) {
        final List<FileVisitDetails> fileVisitDetails = new ArrayList<FileVisitDetails>();
        new DirectoryFileTree(file).visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                fileVisitDetails.add(dirDetails);
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                fileVisitDetails.add(fileDetails);
            }
        });

        // We need to sort here since the file order obtained from the filesystem
        // can change between machines and we always want to have the same shaded jars.
        Collections.sort(fileVisitDetails, new Comparator<FileVisitDetails>() {
            @Override
            public int compare(FileVisitDetails o1, FileVisitDetails o2) {
                return o1.getPath().compareTo(o2.getPath());
            }
        });

        for (FileVisitDetails details : fileVisitDetails) {
            try {
                if (details.isDirectory()) {
                    ZipEntry zipEntry = newZipEntryWithFixedTime(details.getPath() + "/");
                    processEntry(outputStream, null, zipEntry, buffer, seenPaths, services);
                } else {
                    ZipEntry zipEntry = newZipEntryWithFixedTime(details.getPath());
                    InputStream inputStream = details.open();
                    try {
                        processEntry(outputStream, inputStream, zipEntry, buffer, seenPaths, services);
                    } finally {
                        inputStream.close();
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void processJarFile(final ZipOutputStream outputStream, File file, final byte[] buffer, final Set<String> seenPaths, final Map<String, List<String>> services) throws IOException {
        IoActions.withResource(openJarFile(file), new ErroringAction<ZipInputStream>() {
            @Override
            protected void doExecute(ZipInputStream inputStream) throws Exception {
                ZipEntry zipEntry = inputStream.getNextEntry();
                while (zipEntry != null) {
                    processEntry(outputStream, inputStream, zipEntry, buffer, seenPaths, services);
                    zipEntry = inputStream.getNextEntry();
                }
            }
        });
    }

    private void processEntry(ZipOutputStream outputStream, InputStream inputStream, ZipEntry zipEntry, byte[] buffer, final Set<String> seenPaths, Map<String, List<String>> services) throws IOException {
        String name = zipEntry.getName();
        if (zipEntry.isDirectory() || name.equals("META-INF/MANIFEST.MF")) {
            return;
        }
        // Remove license files that cause collisions between a LICENSE file and a license/ directory.
        if (name.startsWith("LICENSE") || name.startsWith("license")) {
            return;
        }
        if (!name.startsWith(SERVICES_DIR_PREFIX) && !seenPaths.add(name)) {
            return;
        }

        if (name.endsWith(".class")) {
            processClassFile(outputStream, inputStream, zipEntry, buffer);
        } else if (name.startsWith(SERVICES_DIR_PREFIX)) {
            processServiceDescriptor(inputStream, zipEntry, buffer, services);
        } else {
            copyEntry(outputStream, inputStream, zipEntry, buffer);
        }
    }

    private void processServiceDescriptor(InputStream inputStream, ZipEntry zipEntry, byte[] buffer, Map<String, List<String>> services) throws IOException {
        String descriptorName = zipEntry.getName().substring(SERVICES_DIR_PREFIX.length());
        String descriptorApiClass = periodsToSlashes(descriptorName);

        byte[] bytes = readEntry(inputStream, zipEntry, buffer);
        String entry = new String(bytes, Charsets.UTF_8).replaceAll("(?m)^#.*", "").trim(); // clean up comments and new lines
        String descriptorImplClass = periodsToSlashes(entry);

        String serviceType = slashesToPeriods(descriptorApiClass);
        String serviceProvider = slashesToPeriods(descriptorImplClass).trim();

        if (!services.containsKey(serviceType)) {
            services.put(serviceType, Lists.newArrayList(serviceProvider));
        } else {
            List<String> providers = services.get(serviceType);
            providers.add(serviceProvider);
        }
    }

    private String slashesToPeriods(String slashClassName) {
        return slashClassName.replace('/', '.');
    }

    private String periodsToSlashes(String periodClassName) {
        return periodClassName.replace('.', '/');
    }

    private void copyEntry(ZipOutputStream outputStream, InputStream inputStream, ZipEntry zipEntry, byte[] buffer) throws IOException {
        StreamByteBuffer streamByteBuffer = new StreamByteBuffer(Math.max(Math.min((int) zipEntry.getSize(), 1024 * 1024), 4096)); // min chunk size 4kB, max size 1MB
        streamByteBuffer.readFully(inputStream);
        byte[] resource = streamByteBuffer.readAsByteArray();
        writeResourceEntry(outputStream, new ByteArrayInputStream(resource), buffer, zipEntry.getName());
    }

    private void writeResourceEntry(ZipOutputStream outputStream, InputStream inputStream, byte[] buffer, String resourceFileName) throws IOException {
        outputStream.putNextEntry(newZipEntryWithFixedTime(resourceFileName));
        pipe(inputStream, outputStream, buffer);
        outputStream.closeEntry();
    }

    private void writeEntry(ZipOutputStream outputStream, String name, byte[] content) throws IOException {
        ZipEntry zipEntry = newZipEntryWithFixedTime(name);
        outputStream.putNextEntry(zipEntry);
        outputStream.write(content);
        outputStream.closeEntry();
    }

    private ZipEntry newZipEntryWithFixedTime(String name) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(GUtil.CONSTANT_TIME_FOR_ZIP_ENTRIES);
        return entry;
    }

    private void processClassFile(ZipOutputStream outputStream, InputStream inputStream, ZipEntry zipEntry, byte[] buffer) throws IOException {
        String fullyQualifiedClassName = zipEntry.getName().substring(0, zipEntry.getName().length() - ".class".length());

        if (classpathFilter.include(slashesToPeriods(fullyQualifiedClassName))) {
            String newFileName = fullyQualifiedClassName.concat(".class");
            byte[] bytes = readEntry(inputStream, zipEntry, buffer);
            writeEntry(outputStream, newFileName, bytes);
        }
    }

    private byte[] readEntry(InputStream inputStream, ZipEntry zipEntry, byte[] buffer) throws IOException {
        int size = (int) zipEntry.getSize();
        if (size == -1) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(buffer.length);
            int read = inputStream.read(buffer);
            while (read != -1) {
                out.write(buffer, 0, read);
                read = inputStream.read(buffer);
            }
            return out.toByteArray();
        } else {
            byte[] bytes = new byte[size];
            int read = inputStream.read(bytes);
            while (read < size) {
                read += inputStream.read(bytes, read, size - read);
            }
            return bytes;
        }
    }

    private void pipe(InputStream inputStream, OutputStream outputStream, byte[] buffer) throws IOException {
        int read = inputStream.read(buffer);
        while (read != -1) {
            outputStream.write(buffer, 0, read);
            read = inputStream.read(buffer);
        }
    }

    private ZipInputStream openJarFile(File file) throws IOException {
        return new ZipInputStream(new FileInputStream(file));
    }
}
