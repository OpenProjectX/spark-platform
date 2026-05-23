plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
