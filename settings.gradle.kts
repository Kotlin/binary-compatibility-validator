/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */
import org.gradle.api.initialization.resolve.RepositoriesMode.PREFER_SETTINGS

rootProject.name = "binary-compatibility-validator"


pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    resolutionStrategy {
        val kotlinVersion: String by settings
        val pluginPublishVersion: String by settings
        eachPlugin {
            if (requested.id.namespace?.startsWith("org.jetbrains.kotlin") == true) {
                useVersion(kotlinVersion)
            }
            if (requested.id.id == "com.gradle.plugin-publish") {
                useVersion(pluginPublishVersion)
            }
        }
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {

    repositoriesMode.set(PREFER_SETTINGS)

    repositories {
        mavenCentral()
        google()
    }
}
