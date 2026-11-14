plugins {
    id("arkikeskus.android.feature")
}

android {
    namespace = "org.arkikeskus.launcher.feature.appdrawer"
    testOptions.unitTests.isIncludeAndroidResources = true
    // Screenshot references (src/test/screenshots): -Pfolark.recordScreenshots writes them anew.
    testOptions.unitTests.all {
        it.systemProperty("folark.recordScreenshots", project.hasProperty("folark.recordScreenshots").toString())
        it.inputs.files(project.fileTree("src/test/screenshots"))
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
