/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */
package kotlinx.validation.build.conventions

/**
 * Convention plugin for Kotlin/JVM projects.
 *
 * This plugin should not be applied to Gradle Plugin projects, as these use Gradle's embedded Kotlin.
 */
plugins {
    id("kotlinx.validation.build.conventions.java-base")
    kotlin("jvm")
}

tasks.compileKotlin {
    kotlinOptions {
        allWarningsAsErrors = true
    }
}

// if the maven-publish plugin is present, register a publication
plugins.withType<MavenPublishPlugin>().configureEach {
    extensions.configure<PublishingExtension> {
        publications.create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
