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

fun PublishingExtension.mavenRepositoryPublishing(project: Project) {
    val isSnapshot = project.isSnapshotRelease()
    repositories {
        maven {
            url = mavenRepositoryUri(isSnapshot)
            credentials {
                if (isSnapshot) {
                    username = project.getSensitiveProperty("libs.space.user")
                    password = project.getSensitiveProperty("libs.space.password")
                } else {
                    username = project.getSensitiveProperty("libs.sonatype.user")
                    password = project.getSensitiveProperty("libs.sonatype.password")
                }
            }
        }
    }
}

private fun Project.isSnapshotRelease(): Boolean {
    return version.toString().endsWith("-SNAPSHOT")
}

private fun mavenRepositoryUri(snapshot: Boolean = false): URI {
    if (snapshot) {
        return URI("https://maven.pkg.jetbrains.space/kotlin/p/kotlinx/dev")
    }
    val repositoryId: String? = System.getenv("libs.repository.id")
    return if (repositoryId == null) {
        URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
    } else {
        URI("https://oss.sonatype.org/service/local/staging/deployByRepositoryId/$repositoryId")
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
