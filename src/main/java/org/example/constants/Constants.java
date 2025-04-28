package org.example.constants;

public final class Constants {

    // Database Config Keys
    public static final String DB = "db";
    public static final String DB_HOST = "host";
    public static final String DB_PORT = "port";
    public static final String DB_DATABASE = "database";
    public static final String DB_USER = "user";
    public static final String DB_PASSWORD = "password";
    public static final String DB_POOL_SIZE = "poolSize";

    // Default Values
    public static final int DEFAULT_DB_POOL_SIZE = 5;

    // HTTP Config Keys
    public static final String HTTP_PORT = "http.port";

    // Messages
    public static final String SERVER_STARTED = "HTTP server started successfully";
    public static final String SERVER_START_FAILED = "Failed to start server: ";
    public static final String SERVER_SHUTTING_DOWN = "Shutting down server and closing resources";
}
