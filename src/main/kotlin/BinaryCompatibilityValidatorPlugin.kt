/*
 * Copyright 2016-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.*
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

const val API_DIR = "api"
const val KLIB_PHONY_TARGET_NAME = "klib"
const val KLIB_ALL_PHONY_TARGET_NAME = "klib-all"

class BinaryCompatibilityValidatorPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        val extension = extensions.create("apiValidation", ApiValidationExtension::class.java)
        validateExtension(extension)
        allprojects {
            configureProject(it, extension)
        }
    }

    private fun Project.validateExtension(extension: ApiValidationExtension) {
        afterEvaluate {
            val ignored = extension.ignoredProjects
            val all = allprojects.map { it.name }
            for (project in ignored) {
                require(project in all) { "Cannot find excluded project $project in all projects: $all" }
            }
        }
    }

    private fun configureProject(project: Project, extension: ApiValidationExtension) {
        configureKotlinPlugin(project, extension)
        configureAndroidPlugin(project, extension)
        configureMultiplatformPlugin(project, extension)
    }

    private fun configurePlugin(
        name: String,
        project: Project,
        extension: ApiValidationExtension,
        action: Action<AppliedPlugin>
    ) = project.pluginManager.withPlugin(name) {
        if (project.name in extension.ignoredProjects) return@withPlugin
        action.execute(it)
    }

    private fun configureMultiplatformPlugin(
        project: Project,
        extension: ApiValidationExtension
    ) = configurePlugin("kotlin-multiplatform", project, extension) {
        if (project.name in extension.ignoredProjects) return@configurePlugin
        val kotlin = project.kotlinMultiplatform

        // Create common tasks for multiplatform
        val commonApiDump = project.tasks.register("apiDump") {
            it.group = "other"
            it.description = "Task that collects all target specific dump tasks"
        }

        val commonApiCheck: TaskProvider<Task> = project.tasks.register("apiCheck") {
            it.group = "verification"
            it.description = "Shortcut task that depends on all specific check tasks"
        }.apply { project.tasks.named("check") { it.dependsOn(this) } }

        val jvmTargetCountProvider = project.provider {
            kotlin.targets.count {
                it.platformType in arrayOf(
                    KotlinPlatformType.jvm,
                    KotlinPlatformType.androidJvm
                )
            }
        }

        val jvmDirConfig = jvmTargetCountProvider.map {
            if (it == 1 && !extension.klib.enabled) DirConfig.COMMON else DirConfig.TARGET_DIR
        }
        val klibDirConfig = jvmTargetCountProvider.map {
            if (it == 0) DirConfig.COMMON else DirConfig.TARGET_DIR
        }

        kotlin.targets.matching { it.jvmBased }.all { target ->
            val targetConfig = TargetConfig(project, target.name, jvmDirConfig)
            if (target.platformType == KotlinPlatformType.jvm) {
                target.mainCompilations.all {
                    project.configureKotlinCompilation(it, extension, targetConfig, commonApiDump, commonApiCheck)
                }
            } else if (target.platformType == KotlinPlatformType.androidJvm) {
                target.compilations.matching { it.name == "release" }.all {
                    project.configureKotlinCompilation(
                        it,
                        extension,
                        targetConfig,
                        commonApiDump,
                        commonApiCheck,
                        useOutput = true
                    )
                }
            }
        }
        KlibValidationPipelineBuilder(klibDirConfig, extension).configureTasks(project, commonApiDump, commonApiCheck)
    }

    private fun configureAndroidPlugin(
        project: Project,
        extension: ApiValidationExtension
    ) {
        configureAndroidPluginForKotlinLibrary(project, extension)

    }

    private fun configureAndroidPluginForKotlinLibrary(
        project: Project,
        extension: ApiValidationExtension
    ) = configurePlugin("kotlin-android", project, extension) {
        val androidExtension = project.extensions
            .getByName("kotlin") as KotlinAndroidProjectExtension
        androidExtension.target.compilations.matching {
            it.compilationName == "release"
        }.all {
            project.configureKotlinCompilation(it, extension, useOutput = true)
        }
    }

    private fun configureKotlinPlugin(
        project: Project,
        extension: ApiValidationExtension
    ) = configurePlugin("kotlin", project, extension) {
        project.configureApiTasks(extension, TargetConfig(project))
    }
}

private class TargetConfig constructor(
    project: Project,
    val targetName: String? = null,
    private val dirConfig: Provider<DirConfig>? = null,
) {

    private val API_DIR_PROVIDER = project.provider { API_DIR }

    fun apiTaskName(suffix: String) = when (targetName) {
        null, "" -> "api$suffix"
        else -> "${targetName}Api$suffix"
    }

    val apiDir
        get() = dirConfig?.map { dirConfig ->
            when (dirConfig) {
                DirConfig.COMMON -> API_DIR
                else -> "$API_DIR/$targetName"
            }
        } ?: API_DIR_PROVIDER
}

private enum class DirConfig {
    /**
     * `api` directory for .api files.
     * Used in single target projects
     */
    COMMON,

    /**
     * Target-based directory, used in multitarget setups.
     * E.g. for the project with targets jvm and android,
     * the resulting paths will be
     * `/api/jvm/project.api` and `/api/android/project.api`
     */
    TARGET_DIR
}

