plugins {
    kotlinx.validation.build.conventions.base
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

group = "org.jetbrains.kotlinx"
providers.gradleProperty("DeployVersion").orNull?.let {
    version = it
}
