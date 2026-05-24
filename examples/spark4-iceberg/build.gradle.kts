plugins {
    application
    java
    id("org.openprojectx.spark.platform")
}

sparkPlatform {
    line.set("spark4")
    variants.set(listOf("iceberg"))
    localPlatformImage.set(true)
}

application {
    mainClass.set("org.openprojectx.spark.platform.examples.spark4.Spark4IcebergExample")
}
