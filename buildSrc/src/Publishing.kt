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

fun PublishingExtension.bintrayRepositoryPublishing(project: Project, user: String, repo: String, name: String) {
    repositories {
        maven {
            url = URI("https://api.bintray.com/maven/$user/$repo/$name/;publish=0")
            credentials {
                username = project.findProperty("bintrayUser") as? String ?: System.getenv("BINTRAY_USER")
                password = project.findProperty("bintrayApiKey") as? String ?: System.getenv("BINTRAY_API_KEY")
            }
        }
    }
}

fun PublishingExtension.mavenRepositoryPublishing() {
    repositories {
        maven {
            url = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("libs.sonatype.user")
                password = System.getenv("libs.sonatype.password")
            }
        }
    }
}

fun Project.signPublicationIfKeyPresent(publication: MavenPublication) {
    val keyId = System.getenv("libs.sign.key.id")
    val signingKey = System.getenv("libs.sign.key.private")
    val signingKeyPassphrase = System.getenv("libs.sign.passphrase")
    if (!signingKey.isNullOrBlank()) {
        extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys(keyId, signingKey, signingKeyPassphrase)
            sign(publication)
        }
    }
}
