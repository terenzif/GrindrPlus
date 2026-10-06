package com.gpp.core

import com.gpp.utils.Hook
import com.gpp.utils.Task

fun Hook.logd(message: String) = Logger.d(message, LogSource.HOOK, this.hookName)
fun Hook.logi(message: String) = Logger.i(message, LogSource.HOOK, this.hookName)
fun Hook.logw(message: String) = Logger.w(message, LogSource.HOOK, this.hookName)
fun Hook.loge(message: String) = Logger.e(message, LogSource.HOOK, this.hookName)
fun Hook.logs(message: String) = Logger.s(message, LogSource.HOOK, this.hookName)

fun Task.logd(message: String) = Logger.d(message, LogSource.TASK, this.id)
fun Task.logi(message: String) = Logger.i(message, LogSource.TASK, this.id)
fun Task.logw(message: String) = Logger.w(message, LogSource.TASK, this.id)
fun Task.loge(message: String) = Logger.e(message, LogSource.TASK, this.id)
fun Task.logs(message: String) = Logger.s(message, LogSource.TASK, this.id)
