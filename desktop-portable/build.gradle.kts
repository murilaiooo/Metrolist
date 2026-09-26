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
