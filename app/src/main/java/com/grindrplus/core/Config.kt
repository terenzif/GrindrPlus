package com.grindrplus.core

import android.content.Context
import com.grindrplus.GrindrPlus
import com.grindrplus.manager.utils.AppCloneUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

object Config {
    private var localConfig = JSONObject()
    @Volatile private var currentPackageName = Constants.GRINDR_PACKAGE_NAME
    private val GLOBAL_SETTINGS = listOf("first_launch", "analytics", "discreet_icon", "material_you", "debug_mode", "disable_permission_checks", "custom_manifest", "maps_api_key")
    private val configMutex = Mutex()
    private val configCache = AtomicReference<Map<String, Any>>(emptyMap())

    suspend fun initialize(packageName: String? = null) {
        configMutex.withLock {
            if (packageName != null) {
                Logger.d("Initializing config for package: $packageName", LogSource.MANAGER)
            }

            localConfig = readRemoteConfig()

            if (packageName != null) {
                currentPackageName = packageName
            }

            migrateToMultiCloneFormat()
            updateCache()
        }
    }

    private fun updateCache() {
        val newCache = HashMap<String, Any>()

        // Global settings
        for (key in GLOBAL_SETTINGS) {
            localConfig.opt(key)?.let { newCache[key] = it }
        }

        // Package settings
        val clones = localConfig.optJSONObject("clones")
        if (clones != null) {
            val pkgKeys = clones.keys()
            while (pkgKeys.hasNext()) {
                val pkgName = pkgKeys.next()
                val pkgConfig = clones.optJSONObject(pkgName) ?: continue

                val keys = pkgConfig.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    newCache["$pkgName/$key"] = pkgConfig.get(key)

                    if (key == "hooks") {
                        val hooks = pkgConfig.optJSONObject("hooks")
                        if (hooks != null) {
                            val hookKeys = hooks.keys()
                            while (hookKeys.hasNext()) {
                                val hookName = hookKeys.next()
                                hooks.optJSONObject(hookName)?.let { hookObj ->
                                    if (hookObj.has("enabled")) {
                                        newCache["$pkgName/hooks/$hookName"] = hookObj.getBoolean("enabled")
                                    }
                                }
                            }
                        }
                    } else if (key == "tasks") {
                        val tasks = pkgConfig.optJSONObject("tasks")
                        if (tasks != null) {
                            val taskKeys = tasks.keys()
                            while (taskKeys.hasNext()) {
                                val taskId = taskKeys.next()
                                tasks.optJSONObject(taskId)?.let { taskObj ->
                                    if (taskObj.has("enabled")) {
                                        newCache["$pkgName/tasks/$taskId"] = taskObj.getBoolean("enabled")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        configCache.set(newCache)
    }

    private fun isGlobalSetting(name: String): Boolean {
        return name in GLOBAL_SETTINGS
    }

    private suspend fun migrateToMultiCloneFormat() {
        if (!localConfig.has("clones")) {
            Logger.d("Migrating to multi-clone format", LogSource.MANAGER)
            val cloneSettings = JSONObject()

            if (localConfig.has("hooks")) {
                val defaultPackageConfig = JSONObject()
                defaultPackageConfig.put("hooks", localConfig.get("hooks"))
                cloneSettings.put(Constants.GRINDR_PACKAGE_NAME, defaultPackageConfig)

                val keysToMove = mutableListOf<String>()
                val keys = localConfig.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key != "hooks" && !isGlobalSetting(key)) {
                        defaultPackageConfig.put(key, localConfig.get(key))
                        keysToMove.add(key)
                    }
                }
                keysToMove.forEach { localConfig.remove(it) }
            } else {
                cloneSettings.put(Constants.GRINDR_PACKAGE_NAME, JSONObject().put("hooks", JSONObject()))
            }

            localConfig.put("clones", cloneSettings)
            writeRemoteConfig(localConfig)
        }

        ensurePackageExists(currentPackageName)
    }

    suspend fun setCurrentPackage(packageName: String) {
        configMutex.withLock {
            Logger.d("Setting current package to $packageName", LogSource.MANAGER)
            currentPackageName = packageName
            ensurePackageExists(packageName)
            updateCache()
        }
    }

    fun getCurrentPackage(): String {
        return currentPackageName
    }

    private suspend fun ensurePackageExists(packageName: String) {
        Logger.d("Ensuring package $packageName exists in config", LogSource.MANAGER)
        val clones = localConfig.optJSONObject("clones") ?: JSONObject().also {
            localConfig.put("clones", it)
        }

        if (!clones.has(packageName)) {
            clones.put(packageName, JSONObject().put("hooks", JSONObject()))
            writeRemoteConfig(localConfig)
        }
    }

    fun getAvailablePackages(context: Context): List<String> = runBlocking {
        configMutex.withLock {
            Logger.d("Getting available packages", LogSource.MANAGER)
            val installedClones = listOf(Constants.GRINDR_PACKAGE_NAME) + AppCloneUtils.getExistingClones(context)
            val clones = localConfig.optJSONObject("clones") ?: return@runBlocking listOf(Constants.GRINDR_PACKAGE_NAME)

            return@runBlocking installedClones.filter { pkg ->
                clones.has(pkg)
            }
        }
    }

    suspend fun readRemoteConfig(): JSONObject {
        return try {
            GrindrPlus.bridgeClient.getConfig()
        } catch (e: Exception) {
            Logger.e("Failed to read config file: ${e.message}", LogSource.MANAGER)
            Logger.writeRaw(e.stackTraceToString())
            JSONObject().put("clones", JSONObject().put(
                Constants.GRINDR_PACKAGE_NAME,
                JSONObject().put("hooks", JSONObject()))
            )
        }
    }

    suspend fun writeRemoteConfig(json: JSONObject) {
        try {
            GrindrPlus.bridgeClient.setConfig(json)
        } catch (e: IOException) {
            Logger.e("Failed to write config file: ${e.message}", LogSource.MANAGER)
            Logger.writeRaw(e.stackTraceToString())
        }
    }

    private fun getCurrentPackageConfig(): JSONObject {
        val clones = localConfig.optJSONObject("clones")
            ?: JSONObject().also { localConfig.put("clones", it) }

        return clones.optJSONObject(currentPackageName)
            ?: JSONObject().also { clones.put(currentPackageName, it) }
    }

    suspend fun put(name: String, value: Any) {
        configMutex.withLock {
            Logger.d("Setting $name to $value", LogSource.MANAGER)
            if (isGlobalSetting(name)) {
                localConfig.put(name, value)
            } else {
                val packageConfig = getCurrentPackageConfig()
                packageConfig.put(name, value)
            }

            writeRemoteConfig(localConfig)
            updateCache()
        }
    }

    fun get(name: String, default: Any, autoPut: Boolean = false): Any {
        val cache = configCache.get()
        val key = if (isGlobalSetting(name)) name else "$currentPackageName/$name"
        val rawValue = cache[key]

        if (rawValue == null) {
            if (autoPut) {
                GrindrPlus.executeAsync {
                    put(name, default)
                }
            }
            return default
        }

        return when (default) {
            is Number -> {
                if (rawValue is String) {
                    try {
                        rawValue.toInt()
                    } catch (_: NumberFormatException) {
                        try {
                            rawValue.toDouble()
                        } catch (_: NumberFormatException) {
                            default
                        }
                    }
                } else {
                    rawValue as? Number ?: default
                }
            }
            else -> rawValue
        }
    }

    suspend fun setHookEnabled(hookName: String, enabled: Boolean) {
        configMutex.withLock {
            Logger.d("Setting hook $hookName to $enabled", LogSource.MANAGER)
            val packageConfig = getCurrentPackageConfig()
            val hooks = packageConfig.optJSONObject("hooks")
                ?: JSONObject().also { packageConfig.put("hooks", it) }

            hooks.optJSONObject(hookName)?.put("enabled", enabled)
            writeRemoteConfig(localConfig)
            updateCache()
        }
    }

    fun isHookEnabled(hookName: String): Boolean {
        val cache = configCache.get()
        val key = "$currentPackageName/hooks/$hookName"
        return cache[key] as? Boolean == true
    }

    suspend fun setTaskEnabled(taskId: String, enabled: Boolean) {
        configMutex.withLock {
            Logger.d("Setting task $taskId to $enabled", LogSource.MANAGER)
            val packageConfig = getCurrentPackageConfig()
            val tasks = packageConfig.optJSONObject("tasks")
                ?: JSONObject().also { packageConfig.put("tasks", it) }

            tasks.optJSONObject(taskId)?.put("enabled", enabled)
            writeRemoteConfig(localConfig)
            updateCache()
        }
    }

    fun isTaskEnabled(taskId: String): Boolean {
        val cache = configCache.get()
        val key = "$currentPackageName/tasks/$taskId"
        return cache[key] as? Boolean == true
    }

    fun getTasksSettings(): Map<String, Pair<String, Boolean>> = runBlocking {
        configMutex.withLock {
            Logger.d("Getting tasks settings", LogSource.MANAGER)
            val packageConfig = getCurrentPackageConfig()
            val tasks = packageConfig.optJSONObject("tasks") ?: return@runBlocking emptyMap()
            val map = mutableMapOf<String, Pair<String, Boolean>>()

            val keys = tasks.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = tasks.getJSONObject(key)
                map[key] = Pair(obj.getString("description"), obj.getBoolean("enabled"))
            }

            return@runBlocking map
        }
    }

    suspend fun initTaskSettings(taskId: String, description: String, state: Boolean) {
        configMutex.withLock {
            Logger.d("Initializing task settings for $taskId", LogSource.MANAGER)
            val packageConfig = getCurrentPackageConfig()
            val tasks = packageConfig.optJSONObject("tasks")
                ?: JSONObject().also { packageConfig.put("tasks", it) }

            if (tasks.optJSONObject(taskId) == null) {
                tasks.put(taskId, JSONObject().apply {
                    put("description", description)
                    put("enabled", state)
                })

                writeRemoteConfig(localConfig)
                updateCache()
            }
        }
    }

    suspend fun initHookSettings(name: String, description: String, state: Boolean) {
        configMutex.withLock {
            Logger.d("Initializing hook settings for $name", LogSource.MANAGER)
            val packageConfig = getCurrentPackageConfig()
            val hooks = packageConfig.optJSONObject("hooks")
                ?: JSONObject().also { packageConfig.put("hooks", it) }

            if (hooks.optJSONObject(name) == null) {
                hooks.put(name, JSONObject().apply {
                    put("description", description)
                    put("enabled", state)
                })

                writeRemoteConfig(localConfig)
                updateCache()
            }
        }
    }

    fun getHooksSettings(): Map<String, Pair<String, Boolean>> = runBlocking {
        configMutex.withLock {
            Logger.d("Getting hooks settings", LogSource.MANAGER)
            val packageConfig = getCurrentPackageConfig()
            val hooks = packageConfig.optJSONObject("hooks") ?: return@runBlocking emptyMap()
            val map = mutableMapOf<String, Pair<String, Boolean>>()

            val keys = hooks.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = hooks.getJSONObject(key)
                map[key] = Pair(obj.getString("description"), obj.getBoolean("enabled"))
            }

            return@runBlocking map
        }
    }
}
