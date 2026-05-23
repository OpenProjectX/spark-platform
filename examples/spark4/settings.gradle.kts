pluginManagement {
    includeBuild("../..")

    repositories {
        val isCi = System.getenv().containsKey("CI") ||
                System.getenv().containsKey("GITHUB_ACTIONS") ||
                System.getenv().containsKey("JENKINS_HOME")

        if (!isCi) {
            maven(url = "https://mirrors.tencent.com/nexus/repository/maven-public/")
            maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        }

        gradlePluginPortal()
    }
}

includeBuild("../..") {
    dependencySubstitution {
        substitute(module("org.openprojectx.spark.platform:platform-bom")).using(project(":platform-bom"))
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val isCi = System.getenv().containsKey("CI") ||
                System.getenv().containsKey("GITHUB_ACTIONS") ||
                System.getenv().containsKey("JENKINS_HOME")

        if (!isCi) {
            maven(url = "https://mirrors.tencent.com/nexus/repository/maven-public/")
            maven(url = "https://maven.aliyun.com/repository/public/")
        }

        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "spark-platform-spark4-iceberg-example"

gradle.extra["isCi"] = System.getenv().containsKey("CI") ||
        System.getenv().containsKey("GITHUB_ACTIONS") ||
        System.getenv().containsKey("JENKINS_HOME")
