package org.openprojectx.spark.platform.examples.spark4;

import org.apache.spark.sql.SparkSession;

public final class Spark4IcebergExample {
    private Spark4IcebergExample() {
    }

    public static void main(String[] args) {
        String warehouse = args.length > 0 ? args[0] : "build/warehouse";

        SparkSession spark = SparkSession.builder()
                .appName("spark4-iceberg-example")
                .master("local[*]")
                .config("spark.sql.catalog.local", "org.apache.iceberg.spark.SparkCatalog")
                .config("spark.sql.catalog.local.type", "hadoop")
                .config("spark.sql.catalog.local.warehouse", warehouse)
                .getOrCreate();

        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS local.demo");
            spark.sql("DROP TABLE IF EXISTS local.demo.events");
            spark.sql("""
                    CREATE TABLE local.demo.events (
                        id BIGINT,
                        name STRING
                    ) USING iceberg
                    """);
            spark.sql("INSERT INTO local.demo.events VALUES (1, 'spark4'), (2, 'iceberg')");
            spark.sql("SELECT * FROM local.demo.events ORDER BY id").show(false);
        } finally {
            spark.stop();
        }
    }
}
