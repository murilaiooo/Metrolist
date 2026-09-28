package com.metrolist.music

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.metrolist.music.car.input.CarInputDecision
import com.metrolist.music.car.input.CarInputDiagnostics
import com.metrolist.music.car.input.CarInputRouter
import com.metrolist.music.car.input.CarKeySignature
import com.metrolist.music.car.input.CarMediaAction
import com.metrolist.music.car.importer.CarImportRepository
import com.metrolist.music.car.startup.CarStartupMilestone
import com.metrolist.music.car.startup.CarStartupMilestoneTracker
import com.metrolist.music.car.ui.CarApp
import com.metrolist.music.car.ui.CarPlayerConnectionHost
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.DownloadUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CarMainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var importRepository: CarImportRepository

    private lateinit var playerHost: CarPlayerConnectionHost
    private val startupTracker = CarStartupMilestoneTracker()
    private val inputRouter = CarInputRouter()
    internal val inputDiagnostics = CarInputDiagnostics()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startupTracker.mark(CarStartupMilestone.ACTIVITY_CREATED)

        playerHost =
            CarPlayerConnectionHost(
                context = this,
                database = database,
                scope = lifecycleScope,
            )

        setContent {
            CarApp(
                connectionFlow = playerHost.connection,
                database = database,
                downloadUtil = downloadUtil,
                importRepository = importRepository,
                inputDiagnostics = inputDiagnostics,
                startupTracker = startupTracker,
            )
        }
        startupTracker.mark(CarStartupMilestone.CONTENT_SET)
    }

    override fun onStart() {
        super.onStart()
        playerHost.start()
    }

    override fun onDestroy() {
        playerHost.dispose()
        super.onDestroy()
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean =
        handleCarKeyEvent(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean =
        handleCarKeyEvent(event) || super.onKeyUp(keyCode, event)

    private fun handleCarKeyEvent(event: KeyEvent): Boolean {
        val signature =
            CarKeySignature(
                keyCode = event.keyCode,
                scanCode = event.scanCode,
                source = event.source,
            )
        val decision = inputRouter.decide(signature, event.action)
        inputDiagnostics.record(signature, event.action, decision)

        if (!decision.consume) return false
        dispatchConfirmedVendorAction(decision)
        return true
    }

    private fun dispatchConfirmedVendorAction(decision: CarInputDecision) {
        val connection = playerHost.connection.value ?: return
        when (decision.action) {
            CarMediaAction.PLAY -> connection.play()
            CarMediaAction.PAUSE -> connection.pause()
            CarMediaAction.PLAY_PAUSE -> connection.togglePlayPause()
            CarMediaAction.NEXT -> connection.seekToNext()
            CarMediaAction.PREVIOUS -> connection.seekToPrevious()
            CarMediaAction.STOP -> connection.pause()
            null -> Unit
        }
    }
}
