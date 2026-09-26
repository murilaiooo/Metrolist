package com.metrolist.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Path

fun main() {
    val projectRoot = Path.of(System.getProperty("user.dir"))
    val runtimeResult = runCatching {
        DesktopRuntime.open(projectRoot)
    }

    application {
        val windowState =
            rememberWindowState(
                width = 1280.dp,
                height = 820.dp,
            )

        Window(
            onCloseRequest = {
                runtimeResult.getOrNull()?.close()
                exitApplication()
            },
            title = "MetroList Windows",
            state = windowState,
        ) {
            val runtime = runtimeResult.getOrNull()

            if (runtime != null) {
                MetroListDesktopApp(runtime)
            } else {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(48.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "MetroList Windows",
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Text("The app could not initialize.")
                            Text(
                                runtimeResult.exceptionOrNull()
                                    ?.let { it::class.simpleName }
                                    ?: "Unknown startup error",
                            )
                        }
                    }
                }
            }
        }
    }
}
