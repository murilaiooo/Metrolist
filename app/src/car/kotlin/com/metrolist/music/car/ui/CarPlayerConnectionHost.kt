package com.metrolist.music.car.ui

import android.app.ForegroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.MusicService.MusicBinder
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class CarPlayerConnectionHost(
    private val context: Context,
    private val database: MusicDatabase,
    private val scope: CoroutineScope,
) {
    private val _connection = MutableStateFlow<PlayerConnection?>(null)
    val connection: StateFlow<PlayerConnection?> = _connection.asStateFlow()

    private var isBound = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val binder = service as? MusicBinder ?: return
                _connection.value?.dispose()
                _connection.value = PlayerConnection(context, binder, database, scope)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                _connection.value?.dispose()
                _connection.value = null
                isBound = false
            }
        }

    fun start() {
        val serviceIntent = Intent(context, MusicService::class.java)
        if (!MusicService.isRunning) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (error: ForegroundServiceStartNotAllowedException) {
                Timber.w(error, "Car cannot start MusicService from background")
            } catch (error: IllegalStateException) {
                Timber.w(error, "Car failed to start MusicService")
            }
        }

        if (!isBound) {
            isBound = context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun dispose() {
        _connection.value?.dispose()
        _connection.value = null

        if (isBound) {
            runCatching { context.unbindService(serviceConnection) }
                .onFailure { Timber.w(it, "Car MusicService unbind failed") }
            isBound = false
        }
    }
}
