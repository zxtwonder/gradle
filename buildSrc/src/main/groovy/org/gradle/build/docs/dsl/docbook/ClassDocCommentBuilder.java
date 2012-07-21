/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.build.docs.dsl.docbook;

import org.gradle.build.docs.dsl.docbook.model.ClassDoc;

public class ClassDocCommentBuilder {
    private final JavadocConverter javadocConverter;
    private final GenerationListener listener;

    public ClassDocCommentBuilder(JavadocConverter javadocConverter, GenerationListener listener) {
        this.javadocConverter = javadocConverter;
        this.listener = listener;
    }

    void build(ClassDoc classDoc) {
        classDoc.setComment(javadocConverter.parse(classDoc.getClassMetaData(), listener).getDocbook());
    }
}
