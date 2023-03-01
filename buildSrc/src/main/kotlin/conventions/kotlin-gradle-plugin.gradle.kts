/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */
package kotlinx.validation.build.conventions

import gradle.kotlin.dsl.accessors._76a3dc6a934da9cff619831d662d70a5.compileKotlin
import org.gradle.kotlin.dsl.invoke

/**
 * Convention plugin for Gradle Plugin projects.
 */
plugins {
    id("kotlinx.validation.build.conventions.java-base")
    //id("org.gradle.kotlin.kotlin-dsl") // TODO remove 'embedded-kotlin', add 'kotlin-dsl'
    id("org.gradle.kotlin.embedded-kotlin")
    id("com.gradle.plugin-publish")
}

tasks.compileKotlin {
    kotlinOptions {
        allWarningsAsErrors = true
    }
}

tasks.validatePlugins {
    //enableStricterValidation.set(true) // TODO enable validation
}
