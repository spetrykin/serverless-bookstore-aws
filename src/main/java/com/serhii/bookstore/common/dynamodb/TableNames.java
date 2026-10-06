package com.serhii.bookstore.common.dynamodb;

public final class TableNames {

    public static final String BOOKSTORE =
            System.getenv().getOrDefault("BOOKSTORE_TABLE_NAME", "bookstore");

    private TableNames() {
    }
}
