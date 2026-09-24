package com.grindrplus

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.grindrplus.bridge.BridgeClient
import com.grindrplus.core.Config
import com.grindrplus.core.Constants
import com.grindrplus.core.EventManager
import com.grindrplus.core.InstanceManager
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.core.NetworkRepository
import com.grindrplus.core.TaskScheduler
import com.grindrplus.core.Utils
import com.grindrplus.core.Utils.handleImports
import com.grindrplus.core.http.Client
import com.grindrplus.core.http.Interceptor
import com.grindrplus.core.mapping.MappingDictionary
import com.grindrplus.persistence.GPDatabase
import com.grindrplus.ui.DialogManager
import com.grindrplus.utils.HookManager
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.PCHIP
import com.grindrplus.utils.TaskManager
import com.grindrplus.utils.hookConstructor
import dalvik.system.DexClassLoader
import de.robv.android.xposed.XposedHelpers.callMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference

@SuppressLint("StaticFieldLeak")
object GrindrPlus {
    lateinit var context: Context
        private set
    lateinit var classLoader: ClassLoader
        private set
    lateinit var database: GPDatabase
        private set
    lateinit var bridgeClient: BridgeClient
        internal set
    lateinit var instanceManager: InstanceManager
        private set
    lateinit var httpClient: Client
        private set
    lateinit var packageName: String
        private set

    lateinit var hookManager: HookManager

    var shouldTriggerAntiblock = true
    var blockCaller: String = ""
    var isImportingSomething = false
    var myProfileId: String = ""

    private var isInitialized = false
    private var isMainInitialized = false
    private var isInstanceManagerInitialized = false

    var spline = PCHIP(Constants.DEFAULT_SPLINE_POINTS)

    val currentActivity: Activity?
        get() = currentActivityRef?.get()

    // Core class names: prefer active mapping pack; fallback literals = Grindr 26.16.1 (179451).
    // JADX puts default-package R8 classes under "defpackage" in sources; runtime names are short DEX names.
    internal val userAgent: String
        get() = MappingDictionary.resolve("core.userAgent", "lrc") // search for 'grindr3/'
    internal val userSession: String
        get() = MappingDictionary.resolve("core.userSession", "atc") // implements UserSession
    private val deviceInfo: String
        get() = MappingDictionary.resolve("core.deviceInfo", "wh3")
    internal val grindrLocationProvider: String
        get() = MappingDictionary.resolve("core.grindrLocationProvider", "sw5")
    // Was ServerDrivenCascadeRepo. On 26.16.1 cascade data layer is default-package us1.
    internal val serverDrivenCascadeRepo: String
        get() = MappingDictionary.resolve("core.serverDrivenCascadeRepo", "us1")
    internal val ageVerificationActivity: String
        get() = MappingDictionary.resolve(
            "core.ageVerificationActivity",
            "com.grindrapp.android.ageverification.presentation.ui.AgeVerificationActivity"
        )
    internal val browseExploreActivity: String
        get() = MappingDictionary.resolve(
            "core.browseExploreActivity",
            "com.grindrapp.android.ui.browse.BrowseExploreMapActivity"
        )
    internal val serverNotification: String
        get() = MappingDictionary.resolve(
            "core.serverNotification",
            "com.grindrapp.android.network.websocket.model.WebSocketNotification\$ServerNotification"
        )

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val taskScheduer = TaskScheduler(ioScope)
    internal val taskManager = TaskManager(taskScheduer)
    private var currentActivityRef: WeakReference<Activity>? = null

    private val splineDataEndpoint = Constants.SPLINE_DATA_ENDPOINT

    val serverNotifications = EventManager.serverNotifications

