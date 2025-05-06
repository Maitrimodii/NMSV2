package org.example.constants;

public final class Constants
{

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

    public static final String CONFIG_FILE_PATH = "Config.json";

    //Jwt config
    public static final long DEFAULT_EXPIRATION_MILLIS = 3600000L; // 1 hour

    public static final long DEFAULT_REFRESH_EXPIRATION_MILLIS = 604800000L; // 7 days

    //jwt Key store related constants
    public static final String KEYSTORE_PATH = "keystore.jceks";

    public static final String KEYSTORE_PASSWORD = "secret";

    // HTTP Config Keys

    public static final String HTTP_PORT = "http.port";


    public static final String CREDENTIAL_TABLE = "credentials";

    public static final String CREDENTIAL_MODULE = "credential";

    public static final String DISCOVERY_TABLE = "discoveries";

    public static final String DISCOVERY_MODULE = "discovery";

    public static final String PROVISION_TABLE = "provisions";

    public static final String PROVISION_MODULE = "provision";

    public static final String USER_TABLE = "users";

    public static final String USER_MODULE = "user";

    // Field names
    public static final String FIELD_ID = "id";

    public static final String REQUEST_TYPE = "requestType";

    public static final String DISCOVERY = "Discovery";

    public static final String DISCOVERY_ID = "discovery_id";

    public static final String IP = "ip";

    public static final String PORT = "port";

    public static final String CREDENTIAL_IDS = "credential_ids";

    public static final String CREDENTIAL_ID = "credential_id";

    // Event bus address
    public static final String DISCOVERY_ADDRESS = "discovery.engine";

    // Status and error codes
    public static final String STATUS = "status";

    public static final String SUCCESS = "Success";

    public static final String FAIL = "Fail";


    // schema path

    public static final String CREDENTIAL_SCEHMA = "Schema/CredentialSchema.json";

    public static final String DISCOVERY_SCEHMA = "Schema/DiscoverySchema.json";

    public static final String USER_SCEHMA = "Schema/UserSchema.json";

    public static final String PROVISION_SCEHMA = "Schema/ProvisionSchema.json";

    //HTTP status code

    public static final int HTTP_OK = 200;

    public static final int HTTP_BAD_REQUEST = 400;

    public static final int HTTP_UNAUTHORIZED = 401;

    public static final int HTTP_FORBIDDEN = 403;

    public static final int HTTP_NOT_FOUND = 404;

    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;

}
