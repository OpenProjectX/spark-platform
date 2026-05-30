package org.openprojectx.spark.platform.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SparkPlatformCatalogTest {
    @Test
    fun `derives bundle names from platform line and variants`() {
        assertEquals("spark-platform-spark5-managed", SparkPlatformCatalog.managedBundle(" Spark5 "))
        assertEquals("spark-platform-spark5-variant-iceberg", SparkPlatformCatalog.variantBundle("spark5", "Iceberg"))
        assertEquals(
            "spark-platform-spark5-variant-hadoopAws",
            SparkPlatformCatalog.variantBundle("spark5", "hadoop_aws")
        )
        assertEquals(
            "spark-platform-spark5-variant-hadoopAws",
            SparkPlatformCatalog.variantBundle("spark5", "hadoop-aws")
        )
        assertEquals(
            "spark-platform-spark5-variant-hadoopAws",
            SparkPlatformCatalog.variantBundle("spark5", "hadoopAws")
        )
        assertEquals(
            "spark-platform-spark5-variant-paimon-managed",
            SparkPlatformCatalog.variantManagedBundle("spark5", "Paimon")
        )
    }

    @Test
    fun `parses variant csv values`() {
        assertEquals(
            listOf("iceberg", "hudi", "hadoopAws"),
            SparkPlatformCatalog.parseVariants("iceberg, hudi,iceberg,hadoop_aws")
        )
    }

    @Test
    fun `derives image tag from platform line variants and version`() {
        assertEquals(
            "spark4-iceberg-hadoopAws-0.1.1-SNAPSHOT",
            SparkPlatformCatalog.imageTag(" Spark4 ", listOf("Iceberg", "hadoop_aws"), "0.1.1-SNAPSHOT")
        )
    }
}
