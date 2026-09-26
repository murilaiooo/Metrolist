plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":desktop-core"))
    testImplementation(kotlin("test"))
}
