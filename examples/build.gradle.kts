import org.gradle.jvm.toolchain.JavaToolchainService

allprojects {
    group = "org.openprojectx.spark.platform.examples"
    version = "0.1.1-SNAPSHOT"
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }

        val toolchains = extensions.getByType<JavaToolchainService>()
        tasks.withType<JavaExec>().configureEach {
            javaLauncher.set(
                toolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(17))
                }
            )
        }
    }
}
