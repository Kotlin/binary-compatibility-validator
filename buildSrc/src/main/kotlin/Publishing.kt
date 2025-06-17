/*
 * Copyright 2016-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.build

import org.gradle.api.*
import org.gradle.api.publish.*
import org.gradle.api.publish.maven.*
import org.gradle.plugins.signing.*
import java.net.*

// Artifacts are published to an intermediate repo (libs.repo.url) first,
// and then deployed to the central portal.
fun PublishingExtension.publishingRepository(project: Project) {
    repositories {
        maven {
            url = URI(project.getSensitiveProperty("libs.repo.url") ?: error("libs.repo.url is not set"))
            credentials {
                username = project.getSensitiveProperty("libs.repo.user")
                password = project.getSensitiveProperty("libs.repo.password")
            }
        }
    }
}

fun Project.signPublicationIfKeyPresent(publication: MavenPublication) {
    val keyId = project.getSensitiveProperty("libs.sign.key.id")
    val signingKey = project.getSensitiveProperty("libs.sign.key.private")
    val signingKeyPassphrase = project.getSensitiveProperty("libs.sign.passphrase")
    if (!signingKey.isNullOrBlank()) {
        extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys(keyId, signingKey, signingKeyPassphrase)
            sign(publication)
        }
    }
}

fun Project.getSensitiveProperty(name: String): String? {
    return project.findProperty(name) as? String ?: System.getenv(name)
}
