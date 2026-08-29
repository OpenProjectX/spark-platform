plugins {
    application
    java
    id("org.openprojectx.spark.platform")
}

sparkPlatform {
    line.set("spark4")
    localPlatformImage.set(true)
}

dependencies {
    sparkPlatform("org.apache.spark:spark-sql_2.13")
}

application {
    mainClass.set("org.openprojectx.spark.platform.examples.spark4.Spark4SqlExample")
}
