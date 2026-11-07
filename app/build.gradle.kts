import java.io.FileInputStream
import java.util.Properties

plugins {
    id("arkikeskus.android.application")
    id("arkikeskus.android.compose")
    id("arkikeskus.android.hilt")
}

// Release signing is read from keystore.properties (gitignored, never committed). On a machine
// without the key file the release build is simply left unsigned instead of failing.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "org.arkikeskus.launcher"
    testOptions.unitTests.isIncludeAndroidResources = true

    defaultConfig {
        applicationId = "org.arkikeskus.launcher"
        versionCode = 224
        versionName = "2.2.4"
    }

    // English only: also drops the translations that come with libraries, so no screen mixes languages.
    androidResources {
        localeFilters += listOf("en")
    }

    // F-Droid rejects the Play "Dependency metadata" signing block AGP adds by default.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // F-Droid's build server has no NDK, so AGP packages the native libraries untouched there.
    // Keep them untouched here too, whatever NDK this machine has, so the release build is
    // byte-identical on both sides.
    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = false
            // R8 ON: without it the dexer mis-lays-out LauncherSettings' huge generated constructor
            // and ART rejects the class (VerifyError). The Vector module is kept intact via the
            // -keep rules in proguard-rules.pro instead of by turning R8 off.
            isMinifyEnabled = true
            vcsInfo {
                include = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            vcsInfo {
                include = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Personal app, single phone: with no keystore.properties, sign release with the debug
            // key so assembleRelease produces an APK that installs and updates in place
            // (adb install -r, keeping the home layout) with R8 optimizations on. A
            // keystore.properties, when added later, takes precedence for a real key. (F-Droid
            // would need the unsigned behavior back — its build server signs releases itself.)
            signingConfig = if (keystorePropsFile.exists()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }
}

if (!keystorePropsFile.exists()) {
    logger.warn("Folark: no keystore.properties - assembleRelease is signed with the debug key (personal-build mode).")
}

dependencies {
    // The Vector (LSPosed) module ships inside this APK: it compiles in :hook (its Xposed API
    // dependency is compile-only there, never packaged — at runtime Vector provides the real
    // de.robv.android.xposed classes), and :app carries it into the APK.
    implementation(project(":hook"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:launcher"))
    implementation(project(":feature:home"))
    implementation(project(":feature:appdrawer"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Applies src/main/baseline-prof.txt (and the libraries' own profiles) on installs that do not
    // come with a cloud profile — F-Droid and sideloaded builds.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.kotlinx.coroutines.test)
}
