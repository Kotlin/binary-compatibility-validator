import com.gradle.publish.*
import kotlinx.validation.build.*
import org.gradle.api.attributes.TestSuiteType.FUNCTIONAL_TEST
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
    signing
    `maven-publish`
    `jvm-test-suite`
}

sourceSets {
    test {
        java.srcDir("src/test/kotlin")
    }
}

// While gradle testkit supports injection of the plugin classpath it doesn't allow using dependency notation
// to determine the actual runtime classpath for the plugin. It uses isolation, so plugins applied by the build
// script are not visible in the plugin classloader. This means optional dependencies (dependent on applied plugins -
// for example kotlin multiplatform) are not visible even if they are in regular gradle use. This hack will allow
// extending the classpath. It is based upon: https://docs.gradle.org/6.0/userguide/test_kit.html#sub:test-kit-classpath-injection

// Create a configuration to register the dependencies against
val testPluginRuntimeConfiguration = configurations.create("testPluginRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isVisible = false
}

// The task that will create a file that stores the classpath needed for the plugin to have additional runtime dependencies
// This file is then used in to tell TestKit which classpath to use.
val createClasspathManifest = tasks.register("createClasspathManifest") {
    val outputDir = buildDir.resolve("cpManifests")
    inputs.files(testPluginRuntimeConfiguration)
        .withPropertyName("runtimeClasspath")
        .withNormalizer(ClasspathNormalizer::class)

    outputs.dir(outputDir)
        .withPropertyName("outputDir")

    doLast {
        file(outputDir.resolve("plugin-classpath.txt")).writeText(testPluginRuntimeConfiguration.joinToString("\n"))
    }
}

val kotlinVersion: String by project
val androidGradlePluginVersion: String = "7.2.2"

configurations.implementation {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.6.0")
    implementation("org.ow2.asm:asm:9.2")
    implementation("org.ow2.asm:asm-tree:9.2")
    implementation("com.googlecode.java-diff-utils:diffutils:1.3.0")
    compileOnly("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.8.10")
    //compileOnly("com.android.tools.build:gradle:${androidGradlePluginVersion}")

    // The test needs the full kotlin multiplatform plugin loaded as it has no visibility of previously loaded plugins,
    // unlike the regular way gradle loads plugins.
    testPluginRuntimeConfiguration("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:$kotlinVersion")
    testPluginRuntimeConfiguration("com.android.tools.build:gradle:${androidGradlePluginVersion}")
}

tasks.compileKotlin {
    kotlinOptions.apply {
        allWarningsAsErrors = true

        languageVersion = "1.4"
        apiVersion = "1.4"
        jvmTarget = "1.8"

        // Suppressing "w: Language version 1.4 is deprecated and its support will be removed" message
        // because LV=1.4 in practice is mandatory as it is a default language version in Gradle 7.0+ for users' kts scripts.
        freeCompilerArgs += "-Xsuppress-version-warnings"
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks.compileTestKotlin {
    kotlinOptions {
        languageVersion = "1.6"
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("overwrite.output", System.getProperty("overwrite.output", "false"))
    systemProperty("testCasesClassesDirs", sourceSets.test.get().output.classesDirs.asPath)
    jvmArgs("-ea")
}

properties["DeployVersion"]?.let { version = it }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            mavenCentralMetadata()
            mavenCentralArtifacts(project, project.sourceSets.main.get().allSource)
        }

        mavenRepositoryPublishing(project)
        mavenCentralMetadata()
    }

    publications.withType<MavenPublication>().all {
        signPublicationIfKeyPresent(this)
    }
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    website.set("https://github.com/Kotlin/binary-compatibility-validator")
    vcsUrl.set("https://github.com/Kotlin/binary-compatibility-validator")

    plugins.configureEach {
        tags.addAll("kotlin", "api-management", "binary-compatibility")
    }

    plugins {
        create("binary-compatibility-validator") {
            id = "org.jetbrains.kotlinx.binary-compatibility-validator"
            implementationClass = "kotlinx.validation.BinaryCompatibilityValidatorPlugin"
            displayName = "Binary compatibility validator"
            description = "Produces binary API dumps and compares them in order to verify that binary API is preserved"
        }
    }
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            useJUnit()
            dependencies {
                implementation(project())
                implementation("org.assertj:assertj-core:3.18.1")
                implementation(project.dependencies.kotlin("test-junit").toString())
            }
        }

        val test by getting(JvmTestSuite::class) {
            description = "Regular unit tests"
        }

        val functionalTest by creating(JvmTestSuite::class) {
            testType.set(FUNCTIONAL_TEST)
            description = "Functional Plugin tests using Gradle TestKit"

            dependencies {
                implementation(files(createClasspathManifest))

                implementation(gradleApi())
                implementation(gradleTestKit())
            }

            targets.configureEach {
                testTask.configure {
                    shouldRunAfter(test)
                }
            }
        }

        gradlePlugin.testSourceSets(functionalTest.sources)

        tasks.check {
            dependsOn(functionalTest)
        }
    }
}
