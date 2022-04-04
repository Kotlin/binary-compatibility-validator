/*
 * Copyright 2016-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation.api

import kotlinx.validation.API_DIR
import org.gradle.testkit.runner.*
import java.io.*

internal fun BaseKotlinGradleTest.test(fn: BaseKotlinScope.() -> Unit): GradleRunner {
    val baseKotlinScope = BaseKotlinScope()
    fn(baseKotlinScope)

    // even though we use mkdirs() on the files, we need this to ensure empty directories are created too
    baseKotlinScope.directories.forEach { dirPath ->
        rootProjectDir.resolve(dirPath).mkdirs()
    }
    baseKotlinScope.files.forEach { scope ->
        val fileWriteTo = rootProjectDir.resolve(scope.filePath)
            .apply {
                parentFile.mkdirs()
                createNewFile()
            }

        scope.files.forEach {
            val fileContent = readFileList(it)
            fileWriteTo.appendText(fileContent)
        }
    }

    return GradleRunner.create() //
        .withProjectDir(rootProjectDir)
        .withPluginClasspath()
        .withArguments(baseKotlinScope.runner.arguments)
        .addPluginTestRuntimeClasspath()
    // disabled because of: https://github.com/gradle/gradle/issues/6862
    // .withDebug(baseKotlinScope.runner.debug)
}

/**
 * same as [file][FileContainer.file], but prepends "src/${sourceSet}/kotlin" before given `classFileName`
 */
internal fun FileContainer.kotlin(classFileName: String, sourceSet:String = "main", fn: AppendableScope.() -> Unit) {
    require(classFileName.endsWith(".kt")) {
        "ClassFileName must end with '.kt'"
    }

    val fileName = "src/${sourceSet}/kotlin/$classFileName"
    file(fileName, fn)
}

/**
 * Shortcut for creating a `build.gradle.kts` by using [file][FileContainer.file]
 */
internal fun FileContainer.buildGradleKts(fn: AppendableScope.() -> Unit) {
    val fileName = "build.gradle.kts"
    file(fileName, fn)
}

/**
 * Shortcut for creating a `settings.gradle.kts` by using [file][FileContainer.file]
 */
internal fun FileContainer.settingsGradleKts(fn: AppendableScope.() -> Unit) {
    val fileName = "settings.gradle.kts"
    file(fileName, fn)
}

// not using default argument in dir(name, fn) for clarity in tests (explicit "empty" in the name)
/**
 * Shortcut for creating an empty directory.
 */
internal fun FileContainer.emptyDir(dirName: String) {
    dir(dirName) {}
}

/**
 * Shortcut for creating a `api/<project>.api` descriptor by using [file][FileContainer.file]
 */
internal fun FileContainer.apiFile(projectName: String, fn: AppendableScope.() -> Unit) {
    dir(API_DIR) {
        file("$projectName.api", fn)
    }
}

// not using default argument in apiFile for clarity in tests (explicit "empty" in the name)
/**
 * Shortcut for creating an empty `api/<project>.api` descriptor by using [file][FileContainer.file]
 */
internal fun FileContainer.emptyApiFile(projectName: String) {
    apiFile(projectName) {}
}

internal fun BaseKotlinScope.runner(fn: Runner.() -> Unit) {
    val runner = Runner()
    fn(runner)

    this.runner = runner
}

internal fun AppendableScope.resolve(fileName: String) {
    this.files.add(fileName)
}

internal interface FileContainer {
    fun file(fileName: String, fn: AppendableScope.() -> Unit)

    /**
     * Declares a directory with the given [dirName] inside the current container.
     * All calls creating files within this scope will create the files nested in this directory.
     *
     * Note that it is valid to call this method multiple times at the same level with the same [dirName].
     * Files declared within 2 independent calls to [dir] will be added to the same directory.
     */
    fun dir(dirName: String, fn: DirectoryScope.() -> Unit)
}

internal class BaseKotlinScope : FileContainer {
    val files: MutableList<AppendableScope> = mutableListOf()
    // even though we create parent directories for the files, we need this to track potential empty dirs
    val directories: MutableList<String> = mutableListOf()
    var runner: Runner = Runner()

    override fun file(fileName: String, fn: AppendableScope.() -> Unit) {
        val appendableScope = AppendableScope(fileName)
        fn(appendableScope)
        files.add(appendableScope)
    }

    override fun dir(dirName: String, fn: DirectoryScope.() -> Unit) {
        directories.add(dirName)
        DirectoryScope(dirName, this).fn()
    }
}

internal class DirectoryScope(
    val dirPath: String,
    val parent: FileContainer
): FileContainer {

    override fun file(fileName: String, fn: AppendableScope.() -> Unit) {
        parent.file("$dirPath/$fileName", fn)
    }

    override fun dir(dirName: String, fn: DirectoryScope.() -> Unit) {
        parent.dir("$dirPath/$dirName", fn)
    }
}

internal class AppendableScope(val filePath: String) {
    val files: MutableList<String> = mutableListOf()
}

internal class Runner {
    val arguments: MutableList<String> = mutableListOf()
}

internal fun readFileList(fileName: String): String {
    val resource = BaseKotlinGradleTest::class.java.classLoader.getResource(fileName)
        ?: throw IllegalStateException("Could not find resource '$fileName'")
    return File(resource.toURI()).readText()
}

private fun GradleRunner.addPluginTestRuntimeClasspath() = apply {
    val cpResource = javaClass.classLoader.getResourceAsStream("plugin-classpath.txt")
        ?.let { InputStreamReader(it) }
        ?: throw IllegalStateException("Could not find classpath resource")

    val pluginClasspath = pluginClasspath + cpResource.readLines().map { File(it) }
    withPluginClasspath(pluginClasspath)
}
