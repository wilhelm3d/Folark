plugins {
    id("arkikeskus.android.library")
    id("arkikeskus.android.hilt")
}

android {
    namespace = "org.arkikeskus.launcher.data"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

ksp {
    // Export Room schemas here so every version bump leaves a JSON the next Migration can diff against.
    arg("room.schemaLocation", "$projectDir/schemas")
}

// MigrationTestHelper reads the exported schemas from androidTest assets. The AGP 9 Kotlin-DSL
// `sourceSets[...]` accessor throws a ClassCastException, so they are kept as copies in
// src/androidTest/assets/<database>/ instead; SchemaCopiesTest (a JVM unit test) fails when a copy
// differs from core/data/schemas, so after a version bump copy the new N.json there too.

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // JVM unit tests (SettingsRepository with a temp-file DataStore).
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.google.truth)
    testImplementation(libs.json)

    // Instrumented tests (HomeLayoutRepository against a real in-memory Room database).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.google.truth)
}
