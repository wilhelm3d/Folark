// Compile-time copy of the classic Xposed API (the parts Folark's Recents hook uses), so Folark
// builds without an outside download. Never packaged: Vector (LSPosed) supplies the real classes
// in the hooked app at run time. The app depends on this as compileOnly.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
