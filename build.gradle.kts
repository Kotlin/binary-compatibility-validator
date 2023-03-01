plugins {
    kotlinx.validation.build.conventions.base
}

group = "org.jetbrains.kotlinx"
providers.gradleProperty("DeployVersion").orNull?.let {
    version = it
}
