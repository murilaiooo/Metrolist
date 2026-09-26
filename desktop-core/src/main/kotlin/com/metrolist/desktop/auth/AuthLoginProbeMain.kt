package com.metrolist.desktop.auth

import java.nio.file.Path

suspend fun main(args: Array<String>) {
    require(args.size == 1) { "Expected project root path" }

    val projectRoot = Path.of(args[0])
    val helperProject = projectRoot.resolve("desktop-auth-helper").resolve("Metrolist.AuthHelper.csproj")

    val session = AuthHelperLauncher(
        command = listOf(
            "dotnet",
            "run",
            "--project",
            helperProject.toString(),
            "--no-build",
            "--",
        ),
        workingDirectory = projectRoot,
    ).login()

    val validated = AuthSessionValidator().validate(session)

    val localAppData = requireNotNull(System.getenv("LOCALAPPDATA")) {
        "LOCALAPPDATA is not available"
    }
    val sessionPath = Path.of(localAppData, "MetroListWindows", "auth", "session.bin")
    DpapiSessionStore(sessionPath).save(validated.session)

    println("AUTH_OK accounts=${validated.accounts.size}")
}
