import org.gradle.api.tasks.WriteProperties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val metroListWindowsVersion = providers.gradleProperty("metrolistWindowsVersion").get()
val generatedVersionResources = layout.buildDirectory.dir("generated/metrolistVersionResources")

val generateMetroListVersionResource =
    tasks.register<WriteProperties>("generateMetroListVersionResource") {
        destinationFile.set(
            generatedVersionResources.map {
                it.file("metrolist-version.properties")
            },
        )
        property("version", metroListWindowsVersion)
    }

kotlin {
    jvmToolchain(21)
}

sourceSets["main"].resources.srcDir(generatedVersionResources)

tasks.named("processResources") {
    dependsOn(generateMetroListVersionResource)
}

dependencies {
    implementation(project(":desktop-core"))
    implementation(project(":desktop-export"))
    implementation(libs.coroutines.core)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.metrolist.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            modules("java.sql", "java.net.http")
            packageName = "MetroListWindows"
            packageVersion = metroListWindowsVersion
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


tasks.register<JavaExec>("downloadStageProbe") {
    group = "verification"
    description = "Diagnoses the active download resolver and first HTTP range."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.metrolist.desktop.DownloadStageProbeMainKt")
}
