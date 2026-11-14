plugins {
    id("arkikeskus.android.library")
    id("arkikeskus.android.compose")
}

android {
    namespace = "org.arkikeskus.launcher.model"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
