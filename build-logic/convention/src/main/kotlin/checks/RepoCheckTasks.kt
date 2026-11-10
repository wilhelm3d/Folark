package checks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Fails the build, with every problem listed, when a string resource would make aapt2 fail. */
abstract class CheckStringsTask : DefaultTask() {
    /** Every values*.xml of every module. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stringFiles: ConfigurableFileCollection

    /** The repository's root folder, so the messages show short paths. */
    @get:Internal
    abstract val rootPath: Property<String>

    /** What was found (empty when all is well); lets Gradle skip the check while nothing changed. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val problems = StringChecks.check(stringFiles.files, File(rootPath.get()))
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(problems.joinToString("\n"))
        if (problems.isNotEmpty()) {
            throw GradleException("String resources aapt2 would refuse:\n" + problems.joinToString("\n"))
        }
    }
}

/**
 * Writes feature/settings' SettingsSearchIndex.kt from the settings screens, so the search in
 * Settings always knows every row. The file is only written when its text changed (so Kotlin
 * compiles incrementally as before), and it is no declared output: it lives among the sources
 * and stays checked in, and other tasks reading those sources must not be tied to this one.
 */
abstract class GenerateSettingsIndexTask : DefaultTask() {
    /** The settings package's .kt files (without the index itself). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sources: ConfigurableFileCollection

    /** Where the index goes. */
    @get:Internal
    abstract val indexPath: Property<String>

    @TaskAction
    fun generate() {
        val files = sources.files.filter { it.isFile && it.name.endsWith(".kt") }.associate { it.name to it.readText() }
        val text = SettingsIndexGenerator.generate(files)
        val out = File(indexPath.get())
        if (!out.exists() || out.readText() != text) {
            out.writeText(text)
            logger.lifecycle("Settings search index updated: ${text.lines().count { it.startsWith("    SettingsSearchEntry(") }} rows")
        }
    }
}