private fun Project.configureKotlinCompilation(
    compilation: KotlinCompilation<KotlinCommonOptions>,
    extension: ApiValidationExtension,
    targetConfig: TargetConfig = TargetConfig(this),
    commonApiDump: TaskProvider<Task>? = null,
    commonApiCheck: TaskProvider<Task>? = null,
    useOutput: Boolean = false,
) {
    val projectName = project.name
    val apiDirProvider = targetConfig.apiDir
    val apiBuildDir = apiDirProvider.map { buildDir.resolve(it) }

    val apiBuild = task<KotlinApiBuildTask>(targetConfig.apiTaskName("Build")) {
        // Do not enable task for empty umbrella modules
        isEnabled =
            apiCheckEnabled(
                projectName,
                extension
            ) && compilation.allKotlinSourceSets.any { it.kotlin.srcDirs.any { it.exists() } }
        // 'group' is not specified deliberately, so it will be hidden from ./gradlew tasks
        description =
            "Builds Kotlin API for 'main' compilations of $projectName. Complementary task and shouldn't be called manually"
        if (useOutput) {
            // Workaround for #4
            inputClassesDirs =
                files(provider<Any> { if (isEnabled) compilation.output.classesDirs else emptyList<Any>() })
            inputDependencies =
                files(provider<Any> { if (isEnabled) compilation.output.classesDirs else emptyList<Any>() })
        } else {
            inputClassesDirs =
                files(provider<Any> { if (isEnabled) compilation.output.classesDirs else emptyList<Any>() })
            inputDependencies =
                files(provider<Any> { if (isEnabled) compilation.compileDependencyFiles else emptyList<Any>() })
        }
        outputApiDir = apiBuildDir.get()
    }
    configureCheckTasks(apiBuildDir, apiBuild, extension, targetConfig, commonApiDump, commonApiCheck)
}

internal val Project.sourceSets: SourceSetContainer
    get() = extensions.getByName("sourceSets") as SourceSetContainer

internal val Project.apiValidationExtensionOrNull: ApiValidationExtension?
    get() =
        generateSequence(this) { it.parent }
            .map { it.extensions.findByType(ApiValidationExtension::class.java) }
            .firstOrNull { it != null }

fun apiCheckEnabled(projectName: String, extension: ApiValidationExtension): Boolean =
    projectName !in extension.ignoredProjects && !extension.validationDisabled

fun klibAbiCheckEnabled(projectName: String, extension: ApiValidationExtension): Boolean =
    projectName !in extension.ignoredProjects && !extension.validationDisabled && extension.klib.enabled

