pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "arkikeskus-launcher"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:data")
include(":core:launcher")
include(":core:ui")
include(":feature:home")
include(":feature:appdrawer")
include(":feature:settings")
// The Xposed API stand-in is consumed by :app as a compile-only JAR (app/libs/xposed-api-stub.jar),
// not as a module, so it is never packaged into the APK. Source lives in xposed-api-stub/src.
