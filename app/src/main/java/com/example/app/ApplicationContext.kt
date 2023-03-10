package com.example.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.core.os.bundleOf
import com.jakewharton.threetenabp.AndroidThreeTen
import com.example.app.commons.util.AppForegroundObserver
import com.example.app.commons.util.AppStartup
import com.example.app.commons.util.Util
import com.example.app.di.ApplicationDependencies
import com.example.app.di.ApplicationDependencyProvider
import com.example.app.service.websocket.AppWebSocket
import com.example.app.service.websocket.WebSocketConnectionState
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class ApplicationContext : Application(), AppForegroundObserver.Listener {

    private val applicationScope = Util.getCustomCoroutineScope()

    private var userEngageStartTime: Long = System.currentTimeMillis()

    private var socketKeepAliveJob: Job? = null

    override fun onCreate() {
        AppStartup.getInstance().onApplicationCreate()
        super.onCreate()

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .setClassInstanceLimit(AppWebSocket::class.java, 1)
                .build()
        )

        Timber.plant(Timber.DebugTree())
        AndroidThreeTen.init(this)

        AppStartup.getInstance()
            .addBlocking("app-dependencies", this::initApplicationDependencies)
            .addBlocking("lifecycle-observer") {
                ApplicationDependencies.getAppForegroundObserver().addListener(this)
            }
            .execute()
    }

    private fun initApplicationDependencies() {
        ApplicationDependencies.init(this,
            ApplicationDependencyProvider(this)
        )
    }

    private fun initializeSocketIfRequired() {
        ApplicationDependencies.getAppWebSocket().apply {
            if (webSocketState.value == WebSocketConnectionState.DISCONNECTED) {
                forceNewWebSockets()
                connect()

                if (BuildConfig.DEBUG) {
                    webSocketState.onEach { state -> Log.d(TAG, "WebSocketState: $state") }
                        .launchIn(applicationScope)
                }
            }
        }
    }

    override fun onForeground() {
        Timber.d("App is foregrounded")
        socketKeepAliveJob?.cancel(CancellationException("App is now visible."))
        initializeSocketIfRequired()
        // TODO: send user engage
        userEngageStartTime = System.currentTimeMillis()
    }

    override fun onBackground() {
        Timber.d("App backgrounded")
        val engagedTime = System.currentTimeMillis() - userEngageStartTime
        Timber.d("JJ: onStop life=$engagedTime")
        val userAwayArgs = bundleOf(
            Constant.EXTRA_ENGAGED_TIME to engagedTime
        )
        // TODO: send user away
        socketKeepAliveJob = applicationScope.launch {
            delay(SOCKET_KEEP_ALIVE_TIMEOUT)
            ApplicationDependencies.getAppWebSocket().disconnect()
            Log.d(TAG, "Socket is destroyed. Reason: App is in background for so long.")
        }
    }

    companion object {
        const val TAG = "PepulLiv.App"
        const val SOCKET_KEEP_ALIVE_TIMEOUT = 5000L
    }
}