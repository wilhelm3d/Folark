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
) {
    val app1ComponentName: ComponentName get() = ComponentName(app1Package, app1ClassName)
    val app2ComponentName: ComponentName get() = ComponentName(app2Package, app2ClassName)
}
