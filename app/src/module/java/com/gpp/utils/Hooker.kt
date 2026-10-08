package com.gpp.utils

import com.gpp.GppXposed
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method

enum class HookStage {
    BEFORE,
    AFTER,
}

object Hooker {
    fun <T> hook(
        clazz: Class<T>,
        methodName: String,
        stage: HookStage,
        filter: (HookAdapter<T>) -> Boolean = { true },
        consumer: (HookAdapter<T>) -> Unit,
    ): Set<Unhook> {
        val methods = clazz.declaredMethods.filter { it.name == methodName } +
            clazz.methods.filter { it.name == methodName && it.declaringClass != clazz }
        return methods.distinctBy { methodKey(it) }.mapNotNull { method ->
            hookExecutable(method, stage, filter, consumer)
        }.toSet()
    }

    fun <T> hook(
        member: Member,
        stage: HookStage,
        filter: (HookAdapter<T>) -> Boolean = { true },
        consumer: (HookAdapter<T>) -> Unit,
    ): Unhook {
        require(member is Executable) { "Can only hook Method/Constructor, got $member" }
        return hookExecutable(member, stage, filter, consumer)
            ?: error("Failed to hook $member")
    }

    fun <T> hookConstructor(
        clazz: Class<T>,
        stage: HookStage,
        filter: (HookAdapter<T>) -> Boolean = { true },
        consumer: (HookAdapter<T>) -> Unit,
    ): Set<Unhook> =
        clazz.declaredConstructors.mapNotNull { ctor ->
            @Suppress("UNCHECKED_CAST")
            hookExecutable(ctor as Constructor<T>, stage, filter, consumer)
        }.toSet()

    fun <T> hookObjectMethod(
        clazz: Class<T>,
        instance: Any,
        methodName: String,
        stage: HookStage,
        hookConsumer: (HookAdapter<T>) -> Unit,
    ): List<() -> Unit> {
        val unhooks = mutableSetOf<Unhook>()
        hook(clazz, methodName, stage) { param ->
            if (param.nullableThisObject().let {
                    if (it == null) unhooks.forEach { u -> u.unhook() }
                    it != instance
                }
            ) {
                return@hook
            }
            hookConsumer(param)
        }.also { unhooks.addAll(it) }
        return unhooks.map { u -> { u.unhook() } }
    }

    fun <T> ephemeralHook(
        clazz: Class<T>,
        methodName: String,
        stage: HookStage,
        hookConsumer: (HookAdapter<T>) -> Unit,
    ) {
        val unhooks: MutableSet<Unhook> = HashSet()
        hook(clazz, methodName, stage) { param ->
            hookConsumer(param)
            unhooks.forEach { it.unhook() }
        }.also { unhooks.addAll(it) }
    }

    fun <T> ephemeralHookObjectMethod(
        clazz: Class<T>,
        instance: Any,
        methodName: String,
        stage: HookStage,
        hookConsumer: (HookAdapter<T>) -> Unit,
    ) {
        val unhooks: MutableSet<Unhook> = HashSet()
        hook(clazz, methodName, stage) { param ->
            if (param.nullableThisObject() != instance) return@hook
            unhooks.forEach { it.unhook() }
            hookConsumer(param)
        }.also { unhooks.addAll(it) }
    }

    fun <T> ephemeralHookConstructor(
        clazz: Class<T>,
        stage: HookStage,
        hookConsumer: (HookAdapter<T>) -> Unit,
    ) {
        val unhooks: MutableSet<Unhook> = HashSet()
        hookConstructor(clazz, stage) { param ->
            hookConsumer(param)
            unhooks.forEach { it.unhook() }
        }.also { unhooks.addAll(it) }
    }

    private fun <T> hookExecutable(
        executable: Executable,
        stage: HookStage,
        filter: (HookAdapter<T>) -> Boolean,
        consumer: (HookAdapter<T>) -> Unit,
    ): Unhook? {
        val xposed = GppXposed.module ?: return null
        val id = stableId(executable, stage)
        val hooker = XposedInterface.Hooker { chain ->
            when (stage) {
                HookStage.BEFORE -> {
                    val adapter = HookAdapter<T>(chain)
                    if (filter(adapter)) consumer(adapter)
                    if (adapter.shouldSkipOriginal()) {
                        return@Hooker adapter.consumeResult()
                    }
                    chain.proceed(adapter.args())
                }
                HookStage.AFTER -> {
                    var proceeded: Any? = null
                    var proceedError: Throwable? = null
                    try {
                        proceeded = chain.proceed()
                    } catch (t: Throwable) {
                        proceedError = t
                    }
                    val adapter = HookAdapter<T>(
                        chain,
                        result = proceeded,
                        thrown = proceedError,
                    )
                    if (filter(adapter)) consumer(adapter)
                    adapter.throwable()?.let { throw it }
                    adapter.getResult()
                }
            }
        }
        val handle = xposed.hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .setId(id)
            .intercept(hooker)
        HookHandleRegistry.put(id, handle)
        return Unhook {
            handle.unhook()
            HookHandleRegistry.remove(id)
        }
    }

    private fun methodKey(m: Method): String =
        m.declaringClass.name + "#" + m.name + m.parameterTypes.joinToString(",", "(", ")") { it.name }

    private fun stableId(executable: Executable, stage: HookStage): String {
        val owner = executable.declaringClass.name
        val name = when (executable) {
            is Method -> executable.name
            is Constructor<*> -> "<init>"
            else -> executable.name
        }
        val params = executable.parameterTypes.joinToString(",") { it.name }
        return "gpp:$owner#$name($params):$stage"
    }
}

fun <T> Class<T>.hookConstructor(
    stage: HookStage,
    consumer: (HookAdapter<T>) -> Unit,
) = Hooker.hookConstructor(this, stage, consumer = consumer)

fun <T> Class<T>.hookConstructor(
    stage: HookStage,
    filter: (HookAdapter<T>) -> Boolean,
    consumer: (HookAdapter<T>) -> Unit,
) = Hooker.hookConstructor(this, stage, filter, consumer)

fun <T> Class<T>.hook(
    methodName: String,
    stage: HookStage,
    consumer: (HookAdapter<T>) -> Unit,
): Set<Unhook> = Hooker.hook(this, methodName, stage, consumer = consumer)

fun <T> Class<T>.hook(
    methodName: String,
    stage: HookStage,
    filter: (HookAdapter<T>) -> Boolean,
    consumer: (HookAdapter<T>) -> Unit,
): Set<Unhook> = Hooker.hook(this, methodName, stage, filter, consumer)

fun Member.hook(
    stage: HookStage,
    consumer: (HookAdapter<Any>) -> Unit,
): Unhook = Hooker.hook(this, stage, consumer = consumer)

fun Member.hook(
    stage: HookStage,
    filter: (HookAdapter<Any>) -> Boolean,
    consumer: (HookAdapter<Any>) -> Unit,
): Unhook = Hooker.hook(this, stage, filter, consumer)

fun Array<Method>.hookAll(stage: HookStage, param: (HookAdapter<Any>) -> Unit) {
    filter { it.declaringClass != Object::class.java }.forEach {
        it.hook(stage, param)
    }
}
