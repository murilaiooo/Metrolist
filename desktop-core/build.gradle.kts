plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    sourceSets {
        main {
            kotlin.srcDir(rootProject.file("innertube/src/main/kotlin"))
        }
    }
}

dependencies {
    if (providers.gradleProperty("useMavenLocalInnerTubeX").isPresent) {
        api("com.github.MetrolistGroup:innertubex-desktop:${libs.versions.innertubex.get()}")
    } else {
        api(libs.innertubex.desktop)
    }

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.jna)
    implementation(libs.jna.platform)

    testImplementation(kotlin("test"))
}

tasks.register<JavaExec>("authLoginProbe") {
    group = "verification"
    description = "Runs the interactive YouTube Music login probe and saves the validated session with DPAPI."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.metrolist.desktop.auth.AuthLoginProbeMainKt")
    args(rootProject.projectDir.absolutePath)
}

tasks.register<JavaExec>("streamProbe") {
    group = "verification"
    description = "Resolves one sanitized HIGH-quality audio stream."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.metrolist.desktop.stream.StreamProbeMainKt")
    args(providers.gradleProperty("videoId").orElse("WcHpojJz8Bc").get())
}
