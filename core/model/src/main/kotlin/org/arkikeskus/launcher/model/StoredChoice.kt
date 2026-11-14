package org.arkikeskus.launcher.model

import kotlin.enums.enumEntries

/**
 * One choice of a setting that has a fixed set of them (an enum). [stored] is the text the settings
 * store (and backups) keep for it: the same text the setting always used, so settings saved by an
 * older Folark read back as the same choice. Renaming an entry never changes it.
 */
interface StoredChoice {
    val stored: String
}

/**
 * The choice of [E] stored as [value]; anything else (nothing stored, an old or garbled value) is
 * [default].
 */
inline fun <reified E> storedChoice(value: String?, default: E): E where E : Enum<E>, E : StoredChoice =
    enumEntries<E>().firstOrNull { it.stored == value } ?: default

/**
 * For a setting that offers only some of a group's choices: the one of [allowed] stored as [value],
 * anything else [default].
 */
fun <E : StoredChoice> storedChoice(value: String?, default: E, allowed: Collection<E>): E =
    allowed.firstOrNull { it.stored == value } ?: default
