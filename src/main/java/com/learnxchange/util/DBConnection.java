package com.learnxchange.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Hands out JDBC connections using settings from db.properties. */
public final class DBConnection {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = DBConnection.class.getResourceAsStream("/db.properties")) {
            if (in == null) throw new IllegalStateException("db.properties not found on classpath");
            PROPS.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DBConnection() { }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(
                PROPS.getProperty("db.url"),
                PROPS.getProperty("db.user"),
                PROPS.getProperty("db.password"));
    }
}
