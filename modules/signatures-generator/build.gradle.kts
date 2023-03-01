plugins {
    kotlinx.validation.build.conventions.`kotlin-jvm`
    kotlinx.validation.build.conventions.`maven-publishing`
}

dependencies {
    implementation(libs.kotlinx.metadata)
    implementation(libs.ow2.asm)
    implementation(libs.ow2.asmTree)

    testImplementation(libs.assertJ.core)
    testImplementation(libs.kotlin.test)
}

sourceSets {
    test {
        java.srcDir("src/test/kotlin")
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()

    systemProperty("overwrite.output", System.getProperty("overwrite.output", "false"))
    systemProperty("testCasesClassesDirs", sourceSets.test.get().output.classesDirs.asPath)
    jvmArgs("-ea")
}
