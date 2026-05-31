plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":core"))
    implementation(libs.jibGradlePlugin)
    implementation(libs.tomlj)
    testImplementation(libs.junitJupiter)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.processResources {
    from(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml")) {
        into("org/openprojectx/spark/platform/plugin")
        rename { "spark-platform.versions.toml" }
    }
}

gradlePlugin {
    plugins {
        create("sparkplatform") {
            id = "org.openprojectx.spark.platform"
            implementationClass = "org.openprojectx.spark.platform.plugin.SparkPlatformPlugin"
            displayName = "Spark Platform"
            description = "Spark Platform Gradle plugin"
        }
    }
}
