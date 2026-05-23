plugins {
    application
    java
    id("org.openprojectx.spark.platform")
}

sparkPlatform {
    line.set("spark4")
    variants.set(listOf("iceberg"))
    platformVersion.set("0.1.0-SNAPSHOT")
}

dependencies {
    sparkPlatform("org.apache.spark:spark-sql_2.13")
    sparkPlatform("org.apache.iceberg:iceberg-spark-runtime-4.0_2.13")
}

application {
    mainClass.set("org.openprojectx.spark.platform.examples.spark4.Spark4IcebergExample")
    applicationDefaultJvmArgs = listOf("--add-opens=java.base/java.nio=ALL-UNNAMED")
}
