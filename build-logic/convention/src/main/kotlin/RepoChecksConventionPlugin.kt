import checks.CheckStringsTask
import checks.GenerateSettingsIndexTask
import checks.SettingsIndexGenerator
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * Repository checks that run with every build (applied to the root project):
 *  - `checkStrings`: string resources aapt2 would refuse (unescaped apostrophe, two non-positional
 *    "%", a bare "&") fail at once, all listed, before any module builds;
 *  - `generateSettingsIndex`: the search index of Settings is written from the settings screens
 *    before :feature:settings builds, so a new setting is always findable.
 * Both are plain Kotlin (no Python needed) and run before each module's `preBuild`.
 */
class RepoChecksConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        require(this == rootProject) { "arkikeskus.repo.checks belongs on the root project" }
        val root = rootDir

        val checkStrings = tasks.register("checkStrings", CheckStringsTask::class.java) {
            group = "verification"
            description = "Finds string resources aapt2 would refuse, before the build gets there."
            for (sub in subprojects) {
                stringFiles.from(fileTree(File(sub.projectDir, "src/main/res")) { include("values*/*.xml") })
            }
            rootPath.set(root.absolutePath)
            report.set(layout.buildDirectory.file("repo-checks/strings.txt"))
        }

        val settingsDir = File(root, "feature/settings/src/main/kotlin/org/arkikeskus/launcher/feature/settings")
        val settingsIndex = tasks.register("generateSettingsIndex", GenerateSettingsIndexTask::class.java) {
            group = "build"
            description = "Writes the Settings search index from the settings screens."
            sources.from(fileTree(settingsDir) {
                include("*.kt")
                exclude(SettingsIndexGenerator.INDEX_FILE)
            })
            indexPath.set(File(settingsDir, SettingsIndexGenerator.INDEX_FILE).absolutePath)
        }

        subprojects {
            val isSettings = path == ":feature:settings"
            tasks.matching { it.name == "preBuild" }.configureEach {
                dependsOn(checkStrings)
                if (isSettings) dependsOn(settingsIndex)
            }
        }
    }
}
