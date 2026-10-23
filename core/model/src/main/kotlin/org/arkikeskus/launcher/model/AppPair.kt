package org.arkikeskus.launcher.model

import android.content.ComponentName

data class AppPair(
    val id: String,
    val name: String,
    val app1Key: String,
    val app1Package: String,
    val app1ClassName: String,
    val app1UserSerial: Long,
    val app2Key: String,
    val app2Package: String,
    val app2ClassName: String,
    val app2UserSerial: Long,
    // Optional third app, opened in a floating window on top of the split pair ("" = none).
    val app3Key: String = "",
    val app3Package: String = "",
    val app3ClassName: String = "",
    val app3UserSerial: Long = 0L,
    /** Opens as Android's peek view: the first app big, the second peeking in from the edge. */
    val peek: Boolean = false,
) {
    val hasThirdApp: Boolean get() = app3Package.isNotEmpty() && app3ClassName.isNotEmpty()
    val app3ComponentName: ComponentName get() = ComponentName(app3Package, app3ClassName)
    val app1ComponentName: ComponentName get() = ComponentName(app1Package, app1ClassName)
    val app2ComponentName: ComponentName get() = ComponentName(app2Package, app2ClassName)
}
