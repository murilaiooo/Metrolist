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
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if ("--version" in args) {
        println(METROLIST_WINDOWS_VERSION)
        exitProcess(0)
    }

    val projectRoot = Path.of(System.getProperty("user.dir"))

    if ("--startup-check" in args) {
        val report = StartupChecks.forCurrentInstall(projectRoot).run()
        report.checks.forEach { check ->
            println(
                "STARTUP_CHECK " + check.name + "=" +
                    if (check.ok) "OK" else "FAIL",
            )
        }
        println("STARTUP_CHECK_RESULT=" + if (report.healthy) "OK" else "FAIL")
        exitProcess(if (report.healthy) 0 else 2)
    }

    if ("--runtime-open-check" in args) {
        val result =
            runCatching {
                DesktopRuntime.open(projectRoot).use { }
            }
        result.onSuccess {
            println("RUNTIME_OPEN_CHECK=OK")
        }.onFailure { error ->
            println(
                "RUNTIME_OPEN_CHECK=FAIL " +
                    error::class.qualifiedName +
                    " " +
                    error.message.orEmpty(),
            )
        }
        exitProcess(if (result.isSuccess) 0 else 3)
    }

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
                            val startupError = runtimeResult.exceptionOrNull()
                            Text(
                                startupError
                                    ?.let { it::class.simpleName }
                                    ?: "Unknown startup error",
                            )
                            startupError?.message?.takeIf(String::isNotBlank)?.let { message ->
                                Text(message)
                            }
                        }
                    }
                }
            }
        }
    }
}
