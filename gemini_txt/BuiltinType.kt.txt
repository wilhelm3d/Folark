package org.arkikeskus.launcher.model

enum class BuiltinType(val value: String) {
    SMARTSPACE("smartspace"),
    NOTIFICATIONS("notifications"),
    BATTERY("battery"),
    PEOPLE("people"),
    NOTHING_CLOCK("nothing_clock"),
    SAMSUNG_WEATHER("samsung_weather"),
    BUILTIN_NOTIFICATION_WIDGET("notification_widget"),
    BUILTIN_INTERACTIVE_NOTIFICATIONS("interactive_notifications");

    companion object {
        fun fromValue(value: String?): BuiltinType? = entries.find { it.value == value }
    }
}
