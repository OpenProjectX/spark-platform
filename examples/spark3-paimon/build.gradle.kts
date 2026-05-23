plugins {
    application
    java
    id("org.openprojectx.spark.platform")
}

sparkPlatform {
    line.set("spark3")
    variants.set(listOf("paimon"))
    platformVersion.set("0.1.0-SNAPSHOT")
}

dependencies {
    sparkPlatform("org.apache.spark:spark-sql_2.12")
    sparkPlatform("org.apache.paimon:paimon-spark-3.5_2.12")
}

application {
    mainClass.set("org.openprojectx.spark.platform.examples.spark3.paimon.Spark3PaimonExample")
    applicationDefaultJvmArgs = listOf(
        "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED"
    )
}
