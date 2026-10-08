package com.gpp.utils

import com.gpp.GppXposed
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.function.Consumer

@Suppress("UNCHECKED_CAST")
class HookAdapter<Clazz>(
    private val chain: XposedInterface.Chain,
    private var result: Any? = UNSET,
    private var thrown: Throwable? = null,
    private var skipOriginal: Boolean = false,
    private var argArray: Array<Any?> = chain.args.toTypedArray(),
) {
    fun thisObject(): Clazz = chain.thisObject as Clazz

    fun nullableThisObject(): Clazz? = chain.thisObject as Clazz?

    fun method(): Member = chain.executable

    fun executable(): Executable = chain.executable

    fun <T : Any> arg(index: Int): T = argArray[index] as T

    fun <T : Any> arg(index: Int, clazz: Class<T>): T? {
        val argValue = argArray[index]
        return try {
            clazz.cast(argValue)
        } catch (_: ClassCastException) {
            convertToType(argValue, clazz) ?: handlePrimitiveDefaults(clazz)
        }
    }

    fun <T : Any> argNullable(index: Int): T? = argArray.getOrNull(index) as T?

    fun setArg(index: Int, value: Any?) {
        if (index < 0 || index >= argArray.size) return
        argArray[index] = value
    }

    fun args(): Array<Any?> = argArray

    fun getResult(): Any? = if (result === UNSET) null else result

    fun setResult(value: Any?) {
        result = value
        skipOriginal = true
        thrown = null
    }

    fun setThrowable(throwable: Throwable) {
        thrown = throwable
        skipOriginal = true
    }

    fun throwable(): Throwable? = thrown

    internal fun shouldSkipOriginal(): Boolean = skipOriginal

    internal fun consumeResult(): Any? {
        thrown?.let { throw it }
        return if (result === UNSET) null else result
    }

    fun invokeOriginal(): Any? = invokeOriginal(argArray)

    fun invokeOriginal(args: Array<Any?>): Any? {
        val exec = chain.executable
        val m = GppXposed.require()
        return when (exec) {
            is Method -> {
                val invoker = m.getInvoker(exec).setType(XposedInterface.Invoker.Type.ORIGIN)
                invoker.invoke(chain.thisObject, args)
            }
            else -> chain.proceed(args)
        }
    }

    fun invokeOriginalSafe(errorCallback: Consumer<Throwable>) {
        invokeOriginalSafe(argArray, errorCallback)
    }

    fun invokeOriginalSafe(args: Array<Any?>, errorCallback: Consumer<Throwable>) {
        runCatching {
            setResult(invokeOriginal(args))
        }.onFailure {
            errorCallback.accept(it)
        }
    }

    private fun invokeMethodSafe(obj: Any, methodName: String): Any? =
        try {
            obj::class.java.getMethod(methodName).invoke(obj)
        } catch (_: NoSuchMethodException) {
            null
        }

    private fun <T : Any> handlePrimitiveDefaults(clazz: Class<T>): T? =
        when (clazz) {
            Int::class.java -> 0 as T
            Double::class.java -> 0.0 as T
            Float::class.java -> 0f as T
            Long::class.java -> 0L as T
            Boolean::class.java -> false as T
            else -> null
        }

    fun <T : Any> convertToType(arg: Any?, clazz: Class<T>): T? {
        if (arg == null) return null
        return try {
            when (clazz) {
                String::class.java -> invokeMethodSafe(arg, "toString") as T
                Int::class.java -> invokeMethodSafe(arg, "toInt") as T
                Double::class.java -> invokeMethodSafe(arg, "toDouble") as T
                Float::class.java -> invokeMethodSafe(arg, "toFloat") as T
                Long::class.java -> invokeMethodSafe(arg, "toLong") as T
                Boolean::class.java -> invokeMethodSafe(arg, "toBoolean") as T
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val UNSET = Any()
    }
}
