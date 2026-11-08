package org.arkikeskus.launcher.xposed

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import org.arkikeskus.launcher.model.catching

/**
 * Reflection for the module's hooks, looked up once per class and kept (E1–E3). The hooks run
 * inside System UI, some on every frame of a divider drag, where walking a class chain (and, for
 * a missing name, throwing on each class) adds up.
 */
internal object Reflect {
    /** "No such field": a lookup that found nothing is remembered too. */
    private val NONE: Field = Reflect::class.java.getDeclaredField("NONE_HOLDER")
    @Suppress("unused") private val NONE_HOLDER: Any? = null

    private val fields = ConcurrentHashMap<Class<*>, ConcurrentHashMap<String, Field>>()
    private val methods = ConcurrentHashMap<Class<*>, List<Method>>()
    private val constructors = ConcurrentHashMap<Class<*>, List<Constructor<*>>>()

    /** The field [name] of [cls] or a superclass, made accessible; null when there is none. */
    fun field(cls: Class<*>, name: String): Field? {
        val byName = fields.getOrPut(cls) { ConcurrentHashMap() }
        val found = byName.getOrPut(name) {
            generateSequence(cls) { it.superclass }
                .mapNotNull { c -> c.declaredFields.firstOrNull { it.name == name } }
                .firstOrNull()?.apply { isAccessible = true } ?: NONE
        }
        return found.takeIf { it !== NONE }
    }

    fun get(target: Any, name: String): Any? =
        field(target.javaClass, name)?.let { catching { it.get(target) }.getOrNull() }

    fun set(target: Any, name: String, value: Any?): Boolean =
        field(target.javaClass, name)?.let { catching { it.set(target, value) }.isSuccess } ?: false

    /** Every method of [cls] and its superclasses (declared ones, accessible). */
    fun methods(cls: Class<*>): List<Method> = methods.getOrPut(cls) {
        generateSequence(cls) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .onEach { catching { it.isAccessible = true } }
            .toList()
    }

    fun constructors(cls: Class<*>): List<Constructor<*>> = constructors.getOrPut(cls) {
        cls.declaredConstructors.toList().onEach { catching { it.isAccessible = true } }
    }
}
