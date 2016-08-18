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
package org.gradle.nativeplatform.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp

class NativeDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    def "can resolve"() {
        given:
        // Need sources to ensure that the library has outputs
        def app = new CHelloWorldApp()
        app.library.writeSources(file("native-lib/src/hello"))

        and:
        settingsFile.text = "include ':native-lib'"
        buildFile << """
project(':native-lib') {
    apply plugin: 'base'
    apply plugin: 'c'

    model {
        components {
            hello(NativeLibrarySpec)
        }
    }
}


task resolve(type: NativeDependencyResolveTask) {
    targetProject = ':native-lib'
    targetComponent = 'hello'
    targetVariant = 'sharedLibrary'
    usage = 'compile'
}

import org.gradle.api.internal.resolve.NativeDependencyResolver
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import javax.inject.Inject;

public class NativeDependencyResolveTask extends DefaultTask {
    private final NativeDependencyResolver resolver;

    @Inject
    public NativeDependencyResolveTask(ArtifactDependencyResolver resolver) {
        this.resolver = new NativeDependencyResolver(resolver);
    }

    @Input
    public String targetProject;

    @Input
    public String targetComponent;

    @Input @Optional
    public String targetVariant;

    @Input String usage;

    @TaskAction
    public void resolve() {
        Set<File> files = resolver.resolveFiles(targetProject, targetComponent, targetVariant, usage);
        for (File file : files) {
            System.out.println("Resolved file: \${file.name} [\${file.absolutePath}]");
        }
    }
}

"""

        expect:
        succeeds ":resolve"
    }
}