private fun Project.configureApiTasks(
    extension: ApiValidationExtension,
    targetConfig: TargetConfig = TargetConfig(this),
) {
    val projectName = project.name
    val apiBuildDir = targetConfig.apiDir.map { buildDir.resolve(it) }
    val sourceSetsOutputsProvider = project.provider {
        sourceSets
            .filter { it.name == SourceSet.MAIN_SOURCE_SET_NAME || it.name in extension.additionalSourceSets }
            .map { it.output.classesDirs }
    }

    val apiBuild = task<KotlinApiBuildTask>(targetConfig.apiTaskName("Build")) {
        isEnabled = apiCheckEnabled(projectName, extension)
        // 'group' is not specified deliberately, so it will be hidden from ./gradlew tasks
        description =
            "Builds Kotlin API for 'main' compilations of $projectName. Complementary task and shouldn't be called manually"
        inputClassesDirs = files(provider<Any> { if (isEnabled) sourceSetsOutputsProvider.get() else emptyList<Any>() })
        inputDependencies =
            files(provider<Any> { if (isEnabled) sourceSetsOutputsProvider.get() else emptyList<Any>() })
        outputApiDir = apiBuildDir.get()
    }

    configureCheckTasks(apiBuildDir, apiBuild, extension, targetConfig)
}

private fun Project.configureCheckTasks(
    apiBuildDir: Provider<File>,
    apiBuild: TaskProvider<*>,
    extension: ApiValidationExtension,
    targetConfig: TargetConfig,
    commonApiDump: TaskProvider<Task>? = null,
    commonApiCheck: TaskProvider<Task>? = null,
) {
    val projectName = project.name
    val apiCheckDir = targetConfig.apiDir.map {
        projectDir.resolve(it).also { r ->
            logger.debug("Configuring api for ${targetConfig.targetName ?: "jvm"} to $r")
        }
    }
    val apiCheck = task<KotlinApiCompareTask>(targetConfig.apiTaskName("Check")) {
        isEnabled = apiCheckEnabled(projectName, extension) && apiBuild.map { it.enabled }.getOrElse(true)
        group = "verification"
        description = "Checks signatures of public API against the golden value in API folder for $projectName"
        compareApiDumps(apiReferenceDir = apiCheckDir.get(), apiBuildDir = apiBuildDir.get())
        dependsOn(apiBuild)
    }

    val apiDump = task<Sync>(targetConfig.apiTaskName("Dump")) {
        isEnabled = apiCheckEnabled(projectName, extension) && apiBuild.map { it.enabled }.getOrElse(true)
        group = "other"
        description = "Syncs API from build dir to ${targetConfig.apiDir} dir for $projectName"
        from(apiBuildDir)
        into(apiCheckDir)
        dependsOn(apiBuild)
    }

    commonApiDump?.configure { it.dependsOn(apiDump) }

    when (commonApiCheck) {
        null -> project.tasks.named("check").configure { it.dependsOn(apiCheck) }
        else -> commonApiCheck.configure { it.dependsOn(apiCheck) }
    }
}

inline fun <reified T : Task> Project.task(
    name: String,
    noinline configuration: T.() -> Unit,
): TaskProvider<T> = tasks.register(name, T::class.java, Action(configuration))

const val BANNED_TARGETS_PROPERTY_NAME = "binary.compatibility.validator.klib.targets.blacklist.for.testing"