    fun init(modulePath: String, application: Application,
             versionCodes: IntArray, versionNames: Array<String>) {

        if (isInitialized) {
            Logger.d("GrindrPlus already initialized, skipping", LogSource.MODULE)
            return
        }

        setupCrashLogging()

        this.context = application
        this.bridgeClient = BridgeClient(context)

        Logger.initialize(context, bridgeClient, true)
        Logger.i("Initializing GrindrPlus...", LogSource.MODULE)

        loadMappingPack(modulePath, application)

        DialogManager.checkVersionCodes(context, versionCodes, versionNames)

        runBlocking {
            val connected = try {
                withTimeout(10000) {
                    bridgeClient.connectWithRetry(5, 1000)
                }
            } catch (e: Exception) {
                Logger.e("Connection timeout: ${e.message}", LogSource.MODULE)
                false
            }

            if (!connected) {
                Logger.e("Failed to connect to the bridge service", LogSource.MODULE)
                DialogManager.shouldShowBridgeConnectionError = true
            }

            Config.initialize(application.packageName)
        }

        val newModule = File(context.filesDir, "grindrplus.dex")
        File(modulePath).copyTo(newModule, true)
        newModule.setReadOnly()

        this.classLoader =
            DexClassLoader(newModule.absolutePath, null, null, context.classLoader)
        this.database = GPDatabase.create(context)
        this.hookManager = HookManager()
        this.instanceManager = InstanceManager(classLoader)
        this.packageName = context.packageName

        runBlocking {
            if (bridgeClient.shouldRegenAndroidId(packageName)) {
                Logger.i("Generating new Android device ID", LogSource.MODULE)
                val androidId = java.util.UUID.randomUUID()
                    .toString().replace("-", "").lowercase().take(16)
                Config.put("android_device_id", androidId)
            }

            val forcedCoordinates = bridgeClient.getForcedLocation(packageName)

            if (forcedCoordinates.isNotEmpty()) {
                val parts = forcedCoordinates.split(",").map { it.trim() }
                if (parts.size != 2 || parts.any { it.toDoubleOrNull() == null }) {
                    Logger.w("Invalid forced coordinates format: $forcedCoordinates", LogSource.MODULE)
                } else {
                    if (parts[0] == "0.0" && parts[1] == "0.0") {
                        Logger.w("Ignoring forced coordinates: $forcedCoordinates", LogSource.MODULE)
                    } else {
                        Logger.i("Using forced coordinates: $forcedCoordinates", LogSource.MODULE)
                        Config.put("forced_coordinates", forcedCoordinates)
                    }
                }
            } else if (Config.get("forced_coordinates", "") != "") {
                Logger.i("Clearing previously set forced coordinates", LogSource.MODULE)
                Config.put("forced_coordinates", "")
            }
        }

        registerActivityLifecycleCallbacks(application)

        if (DialogManager.shouldShowVersionMismatchDialog) {
            Logger.i("Version mismatch detected, stopping initialization", LogSource.MODULE)
            return
        }

        try {
            setupInstanceManager()
            setupServerNotificationHook()
        } catch (t: Throwable) {
            // Soft-fail: InstanceManager already skips missing classes per-name. Do not abort
            // before HookManager — MVP hooks must still initialize.
            Logger.e("Failed to hook critical classes (continuing to HookManager): ${t.message}", LogSource.MODULE)
            Logger.writeRaw(t.stackTraceToString())
            showToast(Toast.LENGTH_LONG, "Failed to hook critical classes: ${t.message}")
        }

        NetworkRepository.fetchRemoteData(splineDataEndpoint) { points ->
            spline = PCHIP(points)
            Logger.i("Updated spline with remote data", LogSource.MODULE)
        }

        try {
            runBlocking {
                val startTime = System.currentTimeMillis()
                initializeCore()
                val initTime = System.currentTimeMillis() - startTime
                Logger.i("Initialization completed in $initTime ms", LogSource.MODULE)
            }
            isInitialized = true
        } catch (t: Throwable) {
            Logger.e("Failed to initialize: ${t.message}", LogSource.MODULE)
            Logger.writeRaw(t.stackTraceToString())
            showToast(Toast.LENGTH_LONG, "Failed to initialize: ${t.message}")
            return
        }
    }

