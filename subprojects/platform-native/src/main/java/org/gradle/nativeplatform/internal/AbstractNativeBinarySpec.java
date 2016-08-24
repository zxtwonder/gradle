/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.resolve.NativeLocalLibraryMetaDataAdapter;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.language.nativeplatform.internal.DependentSourceSetInternal;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.PreprocessingTool;
import org.gradle.nativeplatform.Tool;
import org.gradle.nativeplatform.internal.resolve.NativeBinaryResolveResult;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.ToolSearchBuildAbility;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractNativeBinarySpec extends BaseBinarySpec implements NativeBinarySpecInternal {
    private final Set<? super Object> libs = new LinkedHashSet<Object>();
    private final Tool linker = new DefaultTool();
    private final Tool staticLibArchiver = new DefaultTool();

    // TODO:HH Use managed views for this, only applied when the respective language is applied
    private final Tool assembler = new DefaultTool();
    private final PreprocessingTool cCompiler = new DefaultPreprocessingTool();
    private final PreprocessingTool cppCompiler = new DefaultPreprocessingTool();
    private final PreprocessingTool objcCompiler = new DefaultPreprocessingTool();
    private final PreprocessingTool objcppCompiler = new DefaultPreprocessingTool();
    private final PreprocessingTool rcCompiler = new DefaultPreprocessingTool();
    private final Map<String, Tool> toolsByName = ImmutableMap.<String, Tool>builder()
            .put("assembler", assembler)
            .put("cCompiler", cCompiler)
            .put("cppCompiler", cppCompiler)
            .put("objcCompiler", objcCompiler)
            .put("objcppCompiler", objcppCompiler)
            .put("rcCompiler", rcCompiler)
            .build();

    private PlatformToolProvider toolProvider;
    private Flavor flavor;
    private NativeToolChain toolChain;
    private NativePlatform targetPlatform;
    private BuildType buildType;
    private NativeDependencyResolver resolver;
    private Map<File, PreCompiledHeader> prefixFileToPCH = Maps.newHashMap();
    private FileCollectionFactory fileCollectionFactory;

    @Override
    public String getDisplayName() {
        return getNamingScheme().getDescription();
    }

    @Override
    public NativeComponentSpec getComponent() {
        return getComponentAs(NativeComponentSpec.class);
    }

    @Override
    public Flavor getFlavor() {
        return flavor;
    }

    @Override
    public void setFlavor(Flavor flavor) {
        this.flavor = flavor;
    }

    @Override
    public NativeToolChain getToolChain() {
        return toolChain;
    }

    @Override
    public void setToolChain(NativeToolChain toolChain) {
        this.toolChain = toolChain;
    }

    @Override
    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    @Override
    public void setTargetPlatform(NativePlatform targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    @Override
    public BuildType getBuildType() {
        return buildType;
    }

    @Override
    public void setBuildType(BuildType buildType) {
        this.buildType = buildType;
    }

    @Override
    public Tool getLinker() {
        return linker;
    }

    @Override
    public Tool getStaticLibArchiver() {
        return staticLibArchiver;
    }

    @Override
    public Tool getAssembler() {
        return assembler;
    }

    @Override
    public PreprocessingTool getcCompiler() {
        return cCompiler;
    }

    @Override
    public PreprocessingTool getCppCompiler() {
        return cppCompiler;
    }

    @Override
    public PreprocessingTool getObjcCompiler() {
        return objcCompiler;
    }

    @Override
    public PreprocessingTool getObjcppCompiler() {
        return objcppCompiler;
    }

    @Override
    public PreprocessingTool getRcCompiler() {
        return rcCompiler;
    }

    @Override
    public Tool getToolByName(String name) {
        return toolsByName.get(name);
    }

    @Override
    public Collection<NativeDependencySet> getLibs() {
        return resolve(getAllLibs(getAllDependentSourceSets())).getAllResults();
    }

    @Override
    public Collection<NativeDependencySet> getLibs(DependentSourceSet sourceSet) {
        return resolve(getAllLibs(Collections.singleton(sourceSet))).getAllResults();
    }

    @Override
    public void lib(Object notation) {
        libs.add(notation);
    }

    @Override
    public Collection<NativeLibraryBinary> getDependentBinaries() {
        return resolve(getAllLibs(getAllDependentSourceSets())).getAllLibraryBinaries();
    }

    @Override
    public Map<File, PreCompiledHeader> getPrefixFileToPCH() {
        return prefixFileToPCH;
    }

    @Override
    public void addPreCompiledHeaderFor(DependentSourceSet sourceSet) {
        prefixFileToPCH.put(
            ((DependentSourceSetInternal)sourceSet).getPrefixHeaderFile(),
            new PreCompiledHeader(getIdentifier().child("pch")));
    }

    private NativeBinaryResolveResult resolve(Set<? super Object> allLibs) {
        NativeBinaryResolveResult resolution = new NativeBinaryResolveResult(this, allLibs);
        resolver.resolve(resolution);
        return resolution;
    }

    private DomainObjectSet<DependentSourceSet> getAllDependentSourceSets() {
        return getInputs().withType(DependentSourceSet.class);
    }

    private Set<? super Object> getAllLibs(Iterable<? extends DependentSourceSet> sourceSets) {
        Set<? super Object> allLibs = new LinkedHashSet<Object>(libs);
        for (DependentSourceSet dependentSourceSet : sourceSets) {
            allLibs.addAll(dependentSourceSet.getLibs());
        }
        return allLibs;
    }

    @Override
    public PlatformToolProvider getPlatformToolProvider() {
        return toolProvider;
    }

    @Override
    public void setPlatformToolProvider(PlatformToolProvider toolProvider) {
        this.toolProvider = toolProvider;
    }

    @Override
    public void setResolver(NativeDependencyResolver resolver) {
        this.resolver = resolver;
    }

    protected FileCollectionFactory getFileCollectionFactory() {
        return fileCollectionFactory;
    }

    @Override
    public void setFileCollectionFactory(FileCollectionFactory fileCollectionFactory) {
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    protected BinaryBuildAbility getBinaryBuildAbility() {
        NativeToolChainInternal toolChainInternal = (NativeToolChainInternal) getToolChain();
        NativePlatformInternal platformInternal = (NativePlatformInternal) getTargetPlatform();
        return new ToolSearchBuildAbility(toolChainInternal.select(platformInternal));
    }

    @Override
    public void binaryInputs(FileCollection files) {
        // TODO - should split this up, so that the inputs are attached to an object that represents the binary, which is then later used to configure the link/assemble tasks
        getCreateOrLink().source(files);
    }

    protected abstract ObjectFilesToBinary getCreateOrLink();

    @Override
    public Map<String, Iterable<DependencySpec>> getDependencySpecs() {

        // TODO: Convert from Object into a DependencySpec
        List<DependencySpec> listOfDependencies = Lists.<DependencySpec>newArrayList();
        Set<? super Object> allLibs = getAllLibs(getAllDependentSourceSets());
        for (Object lib : allLibs) {
            // listOfDependencies.add(lib);
        }
        // TODO: SG Add all dependencies to all usage types for now
        Map<String, Iterable<DependencySpec>> dependencies = Maps.newHashMap();
        dependencies.put(NativeLocalLibraryMetaDataAdapter.COMPILE, Lists.<DependencySpec>newArrayList());
        dependencies.put(NativeLocalLibraryMetaDataAdapter.LINK, Lists.<DependencySpec>newArrayList());
        dependencies.put(NativeLocalLibraryMetaDataAdapter.RUN, Lists.<DependencySpec>newArrayList());
        return dependencies;
    }
}
