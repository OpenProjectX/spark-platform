package org.openprojectx.spark.platform.examples.spark4;

import org.apache.spark.sql.SparkSession;

public final class Spark4SqlExample {
    private Spark4SqlExample() {
    }

    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
                .appName("spark4-sql-example")
                .master("local[*]")
                .getOrCreate();

        try {
            spark.sql("SELECT id, upper(name) AS name FROM VALUES "
                    + "(1, 'spark'), (2, 'platform') AS events(id, name) ORDER BY id")
                    .show(false);
        } finally {
            spark.stop();
        }
    }
}
