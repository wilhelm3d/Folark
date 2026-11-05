import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Enables Jetpack Compose for an Android application or library module and wires the
 * Compose BOM + tooling dependencies. Apply AFTER an application/library convention plugin.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.findByType(ApplicationExtension::class.java)?.apply {
            buildFeatures.compose = true
        }
        extensions.findByType(LibraryExtension::class.java)?.apply {
            buildFeatures.compose = true
        }

        // E30: the stability config (compose-stability.conf at the project root), set through the
        // Compose compiler plugin's extension by name (build-logic doesn't depend on its classes).
        extensions.findByName("composeCompiler")?.let { ext ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val files = ext.javaClass.getMethod("getStabilityConfigurationFiles").invoke(ext)
                    as org.gradle.api.provider.ListProperty<org.gradle.api.file.RegularFile>
                files.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
            }.onFailure { logger.warn("Compose stability config not applied: $it") }
        }

        val bom = libs.findLibrary("androidx-compose-bom").get()
        dependencies {
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        }
    }
}
