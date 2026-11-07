plugins {
    id("arkikeskus.android.library")
    id("arkikeskus.android.hilt")
}

// The Vector (LSPosed) module lives here, alone, so it cannot quietly use app-only machinery: its
// files compile against the Xposed API stub (provided by Vector at runtime), :core:model and
// :core:data — and nothing else. The classes stay in the APK under the same package
// (org.arkikeskus.launcher.xposed), so the manifest's provider entry and Vector's class scan
// are unchanged; :app depends on this module and ships the code.
android {
    namespace = "org.arkikeskus.launcher.hook"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))

    // The Xposed API is compile-only on purpose: a project() or implementation dependency leaked
    // the stub classes into classes.dex once (see settings.gradle.kts), and at runtime the module
    // host provides the real de.robv.android.xposed classes.
    compileOnly(files("$rootDir/app/libs/xposed-api-stub.jar"))

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
}
