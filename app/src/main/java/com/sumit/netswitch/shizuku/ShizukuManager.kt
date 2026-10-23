package com.sumit.netswitch.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.sumit.netswitch.BuildConfig
import com.sumit.netswitch.IUserService
import com.sumit.netswitch.data.CommandLogRepository
import com.sumit.netswitch.model.CommandResult
import com.sumit.netswitch.model.ShizukuStatus
import com.sumit.netswitch.service.UserService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    const val SHIZUKU_PERMISSION_REQUEST_CODE = 4421

    private val mutex = Mutex()
    private var appContext: Context? = null
    private var userService: IUserService? = null
    private var connectionDeferred: CompletableDeferred<IUserService>? = null
    private var isListenersRegistered = false

    private val _statusFlow = MutableStateFlow(
        ShizukuStatus(
            isInstalled = false,
            isRunning = false,
            isPermissionGranted = false,
            isServiceConnected = false
        )
    )
    val statusFlow: StateFlow<ShizukuStatus> = _statusFlow.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        updateStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died")
        synchronized(this) {
            userService = null
            connectionDeferred?.cancel()
            connectionDeferred = null
        }
        updateStatus()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            Log.d(TAG, "Permission result received: $grantResult")
            updateStatus()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "UserService onServiceConnected: $name")
            if (service != null && service.pingBinder()) {
                val serviceInterface = IUserService.Stub.asInterface(service)
                synchronized(this@ShizukuManager) {
                    userService = serviceInterface
                    connectionDeferred?.complete(serviceInterface)
                }
            } else {
                synchronized(this@ShizukuManager) {
                    userService = null
                    connectionDeferred?.completeExceptionally(IllegalStateException("Invalid or dead binder received"))
                }
            }
            updateStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "UserService onServiceDisconnected: $name")
            synchronized(this@ShizukuManager) {
                userService = null
                connectionDeferred = null
            }
            updateStatus()
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!isListenersRegistered) {
            try {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
                isListenersRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register Shizuku listeners: ${e.message}", e)
            }
        }
        updateStatus(context)
    }

    fun isShizukuInstalled(context: Context): Boolean {
        // 1. If Shizuku or Sui binder is already active, Shizuku is definitely installed and running
        if (isShizukuRunning()) {
            return true
        }

        val packageNamesToCheck = listOf(
            SHIZUKU_PACKAGE_NAME,
            "moe.shizuku.privileged.api.debug",
            "rikka.sui"
        )

        for (pkg in packageNamesToCheck) {
            val found = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        pkg,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(pkg, 0)
                }
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                Log.d(TAG, "Package lookup error for $pkg: ${e.message}")
                false
            }

            if (found) return true

            // Secondary check via launch intent in case component querying behaves differently
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) return true
            } catch (_: Exception) {
                // Ignore
            }
        }

        return false
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku running: ${e.message}")
            false
        }
    }

    fun isPermissionGranted(): Boolean {
        return try {
            if (!isShizukuRunning()) return false
            if (Shizuku.isPreV11()) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku permission: ${e.message}")
            false
        }
    }

    fun requestPermission() {
        try {
            if (isShizukuRunning()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission: ${e.message}", e)
        }
    }

    fun updateStatus(context: Context? = null) {
        val targetContext = context ?: appContext
        val running = isShizukuRunning()
        val granted = if (running) isPermissionGranted() else false
        val installed = running || (if (targetContext != null) isShizukuInstalled(targetContext) else _statusFlow.value.isInstalled)
        val connected = userService != null && (userService?.asBinder()?.pingBinder() == true)

        _statusFlow.value = ShizukuStatus(
            isInstalled = installed,
            isRunning = running,
            isPermissionGranted = granted,
            isServiceConnected = connected
        )
    }

    private suspend fun getOrBindService(context: Context): IUserService? = mutex.withLock {
        if (userService != null && userService?.asBinder()?.pingBinder() == true) {
            return userService
        }

        if (!isShizukuRunning() || !isPermissionGranted()) {
            return null
        }

        val deferred = CompletableDeferred<IUserService>()
        connectionDeferred = deferred

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, UserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        try {
            Shizuku.bindUserService(args, serviceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService failed: ${e.message}", e)
            connectionDeferred = null
            return null
        }

        return withTimeoutOrNull(7000L) {
            try {
                deferred.await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to await user service connection: ${e.message}", e)
                null
            }
        }
    }

    suspend fun executeCommand(context: Context, command: String): CommandResult = withContext(Dispatchers.IO) {
        if (!isShizukuInstalled(context) && !isShizukuRunning()) {
            val res = CommandResult.error("Shizuku app is not installed.")
            CommandLogRepository.addLog(command, res)
            return@withContext res
        }

        if (!isShizukuRunning()) {
            val res = CommandResult.error("Shizuku is not running.")
            CommandLogRepository.addLog(command, res)
            return@withContext res
        }

        if (!isPermissionGranted()) {
            val res = CommandResult.error("Permission not granted by Shizuku.")
            CommandLogRepository.addLog(command, res)
            return@withContext res
        }

        val service = getOrBindService(context)
        if (service == null) {
            val res = CommandResult.error("Could not bind to privileged UserService via Shizuku.")
            CommandLogRepository.addLog(command, res)
            return@withContext res
        }

        return@withContext try {
            val rawJsonResult = service.runCommand(command)
            val result = CommandResult.fromJson(rawJsonResult)
            CommandLogRepository.addLog(command, result)
            updateStatus(context)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Remote exception executing command '$command': ${e.message}", e)
            val res = CommandResult.error("Failed to execute via Shizuku: ${e.message}")
            CommandLogRepository.addLog(command, res)
            synchronized(this@ShizukuManager) {
                userService = null
            }
            updateStatus(context)
            res
        }
    }
}
