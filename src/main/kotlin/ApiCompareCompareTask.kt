/*
 * Copyright 2016-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.validation

import difflib.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.*

open class ApiCompareCompareTask : DefaultTask() {

    /*
     * Nullability and optionality is a workaround for
     * https://github.com/gradle/gradle/issues/2016
     *
     * Unfortunately, there is no way to skip validation apart from setting 'null'
     */
    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    var projectApiDir: File? = null

    // Used for diagnostic error message when projectApiDir doesn't exist
    @Input
    @Optional
    var nonExistingProjectApiDir: String? = null

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    lateinit var apiBuildDir: File

    // Used for diagnostic error messages when API file is missing/incorrect
    @Input
    @Optional
    lateinit var dumpTaskFqn: String

    @OutputFile
    @Optional
    val dummyOutputFile: File? = null

    private val projectName = project.name

    private val rootDir = project.rootProject.rootDir

    @TaskAction
    fun verify() {
        val projectApiDir = projectApiDir
            ?: error("Expected folder with API declarations '$nonExistingProjectApiDir' does not exist.\n" +
                "Please ensure that '$dumpTaskFqn' was executed in order to get API dump to compare the build against")

        val actualApiFiles = apiBuildDir.listFiles { f: File -> f.isFile }
        val actualApiFile = actualApiFiles.singleOrNull()
            ?: error("Expected a single file $projectName.api, but found: ${actualApiFiles.map { it.relativeTo(rootDir) }}")

        val expectedApiFiles = projectApiDir.listFiles { f: File -> f.isFile }
        val expectedApiFile = expectedApiFiles.find { it.name == actualApiFile.name }
            ?: errorWithExplanation(projectApiDir, actualApiFile, expectedApiFiles)

        val diff = compareFiles(expectedApiFile, actualApiFile)
        if (diff != null) {
            error("API check failed for project $projectName.\n$diff\n\n You can run task '$dumpTaskFqn' to overwrite API declarations")
        }
    }

    private fun errorWithExplanation(projectApiDir: File, actualApiFile: File, expectedApiFiles: Array<File>): Nothing {
        val nonExistingExpectedApiFile = projectApiDir.resolve(actualApiFile.name).relativeTo(rootDir)
        if (expectedApiFiles.size != 1) {
            error("File $nonExistingExpectedApiFile is missing, please run task '$dumpTaskFqn' to generate it")
        }
        val incorrectApiFileName = expectedApiFiles.single().name
        val caseChangeOnly = incorrectApiFileName.equals(actualApiFile.name, ignoreCase = true)
        if (caseChangeOnly) {
            error("File $nonExistingExpectedApiFile is missing, but a similar file was found instead: $incorrectApiFileName.\n" +
                "If you renamed the project, please run task '$dumpTaskFqn' again to re-generate the API file. " +
                "Since the rename only involved a case change, you may need to delete the file manually before " +
                "running the task (if your file system is case-insensitive.")
        } else {
            error("File $nonExistingExpectedApiFile is missing, but another file was found instead: $incorrectApiFileName.\n" +
                "If you renamed the project, please run task '$dumpTaskFqn' again to re-generate the API file.")
        }
    }

    private fun compareFiles(checkFile: File, builtFile: File): String? {
        val checkText = checkFile.readText()
        val builtText = builtFile.readText()

        // We don't compare full text because newlines on Windows & Linux/macOS are different
        val checkLines = checkText.lines()
        val builtLines = builtText.lines()
        if (checkLines == builtLines)
            return null

        val patch = DiffUtils.diff(checkLines, builtLines)
        val diff = DiffUtils.generateUnifiedDiff(checkFile.toString(), builtFile.toString(), checkLines, patch, 3)
        return diff.joinToString("\n")
    }
}
