package com.metrolist.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MetroList Windows",
    ) {
        MaterialTheme {
            Text("MetroList Windows")
        }
    }
}
