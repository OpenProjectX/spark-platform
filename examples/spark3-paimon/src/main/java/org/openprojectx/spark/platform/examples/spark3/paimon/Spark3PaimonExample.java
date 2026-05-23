package org.openprojectx.spark.platform.examples.spark3.paimon;

import java.nio.file.Path;

import org.apache.spark.sql.SparkSession;

public class Spark3PaimonExample {
    public static void main(String[] args) {
        String warehouse = args.length > 0
                ? args[0]
                : Path.of("build", "warehouse").toAbsolutePath().toString();

        SparkSession spark = SparkSession.builder()
                .appName("spark3-paimon-example")
                .master("local[*]")
                .config("spark.sql.extensions", "org.apache.paimon.spark.extensions.PaimonSparkSessionExtensions")
                .config("spark.sql.catalog.paimon", "org.apache.paimon.spark.SparkCatalog")
                .config("spark.sql.catalog.paimon.warehouse", warehouse)
                .getOrCreate();

        try {
            spark.sql("CREATE DATABASE IF NOT EXISTS paimon.demo");
            spark.sql("DROP TABLE IF EXISTS paimon.demo.events");
            spark.sql("CREATE TABLE paimon.demo.events (id BIGINT, name STRING) USING paimon");
            spark.sql("INSERT INTO paimon.demo.events VALUES (1, 'spark3'), (2, 'paimon')");
            spark.sql("SELECT * FROM paimon.demo.events ORDER BY id").show(false);
        } finally {
            spark.stop();
        }
    }
}
