plugins {
    id("arkikeskus.android.library")
}

android {
    namespace = "org.arkikeskus.launcher.root"
}

// Everything that runs `su`: the shell, the helpers for Recents and the grants, the system taskbar
// and update switches. It needs the model (Settings ▸ Root as plain values, `catching`) and nothing
// else of Folark, so the data layer, the screens and the app all build on it, never the other way.
dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
}
