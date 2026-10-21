# Project-specific R8/ProGuard rules for the release build.
#
# The libraries we use (Hilt, Room, Jetpack Compose, Coil, DataStore, Kotlin coroutines) ship their
# own "consumer" R8 rules, and manifest-declared components (Activity, the notification-listener
# service) are kept automatically. So no app-specific keeps are needed yet. Add targeted -keep rules
# below if R8 ever strips or renames something used via reflection.

# Folark Recents with root: started by name through `app_process` (RootTasks.removeTask).
-keep class org.arkikeskus.launcher.data.root.RootMain { public static void main(java.lang.String[]); }

# Folark's Vector (LSPosed) module: loaded by name from another process (the system launcher).
# R8 must keep it by its exact name, keep its members and nested classes, and keep the Xposed
# super-types so the "implements IXposedHookLoadPackage" relationship survives shrinking.
-keep,allowoptimization class org.arkikeskus.launcher.xposed.** { *; }
-keepclassmembers class org.arkikeskus.launcher.xposed.** { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep interface de.robv.android.xposed.** { *; }
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