private class KlibValidationPipelineBuilder(
    val dirConfig: Provider<DirConfig>?,
    val extension: ApiValidationExtension
) {
    lateinit var intermediateFilesConfig: Provider<DirConfig>

    fun configureTasks(project: Project, commonApiDump: TaskProvider<Task>, commonApiCheck: TaskProvider<Task>) {
        // In the intermediate phase of Klib dump generation there are always multiple targets, thus we need
        // target-based directory tree.
        intermediateFilesConfig = project.provider { DirConfig.TARGET_DIR }
        val klibApiDirConfig = dirConfig?.map { TargetConfig(project, KLIB_PHONY_TARGET_NAME, dirConfig) }
        val klibDumpConfig = TargetConfig(project, KLIB_PHONY_TARGET_NAME, intermediateFilesConfig)
        val klibDumpAllConfig = TargetConfig(project, KLIB_ALL_PHONY_TARGET_NAME, intermediateFilesConfig)

        val projectDir = project.projectDir
        val klibApiDir = klibApiDirConfig?.map {
            projectDir.resolve(it.apiDir.get())
        }!!
        val klibMergeDir = project.buildDir.resolve(klibDumpConfig.apiDir.get())
        val klibMergeAllDir = project.buildDir.resolve(klibDumpAllConfig.apiDir.get())

        val klibMerge = project.mergeKlibsUmbrellaTask(klibDumpConfig, klibApiDir, klibMergeDir)
        val klibMergeAll = project.mergeAllKlibsUmbrellaTask(klibDumpConfig, klibApiDir, klibMergeAllDir)
        val klibDump = project.dumpKlibsTask(klibDumpConfig, klibApiDir, klibMergeDir)
        val klibCheck = project.checkKlibsTask(klibDumpConfig, klibApiDir, klibMergeDir)
        val klibDumpAll = project.dumpAllKlibsTask(klibDumpConfig, klibApiDir, klibMergeAllDir)
        val klibCheckAll = project.checkAllKlibsTask(klibDumpConfig, klibApiDir, klibMergeAllDir)

        commonApiDump.configure { it.dependsOn(klibDump) }
        commonApiCheck.configure { it.dependsOn(klibCheck) }

        klibDump.configure { it.dependsOn(klibMerge) }
        klibCheck.configure { it.dependsOn(klibMerge) }
        klibDumpAll.configure { it.dependsOn(klibMergeAll) }
        klibCheckAll.configure { it.dependsOn(klibMergeAll) }

        project.configureTargets(klibMerge, klibMergeAll)
    }

    private fun Project.dumpAllKlibsTask(
        klibDumpConfig: TargetConfig,
        klibApiDir: Provider<File>,
        klibMergeDir: File
    ) = project.task<Sync>(klibDumpConfig.apiTaskName("DumpAll")) {
        isEnabled = klibAbiCheckEnabled(project.name, extension)
        description = "Syncs klib ABI dump generated for all targets " +
                "(including targets not supported by the host compiler) " +
                "from build dir to ${klibDumpConfig.apiDir} dir for ${project.name}"
        group = "other"
        from(klibMergeDir)
        into(klibApiDir)
    }

    private fun Project.checkKlibsTask(
        klibDumpConfig: TargetConfig,
        klibApiDir: Provider<File>,
        klibMergeDir: File
    ) = project.task<KotlinApiCompareTask>(klibDumpConfig.apiTaskName("Check")) {
        isEnabled = klibAbiCheckEnabled(project.name, extension)
        group = "verification"
        description = "Checks signatures of public klib ABI against the golden value in ABI folder for " +
                project.name
        compareApiDumps(apiReferenceDir = klibApiDir.get(), apiBuildDir = klibMergeDir)
    }

    private fun Project.checkAllKlibsTask(
        klibDumpConfig: TargetConfig,
        klibApiDir: Provider<File>,
        klibMergeDir: File
    ) = project.task<KotlinApiCompareTask>(klibDumpConfig.apiTaskName("CheckAll")) {
        isEnabled = klibAbiCheckEnabled(project.name, extension)
        group = "verification"
        description = "Checks signatures of public klib ABI against the golden value in ABI folder for " +
                project.name
        compareApiDumps(apiReferenceDir = klibApiDir.get(), apiBuildDir = klibMergeDir)
    }

    private fun Project.dumpKlibsTask(
        klibDumpConfig: TargetConfig,
        klibApiDir: Provider<File>,
        klibMergeDir: File
    ) = project.task<Sync>(klibDumpConfig.apiTaskName("Dump")) {
        isEnabled = klibAbiCheckEnabled(project.name, extension)
        description = "Syncs klib ABI dump from build dir to ${klibDumpConfig.apiDir} dir for ${project.name}"
        group = "other"
        from(klibMergeDir)
        into(klibApiDir)
    }

    private fun Project.mergeAllKlibsUmbrellaTask(
        klibDumpConfig: TargetConfig,
        klibApiDir: Provider<File>,
        klibMergeDir: File
    ) = project.task<KotlinKlibMergeAbiTask>(
        klibDumpConfig.apiTaskName("MergeAll")
    )
    {
        isEnabled = klibAbiCheckEnabled(project.name, extension)
        description = "Merges multiple klib ABI dump files generated for " +
                "different targets (including files substituting dumps for unsupported target) " +
                "into a single multi-target dump"
        inputImageDir = klibApiDir
        mergedFile = klibMergeDir
    }

    private fun Project.mergeKlibsUmbrellaTask(
        klibDumpConfig: TargetConfig,
        klibApiDir: Provider<File>,
        klibMergeDir: File
    ) = project.task<KotlinKlibMergeAbiTask>(klibDumpConfig.apiTaskName("Merge")) {
        isEnabled = klibAbiCheckEnabled(project.name, extension)
        description = "Merges multiple klib ABI dump files generated for " +
                "different targets into a single multi-target dump"
        inputImageDir = klibApiDir
        mergedFile = klibMergeDir
    }

    fun Project.bannedTargets(): Set<String> {
        val prop = project.properties[BANNED_TARGETS_PROPERTY_NAME] as String?
        prop ?: return emptySet()
        return prop.split(",").map { it.trim() }.toSet().also {
            if (it.isNotEmpty()) {
                project.logger.warn(
                    "WARNING: Following property is not empty: $BANNED_TARGETS_PROPERTY_NAME. " +
                            "If you're don't know what it means, please make sure that its value is empty."
                )
            }
        }
    }

    fun Project.configureTargets(
        mergeTask: TaskProvider<KotlinKlibMergeAbiTask>,
        mergeFakeTask: TaskProvider<KotlinKlibMergeAbiTask>?
    ) {
        val hostManager = HostManager()
        val bannedTargets = bannedTargets()
        val kotlin = project.kotlinMultiplatform
        val targetsProvider: Provider<Map<String, Set<String>>> = targetInfoProvider()

        kotlin.targets.matching { it.emitsKlib }.configureEach { currentTarget ->
            val mainCompilations = currentTarget.mainCompilations
            if (mainCompilations.none()) {
                return@configureEach
            }

            val targetName = currentTarget.targetName
            val targetConfig = TargetConfig(project, targetName, intermediateFilesConfig)
            val apiBuildDir = targetConfig.apiDir.map { project.buildDir.resolve(it) }.get()

            val targetSupported = (currentTarget !is KotlinNativeTarget
                    || (hostManager.isEnabled(currentTarget.konanTarget)))
                    && !bannedTargets.contains(targetName) // the last check is only for testing purposes

            if (targetSupported) {
                mainCompilations.all {
                    val buildTargetAbi = configureKlibCompilation(
                        it, extension, targetConfig,
                        apiBuildDir
                    )
                    mergeTask.configure {
                        it.addInput(targetName, apiBuildDir)
                        it.dependsOn(buildTargetAbi)
                    }
                    mergeFakeTask?.configure {
                        it.addInput(targetName, apiBuildDir)
                        it.dependsOn(buildTargetAbi)
                    }
                }
                return@configureEach
            }

            val unsupportedTargetStub = mergeDependencyForUnsupportedTarget(targetConfig)
            // print a warning or throw an exception when running regular merge task
            mergeTask.configure {
                it.dependsOn(unsupportedTargetStub)
            }
            if (mergeFakeTask != null) {
                val proxy = unsupportedTargetDumpProxy(targetConfig, apiBuildDir, targetsProvider)
                mergeFakeTask.configure {
                    it.addInput(targetName, apiBuildDir)
                    it.dependsOn(proxy)
                }
            }
        }
    }

    private fun Project.targetInfoProvider(): Provider<Map<String, Set<String>>> =
        project.provider {
            val target2srcSet = mutableMapOf<String, Set<String>>()
            val targets = kotlinMultiplatform.targets.matching { it.emitsKlib }
            targets.forEach { target ->
                val mainCompilations = target.mainCompilations
                val flattenedTargets = mutableSetOf<String>()

                if (mainCompilations.any()) {
                    mainCompilations.first().allKotlinSourceSets.forEach { it.flattenSourceSetsTree(flattenedTargets) }
                }
                target2srcSet[target.name] = flattenedTargets
            }
            target2srcSet
        }

    private fun Project.configureKlibCompilation(
        compilation: KotlinCompilation<KotlinCommonOptions>,
        extension: ApiValidationExtension,
        targetConfig: TargetConfig,
        apiBuildDir: File
    ): TaskProvider<KotlinKlibAbiBuildTask> {
        val projectName = project.name
        val buildTask = project.task<KotlinKlibAbiBuildTask>(targetConfig.apiTaskName("Build")) {
            target = targetConfig.targetName!!
            // Do not enable task for empty umbrella modules
            isEnabled =
                klibAbiCheckEnabled(
                    projectName,
                    extension
                ) && compilation.allKotlinSourceSets.any { it.kotlin.srcDirs.any { it.exists() } }
            // 'group' is not specified deliberately, so it will be hidden from ./gradlew tasks
            description = "Builds Kotlin Klib ABI for 'main' compilations of $projectName. " +
                    "Complementary task and shouldn't be called manually"
            klibFile = project.files(project.provider { compilation.output.classesDirs })
            compilationDependencies = project.files(project.provider { compilation.compileDependencyFiles })
            signatureVersion = extension.klib.signatureVersion
            outputApiDir = apiBuildDir
        }
        return buildTask
    }

    private fun Project.mergeDependencyForUnsupportedTarget(targetConfig: TargetConfig): TaskProvider<DefaultTask> {
        return project.task<DefaultTask>(targetConfig.apiTaskName("Build")) {
            isEnabled = apiCheckEnabled(project.name, extension)

            doLast {
                if (extension.klib.ignoreUnsupportedTargets) {
                    logger.warn(
                        "Target ${targetConfig.targetName} is not supported by the host compiler and the " +
                                "KLIB ABI dump could not be generated for it. " +
                                "The project enabled \"apiValidation.klib.ignoreUnsupportedTargets\" option thus the lack" +
                                "of the target's support will not fail the build."
                    )
                } else {
                    // TODO: change the options part of the message
                    throw UnsupportedOperationException(
                        "Target ${targetConfig.targetName} is not supported by the host compiler and the " +
                                "KLIB ABI dump could not be generated for it. Consider running the task on a host " +
                                "where all targets are supported. For other possible solutions please check the " +
                                "https://github.com/Kotlin/binary-compatibility-validator#binary-compatibility-validator"
                    )
                }
            }
        }
    }

    private fun Project.unsupportedTargetDumpProxy(
        targetConfig: TargetConfig, apiBuildDir: File,
        targetInfoProvider: Provider<Map<String, Set<String>>>
    ):
            TaskProvider<KotlinKlibReuseSharedAbiTask> {
        val targetName = targetConfig.targetName!!
        return project.task<KotlinKlibReuseSharedAbiTask>(targetConfig.apiTaskName("FakeAbiDump")) {
            isEnabled = klibAbiCheckEnabled(project.name, extension)
            description = "Try to replace the dump for unsupported target $targetName with the dump " +
                    "generated for one of the supported targets."
            group = "other"
            this.targetInfoProvider = targetInfoProvider
            allowInexactDumpSubstitution = project.apiValidationExtensionOrNull!!.klib.allowInexactDumpSubstitution
            outputApiDir = apiBuildDir
            unsupportedTarget = targetConfig.targetName
            dependsOn(project.tasks.withType(KotlinKlibAbiBuildTask::class.java))
        }
    }
}

private val KotlinTarget.emitsKlib: Boolean
    get() {
        val platformType = this.platformType
        return platformType == KotlinPlatformType.native ||
                platformType == KotlinPlatformType.wasm ||
                platformType == KotlinPlatformType.js
    }

private val KotlinTarget.jvmBased: Boolean
    get() {
        val platformType = this.platformType
        return platformType == KotlinPlatformType.jvm || platformType == KotlinPlatformType.androidJvm
    }

private val Project.kotlinMultiplatform
    get() = extensions.getByName("kotlin") as KotlinMultiplatformExtension

private val KotlinTarget.mainCompilations
    get() = compilations.matching { it.name == "main" }

// Collects a set of all non empty source sets this particular source set depends on
private fun KotlinSourceSet.flattenSourceSetsTree(dst: MutableSet<String>) {
    val isNotEmpty = this.kotlin.srcDirs.any {
        if (!it.exists()) {
            return@any false
        }
        var hasFiles = false
        Files.walkFileTree(it.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                hasFiles = true
                return FileVisitResult.TERMINATE
            }
        })
        hasFiles
    }
    if (isNotEmpty) {
        dst.add(this.name)
    }
    dependsOn.forEach {
        it.flattenSourceSetsTree(dst)
    }
}