    private fun setupServerNotificationHook() {
        try {
            classLoader.loadClass(serverNotification).hookConstructor(HookStage.AFTER) { param ->
                try {
                    val serverNotification = param.thisObject()
                    val typeValue = callMethod(serverNotification, "getTypeValue") as String
                    val notificationId = callMethod(serverNotification, "getNotificationId") as String?
                    val payload = callMethod(serverNotification, "getPayload") as JSONObject?
                    val status = callMethod(serverNotification, "getStatus") as Int?
                    val refValue = callMethod(serverNotification, "getRefValue") as String?

                    EventManager.emitServerNotification(typeValue, notificationId, payload, status, refValue)
                    Logger.d("ServerNotification hooked and event emitted: $typeValue", LogSource.MODULE)
                } catch (e: Exception) {
                    Logger.e("Failed to emit server notification event: ${e.message}", LogSource.MODULE)
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to setup server notification hook: ${e.message}", LogSource.MODULE)
        }
    }

    private fun registerActivityLifecycleCallbacks(application: Application) {
        application.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                when {
                    activity.javaClass.name == ageVerificationActivity -> {
                        DialogManager.showAgeVerificationComplianceDialog(activity)
                    }
                    activity.javaClass.name == browseExploreActivity -> {
                        if ((Config.get("maps_api_key", "") as String).isEmpty()) {
                            executeAsync {
                                if (!bridgeClient.isLSPosed()) {
                                    withContext(Dispatchers.Main) {
                                        DialogManager.showMapsApiKeyDialog(activity)
                                    }
                                }
                            }
                        }
                    }
                    DialogManager.shouldShowBridgeConnectionError -> {
                        DialogManager.showBridgeConnectionError(activity)
                        DialogManager.shouldShowBridgeConnectionError = false
                    }
                    DialogManager.shouldShowVersionMismatchDialog -> {
                        DialogManager.showVersionMismatchDialog(activity)
                        DialogManager.shouldShowVersionMismatchDialog = false
                    }
                    DialogManager.shouldShowMappingMissingDialog -> {
                        DialogManager.showMappingMissingDialog(activity)
                        DialogManager.shouldShowMappingMissingDialog = false
                    }
                }

                if (isImportingSomething) {
                    handleImports(activity)
                }
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                Logger.d("Resuming activity: ${activity.javaClass.name}", LogSource.MODULE)
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                Logger.d("Pausing activity: ${activity.javaClass.name}", LogSource.MODULE)
                if (currentActivity == activity) {
                    currentActivityRef = null
                }
            }

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun loadMappingPack(modulePath: String, application: Application) {
        val versionCode = installedVersionCode(application)
        val versionName = installedVersionName(application)
        // Remote → disk cache → bundled assets → literals (all soft-fail).
        val pack = MappingDictionary.loadForVersion(
            modulePath = modulePath,
            versionCode = versionCode,
            cacheDir = application.filesDir,
        )
        if (pack != null) {
            Logger.i(
                "Mapping pack loaded: ${pack.versionName} (code ${pack.versionCode}), " +
                    "${pack.symbols.size} symbols, confidence=${pack.confidence}",
                LogSource.MODULE
            )
            val missingCore = MappingDictionary.missingCoreKeys(pack)
            if (missingCore.isNotEmpty()) {
                val detail =
                    "Mapping pack for Grindr $versionName (code $versionCode) is incomplete.\n" +
                        "Missing core symbols: ${missingCore.joinToString(", ")}"
                Logger.w(detail, LogSource.MODULE)
                // Don't stack on top of the hard version-mismatch dialog.
                if (!DialogManager.shouldShowVersionMismatchDialog) {
                    DialogManager.mappingMissingDetail = detail
                    DialogManager.shouldShowMappingMissingDialog = true
                    showToast(Toast.LENGTH_LONG, "GrindrPlus: mapping incomplete for $versionName")
                }
            }
        } else {
            val detail =
                "No mapping pack found for Grindr $versionName (code $versionCode).\n" +
                    "Falling back to compile-time literals (likely wrong for this build)."
            Logger.w(detail, LogSource.MODULE)
            if (!DialogManager.shouldShowVersionMismatchDialog) {
                DialogManager.mappingMissingDetail = detail
                DialogManager.shouldShowMappingMissingDialog = true
                showToast(Toast.LENGTH_LONG, "GrindrPlus: no mapping for $versionName ($versionCode)")
            }
        }
    }

    private fun installedVersionName(application: Application): String {
        return try {
            application.packageManager.getPackageInfo(application.packageName, 0).versionName
                ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun installedVersionCode(application: Application): Int {
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.packageManager.getPackageInfo(
                    application.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                application.packageManager.getPackageInfo(application.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode
            }
        } catch (t: Throwable) {
            Logger.e("Failed to read installed versionCode: ${t.message}", LogSource.MODULE)
            0
        }
    }

    private fun setupInstanceManager() {
        if (isInstanceManagerInitialized) {
            Logger.d("InstanceManager already initialized, skipping", LogSource.MODULE)
            return
        }

        val hooked = instanceManager.hookClassConstructors(
            userAgent,
            userSession,
            deviceInfo,
            grindrLocationProvider,
            serverDrivenCascadeRepo
        )
        Logger.i(
            "InstanceManager hooked ${hooked.size}/5 classes: ${hooked.joinToString()}",
            LogSource.MODULE
        )

        instanceManager.setCallback(userSession) { uSession ->
            instanceManager.setCallback(userAgent) { uAgent ->
                instanceManager.setCallback(deviceInfo) { dInfo ->
                    httpClient = Client(Interceptor(uSession, uAgent, dInfo))
                    executeAsync {
                        kotlinx.coroutines.delay(1500)
                        NetworkRepository.fetchOwnUserId()
                    }
                    taskManager.registerTasks()
                }
            }
        }

        isInstanceManagerInitialized = true
    }

    private suspend fun initializeCore() {
        if (isMainInitialized) {
            Logger.d("Core already initialized, skipping", LogSource.MODULE)
            return
        }

        Logger.i("Initializing GrindrPlus core...", LogSource.MODULE)

        if ((Config.get("reset_database", false) as Boolean)) {
            Logger.i("Resetting database...", LogSource.MODULE)
            database.clearAllTables()
            Config.put("reset_database", false)
        }

        hookManager.init()
        isMainInitialized = true
    }

    fun runOnMainThread(appContext: Context? = null, block: (Context) -> Unit) {
        Utils.runOnMainThread(appContext, block)
    }

    fun runOnMainThreadWithCurrentActivity(block: (Activity) -> Unit) {
        runOnMainThread {
            currentActivity?.let { activity ->
                block(activity)
            } ?: Logger.e("Cannot execute action - no active activity", LogSource.MODULE)
        }
    }

    fun executeAsync(block: suspend () -> Unit) {
        ioScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Logger.e("Async operation failed: ${e.message}", LogSource.MODULE)
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }

    fun showToast(duration: Int, message: String, appContext: Context? = null) {
        Utils.showToast(duration, message, appContext)
    }

    fun loadClass(name: String): Class<*> {
        return classLoader.loadClass(name)
    }

    fun restartGrindr(timeout: Long = 0, toast: String? = null) {
        toast?.let { showToast(Toast.LENGTH_LONG, it) }

        if (timeout > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                context.startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
            }, timeout)
        } else {
            val intent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            context.startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun setupCrashLogging() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Logger.e("Uncaught exception in thread: ${thread.name}", LogSource.MODULE)
                Logger.e("Exception: ${throwable.javaClass.simpleName}: ${throwable.message}", LogSource.MODULE)
                Logger.writeRaw("Thread: ${thread.name} (id=${thread.id})")
                Logger.writeRaw("Exception: ${throwable.javaClass.name}")
                Logger.writeRaw("Message: ${throwable.message}")
                Logger.writeRaw("Stack trace:")
                Logger.writeRaw(throwable.stackTraceToString())

                throwable.cause?.let { cause ->
                    Logger.writeRaw("Caused by: ${cause.javaClass.name}: ${cause.message}")
                    Logger.writeRaw(cause.stackTraceToString())
                }
            } catch (e: Exception) {
                Timber.tag("GrindrPlus").e("Failed to log crash: ${e.message}")
                Timber.tag("GrindrPlus").e("Original crash: ${throwable.message}")
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
