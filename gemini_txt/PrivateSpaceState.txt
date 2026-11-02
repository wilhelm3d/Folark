package org.arkikeskus.launcher.data

import android.os.UserHandle

/** This phone's Private Space: its user, and whether it is locked (Android's "quiet mode"). */
data class PrivateSpaceState(val user: UserHandle, val locked: Boolean)
