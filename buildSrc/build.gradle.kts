/*
 * Copyright 2016-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

val props = Properties().apply {
    project.file("../gradle.properties").inputStream().use { load(it) }
}

val kotlinVersion: String = props.getProperty("kotlinVersion")

dependencies {
    implementation(kotlin("gradle-plugin-api", kotlinVersion))
}

sourceSets {
    configureEach {
        when (name) {
            SourceSet.MAIN_SOURCE_SET_NAME -> {
                kotlin.setSrcDirs(listOf("src"))
                resources.setSrcDirs(listOf("resources"))
            }

            else -> {
                kotlin.setSrcDirs(emptyList<String>())
                resources.setSrcDirs(emptyList<String>())
            }
        }
        java.setSrcDirs(emptyList<String>())
        groovy.setSrcDirs(emptyList<String>())
    }
}


tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        allWarningsAsErrors = true
    }
}
