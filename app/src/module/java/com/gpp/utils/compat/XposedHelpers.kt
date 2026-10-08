package com.gpp.utils.compat

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Reflection helpers replacing legacy `de.robv.android.xposed.XposedHelpers`
 * for libxposed API 102 modules (no legacy framework calls).
 */
object XposedHelpers {
    fun findClass(className: String, classLoader: ClassLoader?): Class<*> {
        val cl = classLoader ?: ClassLoader.getSystemClassLoader()
        return Class.forName(className, false, cl)
    }

    fun findClassIfExists(className: String, classLoader: ClassLoader?): Class<*>? =
        try {
            findClass(className, classLoader)
        } catch (_: Throwable) {
            null
        }

    fun findMethodExact(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredMethod(methodName, *parameterTypes).also { it.isAccessible = true }
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        throw NoSuchMethodError("$clazz#$methodName")
    }

    fun findMethodsByExactName(clazz: Class<*>, methodName: String): Array<Method> {
        val out = mutableListOf<Method>()
        var c: Class<*>? = clazz
        while (c != null) {
            c.declaredMethods.filter { it.name == methodName }.forEach {
                it.isAccessible = true
                out.add(it)
            }
            c = c.superclass
        }
        return out.toTypedArray()
    }

    fun findConstructorExact(clazz: Class<*>, vararg parameterTypes: Class<*>): Constructor<*> {
        return clazz.getDeclaredConstructor(*parameterTypes).also { it.isAccessible = true }
    }

    /** Platform [Any!] via Java helper — matches legacy XposedHelpers nullability. */
    fun getObjectField(obj: Any?, fieldName: String): Any {
        requireNotNull(obj) { "getObjectField on null" }
        return ReflectFields.getObjectField(obj, fieldName)
    }

    fun getIntField(obj: Any?, fieldName: String): Int {
        requireNotNull(obj) { "getIntField on null" }
        return ReflectFields.getIntField(obj, fieldName)
    }

    fun setObjectField(obj: Any, fieldName: String, value: Any?) {
        findField(obj.javaClass, fieldName).set(obj, value)
    }

    fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any =
        findField(clazz, fieldName).get(null) as Any

    fun setStaticObjectField(clazz: Class<*>, fieldName: String, value: Any?) {
        findField(clazz, fieldName).set(null, value)
    }

    fun getStaticIntField(clazz: Class<*>, fieldName: String): Int =
        findField(clazz, fieldName).getInt(null)

    /** Non-null return matches legacy stub platform typing used across hooks. */
    fun callMethod(obj: Any?, methodName: String, vararg args: Any?): Any {
        requireNotNull(obj) { "callMethod on null" }
        val method = findBestMethod(obj.javaClass, methodName, args, instance = true)
        return method.invoke(obj, *args) as Any
    }

    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any {
        val method = findBestMethod(clazz, methodName, args, instance = false)
        return method.invoke(null, *args) as Any
    }

    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        val ctor = findBestConstructor(clazz, args)
        return ctor.newInstance(*args) as Any
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredField(fieldName).also { it.isAccessible = true }
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        throw NoSuchFieldError("$clazz#$fieldName")
    }

    private fun findBestMethod(
        clazz: Class<*>,
        methodName: String,
        args: Array<out Any?>,
        instance: Boolean,
    ): Method {
        val candidates = findMethodsByExactName(clazz, methodName).filter {
            Modifier.isStatic(it.modifiers) != instance && it.parameterTypes.size == args.size
        }
        for (m in candidates) {
            if (paramsMatch(m.parameterTypes, args)) {
                m.isAccessible = true
                return m
            }
        }
        throw NoSuchMethodError("$clazz#$methodName(${args.size} args)")
    }

    private fun findBestConstructor(clazz: Class<*>, args: Array<out Any?>): Constructor<*> {
        val ctors = clazz.declaredConstructors.filter { it.parameterTypes.size == args.size }
        for (c in ctors) {
            if (paramsMatch(c.parameterTypes, args)) {
                c.isAccessible = true
                return c
            }
        }
        throw NoSuchMethodError("$clazz.<init>(${args.size} args)")
    }

    private fun paramsMatch(types: Array<Class<*>>, args: Array<out Any?>): Boolean {
        for (i in types.indices) {
            val arg = args[i] ?: continue
            if (!box(types[i]).isInstance(arg)) return false
        }
        return true
    }

    private fun box(type: Class<*>): Class<*> = when (type) {
        java.lang.Integer.TYPE -> Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> Character::class.java
        else -> type
    }
}
