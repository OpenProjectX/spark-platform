plugins {
    application
    java
    id("org.openprojectx.spark.platform")
}

sparkPlatform {
    line.set("spark3")
    variants.set(listOf("paimon"))
}

application {
    mainClass.set("org.openprojectx.spark.platform.examples.spark3.paimon.Spark3PaimonExample")
}
