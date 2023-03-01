/*
 * Copyright 2016-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */
package kotlinx.validation.build.conventions

plugins {
    `maven-publish`
    signing
}

val signingKey = project.getSensitiveProperty("libs.sign.key.private")
val signingKeyId = project.getSensitiveProperty("libs.sign.key.id")
val signingKeyPassphrase = project.getSensitiveProperty("libs.sign.passphrase")
val sonatypeUser = project.getSensitiveProperty("libs.sonatype.user")
val sonatypePassword = project.getSensitiveProperty("libs.sonatype.password")


tasks.withType<AbstractPublishToMaven>().configureEach {
    // Gradle warns about some signing tasks using publishing task outputs without explicit dependencies. Here's a quick fix.
    dependsOn(tasks.withType<Sign>())
}


// the signing plugin must be configured *after* publications are created
afterEvaluate {
    signing {
        if (signingKeyId.isPresent && signingKey.isPresent && signingKeyPassphrase.isPresent) {
            useInMemoryPgpKeys(
                signingKeyId.get().toString(),
                signingKey.get().toString(),
                signingKeyPassphrase.get().toString()
            )
            sign(publishing.publications)
        }
    }
}

tasks.withType<Sign>().configureEach {
    // re-define the values to be compatible with Configuration Cache
    val keyId = signingKeyId
    val key = signingKey
    val passphrase = signingKeyPassphrase
    onlyIf("Signing credentials must be present") {
        keyId.isPresent && key.isPresent && passphrase.isPresent
    }
}

publishing {
    repositories {
        // publish to local dir, for easier manual verification of publishing
        maven(rootProject.layout.buildDirectory.dir("maven-project-local")) {
            name = "ProjectLocalDir"
        }

        maven("https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
            name = "Sonatype"
            credentials {
                username = project.getSensitiveProperty("libs.sonatype.user").orNull?.toString()
                password = project.getSensitiveProperty("libs.sonatype.password").orNull?.toString()
            }
        }
    }

    publications.withType<MavenPublication>().all {
        pom {
            if (!name.isPresent) {
                name.set(artifactId)
            }
            description.set("Kotlin binary public API management tool")
            url.set("https://github.com/Kotlin/binary-compatibility-validator")
            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("JetBrains")
                    name.set("JetBrains Team")
                    organization.set("JetBrains")
                    organizationUrl.set("https://www.jetbrains.com")
                }
            }
            scm {
                url.set("https://github.com/Kotlin/binary-compatibility-validator")
            }
        }
    }
}

fun Project.getSensitiveProperty(name: String): Provider<CharSequence> = with(providers) {
    gradleProperty(name).orElse(environmentVariable(name)).map { it }
}
