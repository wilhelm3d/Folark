plugins {
    id("arkikeskus.android.feature")
}

// Folark's taskbar: the bar at the bottom of the inner display (over home, and over other apps in
// overlay windows), its popups, previews, media pill and drag-to-split, and the accessibility
// service that hosts it. Split out of :feature:home, which only shows it.
//
// The Kotlin package stays org.arkikeskus.launcher.feature.home.taskbar on purpose: Android stores
// an allowed accessibility service by its class name, and root grants it by that name, so moving
// LockAccessibilityService to another package would quietly switch the service off on every phone.
// Only the resources have this module's own namespace (its R class).
android {
    namespace = "org.arkikeskus.launcher.feature.taskbar"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:root"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
}
