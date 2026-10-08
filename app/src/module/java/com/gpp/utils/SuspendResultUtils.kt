package com.gpp.utils

fun withSuspendResult(args: Array<Any?>, result: Any, onResult: (Array<Any?>, Any) -> Any): Any {
    return if (result.toString() == "COROUTINE_SUSPENDED") {
        var unhook: Set<Unhook>? = null
        unhook = args.last()!!.javaClass.hook("invokeSuspend", HookStage.BEFORE) {
            unhook?.forEach { u -> u.unhook() }
            unhook = null
            it.setArg(0, onResult(args, it.arg(0)))
        }
        result
    } else {
        onResult(args, result)
    }
}
