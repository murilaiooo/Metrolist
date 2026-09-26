import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "MetroListWindows"
            packageVersion = "0.1.0"
            vendor = "MetroList Windows"

            appResourcesRootDir.set(
                rootProject.layout.projectDirectory.dir(
                    "runtime-dev/package-resources-current",
                ),
            )

            windows {
                menuGroup = "MetroList Windows"
                shortcut = true
                dirChooser = true
            }
        }
    }
}


tasks.register<JavaExec>("uiDataProbe") {
    group = "verification"
    description = "Checks the real Home, Search, and Library feeds used by the desktop UI."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.metrolist.desktop.UiDataProbeMainKt")
}
