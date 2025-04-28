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
    public static final String DEFAULT_DB_HOST = "localhost";
    public static final int DEFAULT_DB_PORT = 5432;
    public static final String DEFAULT_DB_DATABASE = "default_db";
    public static final String DEFAULT_DB_USER = "postgres";
    public static final String DEFAULT_DB_PASSWORD = "password";
    public static final int DEFAULT_DB_POOL_SIZE = 5;
    public static final String CONFIG_FILE_PATH = "config.json";

    //Jwt config
    public static final long DEFAULT_EXPIRATION_MILLIS = 3600000L; // 1 hour

    //jwt Key store related constants
    public static final String KEYSTORE_PATH = "keystore.jceks";
    public static final String KEYSTORE_PASSWORD = "secret";

    // HTTP Config Keys
    public static final String HTTP_PORT = "http.port";


    public static final String CREDENTIAL_TABLE = "credential";

    public static final String CREDENTIAL_MODULE = "credential";

}
