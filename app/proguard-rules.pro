# Project-specific R8/ProGuard rules for the release build.
#
# The libraries we use (Hilt, Room, Jetpack Compose, Coil, DataStore, Kotlin coroutines) ship their
# own "consumer" R8 rules, and manifest-declared components (Activity, the notification-listener
# service) are kept automatically. So no app-specific keeps are needed yet. Add targeted -keep rules
# below if R8 ever strips or renames something used via reflection.

# Folark Recents with root: started by name through `app_process` (RootTasks.removeTask).
-keep class org.arkikeskus.launcher.data.root.RootMain { public static void main(java.lang.String[]); }

# Folark's Vector (LSPosed) module: loaded by name from another process (the system launcher).
# R8 must keep it by its exact name and keep its members and nested classes so the module entry
# class and its hooks survive shrinking/renaming.
-keep,allowoptimization class org.arkikeskus.launcher.xposed.** { *; }
-keepclassmembers class org.arkikeskus.launcher.xposed.** { *; }
# The de.robv.android.xposed.* classes are PROVIDED by Vector at runtime and are compile-only here.
# We only silence warnings about them — we must NOT -keep them, or R8 would copy the compile-only
# stub into the APK, duplicating Vector's framework classes and breaking module loading.
-dontwarn de.robv.android.xposed.**
