package com.metrolist.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val projectRoot = Path.of(System.getProperty("user.dir"))
    val runtime = DesktopRuntime.open(projectRoot)

    application {
        val windowState =
            rememberWindowState(
                width = 1280.dp,
                height = 820.dp,
            )

        Window(
            onCloseRequest = {
                runtime.close()
                exitApplication()
            },
            title = "MetroList Windows",
            state = windowState,
        ) {
            MetroListDesktopApp(runtime)
        }
    }
}
