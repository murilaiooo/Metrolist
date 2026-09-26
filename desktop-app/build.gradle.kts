plugins {
    kotlin("jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":desktop-core"))
    implementation(project(":desktop-export"))
    implementation(libs.coroutines.core)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.metrolist.desktop.MainKt"
    }
}


tasks.register<JavaExec>("uiDataProbe") {
    group = "verification"
    description = "Checks the real Home, Search, and Library feeds used by the desktop UI."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.metrolist.desktop.UiDataProbeMainKt")
}
