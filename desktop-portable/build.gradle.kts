plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":desktop-core"))
    implementation(libs.ktor.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.register<JavaExec>("portableProbe") {
    group = "verification"
    description = "Creates and validates a one-song MetroList portable backup."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.metrolist.desktop.portable.PortableProbeMainKt")
    args(
        providers.gradleProperty("output").orElse(
            "I:\\codex\\metrolist-windows\\Metrolist_Offline_Downloads_TEST_1_SONG.metrolistbackup",
        ).get(),
    )
}


tasks.register<JavaExec>("queueExportProbe") {
    group = "verification"
    description = "Runs a real persistent queue -> SQLite -> export compatibility probe."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.metrolist.desktop.portable.QueueExportProbeMainKt")
    args(rootProject.projectDir.parentFile.parentFile.absolutePath)
}


tasks.register<JavaExec>("zip64Probe") {
    group = "verification"
    description = "Generates and validates a synthetic >4 GiB logical Zip64 MetroList backup."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.metrolist.desktop.portable.Zip64ProbeMainKt")
    args(
        rootProject.projectDir
            .resolve("runtime-dev")
            .resolve("zip64-probe")
            .resolve("Metrolist_Offline_Downloads_ZIP64_PROBE.metrolistbackup")
            .absolutePath,
    )
}


tasks.register<JavaExec>("finalCarProbe") {
    group = "verification"
    description = "Downloads three real songs and exports a validated final car-test backup."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.metrolist.desktop.portable.FinalCarProbeMainKt")
    args(rootProject.projectDir.absolutePath)
}
